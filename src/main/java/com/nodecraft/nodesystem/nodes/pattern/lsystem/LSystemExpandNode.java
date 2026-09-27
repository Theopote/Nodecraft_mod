package com.nodecraft.nodesystem.nodes.pattern.lsystem;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.core.NodePropertyBindings;
import com.nodecraft.nodesystem.datatypes.LSystemRule;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.DeterministicSeedUtils;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.LSystemRuleUtils;
import com.nodecraft.nodesystem.util.LSystemStringExpander;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "pattern.lsystem.expand",
    displayName = "L-System Expand",
    description = "Expands an L-system axiom using production rules for a fixed number of iterations (longest symbol match; weights are relative)",
    category = "pattern.lsystem",
    order = 1
)
public class LSystemExpandNode extends BaseNode {

    private static final int MIN_RULE_INPUT_COUNT = 1;
    private static final int MAX_RULE_INPUT_COUNT = 20;

    @NodeProperty(displayName = "Iterations", category = "L-System", order = 1,
        description = "Number of parallel rewrite rounds")
    private int iterations = 2;

    @NodeProperty(displayName = "Seed", category = "L-System", order = 2,
        description = "Deterministic seed for weighted rule choice")
    private int seed = DeterministicSeedUtils.DEFAULT_SEED;

    @NodeProperty(displayName = "Rule Input Count", category = "Rules", order = 3,
        description = "Number of individual rule input ports")
    private int ruleInputCount = 3;

    private static final String INPUT_AXIOM_ID = "input_axiom";
    private static final String INPUT_RULES_ID = "input_rules";
    private static final String INPUT_ITERATIONS_ID = "input_iterations";
    private static final String INPUT_SEED_ID = "input_seed";

    private static final String OUTPUT_STRING_ID = "output_string";
    private static final String OUTPUT_ITERATIONS_APPLIED_ID = "output_iterations_applied";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_HIT_LIMIT_ID = "output_hit_limit";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public LSystemExpandNode() {
        super(UUID.randomUUID(), "pattern.lsystem.expand");

        addInputPort(new BasePort(INPUT_AXIOM_ID, "Axiom", "Starting string", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_RULES_ID, "Rules", "Typed list of L-system rules", NodeDataType.L_SYSTEM_RULE_LIST, this));
        addInputPort(new BasePort(INPUT_ITERATIONS_ID, "Iterations", "Rewrite rounds (optional override)", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_SEED_ID, "Seed", "Random seed for probabilistic rule choice", NodeDataType.INTEGER, this));
        rebuildRuleInputPorts();

        addOutputPort(new BasePort(OUTPUT_STRING_ID, "String", "Expanded command string", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_ITERATIONS_APPLIED_ID, "Iterations Applied", "Rewrite rounds actually completed", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when expansion succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_HIT_LIMIT_ID, "Hit Limit", "True when expansion stopped early due to string length cap", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Failure reason when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Expands an L-system axiom using production rules for a fixed number of iterations (longest symbol match; weights are relative)";
    }

    @Override
    public String getDisplayName() {
        return "L-System Expand";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object axiomRaw = inputValues.get(INPUT_AXIOM_ID);
        if (!(axiomRaw instanceof String axiom) || axiom.isEmpty()) {
            writeInvalid("", 0, false, "Axiom is required");
            return;
        }
        if (axiom.length() > GenerationLimits.MAX_LSYSTEM_EXPANDED_LENGTH) {
            writeInvalid("", 0, true, "Axiom exceeds MAX_LSYSTEM_EXPANDED_LENGTH");
            return;
        }

        Integer iters = OptionalPortDrive.resolveOptionalInteger(this, INPUT_ITERATIONS_ID, iterations);
        if (iters == null) {
            writeInvalid("", 0, false, "Iterations connected but invalid");
            return;
        }
        Integer resolvedSeed = OptionalPortDrive.resolveOptionalInteger(this, INPUT_SEED_ID, seed);
        if (resolvedSeed == null) {
            writeInvalid("", 0, false, "Seed connected but invalid");
            return;
        }

        if (iters < 0 || iters > GenerationLimits.MAX_LSYSTEM_ITERATIONS) {
            writeInvalid("", 0, false, "Iterations must be in [0, "
                + GenerationLimits.MAX_LSYSTEM_ITERATIONS + "]");
            return;
        }

        if (iters == 0) {
            writeSuccess(axiom, 0, false);
            return;
        }

        List<LSystemRule> portRules = collectConnectedPortRules();
        if (portRules == null) {
            writeInvalid("", 0, false, "Rule port connected but invalid");
            return;
        }

        List<LSystemRule> listRules;
        if (OptionalPortDrive.isConnected(this, INPUT_RULES_ID)) {
            listRules = LSystemRuleUtils.resolveStrictRuleList(inputValues.get(INPUT_RULES_ID));
            if (listRules == null) {
                writeInvalid("", 0, false, "Rules list connected but invalid");
                return;
            }
        } else {
            listRules = null;
        }

        List<LSystemRule> rules = LSystemRuleUtils.mergeRules(listRules, portRules);
        if (rules.isEmpty()) {
            writeInvalid("", 0, false, "At least one valid rule is required");
            return;
        }
        if (rules.size() > GenerationLimits.MAX_LSYSTEM_RULES) {
            writeInvalid("", 0, false, "Rule count exceeds MAX_LSYSTEM_RULES");
            return;
        }
        if (GenerationLimits.exceedsLSystemRewriteMatchBudget(axiom.length(), rules.size(), iters)) {
            writeInvalid("", 0, false, "L-System rewrite match budget exceeded");
            return;
        }

        LSystemStringExpander.ExpandResult expanded = LSystemStringExpander.expand(
                axiom,
                rules,
                iters,
                resolvedSeed
        );
        if (!expanded.ok()) {
            writeInvalid("", 0, expanded.hitLimit(),
                expanded.error() == null ? "L-System expansion failed" : expanded.error());
            return;
        }

        writeSuccess(expanded.text(), expanded.iterationsApplied(), expanded.hitLimit());
    }

    private void writeSuccess(String text, int iterationsApplied, boolean hitLimit) {
        outputValues.put(OUTPUT_STRING_ID, text);
        outputValues.put(OUTPUT_ITERATIONS_APPLIED_ID, iterationsApplied);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_HIT_LIMIT_ID, hitLimit);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void writeInvalid(String text, int iterationsApplied, boolean hitLimit, String error) {
        outputValues.put(OUTPUT_STRING_ID, text);
        outputValues.put(OUTPUT_ITERATIONS_APPLIED_ID, iterationsApplied);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_HIT_LIMIT_ID, hitLimit);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }

    /**
     * Connection-aware dynamic Rule ports: unconnected skipped; connected must be valid LSystemRule.
     *
     * @return null when a connected port is invalid
     */
    private @Nullable List<LSystemRule> collectConnectedPortRules() {
        List<LSystemRule> rules = new ArrayList<>();
        for (int i = 0; i < ruleInputCount; i++) {
            String portId = ruleInputPortId(i);
            if (!OptionalPortDrive.isConnected(this, portId)) {
                continue;
            }
            Object value = inputValues.get(portId);
            if (!(value instanceof LSystemRule rule) || !LSystemRuleUtils.isValidRule(rule)) {
                return null;
            }
            rules.add(rule);
        }
        return rules;
    }

    private void rebuildRuleInputPorts() {
        inputPorts.removeIf(port -> port.getId().startsWith("input_rule_"));
        for (int i = 0; i < ruleInputCount; i++) {
            addInputPort(new BasePort(
                    ruleInputPortId(i),
                    "Rule " + (i + 1),
                    "Individual L-system rule input " + (i + 1),
                    NodeDataType.L_SYSTEM_RULE,
                    this
            ));
        }
    }

    private static String ruleInputPortId(int index) {
        return "input_rule_" + index;
    }

    public int getRuleInputCount() {
        return ruleInputCount;
    }

    public void setRuleInputCount(int ruleInputCount) {
        int resolved = Math.max(MIN_RULE_INPUT_COUNT, Math.min(MAX_RULE_INPUT_COUNT, ruleInputCount));
        if (this.ruleInputCount != resolved) {
            this.ruleInputCount = resolved;
            rebuildRuleInputPorts();
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("ruleInputCount", ruleInputCount);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        Map<String, Object> typed = new HashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() instanceof String key) {
                typed.put(key, entry.getValue());
            }
        }
        Object countObj = typed.remove("ruleInputCount");
        NodePropertyBindings.deserialize(this, typed);
        if (countObj instanceof Number value) {
            setRuleInputCount(value.intValue());
        }
    }
}

package com.nodecraft.nodesystem.nodes.pattern.lsystem;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.LSystemRule;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.DeterministicSeedUtils;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.LSystemRuleUtils;
import com.nodecraft.nodesystem.util.LSystemStringExpander;
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
    order = 2
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

    public LSystemExpandNode() {
        super(UUID.randomUUID(), "pattern.lsystem.expand");

        addInputPort(new BasePort(INPUT_AXIOM_ID, "Axiom", "Starting string", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_RULES_ID, "Rules", "Typed list of L-system rules", NodeDataType.L_SYSTEM_RULE_LIST, this));
        addInputPort(new BasePort(INPUT_ITERATIONS_ID, "Iterations", "Rewrite rounds (falls back to property)", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_SEED_ID, "Seed", "Random seed for probabilistic rule choice", NodeDataType.INTEGER, this));
        rebuildRuleInputPorts();

        addOutputPort(new BasePort(OUTPUT_STRING_ID, "String", "Expanded command string", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_ITERATIONS_APPLIED_ID, "Iterations Applied", "Rewrite rounds actually completed", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when expansion succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_HIT_LIMIT_ID, "Hit Limit", "True when expansion stopped early due to string length cap", NodeDataType.BOOLEAN, this));
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
        String axiom = inputValues.get(INPUT_AXIOM_ID) instanceof String s ? s : "";
        if (axiom.isEmpty()) {
            writeInvalid("", 0, false);
            return;
        }

        int iters = DeterministicSeedUtils.resolveStrictInteger(inputValues.get(INPUT_ITERATIONS_ID), iterations);
        int resolvedSeed = DeterministicSeedUtils.resolveStrictInteger(inputValues.get(INPUT_SEED_ID), seed);

        if (iters < 0 || iters > GenerationLimits.MAX_LSYSTEM_ITERATIONS) {
            writeInvalid("", 0, false);
            return;
        }

        if (iters == 0) {
            outputValues.put(OUTPUT_STRING_ID, axiom);
            outputValues.put(OUTPUT_ITERATIONS_APPLIED_ID, 0);
            outputValues.put(OUTPUT_VALID_ID, true);
            outputValues.put(OUTPUT_HIT_LIMIT_ID, false);
            return;
        }

        List<LSystemRule> portRules = collectPortRules();
        List<LSystemRule> listRules = LSystemRuleUtils.resolveStrictRuleList(inputValues.get(INPUT_RULES_ID));
        if (inputValues.get(INPUT_RULES_ID) != null && listRules == null) {
            writeInvalid("", 0, false);
            return;
        }

        List<LSystemRule> rules = LSystemRuleUtils.mergeRules(listRules, portRules);
        if (rules.stream().noneMatch(rule -> rule != null && !rule.symbol().trim().isEmpty())) {
            writeInvalid("", 0, false);
            return;
        }

        LSystemStringExpander.ExpandResult expanded = LSystemStringExpander.expand(
                axiom,
                rules,
                iters,
                resolvedSeed
        );
        outputValues.put(OUTPUT_STRING_ID, expanded.text());
        outputValues.put(OUTPUT_ITERATIONS_APPLIED_ID, expanded.iterationsApplied());
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_HIT_LIMIT_ID, expanded.hitLimit());
    }

    private void writeInvalid(String text, int iterationsApplied, boolean hitLimit) {
        outputValues.put(OUTPUT_STRING_ID, text);
        outputValues.put(OUTPUT_ITERATIONS_APPLIED_ID, iterationsApplied);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_HIT_LIMIT_ID, hitLimit);
    }

    private List<LSystemRule> collectPortRules() {
        List<LSystemRule> rules = new ArrayList<>();
        for (int i = 0; i < ruleInputCount; i++) {
            Object value = inputValues.get(ruleInputPortId(i));
            if (value instanceof LSystemRule rule) {
                rules.add(rule);
            }
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
        if (state instanceof Map<?, ?> map && map.get("ruleInputCount") instanceof Number value) {
            setRuleInputCount(value.intValue());
        }
    }
}

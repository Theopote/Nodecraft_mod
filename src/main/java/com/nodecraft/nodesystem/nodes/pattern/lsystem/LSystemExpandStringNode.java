package com.nodecraft.nodesystem.nodes.pattern.lsystem;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.LSystemRule;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.LSystemStringExpander;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "pattern.lsystem.expand_string",
    displayName = "L-System Expand String",
    description = "Expands an L-system axiom using production rules for a fixed number of iterations (longest symbol match; probabilities as weights)",
    category = "pattern.lsystem",
    order = 1
)
public class LSystemExpandStringNode extends BaseNode {

    @NodeProperty(displayName = "Iterations", category = "L-System", order = 1,
        description = "Number of parallel rewrite rounds")
    private int iterations = 2;

    private static final String INPUT_AXIOM_ID = "input_axiom";
    private static final String INPUT_RULES_ID = "input_rules";
    private static final String INPUT_ITERATIONS_ID = "input_iterations";
    private static final String INPUT_SEED_ID = "input_seed";

    private static final String OUTPUT_STRING_ID = "output_string";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_HIT_LIMIT_ID = "output_hit_limit";

    public LSystemExpandStringNode() {
        super(UUID.randomUUID(), "pattern.lsystem.expand_string");

        addInputPort(new BasePort(INPUT_AXIOM_ID, "Axiom", "Starting string", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_RULES_ID, "Rules", "List of LSystemRule entries", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_ITERATIONS_ID, "Iterations", "Rewrite rounds (falls back to property)", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_SEED_ID, "Seed", "Random seed for probabilistic rule choice", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_STRING_ID, "String", "Expanded command string", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when axiom and rules are usable", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_HIT_LIMIT_ID, "Hit Limit", "True when expansion stopped early due to string length cap", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Expands an L-system axiom using production rules for a fixed number of iterations (longest symbol match; probabilities as weights)";
    }

    @Override
    public String getDisplayName() {
        return "L-System Expand String";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object axiomObj = inputValues.get(INPUT_AXIOM_ID);
        Object rulesObj = inputValues.get(INPUT_RULES_ID);
        String axiom = axiomObj instanceof String s ? s : "";
        List<LSystemRule> rules = resolveRules(rulesObj);
        int it = getInputInt(INPUT_ITERATIONS_ID, iterations);
        it = Math.max(0, Math.min(16, it));
        long seed = getInputLong(INPUT_SEED_ID, 0L);

        if (axiom.isEmpty() || rules.isEmpty()) {
            outputValues.put(OUTPUT_STRING_ID, "");
            outputValues.put(OUTPUT_VALID_ID, false);
            outputValues.put(OUTPUT_HIT_LIMIT_ID, false);
            return;
        }

        LSystemStringExpander.ExpandResult expanded = LSystemStringExpander.expand(axiom, rules, it, seed);
        outputValues.put(OUTPUT_STRING_ID, expanded.text());
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_HIT_LIMIT_ID, expanded.hitLimit());
    }

    private static List<LSystemRule> resolveRules(Object rulesObj) {
        if (!(rulesObj instanceof Collection<?> collection)) {
            return List.of();
        }
        List<LSystemRule> out = new ArrayList<>();
        for (Object o : collection) {
            if (o instanceof LSystemRule rule) {
                out.add(rule);
            }
        }
        return out;
    }

    private int getInputInt(String portId, int fallback) {
        Object v = inputValues.get(portId);
        return v instanceof Number n ? n.intValue() : fallback;
    }

    private long getInputLong(String portId, long fallback) {
        Object v = inputValues.get(portId);
        return v instanceof Number n ? n.longValue() : fallback;
    }
}

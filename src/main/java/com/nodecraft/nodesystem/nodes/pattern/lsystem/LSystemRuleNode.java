package com.nodecraft.nodesystem.nodes.pattern.lsystem;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.LSystemRule;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "pattern.lsystem.rule",
    displayName = "L-System Rule",
    description = "Constructs one L-system production rule from symbol, production string, and relative weight",
    category = "pattern.lsystem",
    order = 1
)
public class LSystemRuleNode extends BaseNode {

    @NodeProperty(displayName = "Weight", category = "Rule", order = 1,
        description = "Relative weight when multiple rules share the same symbol")
    private double weight = 1.0d;

    private static final String INPUT_SYMBOL_ID = "input_symbol";
    private static final String INPUT_PRODUCTION_ID = "input_production";
    private static final String INPUT_WEIGHT_ID = "input_weight";

    private static final String OUTPUT_RULE_ID = "output_rule";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public LSystemRuleNode() {
        super(UUID.randomUUID(), "pattern.lsystem.rule");

        addInputPort(new BasePort(INPUT_SYMBOL_ID, "Symbol", "Symbol to rewrite", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_PRODUCTION_ID, "Production", "Replacement string (may be empty)", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_WEIGHT_ID, "Weight", "Relative weight for stochastic choice", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_RULE_ID, "Rule", "Constructed L-system rule", NodeDataType.L_SYSTEM_RULE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when the rule inputs are valid", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Constructs one L-system production rule from symbol, production string, and relative weight";
    }

    @Override
    public String getDisplayName() {
        return "L-System Rule";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        String symbol = readString(inputValues.get(INPUT_SYMBOL_ID));
        String production = readString(inputValues.get(INPUT_PRODUCTION_ID));
        double ruleWeight = readWeight(inputValues.get(INPUT_WEIGHT_ID), weight);

        if (symbol.trim().isEmpty() || !Double.isFinite(ruleWeight) || ruleWeight < 0.0d) {
            writeInvalid();
            return;
        }

        outputValues.put(OUTPUT_RULE_ID, new LSystemRule(symbol, production, ruleWeight));
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_RULE_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private static String readString(@Nullable Object value) {
        return value instanceof String s ? s : "";
    }

    private static double readWeight(@Nullable Object value, double fallback) {
        return value instanceof Number n ? n.doubleValue() : fallback;
    }
}

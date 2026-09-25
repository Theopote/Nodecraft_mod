package com.nodecraft.nodesystem.nodes.math.logic;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Canonical conditional selection node for the v1 logic tree.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.logic.if",
    displayName = "If",
    description = "Selects True Value when Condition is true; otherwise selects False Value.",
    category = "math.logic",
    order = 0
)
public class IfNode extends BaseNode {

    private static final String INPUT_CONDITION_ID = "input_condition";
    private static final String INPUT_TRUE_VALUE_ID = "input_true_value";
    private static final String INPUT_FALSE_VALUE_ID = "input_false_value";
    private static final String OUTPUT_RESULT_ID = "output_result";

    public IfNode() {
        super(UUID.randomUUID(), "math.logic.if");

        addInputPort(new BasePort(INPUT_CONDITION_ID, "Condition",
            "Boolean condition input", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_TRUE_VALUE_ID, "True Value",
            "Value returned when the condition is true", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_FALSE_VALUE_ID, "False Value",
            "Value returned when the condition is false", NodeDataType.ANY, this));

        addOutputPort(new BasePort(OUTPUT_RESULT_ID, "Result",
            "Selected output value", NodeDataType.ANY, this));
    }

    @Override
    public String getDescription() {
        return "Selects True Value when Condition is true; otherwise selects False Value.";
    }

    @Override
    public String getDisplayName() {
        return "If";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        boolean condition = LogicUtils.booleanValue(inputValues.get(INPUT_CONDITION_ID));
        Object result = condition
            ? inputValues.get(INPUT_TRUE_VALUE_ID)
            : inputValues.get(INPUT_FALSE_VALUE_ID);
        outputValues.put(OUTPUT_RESULT_ID, result);
    }
}

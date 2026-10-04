package com.nodecraft.nodesystem.nodes.math.logic;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.math.SelectionResult;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Canonical conditional selection node for the v1 logic tree.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.logic.if",
    displayName = "If",
    description = "Selects True Value when Condition is true; otherwise selects False Value. "
        + "Selects values, not execution paths. Use flow.control.branch for exec branching.",
    category = "math.logic",
    order = 0
)
public class IfNode extends BaseNode {

    private static final String INPUT_CONDITION_ID = "input_condition";
    private static final String INPUT_TRUE_VALUE_ID = "input_true_value";
    private static final String INPUT_FALSE_VALUE_ID = "input_false_value";
    private static final String OUTPUT_RESULT_ID = "output_result";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public IfNode() {
        super(UUID.randomUUID(), "math.logic.if");

        addInputPort(new BasePort(INPUT_CONDITION_ID, "Condition",
            "Boolean condition input", NodeDataType.BOOLEAN, this));
        BasePort trueValue = new BasePort(INPUT_TRUE_VALUE_ID, "True Value",
            "Value returned when the condition is true", NodeDataType.ANY, this);
        trueValue.bindPassthroughType("T");
        addInputPort(trueValue);
        BasePort falseValue = new BasePort(INPUT_FALSE_VALUE_ID, "False Value",
            "Value returned when the condition is false", NodeDataType.ANY, this);
        falseValue.bindPassthroughType("T");
        addInputPort(falseValue);

        BasePort result = new BasePort(OUTPUT_RESULT_ID, "Result",
            "Selected output value", NodeDataType.ANY, this);
        result.bindPassthroughType("T");
        addOutputPort(result);
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "Whether selection succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error",
            "Error message when selection failed", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Selects True Value when Condition is true; otherwise selects False Value. "
            + "Selects values, not execution paths. Use flow.control.branch for exec branching.";
    }

    @Override
    public String getDisplayName() {
        return "If";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        SelectionResult result = LogicUtils.selectIf(
                resolveValue(INPUT_CONDITION_ID),
                isDriven(INPUT_CONDITION_ID),
                resolveValue(INPUT_TRUE_VALUE_ID),
                isConnected(INPUT_TRUE_VALUE_ID),
                resolveValue(INPUT_FALSE_VALUE_ID),
                isConnected(INPUT_FALSE_VALUE_ID)
        );
        emitSelection(result);
    }

    private boolean isDriven(String portId) {
        return OptionalPortDrive.isConnected(this, portId) || inputValues.containsKey(portId);
    }

    private boolean isConnected(String portId) {
        return OptionalPortDrive.isConnected(this, portId);
    }

    private @Nullable Object resolveValue(String portId) {
        if (OptionalPortDrive.isConnected(this, portId)) {
            return getInput(portId);
        }
        return inputValues.get(portId);
    }

    private void emitSelection(SelectionResult result) {
        outputValues.put(OUTPUT_RESULT_ID, result.value());
        outputValues.put(OUTPUT_VALID_ID, result.valid());
        outputValues.put(OUTPUT_ERROR_ID, result.error() == null ? "" : result.error());
    }
}

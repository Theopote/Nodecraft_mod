package com.nodecraft.nodesystem.nodes.input.numeric;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "input.numeric.double_to_integer",
    displayName = "Double To Integer",
    description = "Explicitly converts an exact finite DOUBLE into INTEGER when the value is integral",
    category = "input.numeric",
    order = 21
)
public class DoubleToIntegerNode extends BaseNode {

    private static final String INPUT_VALUE_ID = "input_value";
    private static final String OUTPUT_VALUE_ID = "output_value";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public DoubleToIntegerNode() {
        super(UUID.randomUUID(), "input.numeric.double_to_integer");

        addInputPort(new BasePort(INPUT_VALUE_ID, "Value",
            "Double value to convert", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALUE_ID, "Value",
            "Converted integer value", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when conversion succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error",
            "Failure reason when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object value = inputValues.get(INPUT_VALUE_ID);
        if (!(value instanceof Double d) || !Double.isFinite(d)) {
            outputValues.put(OUTPUT_VALUE_ID, null);
            outputValues.put(OUTPUT_VALID_ID, false);
            outputValues.put(OUTPUT_ERROR_ID, "Value must be exact finite DOUBLE");
            return;
        }
        if (d < Integer.MIN_VALUE || d > Integer.MAX_VALUE || d != Math.rint(d)) {
            outputValues.put(OUTPUT_VALUE_ID, null);
            outputValues.put(OUTPUT_VALID_ID, false);
            outputValues.put(OUTPUT_ERROR_ID, "Double is not an exact integer in INT32 range");
            return;
        }

        outputValues.put(OUTPUT_VALUE_ID, Integer.valueOf((int) d.doubleValue()));
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }
}

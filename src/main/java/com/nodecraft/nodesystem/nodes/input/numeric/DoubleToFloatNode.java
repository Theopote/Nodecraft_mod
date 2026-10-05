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
    id = "input.numeric.double_to_float",
    displayName = "Double To Float",
    description = "Explicitly converts an exact finite DOUBLE into FLOAT when representable",
    category = "input.numeric",
    order = 23
)
public class DoubleToFloatNode extends BaseNode {

    private static final String INPUT_VALUE_ID = "input_value";
    private static final String OUTPUT_VALUE_ID = "output_value";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public DoubleToFloatNode() {
        super(UUID.randomUUID(), "input.numeric.double_to_float");

        addInputPort(new BasePort(INPUT_VALUE_ID, "Value",
            "Double value to convert", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALUE_ID, "Value",
            "Converted float value", NodeDataType.FLOAT, this));
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
        float converted = d.floatValue();
        if (!Float.isFinite(converted) || converted != d) {
            outputValues.put(OUTPUT_VALUE_ID, null);
            outputValues.put(OUTPUT_VALID_ID, false);
            outputValues.put(OUTPUT_ERROR_ID, "Double is not exactly representable as FLOAT");
            return;
        }

        outputValues.put(OUTPUT_VALUE_ID, converted);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }
}

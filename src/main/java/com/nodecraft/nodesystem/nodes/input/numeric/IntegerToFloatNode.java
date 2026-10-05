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
    id = "input.numeric.integer_to_float",
    displayName = "Integer To Float",
    description = "Explicitly converts an INTEGER wire value into an exact FLOAT",
    category = "input.numeric",
    order = 24
)
public class IntegerToFloatNode extends BaseNode {

    private static final String INPUT_VALUE_ID = "input_value";
    private static final String OUTPUT_VALUE_ID = "output_value";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public IntegerToFloatNode() {
        super(UUID.randomUUID(), "input.numeric.integer_to_float");

        addInputPort(new BasePort(INPUT_VALUE_ID, "Value",
            "Integer value to convert", NodeDataType.INTEGER, this));
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
        if (!(value instanceof Integer integer)) {
            outputValues.put(OUTPUT_VALUE_ID, null);
            outputValues.put(OUTPUT_VALID_ID, false);
            outputValues.put(OUTPUT_ERROR_ID, "Value must be INTEGER");
            return;
        }

        outputValues.put(OUTPUT_VALUE_ID, integer.floatValue());
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }
}

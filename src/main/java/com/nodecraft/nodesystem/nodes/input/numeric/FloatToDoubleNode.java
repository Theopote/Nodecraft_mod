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
    id = "input.numeric.float_to_double",
    displayName = "Float To Double",
    description = "Explicitly converts an exact finite FLOAT into DOUBLE",
    category = "input.numeric",
    order = 22
)
public class FloatToDoubleNode extends BaseNode {

    private static final String INPUT_VALUE_ID = "input_value";
    private static final String OUTPUT_VALUE_ID = "output_value";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public FloatToDoubleNode() {
        super(UUID.randomUUID(), "input.numeric.float_to_double");

        addInputPort(new BasePort(INPUT_VALUE_ID, "Value",
            "Float value to convert", NodeDataType.FLOAT, this));
        addOutputPort(new BasePort(OUTPUT_VALUE_ID, "Value",
            "Converted double value", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when conversion succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error",
            "Failure reason when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object value = inputValues.get(INPUT_VALUE_ID);
        if (!(value instanceof Float f) || !Float.isFinite(f)) {
            outputValues.put(OUTPUT_VALUE_ID, null);
            outputValues.put(OUTPUT_VALID_ID, false);
            outputValues.put(OUTPUT_ERROR_ID, "Value must be exact finite FLOAT");
            return;
        }

        outputValues.put(OUTPUT_VALUE_ID, f.doubleValue());
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }
}

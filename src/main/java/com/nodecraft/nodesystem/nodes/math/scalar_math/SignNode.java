package com.nodecraft.nodesystem.nodes.math.scalar_math;

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
    id = "math.scalar_math.sign",
    displayName = "Sign",
    description = "Returns -1, 0, or +1 based on the sign of the input value.",
    category = "math.scalar_math",
    order = 20
)
public class SignNode extends BaseNode {

    private static final String INPUT_VALUE_ID = "input_value";
    private static final String OUTPUT_SIGN_ID = "output_sign";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public SignNode() {
        super(UUID.randomUUID(), "math.scalar_math.sign");

        addInputPort(new BasePort(INPUT_VALUE_ID, "Value", "Input value", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_SIGN_ID, "Sign", "Sign as -1, 0, or +1", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether input is a valid finite number", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDisplayName() {
        return "Sign";
    }

    @Override
    public String getDescription() {
        return "Returns -1, 0, or +1 based on the sign of the input value.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object valueObj = inputValues.get(INPUT_VALUE_ID);
        if (!(valueObj instanceof Number number)) {
            outputValues.put(OUTPUT_SIGN_ID, 0);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        double value = number.doubleValue();
        if (!Double.isFinite(value)) {
            outputValues.put(OUTPUT_SIGN_ID, 0);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        int sign = value > 0.0d ? 1 : (value < 0.0d ? -1 : 0);
        outputValues.put(OUTPUT_SIGN_ID, sign);
        outputValues.put(OUTPUT_VALID_ID, true);
    }
}


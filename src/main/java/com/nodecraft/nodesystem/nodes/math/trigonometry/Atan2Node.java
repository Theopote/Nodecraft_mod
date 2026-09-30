package com.nodecraft.nodesystem.nodes.math.trigonometry;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.math.TrigMathOps;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.trigonometry.atan2",
    displayName = "Atan2",
    description = "Computes the signed angle in degrees from X and Y using atan2(Y, X).",
    category = "math.trigonometry",
    order = 8
)
public class Atan2Node extends TrigScalarNode {

    private static final String INPUT_Y_ID = "input_y";
    private static final String INPUT_X_ID = "input_x";
    private static final String OUTPUT_ANGLE_ID = "output_angle";

    public Atan2Node() {
        super(UUID.randomUUID(), "math.trigonometry.atan2");

        addInputPort(new BasePort(INPUT_Y_ID, "Y", "Y component", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_X_ID, "X", "X component", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_ANGLE_ID, "Angle", "Result of atan2(Y, X) in degrees", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether inputs are valid numeric values", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDisplayName() {
        return "Atan2";
    }

    @Override
    public String getDescription() {
        return "Computes the signed angle in degrees from X and Y using atan2(Y, X).";
    }

    @Override
    public void processNode(ExecutionContext context) {
        Double y = requireInput(INPUT_Y_ID);
        Double x = requireInput(INPUT_X_ID);
        if (y == null || x == null) {
            emitInvalid(OUTPUT_ANGLE_ID);
            return;
        }
        emit(OUTPUT_ANGLE_ID, TrigMathOps.atan2(y, x));
    }
}

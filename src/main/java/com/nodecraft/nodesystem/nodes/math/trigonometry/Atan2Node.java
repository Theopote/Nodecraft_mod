package com.nodecraft.nodesystem.nodes.math.trigonometry;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;

import java.util.UUID;

@NodeInfo(
    id = "math.trigonometry.atan2",
    displayName = "Atan2",
    description = "Computes the signed angle in radians from X and Y using atan2(Y, X).",
    category = "math.trigonometry",
    order = 8
)
public class Atan2Node extends BaseNode {

    private static final String INPUT_Y_ID = "input_y";
    private static final String INPUT_X_ID = "input_x";
    private static final String OUTPUT_ANGLE_ID = "output_angle_rad";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public Atan2Node() {
        super(UUID.randomUUID(), "math.trigonometry.atan2");

        addInputPort(new BasePort(INPUT_Y_ID, "Y", "Y component", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_X_ID, "X", "X component", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_ANGLE_ID, "Angle (rad)", "Result of atan2(Y, X)", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether inputs are valid numeric values", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDisplayName() {
        return "Atan2";
    }

    @Override
    public String getDescription() {
        return "Computes the signed angle in radians from X and Y using atan2(Y, X).";
    }

    @Override
    public void processNode(ExecutionContext context) {
        Object yObj = inputValues.get(INPUT_Y_ID);
        Object xObj = inputValues.get(INPUT_X_ID);

        if (yObj instanceof Number yNum && xObj instanceof Number xNum) {
            double y = yNum.doubleValue();
            double x = xNum.doubleValue();
            outputValues.put(OUTPUT_ANGLE_ID, Math.atan2(y, x));
            outputValues.put(OUTPUT_VALID_ID, true);
            return;
        }

        outputValues.put(OUTPUT_ANGLE_ID, Double.NaN);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}


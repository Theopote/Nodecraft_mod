package com.nodecraft.nodesystem.nodes.math.trigonometry;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    id = "math.trigonometry.tan",
    displayName = "Tangent (Tan)",
    description = "计算角度的正切值（输入为弧度）",
    category = "math.trigonometry",
    order = 2
)
public class TangentNode extends BaseNode {

    private static final String INPUT_ANGLE_ID = "input_angle_rad";
    private static final String OUTPUT_TANGENT_ID = "output_tangent";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public TangentNode() {
        super(UUID.randomUUID(), "math.trigonometry.tan");

        addInputPort(new BasePort(INPUT_ANGLE_ID, "Angle (rad)", "Input angle in radians", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_TANGENT_ID, "Tangent", "Result tan(Angle)", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether input is finite and tangent is defined", NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object val = inputValues.get(INPUT_ANGLE_ID);
        if (!(val instanceof Number number)) {
            outputValues.put(OUTPUT_TANGENT_ID, Double.NaN);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        double angleRad = number.doubleValue();
        if (!Double.isFinite(angleRad)) {
            outputValues.put(OUTPUT_TANGENT_ID, Double.NaN);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        double result = Math.tan(angleRad);
        boolean valid = Double.isFinite(result) && Math.abs(Math.cos(angleRad)) > 1.0e-12d;
        outputValues.put(OUTPUT_TANGENT_ID, valid ? result : Double.NaN);
        outputValues.put(OUTPUT_VALID_ID, valid);
    }

    @Override
    public String getDescription() {
        return "Outputs the tangent of the input angle (in radians).";
    }

    @Override
    public String getDisplayName() {
        return "Tangent (Tan)";
    }
}

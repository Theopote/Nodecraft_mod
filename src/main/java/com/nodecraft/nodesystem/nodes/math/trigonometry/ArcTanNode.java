package com.nodecraft.nodesystem.nodes.math.trigonometry;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    id = "math.trigonometry.atan",
    displayName = "Arctangent (ArcTan)",
    description = "计算输入值的反正切值（结果以弧度为单位）",
    category = "math.trigonometry",
    order = 7
)
public class ArcTanNode extends BaseNode {

    private static final String INPUT_VALUE_ID = "input_value";
    private static final String OUTPUT_ANGLE_ID = "output_angle_rad";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ArcTanNode() {
        super(UUID.randomUUID(), "math.trigonometry.atan");

        addInputPort(new BasePort(INPUT_VALUE_ID, "Value", "Input value", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_ANGLE_ID, "Angle (rad)", "Result atan(Value)", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether input is a valid finite number", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Outputs the arc tangent of the input value (in radians).";
    }

    @Override
    public String getDisplayName() {
        return "Arc Tangent (Atan)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object val = inputValues.get(INPUT_VALUE_ID);
        if (!(val instanceof Number number)) {
            outputValues.put(OUTPUT_ANGLE_ID, Double.NaN);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        double value = number.doubleValue();
        if (!Double.isFinite(value)) {
            outputValues.put(OUTPUT_ANGLE_ID, Double.NaN);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        outputValues.put(OUTPUT_ANGLE_ID, Math.atan(value));
        outputValues.put(OUTPUT_VALID_ID, true);
    }
}

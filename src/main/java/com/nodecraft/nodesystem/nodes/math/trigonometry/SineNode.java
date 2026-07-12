package com.nodecraft.nodesystem.nodes.math.trigonometry;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    id = "math.trigonometry.sin",
    displayName = "Sine (Sin)",
    description = "计算角度的正弦值（输入为弧度）",
    category = "math.trigonometry",
    order = 0
)
public class SineNode extends BaseNode {

    private static final String INPUT_ANGLE_ID = "input_angle_rad";
    private static final String OUTPUT_SINE_ID = "output_sine";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public SineNode() {
        super(UUID.randomUUID(), "math.trigonometry.sin");

        addInputPort(new BasePort(INPUT_ANGLE_ID, "Angle (rad)", "Input angle in radians", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_SINE_ID, "Sine", "Result sin(Angle)", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether input is a valid finite number", NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object val = inputValues.get(INPUT_ANGLE_ID);
        if (!(val instanceof Number number)) {
            outputValues.put(OUTPUT_SINE_ID, Double.NaN);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        double angleRad = number.doubleValue();
        if (!Double.isFinite(angleRad)) {
            outputValues.put(OUTPUT_SINE_ID, Double.NaN);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        double result = Math.sin(angleRad);
        outputValues.put(OUTPUT_SINE_ID, result);
        outputValues.put(OUTPUT_VALID_ID, Double.isFinite(result));
    }

    @Override
    public String getDescription() {
        return "Outputs the sine of the input angle (in radians).";
    }

    @Override
    public String getDisplayName() {
        return "Sine (Sin)";
    }
}

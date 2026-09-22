package com.nodecraft.nodesystem.nodes.math.trigonometry;

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
    id = "math.trigonometry.sin",
    displayName = "Sine (Sin)",
    description = "Computes sine of an angle in degrees.",
    category = "math.trigonometry",
    order = 0
)
public class SineNode extends BaseNode {

    private static final String INPUT_ANGLE_ID = "input_angle";
    private static final String OUTPUT_SINE_ID = "output_sine";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public SineNode() {
        super(UUID.randomUUID(), "math.trigonometry.sin");

        addInputPort(new BasePort(INPUT_ANGLE_ID, "Angle", "Input angle in degrees", NodeDataType.DOUBLE, this));
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

        double angleDeg = number.doubleValue();
        if (!Double.isFinite(angleDeg)) {
            outputValues.put(OUTPUT_SINE_ID, Double.NaN);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        double result = Math.sin(Math.toRadians(angleDeg));
        outputValues.put(OUTPUT_SINE_ID, result);
        outputValues.put(OUTPUT_VALID_ID, Double.isFinite(result));
    }

    @Override
    public String getDescription() {
        return "Outputs the sine of the input angle (in degrees).";
    }

    @Override
    public String getDisplayName() {
        return "Sine (Sin)";
    }
}

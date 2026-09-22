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
    id = "math.trigonometry.cos",
    displayName = "Cosine (Cos)",
    description = "Computes cosine of an angle in degrees.",
    category = "math.trigonometry",
    order = 1
)
public class CosineNode extends BaseNode {

    private static final String INPUT_ANGLE_ID = "input_angle";
    private static final String OUTPUT_COSINE_ID = "output_cosine";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public CosineNode() {
        super(UUID.randomUUID(), "math.trigonometry.cos");

        addInputPort(new BasePort(INPUT_ANGLE_ID, "Angle", "Input angle in degrees", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_COSINE_ID, "Cosine", "Result cos(Angle)", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether input is a valid finite number", NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object val = inputValues.get(INPUT_ANGLE_ID);
        if (!(val instanceof Number number)) {
            outputValues.put(OUTPUT_COSINE_ID, Double.NaN);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        double angleDeg = number.doubleValue();
        if (!Double.isFinite(angleDeg)) {
            outputValues.put(OUTPUT_COSINE_ID, Double.NaN);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        double result = Math.cos(Math.toRadians(angleDeg));
        outputValues.put(OUTPUT_COSINE_ID, result);
        outputValues.put(OUTPUT_VALID_ID, Double.isFinite(result));
    }

    @Override
    public String getDescription() {
        return "Outputs the cosine of the input angle (in degrees).";
    }

    @Override
    public String getDisplayName() {
        return "Cosine (Cos)";
    }
}

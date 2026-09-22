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
    id = "math.trigonometry.atan",
    displayName = "Arctangent (ArcTan)",
    description = "Computes arctangent; result angle is in degrees.",
    category = "math.trigonometry",
    order = 7
)
public class ArcTanNode extends BaseNode {

    private static final String INPUT_VALUE_ID = "input_value";
    private static final String OUTPUT_ANGLE_ID = "output_angle";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ArcTanNode() {
        super(UUID.randomUUID(), "math.trigonometry.atan");

        addInputPort(new BasePort(INPUT_VALUE_ID, "Value", "Input value", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_ANGLE_ID, "Angle", "Result atan(Value) in degrees", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether input is a valid finite number", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Outputs the arctangent of the input value (result in degrees).";
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

        outputValues.put(OUTPUT_ANGLE_ID, Math.toDegrees(Math.atan(value)));
        outputValues.put(OUTPUT_VALID_ID, true);
    }
}

package com.nodecraft.nodesystem.nodes.math.trigonometry;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.math.TrigMathOps;
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
public class ArcTanNode extends TrigScalarNode {

    private static final String INPUT_VALUE_ID = "input_value";
    private static final String OUTPUT_ANGLE_ID = "output_angle";

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
        Double value = requireInput(INPUT_VALUE_ID);
        if (value == null) {
            emitInvalid(OUTPUT_ANGLE_ID);
            return;
        }
        emit(OUTPUT_ANGLE_ID, TrigMathOps.atan(value));
    }
}

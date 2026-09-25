package com.nodecraft.nodesystem.nodes.math.trigonometry;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.math.ScalarResult;
import com.nodecraft.nodesystem.math.TrigMathOps;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.trigonometry.tan",
    displayName = "Tangent (Tan)",
    description = "Computes tangent of an angle in degrees.",
    category = "math.trigonometry",
    order = 2
)
public class TangentNode extends BaseNode {

    private static final String INPUT_ANGLE_ID = "input_angle";
    private static final String OUTPUT_TANGENT_ID = "output_tangent";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public TangentNode() {
        super(UUID.randomUUID(), "math.trigonometry.tan");

        addInputPort(new BasePort(INPUT_ANGLE_ID, "Angle", "Input angle in degrees", NodeDataType.DOUBLE, this));
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

        ScalarResult result = TrigMathOps.tan(number.doubleValue());
        outputValues.put(OUTPUT_TANGENT_ID, result.value());
        outputValues.put(OUTPUT_VALID_ID, result.valid());
    }

    @Override
    public String getDescription() {
        return "Outputs the tangent of the input angle (in degrees).";
    }

    @Override
    public String getDisplayName() {
        return "Tangent (Tan)";
    }
}

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
    id = "math.trigonometry.acos",
    displayName = "Arccosine (ArcCos)",
    description = "Computes arccosine; result angle is in degrees.",
    category = "math.trigonometry",
    order = 6
)
public class ArcCosNode extends BaseNode {

    private static final String INPUT_VALUE_ID = "input_value";
    private static final String OUTPUT_ANGLE_ID = "output_angle";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ArcCosNode() {
        super(UUID.randomUUID(), "math.trigonometry.acos");

        addInputPort(new BasePort(INPUT_VALUE_ID, "Value", "Input value [-1, 1]", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_ANGLE_ID, "Angle", "Result acos(Value) in degrees", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether input is in [-1, 1]", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Outputs the arccosine of the input value (result in degrees).";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object val = inputValues.get(INPUT_VALUE_ID);
        if (!(val instanceof Number number)) {
            outputValues.put(OUTPUT_ANGLE_ID, Double.NaN);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        ScalarResult result = TrigMathOps.acos(number.doubleValue());
        outputValues.put(OUTPUT_ANGLE_ID, result.value());
        outputValues.put(OUTPUT_VALID_ID, result.valid());
    }
}

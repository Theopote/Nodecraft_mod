package com.nodecraft.nodesystem.nodes.math.scalar_math;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.math.ScalarMathOps;
import com.nodecraft.nodesystem.math.ScalarResult;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.scalar_math.smoothstep",
    displayName = "Smoothstep",
    description = "Computes smooth Hermite interpolation 3t^2 - 2t^3 between edge0 and edge1.",
    category = "math.scalar_math",
    order = 19
)
public class SmoothstepNode extends BaseNode {

    private static final String INPUT_VALUE_ID = "input_value";
    private static final String INPUT_EDGE0_ID = "input_edge0";
    private static final String INPUT_EDGE1_ID = "input_edge1";

    private static final String OUTPUT_RESULT_ID = "output_result";
    private static final String OUTPUT_T_ID = "output_t";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public SmoothstepNode() {
        super(UUID.randomUUID(), "math.scalar_math.smoothstep");

        addInputPort(new BasePort(INPUT_VALUE_ID, "Value", "Input value", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_EDGE0_ID, "Edge 0", "First edge (may be greater than Edge 1)", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_EDGE1_ID, "Edge 1", "Second edge (may be less than Edge 0)", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_RESULT_ID, "Result", "Smoothstep result in [0,1]", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_T_ID, "T", "Normalized and clamped parameter", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether inputs are valid and edges are distinct", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDisplayName() {
        return "Smoothstep";
    }

    @Override
    public String getDescription() {
        return "Computes smooth Hermite interpolation 3t^2 - 2t^3 between edge0 and edge1.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object valueObj = inputValues.get(INPUT_VALUE_ID);
        Object edge0Obj = inputValues.get(INPUT_EDGE0_ID);
        Object edge1Obj = inputValues.get(INPUT_EDGE1_ID);

        if (!(valueObj instanceof Number valueNum)
            || !(edge0Obj instanceof Number edge0Num)
            || !(edge1Obj instanceof Number edge1Num)) {
            outputValues.put(OUTPUT_RESULT_ID, Double.NaN);
            outputValues.put(OUTPUT_T_ID, Double.NaN);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        double value = valueNum.doubleValue();
        double edge0 = edge0Num.doubleValue();
        double edge1 = edge1Num.doubleValue();
        ScalarResult result = ScalarMathOps.smoothstep(value, edge0, edge1);
        ScalarResult t = ScalarMathOps.smoothstepT(value, edge0, edge1);
        outputValues.put(OUTPUT_RESULT_ID, result.value());
        outputValues.put(OUTPUT_T_ID, t.value());
        outputValues.put(OUTPUT_VALID_ID, result.valid());
    }
}

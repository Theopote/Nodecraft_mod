package com.nodecraft.nodesystem.nodes.math.scalar_math;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;

import java.util.UUID;

@NodeInfo(
    id = "math.scalar_math.lerp",
    displayName = "Lerp",
    description = "Linearly interpolates between A and B using parameter T.",
    category = "math.scalar_math",
    order = 17
)
public class LerpNode extends BaseNode {

    private static final String INPUT_A_ID = "input_a";
    private static final String INPUT_B_ID = "input_b";
    private static final String INPUT_T_ID = "input_t";
    private static final String OUTPUT_RESULT_ID = "output_result";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public LerpNode() {
        super(UUID.randomUUID(), "math.scalar_math.lerp");

        addInputPort(new BasePort(INPUT_A_ID, "A", "Start value", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_B_ID, "B", "End value", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_T_ID, "T", "Interpolation factor", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_RESULT_ID, "Result", "A + T * (B - A)", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether all inputs are valid numeric values", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDisplayName() {
        return "Lerp";
    }

    @Override
    public String getDescription() {
        return "Linearly interpolates between A and B using parameter T.";
    }

    @Override
    public void processNode(ExecutionContext context) {
        Object aObj = inputValues.get(INPUT_A_ID);
        Object bObj = inputValues.get(INPUT_B_ID);
        Object tObj = inputValues.get(INPUT_T_ID);

        if (!(aObj instanceof Number aNum) || !(bObj instanceof Number bNum) || !(tObj instanceof Number tNum)) {
            outputValues.put(OUTPUT_RESULT_ID, Double.NaN);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        double a = aNum.doubleValue();
        double b = bNum.doubleValue();
        double t = tNum.doubleValue();
        if (!Double.isFinite(a) || !Double.isFinite(b) || !Double.isFinite(t)) {
            outputValues.put(OUTPUT_RESULT_ID, Double.NaN);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        outputValues.put(OUTPUT_RESULT_ID, a + t * (b - a));
        outputValues.put(OUTPUT_VALID_ID, true);
    }
}


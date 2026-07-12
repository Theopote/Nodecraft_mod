package com.nodecraft.nodesystem.nodes.math.scalar_math;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    id = "math.scalar_math.addition",
    displayName = "Addition (+)",
    description = "Adds two numeric inputs.",
    category = "math.scalar_math",
    order = 0
)
public class AdditionNode extends BaseNode implements INode {

    private static final String INPUT_A_ID = "input_a";
    private static final String INPUT_B_ID = "input_b";
    private static final String OUTPUT_SUM_ID = "output_sum";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public AdditionNode() {
        super(UUID.randomUUID(), "math.scalar_math.addition");
        addInputPort(new BasePort(INPUT_A_ID, "A", "First number", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_B_ID, "B", "Second number", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_SUM_ID, "Sum", "Result of A + B", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether both inputs are valid numbers", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Outputs the sum of A and B.";
    }

    @Override
    public String getDisplayName() {
        return "Addition (+)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object valA = inputValues.get(INPUT_A_ID);
        Object valB = inputValues.get(INPUT_B_ID);

        if (valA instanceof Number && valB instanceof Number) {
            double a = ((Number) valA).doubleValue();
            double b = ((Number) valB).doubleValue();
            if (!Double.isFinite(a) || !Double.isFinite(b)) {
                outputValues.put(OUTPUT_SUM_ID, Double.NaN);
                outputValues.put(OUTPUT_VALID_ID, false);
                return;
            }
            double sum = a + b;
            if (!Double.isFinite(sum)) {
                outputValues.put(OUTPUT_SUM_ID, Double.NaN);
                outputValues.put(OUTPUT_VALID_ID, false);
                return;
            }
            outputValues.put(OUTPUT_SUM_ID, sum);
            outputValues.put(OUTPUT_VALID_ID, true);
        } else {
            outputValues.put(OUTPUT_SUM_ID, Double.NaN);
            outputValues.put(OUTPUT_VALID_ID, false);
        }
    }
}

package com.nodecraft.nodesystem.nodes.math.scalar_math;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    id = "math.scalar_math.multiplication",
    displayName = "Multiplication (*)",
    description = "Outputs the product of A and B.",
    category = "math.scalar_math",
    order = 2
)
public class MultiplicationNode extends BaseNode {

    private static final String INPUT_A_ID = "input_a";
    private static final String INPUT_B_ID = "input_b";
    private static final String OUTPUT_PRODUCT_ID = "output_product";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public MultiplicationNode() {
        super(UUID.randomUUID(), "math.scalar_math.multiplication");
        addInputPort(new BasePort(INPUT_A_ID, "A", "Factor A", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_B_ID, "B", "Factor B", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_PRODUCT_ID, "Product", "Result of A * B", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether both inputs are valid numbers", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Outputs the product of A and B.";
    }

    @Override
    public String getDisplayName() {
        return "Multiplication (*)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object valA = inputValues.get(INPUT_A_ID);
        Object valB = inputValues.get(INPUT_B_ID);

        if (valA instanceof Number aNumber && valB instanceof Number bNumber) {
            double result = aNumber.doubleValue() * bNumber.doubleValue();
            outputValues.put(OUTPUT_PRODUCT_ID, result);
            outputValues.put(OUTPUT_VALID_ID, Double.isFinite(result));
        } else {
            outputValues.put(OUTPUT_PRODUCT_ID, Double.NaN);
            outputValues.put(OUTPUT_VALID_ID, false);
        }
    }
}

package com.nodecraft.nodesystem.nodes.reference.vectors;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

/**
 * Computes the dot product of two vectors (A dot B).
 */
@NodeInfo(
    id = "reference.vectors.dot_product",
    displayName = "Dot Product",
    description = "Computes the dot product of vectors A and B.",
    category = "reference.vectors",
    order = 4
)
public class DotProductNode extends BaseNode {

    private static final String INPUT_A_ID = "input_vector_a";
    private static final String INPUT_B_ID = "input_vector_b";

    private static final String OUTPUT_DOT_PRODUCT_ID = "output_dot_product";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public DotProductNode() {
        super(UUID.randomUUID(), "reference.vectors.dot_product");

        addInputPort(new BasePort(INPUT_A_ID, "Vector A", "First vector", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_B_ID, "Vector B", "Second vector", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_DOT_PRODUCT_ID, "Dot Product", "Result A dot B", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether both input vectors are valid",
            NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDisplayName() {
        return "Dot Product";
    }

    @Override
    public String getDescription() {
        return "Computes the dot product of vectors A and B.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d a = VectorUtils.toVector(inputValues.get(INPUT_A_ID));
        Vector3d b = VectorUtils.toVector(inputValues.get(INPUT_B_ID));
        if (!VectorUtils.isFinite(a) || !VectorUtils.isFinite(b)) {
            outputValues.put(OUTPUT_DOT_PRODUCT_ID, Double.NaN);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        outputValues.put(OUTPUT_DOT_PRODUCT_ID, a.dot(b));
        outputValues.put(OUTPUT_VALID_ID, true);
    }
}

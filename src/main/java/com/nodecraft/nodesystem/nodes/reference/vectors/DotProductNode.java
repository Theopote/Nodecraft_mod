package com.nodecraft.nodesystem.nodes.reference.vectors;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.VectorUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "reference.vectors.dot_product",
    displayName = "Dot Product",
    description = "Computes the dot product of vectors A and B.",
    category = "reference.vectors",
    order = 10
)
public class DotProductNode extends BaseNode {

    private static final String INPUT_A_ID = "input_vector_a";
    private static final String INPUT_B_ID = "input_vector_b";

    private static final String OUTPUT_DOT_PRODUCT_ID = "output_dot_product";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public DotProductNode() {
        super(UUID.randomUUID(), "reference.vectors.dot_product");

        addInputPort(new BasePort(INPUT_A_ID, "Vector A", "First vector", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_B_ID, "Vector B", "Second vector", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_DOT_PRODUCT_ID, "Dot Product", "Result A dot B", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether both input vectors are valid",
            NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error",
            "Failure reason when Valid is false", NodeDataType.STRING, this));
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
        if (!VectorUtils.isFinite(a)) {
            writeInvalid("Vector A must be a finite VECTOR");
            return;
        }
        if (!VectorUtils.isFinite(b)) {
            writeInvalid("Vector B must be a finite VECTOR");
            return;
        }

        double dot = VectorUtils.safeDot(a, b);
        if (!VectorUtils.isFinite(dot)) {
            writeInvalid("Dot product is not finite");
            return;
        }

        outputValues.put(OUTPUT_DOT_PRODUCT_ID, dot);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_DOT_PRODUCT_ID, 0.0d);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }
}

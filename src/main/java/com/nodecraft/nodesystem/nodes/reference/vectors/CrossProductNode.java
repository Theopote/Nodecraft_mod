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

/**
 * Computes the cross product of two vectors (A x B).
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "reference.vectors.cross_product",
    displayName = "Cross Product",
    description = "Computes the cross product A x B and its magnitude.",
    category = "reference.vectors",
    order = 11
)
public class CrossProductNode extends BaseNode {

    private static final String INPUT_A_ID = "input_vector_a";
    private static final String INPUT_B_ID = "input_vector_b";

    private static final String OUTPUT_CROSS_PRODUCT_ID = "output_cross_product";
    private static final String OUTPUT_MAGNITUDE_ID = "output_magnitude";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public CrossProductNode() {
        super(UUID.randomUUID(), "reference.vectors.cross_product");

        addInputPort(new BasePort(INPUT_A_ID, "A", "First vector", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_B_ID, "B", "Second vector", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_CROSS_PRODUCT_ID, "Cross Product", "Result A x B", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_MAGNITUDE_ID, "Magnitude", "Length of the cross product", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when both input vectors are finite",
            NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDisplayName() {
        return "Cross Product";
    }

    @Override
    public String getDescription() {
        return "Computes the cross product A x B and its magnitude.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d a = VectorUtils.toVector(inputValues.get(INPUT_A_ID));
        Vector3d b = VectorUtils.toVector(inputValues.get(INPUT_B_ID));
        if (!VectorUtils.isFinite(a) || !VectorUtils.isFinite(b)) {
            writeInvalid();
            return;
        }

        Vector3d cross = new Vector3d(a).cross(b);
        outputValues.put(OUTPUT_CROSS_PRODUCT_ID, cross);
        outputValues.put(OUTPUT_MAGNITUDE_ID, cross.length());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_CROSS_PRODUCT_ID, null);
        outputValues.put(OUTPUT_MAGNITUDE_ID, Double.NaN);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}

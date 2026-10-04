package com.nodecraft.nodesystem.nodes.reference.vectors;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.VectorData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.VectorUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

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
    private static final String OUTPUT_ERROR_ID = "output_error";

    public CrossProductNode() {
        super(UUID.randomUUID(), "reference.vectors.cross_product");

        addInputPort(new BasePort(INPUT_A_ID, "A", "First vector", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_B_ID, "B", "Second vector", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_CROSS_PRODUCT_ID, "Cross Product", "Result A x B", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_MAGNITUDE_ID, "Magnitude", "Length of the cross product", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when both input vectors are finite",
            NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error",
            "Failure reason when Valid is false", NodeDataType.STRING, this));
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
        Vector3d a = VectorUtils.toStrictVectorPortValue(inputValues.get(INPUT_A_ID));
        Vector3d b = VectorUtils.toStrictVectorPortValue(inputValues.get(INPUT_B_ID));
        if (!VectorUtils.isFinite(a)) {
            writeInvalid("Vector A must be a finite VECTOR");
            return;
        }
        if (!VectorUtils.isFinite(b)) {
            writeInvalid("Vector B must be a finite VECTOR");
            return;
        }

        Vector3d cross = VectorUtils.safeCross(a, b);
        VectorData output = VectorUtils.toVectorPort(cross);
        if (output == null) {
            writeInvalid("Cross product is not finite");
            return;
        }

        double magnitude = VectorUtils.safeLength(cross);
        if (!VectorUtils.isFinite(magnitude)) {
            writeInvalid("Cross product magnitude is not finite");
            return;
        }

        outputValues.put(OUTPUT_CROSS_PRODUCT_ID, output);
        outputValues.put(OUTPUT_MAGNITUDE_ID, magnitude);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_CROSS_PRODUCT_ID, null);
        outputValues.put(OUTPUT_MAGNITUDE_ID, 0.0d);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }
}

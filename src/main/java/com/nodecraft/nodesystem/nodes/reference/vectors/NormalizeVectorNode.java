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
 * Normalizes a vector to unit length.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "reference.vectors.normalize_vector",
    displayName = "Normalize Vector",
    description = "Normalizes a vector to unit length.",
    category = "reference.vectors",
    order = 5
)
public class NormalizeVectorNode extends BaseNode {

    private static final String INPUT_VECTOR_ID = "input_vector";

    private static final String OUTPUT_NORMALIZED_ID = "output_normalized_vector";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public NormalizeVectorNode() {
        super(UUID.randomUUID(), "reference.vectors.normalize_vector");

        addInputPort(new BasePort(INPUT_VECTOR_ID, "Vector", "Input vector", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_NORMALIZED_ID, "Normalized", "Normalized vector", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether the input vector can be normalized",
            NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error",
            "Failure reason when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Outputs the normalized (unit length) version of the input vector.";
    }

    @Override
    public String getDisplayName() {
        return "Normalize Vector";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d vector = VectorUtils.toVector(inputValues.get(INPUT_VECTOR_ID));
        if (!VectorUtils.isFinite(vector)) {
            writeInvalid("Vector must be a finite VECTOR");
            return;
        }

        Vector3d normalized = VectorUtils.safeNormalize(vector);
        if (normalized == null) {
            writeInvalid("Vector must be non-zero and normalizable");
            return;
        }

        outputValues.put(OUTPUT_NORMALIZED_ID, VectorUtils.toVectorPort(normalized));
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_NORMALIZED_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }
}

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
 * Normalizes a vector to unit length.
 */
@NodeInfo(
    id = "reference.vectors.normalize_vector",
    displayName = "Normalize Vector",
    description = "Normalizes a vector to unit length.",
    category = "reference.vectors",
    order = 2
)
public class NormalizeVectorNode extends BaseNode {

    private static final String INPUT_VECTOR_ID = "input_vector";

    private static final String OUTPUT_NORMALIZED_ID = "output_normalized_vector";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public NormalizeVectorNode() {
        super(UUID.randomUUID(), "reference.vectors.normalize_vector");

        addInputPort(new BasePort(INPUT_VECTOR_ID, "Vector", "Input vector", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_NORMALIZED_ID, "Normalized", "Normalized vector", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether the input vector can be normalized",
            NodeDataType.BOOLEAN, this));
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
        if (!VectorUtils.isFinite(vector) || vector.lengthSquared() < VectorUtils.EPS) {
            outputValues.put(OUTPUT_NORMALIZED_ID, new Vector3d());
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        outputValues.put(OUTPUT_NORMALIZED_ID, vector.normalize());
        outputValues.put(OUTPUT_VALID_ID, true);
    }
}

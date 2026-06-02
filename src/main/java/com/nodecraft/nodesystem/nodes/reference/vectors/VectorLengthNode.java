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
 * Computes the length (magnitude) of a vector.
 */
@NodeInfo(
    id = "reference.vectors.vector_length",
    displayName = "Vector Length",
    description = "Computes the length (magnitude) of a vector.",
    category = "reference.vectors",
    order = 5
)
public class VectorLengthNode extends BaseNode {

    private static final String INPUT_VECTOR_ID = "input_vector";

    private static final String OUTPUT_LENGTH_ID = "output_length";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public VectorLengthNode() {
        super(UUID.randomUUID(), "reference.vectors.vector_length");

        addInputPort(new BasePort(INPUT_VECTOR_ID, "Vector", "Input vector", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_LENGTH_ID, "Length", "Length of the vector", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether the input vector is valid", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Outputs the length (magnitude) of the input vector.";
    }

    @Override
    public String getDisplayName() {
        return "Vector Length";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d vector = VectorUtils.toVector(inputValues.get(INPUT_VECTOR_ID));
        if (!VectorUtils.isFinite(vector)) {
            outputValues.put(OUTPUT_LENGTH_ID, Double.NaN);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        outputValues.put(OUTPUT_LENGTH_ID, vector.length());
        outputValues.put(OUTPUT_VALID_ID, true);
    }
}

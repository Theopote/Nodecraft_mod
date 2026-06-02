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
 * Outputs the X, Y, and Z components of a vector.
 */
@NodeInfo(
    id = "reference.vectors.deconstruct_vector",
    displayName = "Deconstruct Vector",
    description = "Outputs the X, Y, and Z components of a vector.",
    category = "reference.vectors",
    order = 1
)
public class DeconstructVectorNode extends BaseNode {

    private static final String INPUT_VECTOR_ID = "input_vector";

    private static final String OUTPUT_X_ID = "output_x";
    private static final String OUTPUT_Y_ID = "output_y";
    private static final String OUTPUT_Z_ID = "output_z";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public DeconstructVectorNode() {
        super(UUID.randomUUID(), "reference.vectors.deconstruct_vector");

        addInputPort(new BasePort(INPUT_VECTOR_ID, "Vector", "Input vector", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_X_ID, "X", "X component", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_Y_ID, "Y", "Y component", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_Z_ID, "Z", "Z component", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether the input vector is valid", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Outputs the X, Y, and Z components of the input vector.";
    }

    @Override
    public String getDisplayName() {
        return "Deconstruct Vector";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d vector = VectorUtils.toVector(inputValues.get(INPUT_VECTOR_ID));
        if (!VectorUtils.isFinite(vector)) {
            outputValues.put(OUTPUT_X_ID, Double.NaN);
            outputValues.put(OUTPUT_Y_ID, Double.NaN);
            outputValues.put(OUTPUT_Z_ID, Double.NaN);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        outputValues.put(OUTPUT_X_ID, vector.x);
        outputValues.put(OUTPUT_Y_ID, vector.y);
        outputValues.put(OUTPUT_Z_ID, vector.z);
        outputValues.put(OUTPUT_VALID_ID, true);
    }
}

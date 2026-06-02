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
 * Multiplies a vector by a scalar.
 */
@NodeInfo(
    id = "reference.vectors.vector_scalar_multiply",
    displayName = "Vector Scalar Multiply",
    description = "Multiplies a vector by a scalar.",
    category = "reference.vectors",
    order = 8
)
public class VectorScalarMultiplyNode extends BaseNode {

    private static final String INPUT_VECTOR_ID = "input_vector";
    private static final String INPUT_SCALAR_ID = "input_scalar";

    private static final String OUTPUT_PRODUCT_ID = "output_vector_product";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public VectorScalarMultiplyNode() {
        super(UUID.randomUUID(), "reference.vectors.vector_scalar_multiply");

        addInputPort(new BasePort(INPUT_VECTOR_ID, "Vector", "Input vector", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_SCALAR_ID, "Scalar", "Scalar value", NodeDataType.ANY, this));

        addOutputPort(new BasePort(OUTPUT_PRODUCT_ID, "Scaled Vector", "Result V * s", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether vector and scalar inputs are valid",
            NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Outputs the vector multiplied by the scalar.";
    }

    @Override
    public String getDisplayName() {
        return "Vector Scale (*)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d vector = VectorUtils.toVector(inputValues.get(INPUT_VECTOR_ID));
        Object scalarObj = inputValues.get(INPUT_SCALAR_ID);
        if (!VectorUtils.isFinite(vector) || !(scalarObj instanceof Number scalarNumber)) {
            writeInvalid();
            return;
        }

        double scalar = scalarNumber.doubleValue();
        if (!VectorUtils.isFinite(scalar)) {
            writeInvalid();
            return;
        }

        outputValues.put(OUTPUT_PRODUCT_ID, vector.mul(scalar));
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_PRODUCT_ID, new Vector3d());
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}

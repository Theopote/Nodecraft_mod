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
 * Divides a vector by a scalar.
 */
@NodeInfo(
    id = "reference.vectors.vector_scalar_divide",
    displayName = "Vector Scalar Divide",
    description = "Divides a vector by a scalar.",
    category = "reference.vectors",
    order = 9
)
public class VectorScalarDivideNode extends BaseNode {

    private static final String INPUT_VECTOR_ID = "input_vector";
    private static final String INPUT_SCALAR_ID = "input_scalar";

    private static final String OUTPUT_QUOTIENT_ID = "output_vector_quotient";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public VectorScalarDivideNode() {
        super(UUID.randomUUID(), "reference.vectors.vector_scalar_divide");

        addInputPort(new BasePort(INPUT_VECTOR_ID, "Vector", "Input vector (dividend)", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_SCALAR_ID, "Scalar", "Scalar value (divisor)", NodeDataType.ANY, this));

        addOutputPort(new BasePort(OUTPUT_QUOTIENT_ID, "Scaled Vector", "Result V / s", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether division input is valid",
            NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Outputs the vector divided by the scalar.";
    }

    @Override
    public String getDisplayName() {
        return "Vector Scale (/)";
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
        if (!VectorUtils.isFinite(scalar) || Math.abs(scalar) < VectorUtils.EPS) {
            writeInvalid();
            return;
        }

        outputValues.put(OUTPUT_QUOTIENT_ID, vector.mul(1.0d / scalar));
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_QUOTIENT_ID, new Vector3d());
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}

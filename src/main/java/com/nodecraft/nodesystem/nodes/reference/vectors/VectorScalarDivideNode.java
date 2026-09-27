package com.nodecraft.nodesystem.nodes.reference.vectors;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.VectorData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.StrictDoubleUtils;
import com.nodecraft.nodesystem.util.VectorUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
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
    private static final String OUTPUT_ERROR_ID = "output_error";

    public VectorScalarDivideNode() {
        super(UUID.randomUUID(), "reference.vectors.vector_scalar_divide");

        addInputPort(new BasePort(INPUT_VECTOR_ID, "Vector", "Input vector (dividend)", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_SCALAR_ID, "Scalar", "Scalar value (divisor)", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_QUOTIENT_ID, "Scaled Vector", "Result V / s", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether division input is valid",
            NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error",
            "Failure reason when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Outputs the vector divided by the scalar.";
    }

    @Override
    public String getDisplayName() {
        return "Vector Scalar Divide";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d vector = VectorUtils.toVector(inputValues.get(INPUT_VECTOR_ID));
        Double scalar = StrictDoubleUtils.requireExactFiniteDouble(inputValues.get(INPUT_SCALAR_ID));

        if (!VectorUtils.isFinite(vector)) {
            writeInvalid("Vector must be a finite VECTOR");
            return;
        }
        if (scalar == null) {
            writeInvalid("Scalar must be exact finite DOUBLE");
            return;
        }
        if (Math.abs(scalar) < VectorUtils.EPS) {
            writeInvalid("Scalar magnitude must be greater than EPS");
            return;
        }

        Vector3d result = VectorUtils.safeScale(vector, 1.0d / scalar);
        VectorData output = VectorUtils.toVectorPort(result);
        if (output == null) {
            writeInvalid("Divided vector is not finite");
            return;
        }

        outputValues.put(OUTPUT_QUOTIENT_ID, output);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_QUOTIENT_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }
}

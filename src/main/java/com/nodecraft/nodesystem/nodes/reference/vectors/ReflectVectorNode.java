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
    id = "reference.vectors.reflect",
    displayName = "Reflect Vector",
    description = "Reflects an input vector around a normal vector using v - 2(v·n)n.",
    category = "reference.vectors",
    order = 15
)
public class ReflectVectorNode extends BaseNode {

    private static final String INPUT_VECTOR_ID = "input_vector";
    private static final String INPUT_NORMAL_ID = "input_normal";

    private static final String OUTPUT_REFLECTED_ID = "output_reflected";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public ReflectVectorNode() {
        super(UUID.randomUUID(), "reference.vectors.reflect");

        addInputPort(new BasePort(INPUT_VECTOR_ID, "Vector", "Incoming direction vector", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_NORMAL_ID, "Normal", "Surface normal (will be normalized)", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_REFLECTED_ID, "Reflected", "Reflected vector", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether reflection input is valid", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error",
            "Failure reason when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDisplayName() {
        return "Reflect Vector";
    }

    @Override
    public String getDescription() {
        return "Reflects an input vector around a normal vector using v - 2(v·n)n.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d v = VectorUtils.toVector(inputValues.get(INPUT_VECTOR_ID));
        Vector3d n = VectorUtils.toVector(inputValues.get(INPUT_NORMAL_ID));

        if (!VectorUtils.isFinite(v)) {
            writeInvalid("Vector must be a finite VECTOR");
            return;
        }
        if (!VectorUtils.isFinite(n)) {
            writeInvalid("Normal must be a finite VECTOR");
            return;
        }

        Vector3d nn = VectorUtils.safeNormalize(n);
        if (nn == null) {
            writeInvalid("Normal must be non-zero and normalizable");
            return;
        }

        double dot = VectorUtils.safeDot(v, nn);
        if (!VectorUtils.isFinite(dot)) {
            writeInvalid("Reflection dot product is not finite");
            return;
        }

        double scale = 2.0d * dot;
        if (!VectorUtils.isFinite(scale)) {
            writeInvalid("Reflection scale is not finite");
            return;
        }

        Vector3d reflected = VectorUtils.safeSubtract(v, VectorUtils.safeScale(nn, scale));
        VectorData output = VectorUtils.toVectorPort(reflected);
        if (output == null) {
            writeInvalid("Reflected vector is not finite");
            return;
        }

        outputValues.put(OUTPUT_REFLECTED_ID, output);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_REFLECTED_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }
}

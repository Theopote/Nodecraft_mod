package com.nodecraft.nodesystem.nodes.reference.vectors;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    id = "reference.vectors.reflect",
    displayName = "Reflect Vector",
    description = "Reflects an input vector around a normal vector using v - 2(v·n)n.",
    category = "reference.vectors",
    order = 13
)
public class ReflectVectorNode extends BaseNode {

    private static final String INPUT_VECTOR_ID = "input_vector";
    private static final String INPUT_NORMAL_ID = "input_normal";

    private static final String OUTPUT_REFLECTED_ID = "output_reflected";
    private static final String OUTPUT_NORMALIZED_NORMAL_ID = "output_normalized_normal";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ReflectVectorNode() {
        super(UUID.randomUUID(), "reference.vectors.reflect");

        addInputPort(new BasePort(INPUT_VECTOR_ID, "Vector", "Incoming direction vector", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_NORMAL_ID, "Normal", "Surface normal (will be normalized)", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_REFLECTED_ID, "Reflected", "Reflected vector", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_NORMALIZED_NORMAL_ID, "Normalized Normal", "Normalized normal used for reflection", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether reflection input is valid", NodeDataType.BOOLEAN, this));
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
        if (!VectorUtils.isFinite(v) || !VectorUtils.isFinite(n) || n.lengthSquared() < VectorUtils.EPS) {
            outputValues.put(OUTPUT_REFLECTED_ID, new Vector3d());
            outputValues.put(OUTPUT_NORMALIZED_NORMAL_ID, new Vector3d(0.0d, 1.0d, 0.0d));
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        Vector3d nn = new Vector3d(n).normalize();
        double scale = 2.0d * v.dot(nn);
        Vector3d reflected = new Vector3d(v).sub(new Vector3d(nn).mul(scale));

        outputValues.put(OUTPUT_REFLECTED_ID, reflected);
        outputValues.put(OUTPUT_NORMALIZED_NORMAL_ID, nn);
        outputValues.put(OUTPUT_VALID_ID, true);
    }
}

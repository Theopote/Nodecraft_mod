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
    id = "reference.vectors.project",
    displayName = "Project Vector onto Vector",
    description = "Projects vector A onto vector B as (A·B / |B|^2)B.",
    category = "reference.vectors",
    order = 14
)
public class ProjectVectorNode extends BaseNode {

    private static final String INPUT_A_ID = "input_a";
    private static final String INPUT_B_ID = "input_b";

    private static final String OUTPUT_PROJECTION_ID = "output_projection";
    private static final String OUTPUT_REJECTION_ID = "output_rejection";
    private static final String OUTPUT_SCALE_ID = "output_scale";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ProjectVectorNode() {
        super(UUID.randomUUID(), "reference.vectors.project");

        addInputPort(new BasePort(INPUT_A_ID, "A", "Vector to decompose", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_B_ID, "B", "Target axis vector", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_PROJECTION_ID, "Projection", "Projection of A onto B", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_REJECTION_ID, "Rejection", "Component orthogonal to B", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_SCALE_ID, "Scale", "Scalar coefficient (A·B / |B|^2)", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether projection input is valid", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDisplayName() {
        return "Project Vector onto Vector";
    }

    @Override
    public String getDescription() {
        return "Projects vector A onto vector B as (A·B / |B|^2)B.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d a = VectorUtils.toVector(inputValues.get(INPUT_A_ID));
        Vector3d b = VectorUtils.toVector(inputValues.get(INPUT_B_ID));
        if (!VectorUtils.isFinite(a) || !VectorUtils.isFinite(b)) {
            outputValues.put(OUTPUT_PROJECTION_ID, new Vector3d());
            outputValues.put(OUTPUT_REJECTION_ID, new Vector3d());
            outputValues.put(OUTPUT_SCALE_ID, Double.NaN);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        double bLenSq = b.lengthSquared();
        if (bLenSq < VectorUtils.EPS) {
            outputValues.put(OUTPUT_PROJECTION_ID, new Vector3d());
            outputValues.put(OUTPUT_REJECTION_ID, new Vector3d(a));
            outputValues.put(OUTPUT_SCALE_ID, Double.NaN);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        double scale = a.dot(b) / bLenSq;
        Vector3d projection = new Vector3d(b).mul(scale);
        Vector3d rejection = new Vector3d(a).sub(projection);

        outputValues.put(OUTPUT_PROJECTION_ID, projection);
        outputValues.put(OUTPUT_REJECTION_ID, rejection);
        outputValues.put(OUTPUT_SCALE_ID, scale);
        outputValues.put(OUTPUT_VALID_ID, true);
    }
}

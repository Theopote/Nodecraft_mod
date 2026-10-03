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
    id = "reference.vectors.project",
    displayName = "Project Vector onto Vector",
    description = "Projects vector A onto vector B as (A·B / |B|^2)B.",
    category = "reference.vectors",
    order = 16
)
public class ProjectVectorNode extends BaseNode {

    private static final String INPUT_A_ID = "input_a";
    private static final String INPUT_B_ID = "input_b";

    private static final String OUTPUT_PROJECTION_ID = "output_projection";
    private static final String OUTPUT_REJECTION_ID = "output_rejection";
    private static final String OUTPUT_SCALE_ID = "output_scale";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public ProjectVectorNode() {
        super(UUID.randomUUID(), "reference.vectors.project");

        addInputPort(new BasePort(INPUT_A_ID, "A", "Vector to decompose", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_B_ID, "B", "Target axis vector", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_PROJECTION_ID, "Projection", "Projection of A onto B", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_REJECTION_ID, "Rejection", "Component orthogonal to B", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_SCALE_ID, "Scale", "Scalar coefficient (A·B / |B|^2)", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether projection input is valid", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error",
            "Failure reason when Valid is false", NodeDataType.STRING, this));
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
        if (!VectorUtils.isFinite(a)) {
            writeInvalid("Vector A must be a finite VECTOR");
            return;
        }
        if (!VectorUtils.isFinite(b)) {
            writeInvalid("Vector B must be a finite VECTOR");
            return;
        }

        double lenB = VectorUtils.safeLength(b);
        if (!VectorUtils.isFinite(lenB) || lenB <= VectorUtils.EPS) {
            writeInvalid("Vector B must be non-zero");
            return;
        }

        Vector3d unitB = VectorUtils.normalizeByLength(b, lenB);
        if (unitB == null) {
            writeInvalid("Target axis unit vector is not finite");
            return;
        }

        double scaleAlongUnit = VectorUtils.safeDot(a, unitB);
        if (!VectorUtils.isFinite(scaleAlongUnit)) {
            writeInvalid("Projection scalar along axis is not finite");
            return;
        }

        Vector3d projection = VectorUtils.safeScale(unitB, scaleAlongUnit);
        VectorData projectionOut = VectorUtils.toVectorPort(projection);
        if (projectionOut == null) {
            writeInvalid("Projection vector is not finite");
            return;
        }

        Vector3d rejection = VectorUtils.safeSubtract(a, projection);
        VectorData rejectionOut = VectorUtils.toVectorPort(rejection);
        if (rejectionOut == null) {
            writeInvalid("Rejection vector is not finite");
            return;
        }

        double scale = scaleAlongUnit / lenB;
        if (!VectorUtils.isFinite(scale)) {
            writeInvalid("Projection scale is not finite");
            return;
        }

        outputValues.put(OUTPUT_PROJECTION_ID, projectionOut);
        outputValues.put(OUTPUT_REJECTION_ID, rejectionOut);
        outputValues.put(OUTPUT_SCALE_ID, scale);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_PROJECTION_ID, null);
        outputValues.put(OUTPUT_REJECTION_ID, null);
        outputValues.put(OUTPUT_SCALE_ID, 0.0d);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }
}

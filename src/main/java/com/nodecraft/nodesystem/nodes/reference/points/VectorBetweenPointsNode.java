package com.nodecraft.nodesystem.nodes.reference.points;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.VectorData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.PointUtils;
import com.nodecraft.nodesystem.util.VectorUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "reference.points.vector_between_points",
    displayName = "Vector Between Points",
    description = "Computes the displacement vector from one geometric point to another (To − From)",
    category = "reference.points",
    order = 10
)
public class VectorBetweenPointsNode extends BaseNode {

    private static final String INPUT_FROM_ID = "input_from";
    private static final String INPUT_TO_ID = "input_to";

    private static final String OUTPUT_VECTOR_ID = "output_vector";
    private static final String OUTPUT_LENGTH_ID = "output_length";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public VectorBetweenPointsNode() {
        super(UUID.randomUUID(), "reference.points.vector_between_points");

        addInputPort(new BasePort(INPUT_FROM_ID, "From",
            "Start geometric point",
            NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_TO_ID, "To",
            "End geometric point",
            NodeDataType.POINT, this));

        addOutputPort(new BasePort(OUTPUT_VECTOR_ID, "Vector",
            "Displacement vector from From to To", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_LENGTH_ID, "Length",
            "Distance between From and To", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when both input points are valid", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error",
            "Failure reason when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDisplayName() {
        return "Vector Between Points";
    }

    @Override
    public String getDescription() {
        return "Computes the displacement vector from one geometric point to another (To − From)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d from = PointUtils.toPointPosition(inputValues.get(INPUT_FROM_ID));
        Vector3d to = PointUtils.toPointPosition(inputValues.get(INPUT_TO_ID));

        if (!PointUtils.isFinite(from)) {
            writeInvalid("From must be a finite POINT");
            return;
        }
        if (!PointUtils.isFinite(to)) {
            writeInvalid("To must be a finite POINT");
            return;
        }

        Vector3d vector = PointUtils.safeDisplacement(from, to);
        if (vector == null) {
            writeInvalid("Displacement vector is not finite");
            return;
        }

        double length = PointUtils.safeDistance(from, to);
        if (!PointUtils.isFinite(length)) {
            writeInvalid("Vector length is not finite");
            return;
        }

        VectorData output = VectorUtils.toVectorPort(vector);
        if (output == null) {
            writeInvalid("Displacement vector is not finite");
            return;
        }

        outputValues.put(OUTPUT_VECTOR_ID, output);
        outputValues.put(OUTPUT_LENGTH_ID, length);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_VECTOR_ID, null);
        outputValues.put(OUTPUT_LENGTH_ID, 0.0d);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }
}

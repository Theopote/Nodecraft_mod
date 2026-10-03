package com.nodecraft.nodesystem.nodes.reference.points;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.PointUtils;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import com.nodecraft.nodesystem.util.StrictDoubleUtils;
import com.nodecraft.nodesystem.util.VectorUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "reference.points.point_along_vector",
    displayName = "Move Point Along Direction",
    description = "Moves a start point along a direction vector by a distance (direction is always normalized)",
    category = "reference.points",
    order = 7
)
public class PointAlongVectorNode extends BaseNode {

    private static final String INPUT_POINT_ID = "input_point";
    private static final String INPUT_VECTOR_ID = "input_vector";
    private static final String INPUT_DISTANCE_ID = "input_distance";

    private static final String OUTPUT_POINT_ID = "output_point";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public PointAlongVectorNode() {
        super(UUID.randomUUID(), "reference.points.point_along_vector");

        addInputPort(new BasePort(INPUT_POINT_ID, "Point",
            "Start geometric point",
            NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_VECTOR_ID, "Direction",
            "Direction vector; normalized before applying distance",
            NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_DISTANCE_ID, "Distance",
            "Distance to move along the direction. Negative values move in the opposite direction.",
            NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_POINT_ID, "Point",
            "Resulting point after moving along the direction", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when point, direction, and distance inputs are valid", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error",
            "Failure reason when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDisplayName() {
        return "Move Point Along Direction";
    }

    @Override
    public String getDescription() {
        return "Moves a start point along a direction vector by a distance (direction is always normalized)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d point = PointUtils.toPointPosition(inputValues.get(INPUT_POINT_ID));
        Vector3d direction = SpatialValueResolver.resolveVector(inputValues.get(INPUT_VECTOR_ID));
        Double distance = StrictDoubleUtils.requireExactFiniteDouble(inputValues.get(INPUT_DISTANCE_ID));

        if (!PointUtils.isFinite(point)) {
            writeInvalid("Point must be a finite POINT");
            return;
        }
        if (!PointUtils.isFinite(direction)) {
            writeInvalid("Direction must be a finite VECTOR");
            return;
        }
        if (distance == null) {
            writeInvalid("Distance must be an exact finite DOUBLE");
            return;
        }

        Vector3d unitDirection = VectorUtils.safeNormalize(direction);
        if (unitDirection == null) {
            writeInvalid("Direction must be finite and non-zero");
            return;
        }

        Vector3d displacement = VectorUtils.safeScale(unitDirection, distance);
        Vector3d result = VectorUtils.safeAdd(point, displacement);
        if (result == null) {
            writeInvalid("Resulting point is not finite");
            return;
        }

        outputValues.put(OUTPUT_POINT_ID, new PointData(result));
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_POINT_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }
}

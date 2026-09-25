package com.nodecraft.nodesystem.nodes.reference.points;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "reference.points.point_along_vector",
    displayName = "Move Point Along Direction",
    description = "Moves a start point along a direction vector by a distance (direction is always normalized)",
    category = "reference.points",
    order = 2
)
public class PointAlongVectorNode extends BaseNode {

    private static final String INPUT_POINT_ID = "input_point";
    private static final String INPUT_VECTOR_ID = "input_vector";
    private static final String INPUT_DISTANCE_ID = "input_distance";

    private static final String OUTPUT_POINT_ID = "output_point";
    private static final String OUTPUT_VALID_ID = "output_valid";

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
        Object vectorObj = inputValues.get(INPUT_VECTOR_ID);
        Object distanceObj = inputValues.get(INPUT_DISTANCE_ID);

        if (!PointUtils.isFinite(point) || !(vectorObj instanceof Vector3d inputVector)
            || !PointUtils.isFinite(inputVector) || !(distanceObj instanceof Number number)) {
            outputValues.put(OUTPUT_POINT_ID, null);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        Vector3d direction = new Vector3d(inputVector);
        if (direction.lengthSquared() <= PointUtils.EPS) {
            outputValues.put(OUTPUT_POINT_ID, null);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }
        direction.normalize();

        double distance = number.doubleValue();
        if (!PointUtils.isFinite(distance)) {
            outputValues.put(OUTPUT_POINT_ID, null);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        Vector3d result = new Vector3d(point).fma(distance, direction);
        outputValues.put(OUTPUT_POINT_ID, new PointData(result));
        outputValues.put(OUTPUT_VALID_ID, true);
    }
}

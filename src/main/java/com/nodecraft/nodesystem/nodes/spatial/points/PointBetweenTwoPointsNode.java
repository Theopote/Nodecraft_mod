package com.nodecraft.nodesystem.nodes.spatial.points;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "spatial.points.point_between_two_points",
    displayName = "Point Between Two Points",
    description = "Interpolates a point between A and B using a parameter t where 0 is A and 1 is B",
    category = "spatial.points"
)
public class PointBetweenTwoPointsNode extends BaseNode {

    private static final String INPUT_POINT_A_ID = "input_point_a";
    private static final String INPUT_POINT_B_ID = "input_point_b";
    private static final String INPUT_T_ID = "input_t";

    private static final String OUTPUT_POINT_ID = "output_point";
    private static final String OUTPUT_VECTOR_ID = "output_vector";
    private static final String OUTPUT_DIRECTION_ID = "output_direction";
    private static final String OUTPUT_DISTANCE_ID = "output_distance";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public PointBetweenTwoPointsNode() {
        super(UUID.randomUUID(), "spatial.points.point_between_two_points");

        addInputPort(new BasePort(INPUT_POINT_A_ID, "Point A",
            "Start point. Supports Point, Vector, Position, or Block Coordinate.",
            NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_POINT_B_ID, "Point B",
            "End point. Supports Point, Vector, Position, or Block Coordinate.",
            NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_T_ID, "T",
            "Interpolation parameter. 0 = A, 1 = B, 0.5 = midpoint.",
            NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_POINT_ID, "Point",
            "Interpolated point between A and B", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_VECTOR_ID, "Vector",
            "Interpolated point as a Vector3d position", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_DIRECTION_ID, "Direction",
            "Normalized direction vector from A toward B", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_DISTANCE_ID, "Distance",
            "Distance between A and B", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when both points and the interpolation parameter are valid", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDisplayName() {
        return "Point Between Two Points";
    }

    @Override
    public String getDescription() {
        return "Interpolates a point between A and B using a parameter t where 0 is A and 1 is B";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d pointA = resolvePoint(inputValues.get(INPUT_POINT_A_ID));
        Vector3d pointB = resolvePoint(inputValues.get(INPUT_POINT_B_ID));
        Object tObj = inputValues.get(INPUT_T_ID);

        if (pointA == null || pointB == null || !(tObj instanceof Number number)) {
            outputValues.put(OUTPUT_POINT_ID, null);
            outputValues.put(OUTPUT_VECTOR_ID, null);
            outputValues.put(OUTPUT_DIRECTION_ID, null);
            outputValues.put(OUTPUT_DISTANCE_ID, 0.0D);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        double t = number.doubleValue();
        Vector3d delta = new Vector3d(pointB).sub(pointA);
        double distance = delta.length();

        Vector3d direction = null;
        if (distance > 1.0E-12D) {
            direction = new Vector3d(delta).normalize();
        }

        Vector3d result = new Vector3d(
            pointA.x + (pointB.x - pointA.x) * t,
            pointA.y + (pointB.y - pointA.y) * t,
            pointA.z + (pointB.z - pointA.z) * t
        );

        outputValues.put(OUTPUT_POINT_ID, new PointData(result));
        outputValues.put(OUTPUT_VECTOR_ID, result);
        outputValues.put(OUTPUT_DIRECTION_ID, direction);
        outputValues.put(OUTPUT_DISTANCE_ID, distance);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    @Override
    public Object getNodeState() {
        return new HashMap<String, Object>();
    }

    @Override
    public void setNodeState(Object state) {
        // stateless
    }

    private Vector3d resolvePoint(Object value) {
        if (value instanceof PointData pointData) {
            return pointData.getPosition();
        }
        if (value instanceof Vector3d vector) {
            return new Vector3d(vector);
        }
        if (value instanceof BlockPos blockPos) {
            return new Vector3d(blockPos.getX(), blockPos.getY(), blockPos.getZ());
        }
        return null;
    }
}

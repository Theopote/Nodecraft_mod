package com.nodecraft.nodesystem.nodes.reference.points;

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
    id = "reference.points.point_along_vector",
    displayName = "Point Along Vector",
    description = "Creates a new point by moving a start point along a direction vector by a distance",
    category = "reference.points"
)
public class PointAlongVectorNode extends BaseNode {

    private static final String INPUT_POINT_ID = "input_point";
    private static final String INPUT_VECTOR_ID = "input_vector";
    private static final String INPUT_DISTANCE_ID = "input_distance";

    private static final String OUTPUT_POINT_ID = "output_point";
    private static final String OUTPUT_VECTOR_ID = "output_vector";
    private static final String OUTPUT_DIRECTION_ID = "output_direction";
    private static final String OUTPUT_VALID_ID = "output_valid";

    private boolean normalizeDirection = true;

    public PointAlongVectorNode() {
        super(UUID.randomUUID(), "reference.points.point_along_vector");

        addInputPort(new BasePort(INPUT_POINT_ID, "Point",
            "Start point. Supports Point, Vector, Position, or Block Coordinate.",
            NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_VECTOR_ID, "Vector",
            "Direction vector used to move the point",
            NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_DISTANCE_ID, "Distance",
            "Distance to move along the vector. Negative values move in the opposite direction.",
            NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_POINT_ID, "Point",
            "Resulting point after moving along the vector", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_VECTOR_ID, "Vector",
            "Resulting point as a Vector3d position", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_DIRECTION_ID, "Direction",
            "Direction vector actually used for the move", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when point, vector, and distance inputs are valid", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDisplayName() {
        return "Point Along Vector";
    }

    @Override
    public String getDescription() {
        return "Creates a new point by moving a start point along a direction vector by a distance";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d point = resolvePoint(inputValues.get(INPUT_POINT_ID));
        Object vectorObj = inputValues.get(INPUT_VECTOR_ID);
        Object distanceObj = inputValues.get(INPUT_DISTANCE_ID);

        if (point == null || !(vectorObj instanceof Vector3d inputVector) || !(distanceObj instanceof Number number)) {
            outputValues.put(OUTPUT_POINT_ID, null);
            outputValues.put(OUTPUT_VECTOR_ID, null);
            outputValues.put(OUTPUT_DIRECTION_ID, null);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        Vector3d direction = new Vector3d(inputVector);
        if (direction.lengthSquared() <= 1.0E-12D) {
            outputValues.put(OUTPUT_POINT_ID, null);
            outputValues.put(OUTPUT_VECTOR_ID, null);
            outputValues.put(OUTPUT_DIRECTION_ID, null);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        if (normalizeDirection) {
            direction.normalize();
        }

        double distance = number.doubleValue();
        Vector3d result = new Vector3d(point).fma(distance, direction);

        outputValues.put(OUTPUT_POINT_ID, new PointData(result));
        outputValues.put(OUTPUT_VECTOR_ID, result);
        outputValues.put(OUTPUT_DIRECTION_ID, direction);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    public boolean isNormalizeDirection() {
        return normalizeDirection;
    }

    public void setNormalizeDirection(boolean normalizeDirection) {
        this.normalizeDirection = normalizeDirection;
        markDirty();
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("normalizeDirection", normalizeDirection);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Map<?, ?> stateMap) {
            Object normalize = stateMap.get("normalizeDirection");
            if (normalize instanceof Boolean enabled) {
                setNormalizeDirection(enabled);
            }
        }
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

package com.nodecraft.nodesystem.nodes.world.query;

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

/**
 * Checks whether a geometric point already lies on the integer block grid.
 */
@NodeInfo(
    id = "world.query.is_grid_point",
    displayName = "Is Grid Point",
    description = "Checks whether a geometric point already lies on the block grid without snapping",
    category = "world.query"
)
public class IsGridPointNode extends BaseNode {

    private static final String INPUT_POINT_ID = "input_point";

    private static final String OUTPUT_IS_GRID_POINT_ID = "output_is_grid_point";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_NEAREST_COORDINATE_ID = "output_nearest_coordinate";
    private static final String OUTPUT_OFFSET_VECTOR_ID = "output_offset_vector";
    private static final String OUTPUT_DISTANCE_ID = "output_distance";

    private double tolerance = 1.0E-6D;

    public IsGridPointNode() {
        super(UUID.randomUUID(), "world.query.is_grid_point");

        addInputPort(new BasePort(INPUT_POINT_ID, "Point",
            "Point to test against the integer block grid. Supports Point, Vector, Position, or Block Coordinate.",
            NodeDataType.ANY, this));

        addOutputPort(new BasePort(OUTPUT_IS_GRID_POINT_ID, "Is Grid Point",
            "True when the point lies on an integer block grid position", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when the input could be resolved to a geometric point", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_NEAREST_COORDINATE_ID, "Nearest Coordinate",
            "Nearest integer block coordinate", NodeDataType.BLOCK_POS, this));
        addOutputPort(new BasePort(OUTPUT_OFFSET_VECTOR_ID, "Offset Vector",
            "Vector from the nearest integer grid coordinate to the point", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_DISTANCE_ID, "Distance",
            "Distance from the point to the nearest integer grid coordinate", NodeDataType.DOUBLE, this));
    }

    @Override
    public String getDisplayName() {
        return "Is Grid Point";
    }

    @Override
    public String getDescription() {
        return "Checks whether a geometric point already lies on the block grid without snapping";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d point = resolvePoint(inputValues.get(INPUT_POINT_ID));
        if (point == null) {
            outputValues.put(OUTPUT_IS_GRID_POINT_ID, false);
            outputValues.put(OUTPUT_VALID_ID, false);
            outputValues.put(OUTPUT_NEAREST_COORDINATE_ID, BlockPos.ORIGIN);
            outputValues.put(OUTPUT_OFFSET_VECTOR_ID, new Vector3d());
            outputValues.put(OUTPUT_DISTANCE_ID, 0.0D);
            return;
        }

        int nearestX = (int) Math.round(point.x);
        int nearestY = (int) Math.round(point.y);
        int nearestZ = (int) Math.round(point.z);

        BlockPos nearest = new BlockPos(nearestX, nearestY, nearestZ);
        Vector3d offset = new Vector3d(
            point.x - nearestX,
            point.y - nearestY,
            point.z - nearestZ
        );
        double distance = offset.length();
        boolean isGridPoint = distance <= Math.max(0.0D, tolerance);

        outputValues.put(OUTPUT_IS_GRID_POINT_ID, isGridPoint);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_NEAREST_COORDINATE_ID, nearest);
        outputValues.put(OUTPUT_OFFSET_VECTOR_ID, offset);
        outputValues.put(OUTPUT_DISTANCE_ID, distance);
    }

    public double getTolerance() {
        return tolerance;
    }

    public void setTolerance(double tolerance) {
        this.tolerance = Math.max(0.0D, tolerance);
        markDirty();
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("tolerance", tolerance);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Map<?, ?> stateMap) {
            Object toleranceObj = stateMap.get("tolerance");
            if (toleranceObj instanceof Number number) {
                setTolerance(number.doubleValue());
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

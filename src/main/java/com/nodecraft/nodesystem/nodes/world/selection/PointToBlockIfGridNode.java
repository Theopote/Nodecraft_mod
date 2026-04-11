package com.nodecraft.nodesystem.nodes.world.selection;

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
    id = "world.selection.point_to_block_if_grid",
    displayName = "Point To Block If Grid",
    description = "Strict conversion: outputs a block coordinate only when the point is already grid-aligned",
    category = "world.selection"
)
public class PointToBlockIfGridNode extends BaseNode {

    private static final String INPUT_POINT_ID = "input_point";

    private static final String OUTPUT_COORDINATE_ID = "output_coordinate";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_IS_GRID_POINT_ID = "output_is_grid_point";
    private static final String OUTPUT_DISTANCE_ID = "output_distance";
    private static final String OUTPUT_OFFSET_VECTOR_ID = "output_offset_vector";

    private double tolerance = 1.0E-6D;

    public PointToBlockIfGridNode() {
        super(UUID.randomUUID(), "world.selection.point_to_block_if_grid");

        addInputPort(new BasePort(INPUT_POINT_ID, "Point",
            "Point to convert without snapping. Supports Point, Vector, Position, or Block Coordinate.",
            NodeDataType.ANY, this));

        addOutputPort(new BasePort(OUTPUT_COORDINATE_ID, "Coordinate",
            "Block coordinate only when the point already lies on the integer grid", NodeDataType.BLOCK_POS, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when the input could be resolved to a geometric point", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_IS_GRID_POINT_ID, "Is Grid Point",
            "True when the point lies on the integer grid within tolerance", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_DISTANCE_ID, "Distance",
            "Distance from the point to the nearest integer grid coordinate", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_OFFSET_VECTOR_ID, "Offset Vector",
            "Vector from the nearest integer grid coordinate to the point", NodeDataType.VECTOR, this));
    }

    @Override
    public String getDisplayName() {
        return "Point To Block If Grid";
    }

    @Override
    public String getDescription() {
        return "Strict conversion: outputs a block coordinate only when the point is already grid-aligned";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d point = resolvePoint(inputValues.get(INPUT_POINT_ID));
        if (point == null) {
            outputValues.put(OUTPUT_COORDINATE_ID, null);
            outputValues.put(OUTPUT_VALID_ID, false);
            outputValues.put(OUTPUT_IS_GRID_POINT_ID, false);
            outputValues.put(OUTPUT_DISTANCE_ID, 0.0D);
            outputValues.put(OUTPUT_OFFSET_VECTOR_ID, new Vector3d());
            return;
        }

        int nearestX = (int) Math.round(point.x);
        int nearestY = (int) Math.round(point.y);
        int nearestZ = (int) Math.round(point.z);

        Vector3d offset = new Vector3d(
            point.x - nearestX,
            point.y - nearestY,
            point.z - nearestZ
        );
        double distance = offset.length();
        boolean isGridPoint = distance <= Math.max(0.0D, tolerance);

        outputValues.put(OUTPUT_COORDINATE_ID, isGridPoint ? new BlockPos(nearestX, nearestY, nearestZ) : null);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_IS_GRID_POINT_ID, isGridPoint);
        outputValues.put(OUTPUT_DISTANCE_ID, distance);
        outputValues.put(OUTPUT_OFFSET_VECTOR_ID, offset);
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

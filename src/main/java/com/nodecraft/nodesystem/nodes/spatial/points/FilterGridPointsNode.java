package com.nodecraft.nodesystem.nodes.spatial.points;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "spatial.points.filter_grid_points",
    displayName = "Filter Grid Points",
    description = "Splits a point list into grid-aligned points and off-grid points",
    category = "spatial.points"
)
public class FilterGridPointsNode extends BaseNode {

    private static final String INPUT_POINTS_ID = "input_points";

    private static final String OUTPUT_GRID_POINTS_ID = "output_grid_points";
    private static final String OUTPUT_GRID_BLOCKS_ID = "output_grid_blocks";
    private static final String OUTPUT_OFF_GRID_POINTS_ID = "output_off_grid_points";
    private static final String OUTPUT_GRID_COUNT_ID = "output_grid_count";
    private static final String OUTPUT_OFF_GRID_COUNT_ID = "output_off_grid_count";
    private static final String OUTPUT_VALID_COUNT_ID = "output_valid_count";
    private static final String OUTPUT_SKIPPED_COUNT_ID = "output_skipped_count";

    private double tolerance = 1.0E-6D;

    public FilterGridPointsNode() {
        super(UUID.randomUUID(), "spatial.points.filter_grid_points");

        addInputPort(new BasePort(INPUT_POINTS_ID, "Points",
            "Collection of Point, Vector, Position, or Block Coordinate values to classify",
            NodeDataType.LIST, this));

        addOutputPort(new BasePort(OUTPUT_GRID_POINTS_ID, "Grid Points",
            "Points that already lie on integer grid positions", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_GRID_BLOCKS_ID, "Grid Blocks",
            "Grid-aligned points converted to block coordinates", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_OFF_GRID_POINTS_ID, "Off-Grid Points",
            "Points that do not lie on integer grid positions", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_GRID_COUNT_ID, "Grid Count",
            "Number of points already on the integer grid", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_OFF_GRID_COUNT_ID, "Off-Grid Count",
            "Number of points not on the integer grid", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_COUNT_ID, "Valid Count",
            "Number of inputs that were successfully resolved to points", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_SKIPPED_COUNT_ID, "Skipped Count",
            "Number of inputs skipped because they were not valid points", NodeDataType.INTEGER, this));
    }

    @Override
    public String getDisplayName() {
        return "Filter Grid Points";
    }

    @Override
    public String getDescription() {
        return "Splits a point list into grid-aligned points and off-grid points";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object value = inputValues.get(INPUT_POINTS_ID);
        if (!(value instanceof Collection<?> values)) {
            outputValues.put(OUTPUT_GRID_POINTS_ID, new ArrayList<>());
            outputValues.put(OUTPUT_GRID_BLOCKS_ID, new BlockPosList());
            outputValues.put(OUTPUT_OFF_GRID_POINTS_ID, new ArrayList<>());
            outputValues.put(OUTPUT_GRID_COUNT_ID, 0);
            outputValues.put(OUTPUT_OFF_GRID_COUNT_ID, 0);
            outputValues.put(OUTPUT_VALID_COUNT_ID, 0);
            outputValues.put(OUTPUT_SKIPPED_COUNT_ID, 0);
            return;
        }

        List<PointData> gridPoints = new ArrayList<>();
        BlockPosList gridBlocks = new BlockPosList();
        List<PointData> offGridPoints = new ArrayList<>();
        int validCount = 0;
        int skippedCount = 0;

        for (Object entry : values) {
            Vector3d point = resolvePoint(entry);
            if (point == null) {
                skippedCount++;
                continue;
            }

            validCount++;
            int nearestX = (int) Math.round(point.x);
            int nearestY = (int) Math.round(point.y);
            int nearestZ = (int) Math.round(point.z);

            double dx = point.x - nearestX;
            double dy = point.y - nearestY;
            double dz = point.z - nearestZ;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

            PointData pointData = new PointData(point);
            if (distance <= Math.max(0.0D, tolerance)) {
                gridPoints.add(pointData);
                gridBlocks.add(new BlockPos(nearestX, nearestY, nearestZ));
            } else {
                offGridPoints.add(pointData);
            }
        }

        outputValues.put(OUTPUT_GRID_POINTS_ID, gridPoints);
        outputValues.put(OUTPUT_GRID_BLOCKS_ID, gridBlocks);
        outputValues.put(OUTPUT_OFF_GRID_POINTS_ID, offGridPoints);
        outputValues.put(OUTPUT_GRID_COUNT_ID, gridPoints.size());
        outputValues.put(OUTPUT_OFF_GRID_COUNT_ID, offGridPoints.size());
        outputValues.put(OUTPUT_VALID_COUNT_ID, validCount);
        outputValues.put(OUTPUT_SKIPPED_COUNT_ID, skippedCount);
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

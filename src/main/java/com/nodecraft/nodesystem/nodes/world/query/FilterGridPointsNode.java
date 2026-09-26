package com.nodecraft.nodesystem.nodes.world.query;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.BlockSpace;
import com.nodecraft.nodesystem.util.PointUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "world.query.filter_grid_points",
    displayName = "Filter Grid Points",
    description = "Splits a point list into cell-center grid points and off-grid points without snapping",
    category = "world.query",
    order = 1
)
public class FilterGridPointsNode extends BaseNode {

    private static final String INPUT_POINTS_ID = "input_points";

    private static final String OUTPUT_GRID_POINTS_ID = "output_grid_points";
    private static final String OUTPUT_GRID_BLOCKS_ID = "output_grid_blocks";
    private static final String OUTPUT_OFF_GRID_POINTS_ID = "output_off_grid_points";
    private static final String OUTPUT_GRID_COUNT_ID = "output_grid_count";
    private static final String OUTPUT_OFF_GRID_COUNT_ID = "output_off_grid_count";
    private static final String OUTPUT_VALID_COUNT_ID = "output_valid_count";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    private double tolerance = 1.0E-6D;

    public FilterGridPointsNode() {
        super(UUID.randomUUID(), "world.query.filter_grid_points");

        addInputPort(new BasePort(INPUT_POINTS_ID, "Points",
            "Point list to classify against the block cell-center grid",
            NodeDataType.POINT_LIST, this));

        addOutputPort(new BasePort(OUTPUT_GRID_POINTS_ID, "Grid Points",
            "Points that lie on block cell-center grid positions", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_GRID_BLOCKS_ID, "Grid Blocks",
            "Grid-aligned points as block cell indices", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_OFF_GRID_POINTS_ID, "Off-Grid Points",
            "Points that do not lie on block cell-center grid positions", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_GRID_COUNT_ID, "Grid Count",
            "Number of points on the cell-center grid", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_OFF_GRID_COUNT_ID, "Off-Grid Count",
            "Number of points not on the cell-center grid", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_COUNT_ID, "Valid Count",
            "Number of input points successfully resolved", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether the point list was valid", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Error message when classification fails", NodeDataType.STRING, this));
    }

    @Override
    public String getDisplayName() {
        return "Filter Grid Points";
    }

    @Override
    public String getDescription() {
        return "Splits a point list into cell-center grid points and off-grid points without snapping";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> points = PointUtils.resolveStrictPointList(inputValues.get(INPUT_POINTS_ID));
        if (points == null) {
            writeFailure("Points input must be a non-empty POINT_LIST with finite PointData entries.");
            return;
        }

        List<PointData> gridPoints = new ArrayList<>();
        BlockPosList gridBlocks = new BlockPosList();
        List<PointData> offGridPoints = new ArrayList<>();

        for (Vector3d point : points) {
            if (BlockSpace.isCellCenter(point, tolerance)) {
                gridPoints.add(new PointData(point));
                gridBlocks.add(BlockSpace.nearestCellBlockPos(point));
            } else {
                offGridPoints.add(new PointData(point));
            }
        }

        outputValues.put(OUTPUT_GRID_POINTS_ID, gridPoints);
        outputValues.put(OUTPUT_GRID_BLOCKS_ID, gridBlocks);
        outputValues.put(OUTPUT_OFF_GRID_POINTS_ID, offGridPoints);
        outputValues.put(OUTPUT_GRID_COUNT_ID, gridPoints.size());
        outputValues.put(OUTPUT_OFF_GRID_COUNT_ID, offGridPoints.size());
        outputValues.put(OUTPUT_VALID_COUNT_ID, points.size());
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void writeFailure(String error) {
        outputValues.put(OUTPUT_GRID_POINTS_ID, List.of());
        outputValues.put(OUTPUT_GRID_BLOCKS_ID, new BlockPosList());
        outputValues.put(OUTPUT_OFF_GRID_POINTS_ID, List.of());
        outputValues.put(OUTPUT_GRID_COUNT_ID, 0);
        outputValues.put(OUTPUT_OFF_GRID_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
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
}

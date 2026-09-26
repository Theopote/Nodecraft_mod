package com.nodecraft.nodesystem.nodes.pattern.grid;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "pattern.grid.staggered_grid",
    displayName = "Staggered Grid",
    description = "Generates staggered grid anchor points with parity-controlled row offsets",
    category = "pattern.grid",
    order = 2
)
public class StaggeredGridNode extends BaseNode {
    public enum RowParityMode {
        OFFSET_ODD_ROWS,
        OFFSET_EVEN_ROWS
    }

    @NodeProperty(displayName = "Row Parity Mode", category = "Pattern", order = 1)
    private RowParityMode rowParityMode = RowParityMode.OFFSET_ODD_ROWS;

    @NodeProperty(displayName = "Alternate Row Height", category = "Pattern", order = 2)
    private double alternateRowHeight = 0.0d;

    private static final String INPUT_ORIGIN_ID = "input_origin";
    private static final String INPUT_STEP_DIRECTION_ID = "input_step_direction";
    private static final String INPUT_ROW_DIRECTION_ID = "input_row_direction";
    private static final String INPUT_STEP_DISTANCE_ID = "input_step_distance";
    private static final String INPUT_ROW_DISTANCE_ID = "input_row_distance";
    private static final String INPUT_STAGGER_OFFSET_ID = "input_stagger_offset";
    private static final String INPUT_STEP_COUNT_ID = "input_step_count";
    private static final String INPUT_ROW_COUNT_ID = "input_row_count";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public StaggeredGridNode() {
        super(UUID.randomUUID(), "pattern.grid.staggered_grid");
        addInputPort(new BasePort(INPUT_ORIGIN_ID, "Origin", "Grid origin anchor point", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_STEP_DIRECTION_ID, "Step Direction", "Direction for each repeated step", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_ROW_DIRECTION_ID, "Row Direction", "Direction for each row", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_STEP_DISTANCE_ID, "Step Distance", "Distance between repeated steps", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_ROW_DISTANCE_ID, "Row Distance", "Distance between rows", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_STAGGER_OFFSET_ID, "Stagger Offset", "Offset applied to staggered rows", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_STEP_COUNT_ID, "Step Count", "Total copies per row (including offset 0)", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_ROW_COUNT_ID, "Row Count", "Total number of rows (including offset 0)", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Staggered grid anchor points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of emitted anchor points", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a staggered grid was generated", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Generates staggered grid anchor points with parity-controlled row offsets";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d origin = SpatialValueResolver.resolvePoint(inputValues.get(INPUT_ORIGIN_ID));
        if (origin == null) {
            origin = new Vector3d(0.0d, 0.0d, 0.0d);
        }

        int requestedStepCount = getInteger(INPUT_STEP_COUNT_ID, 5);
        int requestedRowCount = getInteger(INPUT_ROW_COUNT_ID, 3);
        if (requestedStepCount <= 0 || requestedRowCount <= 0) {
            writeEmpty();
            return;
        }

        Vector3d stepDir = resolveDirection(inputValues.get(INPUT_STEP_DIRECTION_ID), new Vector3d(1, 0, 0));
        Vector3d rowDir = resolveDirection(inputValues.get(INPUT_ROW_DIRECTION_ID), new Vector3d(0, 0, 1));
        if (stepDir == null || rowDir == null) {
            writeEmpty();
            return;
        }

        double stepDistance = getDouble(INPUT_STEP_DISTANCE_ID, 1.0d);
        double rowDistance = getDouble(INPUT_ROW_DISTANCE_ID, 1.0d);
        double staggerOffset = getDouble(INPUT_STAGGER_OFFSET_ID, stepDistance * 0.5d);
        if (!Double.isFinite(stepDistance) || !Double.isFinite(rowDistance) || !Double.isFinite(staggerOffset)) {
            writeEmpty();
            return;
        }

        GenerationLimits.GridAxisCounts gridCounts = GenerationLimits.clampExclusiveGridCounts(
            requestedStepCount,
            requestedRowCount,
            1,
            1
        );
        int stepCount = gridCounts.xCount();
        int rowCount = gridCounts.yCount();

        Vector3d stepVec = new Vector3d(stepDir).mul(stepDistance);
        Vector3d rowVec = new Vector3d(rowDir).mul(rowDistance);
        Vector3d staggerVec = new Vector3d(stepDir).mul(staggerOffset);

        List<Vector3d> points = new ArrayList<>(stepCount * rowCount);
        for (int row = 0; row < rowCount; row++) {
            Vector3d rowOffset = new Vector3d(rowVec).mul(row);
            if (shouldOffsetRow(row)) {
                rowOffset.add(staggerVec);
            }
            if ((row & 1) == 1 && Math.abs(alternateRowHeight) > 1.0e-9d) {
                rowOffset.y += alternateRowHeight;
            }
            for (int step = 0; step < stepCount; step++) {
                Vector3d offset = new Vector3d(stepVec).mul(step).add(rowOffset);
                points.add(new Vector3d(origin).add(offset));
            }
        }

        outputValues.put(OUTPUT_POINTS_ID, SpatialValueResolver.toPointDataList(points));
        outputValues.put(OUTPUT_COUNT_ID, points.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private boolean shouldOffsetRow(int row) {
        boolean oddRow = (row & 1) == 1;
        return (rowParityMode == RowParityMode.OFFSET_ODD_ROWS) == oddRow;
    }

    private @Nullable Vector3d resolveDirection(Object value, Vector3d fallback) {
        Vector3d direction = SpatialValueResolver.resolveVector(value);
        if (direction == null) {
            return new Vector3d(fallback);
        }
        if (!Double.isFinite(direction.x) || !Double.isFinite(direction.y) || !Double.isFinite(direction.z)
                || direction.lengthSquared() < 1.0e-12d) {
            return null;
        }
        return direction.normalize();
    }

    private double getDouble(String portId, double fallback) {
        Object v = inputValues.get(portId);
        return v instanceof Number n ? n.doubleValue() : fallback;
    }

    private int getInteger(String portId, int fallback) {
        Object v = inputValues.get(portId);
        return v instanceof Integer i ? i : fallback;
    }

    private void writeEmpty() {
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}

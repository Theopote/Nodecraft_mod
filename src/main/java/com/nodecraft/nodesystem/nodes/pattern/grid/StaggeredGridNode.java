package com.nodecraft.nodesystem.nodes.pattern.grid;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "pattern.grid.staggered_grid",
    displayName = "Staggered Grid",
    description = "Generates staggered grid anchor points with parity-controlled row offsets",
    category = "pattern.grid",
    order = 2
)
public class StaggeredGridNode extends AbstractPatternGridNode {
    public enum RowParityMode {
        OFFSET_ODD_ROWS,
        OFFSET_EVEN_ROWS
    }

    @NodeProperty(displayName = "Row Parity Mode", category = "Pattern", order = 1)
    private RowParityMode rowParityMode = RowParityMode.OFFSET_ODD_ROWS;

    @NodeProperty(displayName = "Alternate Row Height", category = "Pattern", order = 2)
    private double alternateRowHeight = 0.0d;

    @NodeProperty(displayName = "Step Distance", category = "Pattern", order = 3)
    private double stepDistance = 1.0d;

    @NodeProperty(displayName = "Row Distance", category = "Pattern", order = 4)
    private double rowDistance = 1.0d;

    @NodeProperty(displayName = "Step Count", category = "Pattern", order = 5)
    private int stepCount = 5;

    @NodeProperty(displayName = "Row Count", category = "Pattern", order = 6)
    private int rowCount = 3;

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

    public StaggeredGridNode() {
        super(UUID.randomUUID(), "pattern.grid.staggered_grid");
        addInputPort(new BasePort(INPUT_ORIGIN_ID, "Origin", "Grid origin anchor point", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_STEP_DIRECTION_ID, "Step Direction", "Direction for each repeated step", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_ROW_DIRECTION_ID, "Row Direction", "Direction for each row", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_STEP_DISTANCE_ID, "Step Distance", "Distance between repeated steps (signed allowed)", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_ROW_DISTANCE_ID, "Row Distance", "Distance between rows (signed allowed)", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_STAGGER_OFFSET_ID, "Stagger Offset", "Offset applied to staggered rows (signed allowed)", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_STEP_COUNT_ID, "Step Count", "Total copies per row (including offset 0)", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_ROW_COUNT_ID, "Row Count", "Total number of rows (including offset 0)", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Staggered grid anchor points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of emitted anchor points", NodeDataType.INTEGER, this));
        addValidAndErrorOutputs();
    }

    @Override
    public String getDescription() {
        return "Generates staggered grid anchor points with parity-controlled row offsets";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        if (!Double.isFinite(alternateRowHeight)) {
            writeFail("Alternate Row Height must be finite");
            return;
        }

        Vector3d origin = resolveOptionalOrigin(this, INPUT_ORIGIN_ID);
        if (origin == null) {
            writeFail("Origin connected but invalid");
            return;
        }

        Integer resolvedStepCount = OptionalPortDrive.resolveOptionalInteger(this, INPUT_STEP_COUNT_ID, stepCount);
        Integer resolvedRowCount = OptionalPortDrive.resolveOptionalInteger(this, INPUT_ROW_COUNT_ID, rowCount);
        if (resolvedStepCount == null) {
            writeFail("Step Count connected but invalid");
            return;
        }
        if (resolvedRowCount == null) {
            writeFail("Row Count connected but invalid");
            return;
        }

        String productError = GenerationLimits.validateGridProduct(
            resolvedStepCount, resolvedRowCount, GenerationLimits.MAX_LAYOUT_INSTANCES);
        if (productError != null) {
            writeFail(productError);
            return;
        }

        Vector3d stepDir = resolveOptionalNonZeroDirection(this, INPUT_STEP_DIRECTION_ID, new Vector3d(1, 0, 0));
        if (stepDir == null) {
            writeFail("Step Direction connected but invalid or zero");
            return;
        }
        Vector3d rowDir = resolveOptionalNonZeroDirection(this, INPUT_ROW_DIRECTION_ID, new Vector3d(0, 0, 1));
        if (rowDir == null) {
            writeFail("Row Direction connected but invalid or zero");
            return;
        }

        Double resolvedStepDistance = OptionalPortDrive.resolveOptionalDouble(this, INPUT_STEP_DISTANCE_ID, stepDistance);
        Double resolvedRowDistance = OptionalPortDrive.resolveOptionalDouble(this, INPUT_ROW_DISTANCE_ID, rowDistance);
        if (resolvedStepDistance == null) {
            writeFail("Step Distance connected but invalid");
            return;
        }
        if (resolvedRowDistance == null) {
            writeFail("Row Distance connected but invalid");
            return;
        }

        double staggerOffset;
        if (OptionalPortDrive.isConnected(this, INPUT_STAGGER_OFFSET_ID)) {
            Double resolved = OptionalPortDrive.resolveOptionalDouble(this, INPUT_STAGGER_OFFSET_ID, 0.0d);
            if (resolved == null) {
                writeFail("Stagger Offset connected but invalid");
                return;
            }
            staggerOffset = resolved;
        } else {
            staggerOffset = resolvedStepDistance * 0.5d;
        }
        if (!Double.isFinite(staggerOffset)) {
            writeFail("Stagger Offset must be finite");
            return;
        }

        Vector3d stepVec = new Vector3d(stepDir).mul(resolvedStepDistance);
        Vector3d rowVec = new Vector3d(rowDir).mul(resolvedRowDistance);
        Vector3d staggerVec = new Vector3d(stepDir).mul(staggerOffset);

        List<Vector3d> points = new ArrayList<>(resolvedStepCount * resolvedRowCount);
        for (int row = 0; row < resolvedRowCount; row++) {
            Vector3d rowOffset = new Vector3d(rowVec).mul(row);
            if (shouldOffsetRow(row)) {
                rowOffset.add(staggerVec);
            }
            if ((row & 1) == 1 && Math.abs(alternateRowHeight) > 1.0e-9d) {
                rowOffset.y += alternateRowHeight;
            }
            for (int step = 0; step < resolvedStepCount; step++) {
                Vector3d offset = new Vector3d(stepVec).mul(step).add(rowOffset);
                points.add(new Vector3d(origin).add(offset));
            }
        }

        commitPointList(OUTPUT_POINTS_ID, OUTPUT_COUNT_ID, points);
    }

    private boolean shouldOffsetRow(int row) {
        boolean oddRow = (row & 1) == 1;
        return (rowParityMode == RowParityMode.OFFSET_ODD_ROWS) == oddRow;
    }

    private void writeFail(String error) {
        markInvalid(error);
        putEmptyListOutputs(OUTPUT_POINTS_ID);
        putIntOutputs(0, OUTPUT_COUNT_ID);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("rowParityMode", rowParityMode.name());
        state.put("alternateRowHeight", alternateRowHeight);
        state.put("stepDistance", stepDistance);
        state.put("rowDistance", rowDistance);
        state.put("stepCount", stepCount);
        state.put("rowCount", rowCount);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        Object mode = map.get("rowParityMode");
        if (mode instanceof String text) {
            try {
                rowParityMode = RowParityMode.valueOf(text.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // Keep existing mode on invalid saved enum (P2).
            }
        }
        if (map.get("alternateRowHeight") instanceof Number value) {
            alternateRowHeight = value.doubleValue();
        }
        if (map.get("stepDistance") instanceof Number value) {
            stepDistance = value.doubleValue();
        }
        if (map.get("rowDistance") instanceof Number value) {
            rowDistance = value.doubleValue();
        }
        if (map.get("stepCount") instanceof Number value) {
            stepCount = value.intValue();
        }
        if (map.get("rowCount") instanceof Number value) {
            rowCount = value.intValue();
        }
    }
}

package com.nodecraft.nodesystem.nodes.pattern.linear;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.GenerationLimits;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    id = "pattern.linear.staggered_array",
    displayName = "Staggered Array",
    description = "Repeats coordinates in rows and applies an alternating offset for brick-like staggering",
    category = "pattern.linear",
    order = 2
)
public class StaggeredArrayNode extends BaseNode {
    public enum RowParityMode {
        OFFSET_ODD_ROWS,
        OFFSET_EVEN_ROWS
    }

    @NodeProperty(displayName = "Include Original", category = "Pattern", order = 1)
    private boolean includeOriginal = true;

    @NodeProperty(displayName = "Row Parity Mode", category = "Pattern", order = 2)
    private RowParityMode rowParityMode = RowParityMode.OFFSET_ODD_ROWS;

    @NodeProperty(displayName = "Alternate Row Height", category = "Pattern", order = 3)
    private double alternateRowHeight = 0.0d;

    @NodeProperty(displayName = "Limit By Envelope", category = "Pattern", order = 4)
    private boolean limitByEnvelope = false;

    private static final String INPUT_COORDINATES_ID = "input_coordinates";
    private static final String INPUT_STEP_DIRECTION_ID = "input_step_direction";
    private static final String INPUT_ROW_DIRECTION_ID = "input_row_direction";
    private static final String INPUT_STEP_DISTANCE_ID = "input_step_distance";
    private static final String INPUT_ROW_DISTANCE_ID = "input_row_distance";
    private static final String INPUT_STAGGER_OFFSET_ID = "input_stagger_offset";
    private static final String INPUT_STEP_COUNT_ID = "input_step_count";
    private static final String INPUT_ROW_COUNT_ID = "input_row_count";
    private static final String OUTPUT_COORDINATES_ID = "output_array_coordinates";

    public StaggeredArrayNode() {
        super(UUID.randomUUID(), "pattern.linear.staggered_array");
        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", "Coordinates to repeat", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_STEP_DIRECTION_ID, "Step Direction", "Direction for each repeated step", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_ROW_DIRECTION_ID, "Row Direction", "Direction for each row", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_STEP_DISTANCE_ID, "Step Distance", "Distance between repeated steps", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_ROW_DISTANCE_ID, "Row Distance", "Distance between rows", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_STAGGER_OFFSET_ID, "Stagger Offset", "Offset applied to every odd row", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_STEP_COUNT_ID, "Step Count", "Number of copies per row", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_ROW_COUNT_ID, "Row Count", "Number of rows", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_COORDINATES_ID, "Array Coordinates", "Staggered array coordinates", NodeDataType.BLOCK_LIST, this));
    }

    @Override
    public String getDescription() {
        return "Repeats coordinates in rows with parity-controlled staggering, optional alternate row height, and optional envelope clipping";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object coordsObj = inputValues.get(INPUT_COORDINATES_ID);
        if (!(coordsObj instanceof BlockPosList source)) {
            outputValues.put(OUTPUT_COORDINATES_ID, new BlockPosList());
            return;
        }

        Vector3d stepDir = normalize((Vector3d) inputValues.getOrDefault(INPUT_STEP_DIRECTION_ID, new Vector3d(1, 0, 0)), new Vector3d(1, 0, 0));
        Vector3d rowDir = normalize((Vector3d) inputValues.getOrDefault(INPUT_ROW_DIRECTION_ID, new Vector3d(0, 0, 1)), new Vector3d(0, 0, 1));
        double stepDistance = getDouble(INPUT_STEP_DISTANCE_ID, 1.0d);
        double rowDistance = getDouble(INPUT_ROW_DISTANCE_ID, 1.0d);
        double staggerOffset = getDouble(INPUT_STAGGER_OFFSET_ID, stepDistance * 0.5d);
        int stepCount = GenerationLimits.clampGridAxis(getInt(INPUT_STEP_COUNT_ID, 5));
        int rowCount = GenerationLimits.clampGridAxis(getInt(INPUT_ROW_COUNT_ID, 3));

        Vector3d stepVec = new Vector3d(stepDir).mul(stepDistance);
        Vector3d rowVec = new Vector3d(rowDir).mul(rowDistance);
        Vector3d staggerVec = new Vector3d(stepDir).mul(staggerOffset);
        Bounds envelope = computeBounds(source);

        BlockPosList result = new BlockPosList();
        for (int row = 0; row <= rowCount; row++) {
            Vector3d rowOffset = new Vector3d(rowVec).mul(row);
            if (shouldOffsetRow(row)) {
                rowOffset.add(staggerVec);
            }
            if ((row & 1) == 1 && Math.abs(alternateRowHeight) > 1.0e-9d) {
                rowOffset.y += alternateRowHeight;
            }
            for (int step = 0; step <= stepCount; step++) {
                if (!includeOriginal && row == 0 && step == 0) {
                    continue;
                }
                Vector3d offset = new Vector3d(stepVec).mul(step).add(rowOffset);
                for (BlockPos pos : source) {
                    BlockPos generated = new BlockPos(
                        (int) Math.round(pos.getX() + offset.x),
                        (int) Math.round(pos.getY() + offset.y),
                        (int) Math.round(pos.getZ() + offset.z)
                    );
                    if (limitByEnvelope && !envelope.contains(generated)) {
                        continue;
                    }
                    result.add(generated);
                }
            }
        }
        outputValues.put(OUTPUT_COORDINATES_ID, result);
    }

    private boolean shouldOffsetRow(int row) {
        boolean oddRow = (row & 1) == 1;
        return rowParityMode == RowParityMode.OFFSET_ODD_ROWS ? oddRow : !oddRow;
    }

    private Bounds computeBounds(BlockPosList source) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : source) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        boolean contains(BlockPos pos) {
            return pos.getX() >= minX && pos.getX() <= maxX
                && pos.getY() >= minY && pos.getY() <= maxY
                && pos.getZ() >= minZ && pos.getZ() <= maxZ;
        }
    }

    private Vector3d normalize(Vector3d input, Vector3d fallback) {
        if (input == null || input.lengthSquared() < 1e-8d) {
            return new Vector3d(fallback);
        }
        return new Vector3d(input).normalize();
    }

    private double getDouble(String portId, double fallback) {
        Object v = inputValues.get(portId);
        return v instanceof Number n ? n.doubleValue() : fallback;
    }

    private int getInt(String portId, int fallback) {
        Object v = inputValues.get(portId);
        return v instanceof Number n ? n.intValue() : fallback;
    }
}


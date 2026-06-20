package com.nodecraft.nodesystem.nodes.world.query;

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
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@NodeInfo(
    id = "world.query.filter_points_by_rule",
    displayName = "Filter Points By Rule",
    description = "Filters point sets by height and optional surface slope rules.",
    category = "world.query",
    order = 12
)
public class FilterPointsByRuleNode extends BaseNode {

    private static final String INPUT_POINTS_ID = "input_points";
    private static final String INPUT_NORMALS_ID = "input_normals";
    private static final String INPUT_MIN_HEIGHT_ID = "input_min_height";
    private static final String INPUT_MAX_HEIGHT_ID = "input_max_height";
    private static final String INPUT_MIN_SLOPE_ID = "input_min_slope";
    private static final String INPUT_MAX_SLOPE_ID = "input_max_slope";
    private static final String INPUT_MODE_ID = "input_mode";
    private static final String INPUT_INVERT_ID = "input_invert";

    private static final String OUTPUT_FILTERED_POINTS_ID = "output_filtered_points";
    private static final String OUTPUT_REMOVED_POINTS_ID = "output_removed_points";
    private static final String OUTPUT_FILTERED_BLOCKS_ID = "output_filtered_blocks";
    private static final String OUTPUT_REMOVED_BLOCKS_ID = "output_removed_blocks";
    private static final String OUTPUT_MASK_ID = "output_mask";
    private static final String OUTPUT_SLOPES_ID = "output_slopes";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_TOTAL_COUNT_ID = "output_total_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public FilterPointsByRuleNode() {
        super(UUID.randomUUID(), "world.query.filter_points_by_rule");

        addInputPort(new BasePort(INPUT_POINTS_ID, "Points", "Points or block positions to filter", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_NORMALS_ID, "Normals", "Optional normals aligned with points for slope tests", NodeDataType.VECTOR_LIST, this));
        addInputPort(new BasePort(INPUT_MIN_HEIGHT_ID, "Min Height", "Minimum Y value to keep", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_MAX_HEIGHT_ID, "Max Height", "Maximum Y value to keep", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_MIN_SLOPE_ID, "Min Slope", "Minimum slope angle in degrees", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_MAX_SLOPE_ID, "Max Slope", "Maximum slope angle in degrees", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_MODE_ID, "Mode", "Rule combination: all or any", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_INVERT_ID, "Invert", "Invert the final keep mask", NodeDataType.BOOLEAN, this));

        addOutputPort(new BasePort(OUTPUT_FILTERED_POINTS_ID, "Filtered Points", "Points that passed the rule", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_REMOVED_POINTS_ID, "Removed Points", "Points rejected by the rule", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_FILTERED_BLOCKS_ID, "Filtered Blocks", "Filtered points snapped to block positions", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_REMOVED_BLOCKS_ID, "Removed Blocks", "Rejected points snapped to block positions", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_MASK_ID, "Mask", "Boolean keep mask aligned with input points", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_SLOPES_ID, "Slopes", "Computed slope degrees aligned with input points when normals are available", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of kept points", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_TOTAL_COUNT_ID, "Total Count", "Number of resolved input points", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether filtering had valid point input", NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> points = resolvePoints(inputValues.get(INPUT_POINTS_ID));
        if (points.isEmpty()) {
            writeOutputs(List.of(), List.of(), new BlockPosList(), new BlockPosList(), List.of(), List.of(), false);
            return;
        }

        List<Vector3d> normals = resolveNormals(inputValues.get(INPUT_NORMALS_ID));
        Double minHeight = resolveDouble(inputValues.get(INPUT_MIN_HEIGHT_ID));
        Double maxHeight = resolveDouble(inputValues.get(INPUT_MAX_HEIGHT_ID));
        Double minSlope = resolveDouble(inputValues.get(INPUT_MIN_SLOPE_ID));
        Double maxSlope = resolveDouble(inputValues.get(INPUT_MAX_SLOPE_ID));
        boolean anyMode = "any".equals(resolveMode(inputValues.get(INPUT_MODE_ID)));
        boolean invert = inputValues.get(INPUT_INVERT_ID) instanceof Boolean value && value;

        List<Vector3d> kept = new ArrayList<>();
        List<Vector3d> removed = new ArrayList<>();
        BlockPosList keptBlocks = new BlockPosList();
        BlockPosList removedBlocks = new BlockPosList();
        List<Boolean> mask = new ArrayList<>(points.size());
        List<Double> slopes = new ArrayList<>(points.size());

        for (int i = 0; i < points.size(); i++) {
            Vector3d point = points.get(i);
            boolean hasHeightRule = minHeight != null || maxHeight != null;
            boolean hasSlopeRule = (minSlope != null || maxSlope != null) && i < normals.size();

            boolean heightPass = !hasHeightRule || inRange(point.y, minHeight, maxHeight);
            Double slope = i < normals.size() ? slopeDegrees(normals.get(i)) : null;
            boolean slopePass = !hasSlopeRule || inRange(slope, minSlope, maxSlope);
            slopes.add(slope);

            boolean keep;
            if (anyMode && (hasHeightRule || hasSlopeRule)) {
                keep = (hasHeightRule && heightPass) || (hasSlopeRule && slopePass);
            } else {
                keep = heightPass && slopePass;
            }
            if (invert) {
                keep = !keep;
            }

            mask.add(keep);
            BlockPos blockPos = BlockPos.ofFloored(point.x, point.y, point.z);
            if (keep) {
                kept.add(new Vector3d(point));
                keptBlocks.add(blockPos);
            } else {
                removed.add(new Vector3d(point));
                removedBlocks.add(blockPos);
            }
        }

        writeOutputs(kept, removed, keptBlocks, removedBlocks, mask, slopes, true);
    }

    private List<Vector3d> resolvePoints(Object value) {
        List<Vector3d> out = new ArrayList<>();
        if (value instanceof BlockPosList blockPosList) {
            for (BlockPos pos : blockPosList) {
                out.add(new Vector3d(pos.getX(), pos.getY(), pos.getZ()));
            }
        } else if (value instanceof List<?> list) {
            for (Object entry : list) {
                Vector3d point = resolvePoint(entry);
                if (point != null) {
                    out.add(point);
                }
            }
        } else {
            Vector3d point = resolvePoint(value);
            if (point != null) {
                out.add(point);
            }
        }
        return out;
    }

    private List<Vector3d> resolveNormals(Object value) {
        List<Vector3d> out = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object entry : list) {
                Vector3d normal = resolvePoint(entry);
                if (normal != null && normal.lengthSquared() > 1.0e-12d) {
                    out.add(normal.normalize(new Vector3d()));
                }
            }
        }
        return out;
    }

    private Vector3d resolvePoint(Object value) {
        if (value instanceof Vector3d vector) {
            return new Vector3d(vector);
        }
        if (value instanceof BlockPos pos) {
            return new Vector3d(pos.getX(), pos.getY(), pos.getZ());
        }
        if (value instanceof PointData pointData) {
            return new Vector3d(pointData.getPosition());
        }
        return null;
    }

    private Double resolveDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String resolveMode(Object value) {
        if (value instanceof String text) {
            String normalized = text.trim().toLowerCase(Locale.ROOT);
            if ("any".equals(normalized)) {
                return "any";
            }
        }
        return "all";
    }

    private boolean inRange(double value, Double min, Double max) {
        return (min == null || value >= min) && (max == null || value <= max);
    }

    private Double slopeDegrees(Vector3d normal) {
        if (normal == null || normal.lengthSquared() <= 1.0e-12d) {
            return null;
        }
        double y = Math.max(-1.0d, Math.min(1.0d, Math.abs(normal.normalize(new Vector3d()).y)));
        return Math.toDegrees(Math.acos(y));
    }

    private void writeOutputs(List<Vector3d> kept,
                              List<Vector3d> removed,
                              BlockPosList keptBlocks,
                              BlockPosList removedBlocks,
                              List<Boolean> mask,
                              List<Double> slopes,
                              boolean valid) {
        outputValues.put(OUTPUT_FILTERED_POINTS_ID, kept);
        outputValues.put(OUTPUT_REMOVED_POINTS_ID, removed);
        outputValues.put(OUTPUT_FILTERED_BLOCKS_ID, keptBlocks);
        outputValues.put(OUTPUT_REMOVED_BLOCKS_ID, removedBlocks);
        outputValues.put(OUTPUT_MASK_ID, mask);
        outputValues.put(OUTPUT_SLOPES_ID, slopes);
        outputValues.put(OUTPUT_COUNT_ID, kept.size());
        outputValues.put(OUTPUT_TOTAL_COUNT_ID, kept.size() + removed.size());
        outputValues.put(OUTPUT_VALID_ID, valid);
    }
}

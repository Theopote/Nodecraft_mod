package com.nodecraft.nodesystem.nodes.world.read;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.GenerationLimits;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.WORLD_READ,
    id = "world.read.get_heightmap",
    displayName = "Get Heightmap",
    description = "Reads the top Y value for each X/Z column in a region's XZ footprint (Region Y is ignored).",
    category = "world.read",
    order = 5
)
public class GetHeightmapNode extends BaseNode {

    private static final String INPUT_REGION_ID = "input_region";
    private static final String INPUT_HEIGHTMAP_TYPE_ID = "input_heightmap_type";
    private static final String INPUT_STEP_ID = "input_step";
    private static final String INPUT_MAX_COLUMNS_ID = "input_max_columns";

    private static final String OUTPUT_SURFACE_POINTS_ID = "output_surface_points";
    private static final String OUTPUT_HEIGHT_VALUES_ID = "output_height_values";
    private static final String OUTPUT_COLUMN_COUNT_ID = "output_column_count";
    private static final String OUTPUT_HEIGHTMAP_TYPE_ID = "output_resolved_heightmap_type";
    private static final String OUTPUT_MIN_HEIGHT_ID = "output_min_height";
    private static final String OUTPUT_MAX_HEIGHT_ID = "output_max_height";
    private static final String OUTPUT_AVERAGE_HEIGHT_ID = "output_average_height";
    private static final String OUTPUT_TOTAL_POSSIBLE_ID = "output_total_possible";
    private static final String OUTPUT_HIT_LIMIT_ID = "output_hit_limit";
    private static final String OUTPUT_COMPLETE_ID = "output_complete";
    private static final String OUTPUT_STOPPED_REASON_ID = "output_stopped_reason";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    @NodeProperty(displayName = "Heightmap Type", category = "Heightmap", order = 1)
    private String heightmapType = "WORLD_SURFACE";

    public GetHeightmapNode() {
        super(UUID.randomUUID(), "world.read.get_heightmap");

        addInputPort(new BasePort(INPUT_REGION_ID, "Region", "Region whose X/Z footprint should be sampled (Y ignored)", NodeDataType.REGION, this));
        addInputPort(new BasePort(INPUT_HEIGHTMAP_TYPE_ID, "Heightmap Type", "Optional heightmap type name such as WORLD_SURFACE or MOTION_BLOCKING", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_STEP_ID, "Step", "Sample every Nth X/Z column", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_MAX_COLUMNS_ID, "Max Columns", "Maximum number of columns to sample", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_SURFACE_POINTS_ID, "Surface Points", "Top point for each X/Z column", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_HEIGHT_VALUES_ID, "Height Values", "List of sampled top Y values", NodeDataType.INTEGER_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COLUMN_COUNT_ID, "Column Count", "Number of sampled X/Z columns", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_HEIGHTMAP_TYPE_ID, "Resolved Heightmap Type", "Resolved heightmap type id", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_MIN_HEIGHT_ID, "Min Height", "Minimum sampled top Y", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_MAX_HEIGHT_ID, "Max Height", "Maximum sampled top Y", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_AVERAGE_HEIGHT_ID, "Average Height", "Average sampled top Y", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_TOTAL_POSSIBLE_ID, "Total Possible", "Total X/Z columns in the region", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_HIT_LIMIT_ID, "Hit Limit", "Whether Max Columns stopped sampling", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_COMPLETE_ID, "Complete", "Whether all stepped columns were sampled", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_STOPPED_REASON_ID, "Stopped Reason", "completed, max_columns, or invalid", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether heightmap sampling was executed", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Error message when heightmap sampling is invalid", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Reads the top Y value for each X/Z column in a region's XZ footprint (Region Y is ignored).";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Heightmap.Type resolvedType = WorldReadUtils.resolveHeightmapType(this, INPUT_HEIGHTMAP_TYPE_ID, heightmapType);
        if (resolvedType == null) {
            publishInvalid("Heightmap Type is connected but null or invalid.", 0L);
            return;
        }

        Integer step = WorldReadUtils.resolveExactStep(this, INPUT_STEP_ID, 1);
        if (step == null) {
            publishInvalid("Step must be an exact INTEGER greater than or equal to 1.", 0L);
            return;
        }

        Integer maxColumns = WorldReadUtils.resolveBoundedWorldReadCount(
                this, INPUT_MAX_COLUMNS_ID, GenerationLimits.MAX_WORLD_READ_COLUMNS, WorldReadUtils.DEFAULT_MAX_COLUMNS);
        if (maxColumns == null) {
            publishInvalid("Max Columns must be an exact INTEGER between 1 and "
                    + GenerationLimits.MAX_WORLD_READ_COLUMNS + ".", 0L);
            return;
        }

        Object regionObj = inputValues.get(INPUT_REGION_ID);
        if (!(regionObj instanceof RegionData region) || !region.isComplete()) {
            publishInvalid("Region input is incomplete.", 0L);
            return;
        }

        long totalPossible = WorldReadUtils.columnCount(region);
        if (totalPossible == WorldReadUtils.OVERFLOW) {
            publishInvalid("Region column count overflows integer coordinate range.", 0L);
            return;
        }

        BlockPos min = region.getMinCorner();
        BlockPos max = region.getMaxCorner();
        if (min == null || max == null) {
            publishInvalid("Region bounds are invalid.", totalPossible);
            return;
        }

        if (context == null || context.getWorld() == null) {
            publishInvalid("Execution context or world is missing.", totalPossible);
            return;
        }

        BlockPosList points = new BlockPosList();
        List<Integer> heights = new ArrayList<>();
        int columnCount = 0;
        int minHeight = 0;
        int maxHeight = 0;
        long heightSum = 0L;
        boolean hitLimit = false;
        String stoppedReason = "completed";

        sample:
        for (long x = min.getX(); x <= max.getX(); ) {
            for (long z = min.getZ(); z <= max.getZ(); ) {
                if (columnCount >= maxColumns) {
                    hitLimit = true;
                    stoppedReason = "max_columns";
                    break sample;
                }
                int xi = (int) x;
                int zi = (int) z;
                int topY = context.getWorld().getTopY(resolvedType, xi, zi) - 1;
                points.add(new BlockPos(xi, topY, zi));
                heights.add(topY);
                if (columnCount == 0 || topY < minHeight) {
                    minHeight = topY;
                }
                if (columnCount == 0 || topY > maxHeight) {
                    maxHeight = topY;
                }
                heightSum += topY;
                columnCount++;

                Integer nextZ = WorldReadUtils.nextAxisCoordinate(z, step, max.getZ());
                if (nextZ == null) {
                    break;
                }
                z = nextZ;
            }
            Integer nextX = WorldReadUtils.nextAxisCoordinate(x, step, max.getX());
            if (nextX == null) {
                break;
            }
            x = nextX;
        }

        outputValues.put(OUTPUT_SURFACE_POINTS_ID, points);
        outputValues.put(OUTPUT_HEIGHT_VALUES_ID, heights);
        outputValues.put(OUTPUT_COLUMN_COUNT_ID, columnCount);
        outputValues.put(OUTPUT_HEIGHTMAP_TYPE_ID, resolvedType.asString());
        outputValues.put(OUTPUT_MIN_HEIGHT_ID, minHeight);
        outputValues.put(OUTPUT_MAX_HEIGHT_ID, maxHeight);
        outputValues.put(OUTPUT_AVERAGE_HEIGHT_ID, columnCount > 0 ? (double) heightSum / columnCount : 0.0d);
        outputValues.put(OUTPUT_TOTAL_POSSIBLE_ID, (int) Math.min(Integer.MAX_VALUE, totalPossible));
        outputValues.put(OUTPUT_HIT_LIMIT_ID, hitLimit);
        outputValues.put(OUTPUT_COMPLETE_ID, !hitLimit);
        outputValues.put(OUTPUT_STOPPED_REASON_ID, stoppedReason);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void publishInvalid(String error, long totalPossible) {
        outputValues.put(OUTPUT_SURFACE_POINTS_ID, new BlockPosList());
        outputValues.put(OUTPUT_HEIGHT_VALUES_ID, List.of());
        outputValues.put(OUTPUT_COLUMN_COUNT_ID, 0);
        outputValues.put(OUTPUT_HEIGHTMAP_TYPE_ID, "");
        outputValues.put(OUTPUT_MIN_HEIGHT_ID, 0);
        outputValues.put(OUTPUT_MAX_HEIGHT_ID, 0);
        outputValues.put(OUTPUT_AVERAGE_HEIGHT_ID, 0.0d);
        outputValues.put(OUTPUT_TOTAL_POSSIBLE_ID, (int) Math.min(Integer.MAX_VALUE, Math.max(0L, totalPossible)));
        outputValues.put(OUTPUT_HIT_LIMIT_ID, false);
        outputValues.put(OUTPUT_COMPLETE_ID, false);
        outputValues.put(OUTPUT_STOPPED_REASON_ID, "invalid");
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error);
    }

    public String getHeightmapType() {
        return heightmapType;
    }

    public void setHeightmapType(String heightmapType) {
        if (heightmapType != null && !heightmapType.isBlank()) {
            this.heightmapType = heightmapType;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("heightmapType", heightmapType);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof String value && !value.isBlank()) {
            heightmapType = value;
            return;
        }
        if (state instanceof Map<?, ?> map && map.get("heightmapType") instanceof String value && !value.isBlank()) {
            heightmapType = value;
        }
    }
}

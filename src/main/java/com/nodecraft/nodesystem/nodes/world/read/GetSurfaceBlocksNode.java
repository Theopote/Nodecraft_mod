package com.nodecraft.nodesystem.nodes.world.read;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "world.read.get_surface_blocks",
    displayName = "Get Surface Blocks",
    description = "Gets the top visible block for each X/Z column inside a region",
    category = "world.read",
    order = 7
)
public class GetSurfaceBlocksNode extends BaseNode {

    private static final String INPUT_REGION_ID = "input_region";
    private static final String INPUT_HEIGHTMAP_TYPE_ID = "input_heightmap_type";
    private static final String INPUT_EXCLUDE_AIR_ID = "input_exclude_air";
    private static final String INPUT_STEP_ID = "input_step";
    private static final String INPUT_MAX_COLUMNS_ID = "input_max_columns";

    private static final String OUTPUT_SURFACE_BLOCKS_ID = "output_surface_blocks";
    private static final String OUTPUT_SURFACE_POSITIONS_ID = "output_surface_positions";
    private static final String OUTPUT_BLOCK_TYPES_ID = "output_block_types";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_TOTAL_COLUMNS_ID = "output_total_columns";
    private static final String OUTPUT_DOMINANT_BLOCK_ID = "output_dominant_block";
    private static final String OUTPUT_HIT_LIMIT_ID = "output_hit_limit";
    private static final String OUTPUT_STOPPED_REASON_ID = "output_stopped_reason";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    private String heightmapType = "WORLD_SURFACE";
    private boolean excludeAir = true;

    public GetSurfaceBlocksNode() {
        super(UUID.randomUUID(), "world.read.get_surface_blocks");

        addInputPort(new BasePort(INPUT_REGION_ID, "Region", "Region whose X/Z columns should be sampled", NodeDataType.REGION, this));
        addInputPort(new BasePort(INPUT_HEIGHTMAP_TYPE_ID, "Heightmap Type", "Optional heightmap type name", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_EXCLUDE_AIR_ID, "Exclude Air", "Whether columns resolving to air should be excluded", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_STEP_ID, "Step", "Sample every Nth X/Z column", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_MAX_COLUMNS_ID, "Max Columns", "Maximum number of columns to sample", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_SURFACE_BLOCKS_ID, "Surface Blocks", "Top block state for each sampled column", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_SURFACE_POSITIONS_ID, "Surface Positions", "Block positions of sampled surface blocks", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_BLOCK_TYPES_ID, "Block Types", "Registry ids of sampled surface blocks", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of sampled surface blocks", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_TOTAL_COLUMNS_ID, "Total Columns", "Total X/Z columns in the region", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_DOMINANT_BLOCK_ID, "Dominant Surface Block", "Most common sampled surface block id", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_HIT_LIMIT_ID, "Hit Limit", "Whether Max Columns stopped sampling", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_STOPPED_REASON_ID, "Stopped Reason", "completed, max_columns, or invalid", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether surface sampling was executed", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Error message when surface sampling is invalid", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Gets the top visible block for each X/Z column inside a region";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<BlockState> blocks = new ArrayList<>();
        BlockPosList positions = new BlockPosList();
        List<String> blockTypes = new ArrayList<>();
        int count = 0;
        int sampledColumns = 0;
        long totalColumns = 0L;
        boolean hitLimit = false;
        String stoppedReason = "completed";
        boolean valid = false;
        String error = "";
        Map<String, Integer> blockCounts = new HashMap<>();

        Object regionObj = inputValues.get(INPUT_REGION_ID);
        Heightmap.Type resolvedType = resolveHeightmapType(inputValues.get(INPUT_HEIGHTMAP_TYPE_ID));
        boolean resolvedExcludeAir = inputValues.get(INPUT_EXCLUDE_AIR_ID) instanceof Boolean value ? value : excludeAir;
        int step = inputValues.get(INPUT_STEP_ID) instanceof Number n ? Math.max(1, n.intValue()) : 1;
        int maxColumns = inputValues.get(INPUT_MAX_COLUMNS_ID) instanceof Number n ? Math.max(1, n.intValue()) : WorldReadUtils.DEFAULT_MAX_COLUMNS;

        if (context == null || context.getWorld() == null) {
            stoppedReason = "invalid";
            error = "Execution context or world is missing.";
        } else if (!(regionObj instanceof RegionData region) || !region.isComplete()) {
            stoppedReason = "invalid";
            error = "Region input is incomplete.";
        } else {
            totalColumns = WorldReadUtils.columnCount(region);
            BlockPos min = region.getMinCorner();
            BlockPos max = region.getMaxCorner();
            if (min != null && max != null) {
                valid = true;
                sample:
                for (int x = min.getX(); x <= max.getX(); x += step) {
                    for (int z = min.getZ(); z <= max.getZ(); z += step) {
                        if (sampledColumns >= maxColumns) {
                            hitLimit = true;
                            stoppedReason = "max_columns";
                            break sample;
                        }
                        sampledColumns++;
                        int topY = context.getWorld().getTopY(resolvedType, x, z) - 1;
                        BlockPos pos = new BlockPos(x, topY, z);
                        BlockState state = context.getWorld().getBlockState(pos);
                        if (resolvedExcludeAir && state.isAir()) {
                            continue;
                        }
                        String blockId = WorldReadUtils.blockId(state);
                        blocks.add(state);
                        positions.add(pos);
                        blockTypes.add(blockId);
                        blockCounts.put(blockId, blockCounts.getOrDefault(blockId, 0) + 1);
                        count++;
                    }
                }
            }
        }
        String dominantBlock = blockCounts.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("");

        outputValues.put(OUTPUT_SURFACE_BLOCKS_ID, blocks);
        outputValues.put(OUTPUT_SURFACE_POSITIONS_ID, positions);
        outputValues.put(OUTPUT_BLOCK_TYPES_ID, blockTypes);
        outputValues.put(OUTPUT_COUNT_ID, count);
        outputValues.put(OUTPUT_TOTAL_COLUMNS_ID, (int) Math.min(Integer.MAX_VALUE, totalColumns));
        outputValues.put(OUTPUT_DOMINANT_BLOCK_ID, dominantBlock);
        outputValues.put(OUTPUT_HIT_LIMIT_ID, hitLimit);
        outputValues.put(OUTPUT_STOPPED_REASON_ID, stoppedReason);
        outputValues.put(OUTPUT_VALID_ID, valid);
        outputValues.put(OUTPUT_ERROR_ID, error);
    }

    private Heightmap.Type resolveHeightmapType(Object value) {
        String raw = value instanceof String string && !string.isBlank() ? string : heightmapType;
        try {
            return Heightmap.Type.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return Heightmap.Type.WORLD_SURFACE;
        }
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

    public boolean isExcludeAir() {
        return excludeAir;
    }

    public void setExcludeAir(boolean excludeAir) {
        this.excludeAir = excludeAir;
        markDirty();
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("heightmapType", heightmapType);
        state.put("excludeAir", excludeAir);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Object[] values && values.length >= 2) {
            if (values[0] instanceof String type && !type.isBlank()) {
                heightmapType = type;
            }
            if (values[1] instanceof Boolean flag) {
                excludeAir = flag;
            }
            return;
        }
        if (state instanceof Map<?, ?> map) {
            if (map.get("heightmapType") instanceof String type && !type.isBlank()) {
                heightmapType = type;
            }
            if (map.get("excludeAir") instanceof Boolean flag) {
                excludeAir = flag;
            }
        }
    }
}

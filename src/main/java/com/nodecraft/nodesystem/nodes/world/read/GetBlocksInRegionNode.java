package com.nodecraft.nodesystem.nodes.world.read;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "world.read.get_blocks_in_region",
    displayName = "Get Blocks In Region",
    description = "Reads block states and coordinates inside a region with scan limits.",
    category = "world.read",
    order = 1
)
public class GetBlocksInRegionNode extends BaseNode {

    private boolean excludeAir = true;

    private static final String INPUT_REGION_ID = "input_region";
    private static final String INPUT_EXCLUDE_AIR_ID = "input_exclude_air";
    private static final String INPUT_MAX_BLOCKS_ID = "input_max_blocks";

    private static final String OUTPUT_BLOCKS_LIST_ID = "output_blocks_list";
    private static final String OUTPUT_COORDINATES_ID = "output_coordinates";
    private static final String OUTPUT_BLOCK_COUNT_ID = "output_block_count";
    private static final String OUTPUT_OUTPUT_COUNT_ID = "output_output_count";
    private static final String OUTPUT_AIR_COUNT_ID = "output_air_count";
    private static final String OUTPUT_SOLID_COUNT_ID = "output_solid_count";
    private static final String OUTPUT_TOTAL_POSSIBLE_ID = "output_total_possible";
    private static final String OUTPUT_HIT_LIMIT_ID = "output_hit_limit";
    private static final String OUTPUT_STOPPED_REASON_ID = "output_stopped_reason";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public GetBlocksInRegionNode() {
        super(UUID.randomUUID(), "world.read.get_blocks_in_region");

        addInputPort(new BasePort(INPUT_REGION_ID, "Region", "Region to read", NodeDataType.REGION, this));
        addInputPort(new BasePort(INPUT_EXCLUDE_AIR_ID, "Exclude Air", "Whether air blocks should be omitted from output lists", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_MAX_BLOCKS_ID, "Max Blocks", "Maximum number of positions to scan", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_BLOCKS_LIST_ID, "Blocks List", "Output block states", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_COORDINATES_ID, "Coordinates", "Coordinates for output block states", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_BLOCK_COUNT_ID, "Scanned Count", "Number of positions scanned", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_OUTPUT_COUNT_ID, "Output Count", "Number of block states returned", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_AIR_COUNT_ID, "Air Count", "Number of air positions scanned", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_SOLID_COUNT_ID, "Solid Count", "Number of non-air positions scanned", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_TOTAL_POSSIBLE_ID, "Total Possible", "Total positions in the region", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_HIT_LIMIT_ID, "Hit Limit", "Whether Max Blocks stopped the scan", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_STOPPED_REASON_ID, "Stopped Reason", "completed, max_blocks, or invalid", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether the region read was executed", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Error message when region read fails", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Reads block states and coordinates inside a region with scan limits.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<BlockState> blocksList = new ArrayList<>();
        BlockPosList coordsList = new BlockPosList();
        int scannedCount = 0;
        int airCount = 0;
        int solidCount = 0;
        int errorCount = 0;
        boolean hitLimit = false;
        String stoppedReason = "completed";
        String firstError = "";

        Object regionObj = inputValues.get(INPUT_REGION_ID);
        boolean excludeAirValue = inputValues.get(INPUT_EXCLUDE_AIR_ID) instanceof Boolean value ? value : excludeAir;
        int maxBlocks = inputValues.get(INPUT_MAX_BLOCKS_ID) instanceof Number value
            ? Math.max(1, value.intValue())
            : WorldReadUtils.DEFAULT_MAX_BLOCKS;

        if (context == null || context.getWorld() == null) {
            publish(blocksList, coordsList, 0, 0, 0, 0, false, "invalid", false, "Execution context or world is missing.");
            return;
        }
        if (!(regionObj instanceof RegionData region) || !region.isComplete()) {
            publish(blocksList, coordsList, 0, 0, 0, 0, false, "invalid", false, "Region input is incomplete.");
            return;
        }

        long totalPossible = WorldReadUtils.volume(region);
        BlockPos min = region.getMinCorner();
        BlockPos max = region.getMaxCorner();
        if (min == null || max == null) {
            publish(blocksList, coordsList, totalPossible, 0, 0, 0, false, "invalid", false, "Region bounds are invalid.");
            return;
        }

        for (BlockPos pos : BlockPos.iterate(min, max)) {
            if (scannedCount >= maxBlocks) {
                hitLimit = true;
                stoppedReason = "max_blocks";
                break;
            }
            BlockPos immutablePos = pos.toImmutable();
            scannedCount++;
            try {
                BlockState blockState = context.getWorld().getBlockState(immutablePos);
                boolean isAir = blockState.isAir();
                if (isAir) {
                    airCount++;
                } else {
                    solidCount++;
                }
                if (!excludeAirValue || !isAir) {
                    blocksList.add(blockState);
                    coordsList.add(immutablePos);
                }
            } catch (Exception e) {
                errorCount++;
                if (firstError.isEmpty()) {
                    firstError = "Error getting block at " + immutablePos + ": " + e.getMessage();
                }
                NodeCraft.LOGGER.warn("GetBlocksInRegionNode failed at {}: {}", immutablePos, e.getMessage());
            }
        }

        publish(
            blocksList,
            coordsList,
            totalPossible,
            scannedCount,
            airCount,
            solidCount,
            hitLimit,
            stoppedReason,
            true,
            errorCount > 0 ? firstError : ""
        );
    }

    private void publish(List<BlockState> blocksList,
                         BlockPosList coordsList,
                         long totalPossible,
                         int scannedCount,
                         int airCount,
                         int solidCount,
                         boolean hitLimit,
                         String stoppedReason,
                         boolean valid,
                         String error) {
        outputValues.put(OUTPUT_BLOCKS_LIST_ID, blocksList);
        outputValues.put(OUTPUT_COORDINATES_ID, coordsList);
        outputValues.put(OUTPUT_BLOCK_COUNT_ID, scannedCount);
        outputValues.put(OUTPUT_OUTPUT_COUNT_ID, blocksList.size());
        outputValues.put(OUTPUT_AIR_COUNT_ID, airCount);
        outputValues.put(OUTPUT_SOLID_COUNT_ID, solidCount);
        outputValues.put(OUTPUT_TOTAL_POSSIBLE_ID, (int) Math.min(Integer.MAX_VALUE, totalPossible));
        outputValues.put(OUTPUT_HIT_LIMIT_ID, hitLimit);
        outputValues.put(OUTPUT_STOPPED_REASON_ID, stoppedReason);
        outputValues.put(OUTPUT_VALID_ID, valid);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }

    public boolean isExcludeAir() {
        return excludeAir;
    }

    public void setExcludeAir(boolean excludeAir) {
        this.excludeAir = excludeAir;
        markDirty();
    }
}

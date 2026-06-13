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

import java.util.UUID;

@NodeInfo(
    id = "world.read.find_blocks",
    displayName = "Find Blocks",
    description = "Finds matching block positions inside a region using block type or exact block state matching.",
    category = "world.read",
    order = 2
)
public class FindBlocksNode extends BaseNode {

    private int maxResults = 1000;

    private static final String INPUT_REGION_ID = "input_region";
    private static final String INPUT_BLOCK_INFO_ID = "input_block_info";
    private static final String INPUT_TARGET_BLOCK_TYPE_ID = "input_target_block_type";
    private static final String INPUT_EXACT_MATCH_ID = "input_exact_match";
    private static final String INPUT_MAX_RESULTS_ID = "input_max_results";
    private static final String INPUT_MAX_BLOCKS_ID = "input_max_blocks";

    private static final String OUTPUT_FOUND_BLOCKS_ID = "output_found_blocks";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_FIRST_POS_ID = "output_first_pos";
    private static final String OUTPUT_FOUND_ANY_ID = "output_found_any";
    private static final String OUTPUT_VISITED_COUNT_ID = "output_visited_count";
    private static final String OUTPUT_TOTAL_POSSIBLE_ID = "output_total_possible";
    private static final String OUTPUT_HIT_LIMIT_ID = "output_hit_limit";
    private static final String OUTPUT_STOPPED_REASON_ID = "output_stopped_reason";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public FindBlocksNode() {
        super(UUID.randomUUID(), "world.read.find_blocks");

        addInputPort(new BasePort(INPUT_REGION_ID, "Region", "Region to search", NodeDataType.REGION, this));
        addInputPort(new BasePort(INPUT_BLOCK_INFO_ID, "Block Info", "Target block state for exact matching", NodeDataType.BLOCK_INFO, this));
        addInputPort(new BasePort(INPUT_TARGET_BLOCK_TYPE_ID, "Target Block Type", "Target block registry id", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_EXACT_MATCH_ID, "Exact Match", "When Block Info is a BlockState, require exact state equality", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_MAX_RESULTS_ID, "Max Results", "Maximum number of matching positions to return", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_MAX_BLOCKS_ID, "Max Blocks", "Maximum number of block positions to scan", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_FOUND_BLOCKS_ID, "Found Blocks", "Matching block positions", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of matching positions returned", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_FIRST_POS_ID, "First Position", "First matching position", NodeDataType.BLOCK_POS, this));
        addOutputPort(new BasePort(OUTPUT_FOUND_ANY_ID, "Found Any", "Whether any matching block was found", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_VISITED_COUNT_ID, "Visited Count", "Number of block positions inspected", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_TOTAL_POSSIBLE_ID, "Total Possible", "Total positions in the region", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_HIT_LIMIT_ID, "Hit Limit", "Whether Max Results or Max Blocks stopped the search", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_STOPPED_REASON_ID, "Stopped Reason", "completed, max_results, max_blocks, or invalid", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether the search inputs and world were valid", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Error message when the search is invalid", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Finds matching block positions inside a region using block type or exact block state matching.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        SearchResult result = new SearchResult();

        Object regionObj = inputValues.get(INPUT_REGION_ID);
        Object target = resolveTargetInput();
        boolean exactMatch = inputValues.get(INPUT_EXACT_MATCH_ID) instanceof Boolean value && value;
        int maxResultsValue = inputValues.get(INPUT_MAX_RESULTS_ID) instanceof Number value
            ? Math.max(1, value.intValue())
            : maxResults;
        int maxBlocks = inputValues.get(INPUT_MAX_BLOCKS_ID) instanceof Number value
            ? Math.max(1, value.intValue())
            : WorldReadUtils.DEFAULT_MAX_BLOCKS;

        if (context == null || context.getWorld() == null) {
            publishInvalid("Execution context or world is missing.", 0L);
            return;
        }
        if (!(regionObj instanceof RegionData region) || !region.isComplete()) {
            publishInvalid("Region input is incomplete.", 0L);
            return;
        }
        if (target == null) {
            publishInvalid("Target Block Type or Block Info is required.", WorldReadUtils.volume(region));
            return;
        }

        long totalPossible = WorldReadUtils.volume(region);
        BlockPos min = region.getMinCorner();
        BlockPos max = region.getMaxCorner();
        if (min == null || max == null) {
            publishInvalid("Region bounds are invalid.", totalPossible);
            return;
        }

        blockSearch:
        for (BlockPos pos : BlockPos.iterate(min, max)) {
            if (result.visitedCount >= maxBlocks) {
                result.hitLimit = true;
                result.stoppedReason = "max_blocks";
                break;
            }

            BlockPos immutablePos = pos.toImmutable();
            result.visitedCount++;
            try {
                BlockState state = context.getWorld().getBlockState(immutablePos);
                if (WorldReadUtils.matchesBlock(state, target, exactMatch)) {
                    result.foundBlocks.add(immutablePos);
                    if (result.firstPos == null) {
                        result.firstPos = immutablePos;
                    }
                    if (result.foundBlocks.size() >= maxResultsValue) {
                        result.hitLimit = true;
                        result.stoppedReason = "max_results";
                        break blockSearch;
                    }
                }
            } catch (Exception e) {
                result.errorCount++;
                if (result.firstError.isEmpty()) {
                    result.firstError = "Error checking block at " + immutablePos + ": " + e.getMessage();
                }
                NodeCraft.LOGGER.warn("FindBlocksNode failed at {}: {}", immutablePos, e.getMessage());
            }
        }

        outputValues.put(OUTPUT_FOUND_BLOCKS_ID, result.foundBlocks);
        outputValues.put(OUTPUT_COUNT_ID, result.foundBlocks.size());
        outputValues.put(OUTPUT_FIRST_POS_ID, result.firstPos);
        outputValues.put(OUTPUT_FOUND_ANY_ID, !result.foundBlocks.isEmpty());
        outputValues.put(OUTPUT_VISITED_COUNT_ID, result.visitedCount);
        outputValues.put(OUTPUT_TOTAL_POSSIBLE_ID, (int) Math.min(Integer.MAX_VALUE, totalPossible));
        outputValues.put(OUTPUT_HIT_LIMIT_ID, result.hitLimit);
        outputValues.put(OUTPUT_STOPPED_REASON_ID, result.stoppedReason);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, result.errorCount > 0 ? result.firstError : "");
    }

    private @Nullable Object resolveTargetInput() {
        Object blockInfo = inputValues.get(INPUT_BLOCK_INFO_ID);
        if (blockInfo instanceof BlockState || WorldReadUtils.resolveBlockId(blockInfo) != null) {
            return blockInfo;
        }
        Object targetBlockType = inputValues.get(INPUT_TARGET_BLOCK_TYPE_ID);
        if (WorldReadUtils.resolveBlockId(targetBlockType) != null) {
            return targetBlockType;
        }
        return null;
    }

    private void publishInvalid(String error, long totalPossible) {
        outputValues.put(OUTPUT_FOUND_BLOCKS_ID, new BlockPosList());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_FIRST_POS_ID, null);
        outputValues.put(OUTPUT_FOUND_ANY_ID, false);
        outputValues.put(OUTPUT_VISITED_COUNT_ID, 0);
        outputValues.put(OUTPUT_TOTAL_POSSIBLE_ID, (int) Math.min(Integer.MAX_VALUE, totalPossible));
        outputValues.put(OUTPUT_HIT_LIMIT_ID, false);
        outputValues.put(OUTPUT_STOPPED_REASON_ID, "invalid");
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error);
    }

    public int getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(int maxResults) {
        if (maxResults > 0) {
            this.maxResults = maxResults;
            markDirty();
        }
    }

    private static final class SearchResult {
        final BlockPosList foundBlocks = new BlockPosList();
        int visitedCount;
        int errorCount;
        boolean hitLimit;
        String stoppedReason = "completed";
        String firstError = "";
        @Nullable
        BlockPos firstPos;
    }
}

package com.nodecraft.nodesystem.nodes.world.read;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "world.read.scan_region_by_type",
    displayName = "Scan Region By Type",
    description = "Scans a region and returns per-block-type counts for analysis and conditional building",
    category = "world.read",
    order = 9
)
public class ScanRegionByTypeNode extends BaseNode {
    private static final String INPUT_REGION_ID = "input_region";
    private static final String INPUT_INCLUDE_AIR_ID = "input_include_air";
    private static final String INPUT_TARGET_BLOCK_ID = "input_target_block";
    private static final String INPUT_MAX_BLOCKS_ID = "input_max_blocks";
    private static final String INPUT_SAMPLE_STEP_ID = "input_sample_step";

    private static final String OUTPUT_TYPE_COUNTS_ID = "output_type_counts";
    private static final String OUTPUT_BLOCK_TYPE_IDS_ID = "output_block_type_ids";
    private static final String OUTPUT_COUNTS_ID = "output_counts";
    private static final String OUTPUT_ENTRIES_ID = "output_entries";
    private static final String OUTPUT_TOTAL_SCANNED_ID = "output_total_scanned";
    private static final String OUTPUT_TOTAL_POSSIBLE_ID = "output_total_possible";
    private static final String OUTPUT_UNIQUE_TYPE_COUNT_ID = "output_unique_type_count";
    private static final String OUTPUT_MOST_COMMON_BLOCK_ID = "output_most_common_block";
    private static final String OUTPUT_MOST_COMMON_COUNT_ID = "output_most_common_count";
    private static final String OUTPUT_TARGET_COUNT_ID = "output_target_count";
    private static final String OUTPUT_HIT_LIMIT_ID = "output_hit_limit";
    private static final String OUTPUT_STOPPED_REASON_ID = "output_stopped_reason";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    private boolean includeAir = false;

    public ScanRegionByTypeNode() {
        super(UUID.randomUUID(), "world.read.scan_region_by_type");
        addInputPort(new BasePort(INPUT_REGION_ID, "Region", "Region to scan", NodeDataType.REGION, this));
        addInputPort(new BasePort(INPUT_INCLUDE_AIR_ID, "Include Air", "Whether air blocks should be counted", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_TARGET_BLOCK_ID, "Target Block", "Optional target block id or block state for direct counting", NodeDataType.BLOCK_INFO, this));
        addInputPort(new BasePort(INPUT_MAX_BLOCKS_ID, "Max Blocks", "Maximum number of positions to scan", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_SAMPLE_STEP_ID, "Sample Step", "Scan every Nth block on each axis", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_TYPE_COUNTS_ID, "Type Counts", "List of \"block_id=count\" pairs sorted by count desc", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_BLOCK_TYPE_IDS_ID, "Block Type IDs", "Sorted block type ids", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNTS_ID, "Counts", "Sorted counts matching Block Type IDs", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_ENTRIES_ID, "Entries", "List of {block_id,count} maps", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_TOTAL_SCANNED_ID, "Total Scanned", "Total scanned block positions", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_TOTAL_POSSIBLE_ID, "Total Possible", "Total positions in the region", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_UNIQUE_TYPE_COUNT_ID, "Unique Type Count", "Number of distinct block types in scan", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_MOST_COMMON_BLOCK_ID, "Most Common Block", "Most common block id in scan", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_MOST_COMMON_COUNT_ID, "Most Common Count", "Count of most common block id", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_TARGET_COUNT_ID, "Target Count", "Count of target block id if provided", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_HIT_LIMIT_ID, "Hit Limit", "Whether Max Blocks stopped the scan", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_STOPPED_REASON_ID, "Stopped Reason", "completed, max_blocks, or invalid", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether region scan was executed", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Error message when region scan is invalid", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Scans a region and returns per-block-type counts for analysis and conditional building";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<String> typeCounts = new ArrayList<>();
        List<String> blockTypeIds = new ArrayList<>();
        List<Integer> countsList = new ArrayList<>();
        List<Map<String, Object>> entries = new ArrayList<>();
        int totalScanned = 0;
        long totalPossible = 0L;
        int uniqueTypeCount = 0;
        String mostCommonBlock = "";
        int mostCommonCount = 0;
        int targetCount = 0;
        boolean hitLimit = false;
        String stoppedReason = "completed";
        boolean valid = false;
        String error = "";

        Object regionObj = inputValues.get(INPUT_REGION_ID);
        boolean includeAirValue = inputValues.get(INPUT_INCLUDE_AIR_ID) instanceof Boolean value ? value : includeAir;
        String targetId = WorldReadUtils.resolveBlockId(inputValues.get(INPUT_TARGET_BLOCK_ID));
        int maxBlocks = inputValues.get(INPUT_MAX_BLOCKS_ID) instanceof Number value
            ? Math.max(1, value.intValue())
            : WorldReadUtils.DEFAULT_MAX_BLOCKS;
        int sampleStep = inputValues.get(INPUT_SAMPLE_STEP_ID) instanceof Number value
            ? Math.max(1, value.intValue())
            : 1;

        if (context == null || context.getWorld() == null) {
            stoppedReason = "invalid";
            error = "Execution context or world is missing.";
        } else if (!(regionObj instanceof RegionData region) || !region.isComplete()) {
            stoppedReason = "invalid";
            error = "Region input is incomplete.";
        } else {
            totalPossible = WorldReadUtils.volume(region);
            Map<String, Integer> counts = new LinkedHashMap<>();
            BlockPos min = region.getMinCorner();
            BlockPos max = region.getMaxCorner();
            if (min != null && max != null) {
                valid = true;
                scan:
                for (int x = min.getX(); x <= max.getX(); x += sampleStep) {
                    for (int y = min.getY(); y <= max.getY(); y += sampleStep) {
                        for (int z = min.getZ(); z <= max.getZ(); z += sampleStep) {
                            if (totalScanned >= maxBlocks) {
                                hitLimit = true;
                                stoppedReason = "max_blocks";
                                break scan;
                            }
                            BlockPos pos = new BlockPos(x, y, z);
                            totalScanned++;
                            boolean isAir = context.getWorld().isAir(pos);
                            if (!includeAirValue && isAir) {
                                continue;
                            }
                            String blockId = WorldReadUtils.blockId(context.getWorld().getBlockState(pos));
                            counts.put(blockId, counts.getOrDefault(blockId, 0) + 1);
                        }
                    }
                }
            }

            List<Map.Entry<String, Integer>> sorted = counts.entrySet()
                .stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                    .thenComparing(Map.Entry.comparingByKey()))
                .toList();
            for (Map.Entry<String, Integer> entry : sorted) {
                typeCounts.add(entry.getKey() + "=" + entry.getValue());
                blockTypeIds.add(entry.getKey());
                countsList.add(entry.getValue());
                Map<String, Object> entryView = new LinkedHashMap<>();
                entryView.put("block_id", entry.getKey());
                entryView.put("count", entry.getValue());
                entries.add(entryView);
            }
            uniqueTypeCount = counts.size();
            if (!sorted.isEmpty()) {
                mostCommonBlock = sorted.get(0).getKey();
                mostCommonCount = sorted.get(0).getValue();
            }
            if (targetId != null) {
                targetCount = counts.getOrDefault(targetId, 0);
            }
        }

        outputValues.put(OUTPUT_TYPE_COUNTS_ID, typeCounts);
        outputValues.put(OUTPUT_BLOCK_TYPE_IDS_ID, blockTypeIds);
        outputValues.put(OUTPUT_COUNTS_ID, countsList);
        outputValues.put(OUTPUT_ENTRIES_ID, entries);
        outputValues.put(OUTPUT_TOTAL_SCANNED_ID, totalScanned);
        outputValues.put(OUTPUT_TOTAL_POSSIBLE_ID, (int) Math.min(Integer.MAX_VALUE, totalPossible));
        outputValues.put(OUTPUT_UNIQUE_TYPE_COUNT_ID, uniqueTypeCount);
        outputValues.put(OUTPUT_MOST_COMMON_BLOCK_ID, mostCommonBlock);
        outputValues.put(OUTPUT_MOST_COMMON_COUNT_ID, mostCommonCount);
        outputValues.put(OUTPUT_TARGET_COUNT_ID, targetCount);
        outputValues.put(OUTPUT_HIT_LIMIT_ID, hitLimit);
        outputValues.put(OUTPUT_STOPPED_REASON_ID, stoppedReason);
        outputValues.put(OUTPUT_VALID_ID, valid);
        outputValues.put(OUTPUT_ERROR_ID, error);
    }
}

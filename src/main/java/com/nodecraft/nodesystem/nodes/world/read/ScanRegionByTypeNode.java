package com.nodecraft.nodesystem.nodes.world.read;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.WORLD_READ,
    id = "world.read.scan_region_by_type",
    displayName = "Scan Region By Type",
    description = "Scans a region and returns per-block-type counts for analysis and conditional building",
    category = "world.read",
    order = 7
)
public class ScanRegionByTypeNode extends BaseNode {

    private static final String INPUT_REGION_ID = "input_region";
    private static final String INPUT_INCLUDE_AIR_ID = "input_include_air";
    private static final String INPUT_TARGET_BLOCK_ID = "input_target_block";
    private static final String INPUT_MAX_BLOCKS_ID = "input_max_blocks";
    private static final String INPUT_SAMPLE_STEP_ID = "input_sample_step";

    private static final String OUTPUT_BLOCK_TYPE_IDS_ID = "output_block_type_ids";
    private static final String OUTPUT_COUNTS_ID = "output_counts";
    private static final String OUTPUT_TOTAL_SCANNED_ID = "output_total_scanned";
    private static final String OUTPUT_TOTAL_POSSIBLE_ID = "output_total_possible";
    private static final String OUTPUT_UNIQUE_TYPE_COUNT_ID = "output_unique_type_count";
    private static final String OUTPUT_MOST_COMMON_BLOCK_ID = "output_most_common_block";
    private static final String OUTPUT_MOST_COMMON_COUNT_ID = "output_most_common_count";
    private static final String OUTPUT_TARGET_COUNT_ID = "output_target_count";
    private static final String OUTPUT_HIT_LIMIT_ID = "output_hit_limit";
    private static final String OUTPUT_COMPLETE_ID = "output_complete";
    private static final String OUTPUT_STOPPED_REASON_ID = "output_stopped_reason";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    @NodeProperty(displayName = "Include Air", category = "Filter", order = 1)
    private boolean includeAir = false;

    public ScanRegionByTypeNode() {
        super(UUID.randomUUID(), "world.read.scan_region_by_type");
        addInputPort(new BasePort(INPUT_REGION_ID, "Region", "Region to scan", NodeDataType.REGION, this));
        addInputPort(new BasePort(INPUT_INCLUDE_AIR_ID, "Include Air", "Whether air blocks should be counted", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_TARGET_BLOCK_ID, "Target Block Type", "Optional target block registry id for direct counting", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_MAX_BLOCKS_ID, "Max Blocks", "Maximum number of positions to scan", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_SAMPLE_STEP_ID, "Sample Step", "Scan every Nth block on each axis", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_BLOCK_TYPE_IDS_ID, "Block Type IDs", "Sorted block type ids", NodeDataType.STRING_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNTS_ID, "Counts", "Sorted counts matching Block Type IDs", NodeDataType.INTEGER_LIST, this));
        addOutputPort(new BasePort(OUTPUT_TOTAL_SCANNED_ID, "Total Scanned", "Total scanned block positions", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_TOTAL_POSSIBLE_ID, "Total Possible", "Total positions in the region", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_UNIQUE_TYPE_COUNT_ID, "Unique Type Count", "Number of distinct block types in scan", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_MOST_COMMON_BLOCK_ID, "Most Common Block", "Most common block id in scan", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_MOST_COMMON_COUNT_ID, "Most Common Count", "Count of most common block id", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_TARGET_COUNT_ID, "Target Count", "Count of target block id if provided", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_HIT_LIMIT_ID, "Hit Limit", "Whether Max Blocks stopped the scan", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_COMPLETE_ID, "Complete", "Whether the stepped region was fully scanned", NodeDataType.BOOLEAN, this));
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
        Boolean includeAirValue = OptionalPortDrive.resolveOptionalBoolean(this, INPUT_INCLUDE_AIR_ID, includeAir);
        if (includeAirValue == null) {
            publishInvalid("Include Air is connected but null or invalid.", 0L);
            return;
        }

        String targetId = null;
        if (OptionalPortDrive.isConnected(this, INPUT_TARGET_BLOCK_ID)) {
            Object raw = inputValues.get(INPUT_TARGET_BLOCK_ID);
            targetId = WorldReadUtils.resolveBlockId(raw);
            if (targetId == null) {
                publishInvalid("Target Block Type is connected but null or invalid.", 0L);
                return;
            }
        } else {
            targetId = WorldReadUtils.resolveBlockId(inputValues.get(INPUT_TARGET_BLOCK_ID));
        }

        Integer maxBlocks = WorldReadUtils.resolveBoundedWorldReadCount(
                this, INPUT_MAX_BLOCKS_ID, GenerationLimits.MAX_WORLD_READ_BLOCKS, WorldReadUtils.DEFAULT_MAX_BLOCKS);
        if (maxBlocks == null) {
            publishInvalid("Max Blocks must be an exact INTEGER between 1 and "
                    + GenerationLimits.MAX_WORLD_READ_BLOCKS + ".", 0L);
            return;
        }

        Integer sampleStep = WorldReadUtils.resolveExactStep(this, INPUT_SAMPLE_STEP_ID, 1);
        if (sampleStep == null) {
            publishInvalid("Sample Step must be an exact INTEGER greater than or equal to 1.", 0L);
            return;
        }

        Object regionObj = inputValues.get(INPUT_REGION_ID);
        if (!(regionObj instanceof RegionData region) || !region.isComplete()) {
            publishInvalid("Region input is incomplete.", 0L);
            return;
        }

        long totalPossible = WorldReadUtils.volume(region);
        if (totalPossible == WorldReadUtils.OVERFLOW) {
            publishInvalid("Region volume overflows integer coordinate range.", 0L);
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

        Map<String, Integer> counts = new LinkedHashMap<>();
        int totalScanned = 0;
        boolean hitLimit = false;
        String stoppedReason = "completed";

        scan:
        for (long x = min.getX(); x <= max.getX(); ) {
            for (long y = min.getY(); y <= max.getY(); ) {
                for (long z = min.getZ(); z <= max.getZ(); ) {
                    if (totalScanned >= maxBlocks) {
                        hitLimit = true;
                        stoppedReason = "max_blocks";
                        break scan;
                    }
                    BlockPos pos = new BlockPos((int) x, (int) y, (int) z);
                    totalScanned++;
                    boolean isAir = context.getWorld().isAir(pos);
                    if (!includeAirValue && isAir) {
                        Integer nextZ = WorldReadUtils.nextAxisCoordinate(z, sampleStep, max.getZ());
                        if (nextZ == null) {
                            break;
                        }
                        z = nextZ;
                        continue;
                    }
                    String blockId = WorldReadUtils.blockId(context.getWorld().getBlockState(pos));
                    counts.put(blockId, counts.getOrDefault(blockId, 0) + 1);

                    Integer nextZ = WorldReadUtils.nextAxisCoordinate(z, sampleStep, max.getZ());
                    if (nextZ == null) {
                        break;
                    }
                    z = nextZ;
                }
                Integer nextY = WorldReadUtils.nextAxisCoordinate(y, sampleStep, max.getY());
                if (nextY == null) {
                    break;
                }
                y = nextY;
            }
            Integer nextX = WorldReadUtils.nextAxisCoordinate(x, sampleStep, max.getX());
            if (nextX == null) {
                break;
            }
            x = nextX;
        }

        List<Map.Entry<String, Integer>> sorted = counts.entrySet()
            .stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                .thenComparing(Map.Entry.comparingByKey()))
            .toList();

        List<String> blockTypeIds = new ArrayList<>(sorted.size());
        List<Integer> countsList = new ArrayList<>(sorted.size());
        for (Map.Entry<String, Integer> entry : sorted) {
            blockTypeIds.add(entry.getKey());
            countsList.add(entry.getValue());
        }

        String mostCommonBlock = "";
        int mostCommonCount = 0;
        if (!sorted.isEmpty()) {
            mostCommonBlock = sorted.getFirst().getKey();
            mostCommonCount = sorted.getFirst().getValue();
        }
        int targetCount = targetId != null ? counts.getOrDefault(targetId, 0) : 0;

        outputValues.put(OUTPUT_BLOCK_TYPE_IDS_ID, blockTypeIds);
        outputValues.put(OUTPUT_COUNTS_ID, countsList);
        outputValues.put(OUTPUT_TOTAL_SCANNED_ID, totalScanned);
        outputValues.put(OUTPUT_TOTAL_POSSIBLE_ID, (int) Math.min(Integer.MAX_VALUE, totalPossible));
        outputValues.put(OUTPUT_UNIQUE_TYPE_COUNT_ID, counts.size());
        outputValues.put(OUTPUT_MOST_COMMON_BLOCK_ID, mostCommonBlock);
        outputValues.put(OUTPUT_MOST_COMMON_COUNT_ID, mostCommonCount);
        outputValues.put(OUTPUT_TARGET_COUNT_ID, targetCount);
        outputValues.put(OUTPUT_HIT_LIMIT_ID, hitLimit);
        outputValues.put(OUTPUT_COMPLETE_ID, !hitLimit);
        outputValues.put(OUTPUT_STOPPED_REASON_ID, stoppedReason);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void publishInvalid(String error, long totalPossible) {
        outputValues.put(OUTPUT_BLOCK_TYPE_IDS_ID, List.of());
        outputValues.put(OUTPUT_COUNTS_ID, List.of());
        outputValues.put(OUTPUT_TOTAL_SCANNED_ID, 0);
        outputValues.put(OUTPUT_TOTAL_POSSIBLE_ID, (int) Math.min(Integer.MAX_VALUE, Math.max(0L, totalPossible)));
        outputValues.put(OUTPUT_UNIQUE_TYPE_COUNT_ID, 0);
        outputValues.put(OUTPUT_MOST_COMMON_BLOCK_ID, "");
        outputValues.put(OUTPUT_MOST_COMMON_COUNT_ID, 0);
        outputValues.put(OUTPUT_TARGET_COUNT_ID, 0);
        outputValues.put(OUTPUT_HIT_LIMIT_ID, false);
        outputValues.put(OUTPUT_COMPLETE_ID, false);
        outputValues.put(OUTPUT_STOPPED_REASON_ID, "invalid");
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error);
    }

    public boolean isIncludeAir() {
        return includeAir;
    }

    public void setIncludeAir(boolean includeAir) {
        this.includeAir = includeAir;
        markDirty();
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("includeAir", includeAir);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Map<?, ?> map && map.get("includeAir") instanceof Boolean value) {
            includeAir = value;
        }
    }
}

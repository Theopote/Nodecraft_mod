package com.nodecraft.nodesystem.nodes.world.selection;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockListUtils;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "world.selection.multi_region",
    displayName = "Multi-Region Selection",
    description = "Aggregates region inputs and strict min/max block pairs into a region list with overall bounds.",
    category = "world.selection",
    order = 6
)
public class MultiRegionSelectionNode extends BaseNode {

    private static final String INPUT_REGIONS_ID = "input_regions";
    private static final String INPUT_REGION_A_ID = "input_region_a";
    private static final String INPUT_REGION_B_ID = "input_region_b";
    private static final String INPUT_REGION_C_ID = "input_region_c";
    private static final String INPUT_MIN_BLOCKS_ID = "input_min_blocks";
    private static final String INPUT_MAX_BLOCKS_ID = "input_max_blocks";

    private static final String OUTPUT_REGIONS_ID = "output_regions";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_BOUNDS_REGION_ID = "output_bounds_region";
    private static final String OUTPUT_MIN_BLOCK_ID = "output_min_block";
    private static final String OUTPUT_MAX_BLOCK_ID = "output_max_block";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public MultiRegionSelectionNode() {
        super(UUID.randomUUID(), "world.selection.multi_region");

        addInputPort(new BasePort(INPUT_REGIONS_ID, "Regions", "Optional REGION_LIST to aggregate", NodeDataType.REGION_LIST, this));
        addInputPort(new BasePort(INPUT_REGION_A_ID, "Region A", "Optional region input A", NodeDataType.REGION, this));
        addInputPort(new BasePort(INPUT_REGION_B_ID, "Region B", "Optional region input B", NodeDataType.REGION, this));
        addInputPort(new BasePort(INPUT_REGION_C_ID, "Region C", "Optional region input C", NodeDataType.REGION, this));
        addInputPort(new BasePort(INPUT_MIN_BLOCKS_ID, "Min Blocks", "Optional BLOCK_LIST of region min corners", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_MAX_BLOCKS_ID, "Max Blocks", "Optional BLOCK_LIST of region max corners", NodeDataType.BLOCK_LIST, this));

        addOutputPort(new BasePort(OUTPUT_REGIONS_ID, "Regions", "Resolved region list", NodeDataType.REGION_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Region count", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDS_REGION_ID, "Bounds Region", "Overall bounds covering all regions", NodeDataType.REGION, this));
        addOutputPort(new BasePort(OUTPUT_MIN_BLOCK_ID, "Bounds Min Block", "Overall min corner as a block position", NodeDataType.BLOCK_POS, this));
        addOutputPort(new BasePort(OUTPUT_MAX_BLOCK_ID, "Bounds Max Block", "Overall max corner as a block position", NodeDataType.BLOCK_POS, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether aggregation succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Error message when aggregation fails", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Aggregates region inputs and strict min/max block pairs into a region list with overall bounds.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<RegionData> regions = new ArrayList<>();

        String regionListError = appendStrictRegionList(regions, inputValues.get(INPUT_REGIONS_ID), INPUT_REGIONS_ID);
        if (regionListError != null) {
            publishInvalid(regionListError);
            return;
        }

        String aError = appendOptionalRegion(regions, INPUT_REGION_A_ID);
        if (aError != null) {
            publishInvalid(aError);
            return;
        }
        String bError = appendOptionalRegion(regions, INPUT_REGION_B_ID);
        if (bError != null) {
            publishInvalid(bError);
            return;
        }
        String cError = appendOptionalRegion(regions, INPUT_REGION_C_ID);
        if (cError != null) {
            publishInvalid(cError);
            return;
        }

        String pairError = appendRegionsFromMinMaxLists(regions);
        if (pairError != null) {
            publishInvalid(pairError);
            return;
        }

        List<RegionData> complete = new ArrayList<>();
        for (RegionData region : regions) {
            if (region == null || !region.isComplete()) {
                publishInvalid("All regions must be complete (both corners set).");
                return;
            }
            complete.add(region);
        }

        if (complete.isEmpty()) {
            publishInvalid("No regions were provided.");
            return;
        }

        BlockPos min = null;
        BlockPos max = null;
        for (RegionData region : complete) {
            BlockPos rMin = region.getMinCorner();
            BlockPos rMax = region.getMaxCorner();
            if (rMin == null || rMax == null) {
                publishInvalid("Region corners are incomplete.");
                return;
            }
            if (min == null || max == null) {
                min = rMin.toImmutable();
                max = rMax.toImmutable();
            } else {
                min = new BlockPos(
                    Math.min(min.getX(), rMin.getX()),
                    Math.min(min.getY(), rMin.getY()),
                    Math.min(min.getZ(), rMin.getZ())
                );
                max = new BlockPos(
                    Math.max(max.getX(), rMax.getX()),
                    Math.max(max.getY(), rMax.getY()),
                    Math.max(max.getZ(), rMax.getZ())
                );
            }
        }

        RegionData bounds = new RegionData(min, max);
        outputValues.put(OUTPUT_REGIONS_ID, List.copyOf(complete));
        outputValues.put(OUTPUT_COUNT_ID, complete.size());
        outputValues.put(OUTPUT_BOUNDS_REGION_ID, bounds);
        outputValues.put(OUTPUT_MIN_BLOCK_ID, min);
        outputValues.put(OUTPUT_MAX_BLOCK_ID, max);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private @Nullable String appendStrictRegionList(List<RegionData> out, Object value, String portId) {
        boolean connected = OptionalPortDrive.isConnected(this, portId);
        if (!connected && value == null) {
            return null;
        }
        if (!(value instanceof Collection<?> list)) {
            return "Regions must be a REGION_LIST.";
        }
        for (Object entry : list) {
            if (!(entry instanceof RegionData region) || !region.isComplete()) {
                return "Regions list contains a non-RegionData or incomplete region member.";
            }
            out.add(region);
        }
        return null;
    }

    private @Nullable String appendOptionalRegion(List<RegionData> out, String portId) {
        boolean connected = OptionalPortDrive.isConnected(this, portId);
        Object value = inputValues.get(portId);
        if (!connected && value == null) {
            return null;
        }
        if (!(value instanceof RegionData region)) {
            return portId + " must be a Region when connected or provided.";
        }
        if (!region.isComplete()) {
            return portId + " region is incomplete.";
        }
        out.add(region);
        return null;
    }

    private @Nullable String appendRegionsFromMinMaxLists(List<RegionData> out) {
        boolean minConnected = OptionalPortDrive.isConnected(this, INPUT_MIN_BLOCKS_ID);
        boolean maxConnected = OptionalPortDrive.isConnected(this, INPUT_MAX_BLOCKS_ID);
        Object minObj = inputValues.get(INPUT_MIN_BLOCKS_ID);
        Object maxObj = inputValues.get(INPUT_MAX_BLOCKS_ID);

        if (!minConnected && minObj == null && !maxConnected && maxObj == null) {
            return null;
        }
        if ((minObj == null) != (maxObj == null)) {
            return "Min Blocks and Max Blocks must both be provided together.";
        }
        if (minObj == null) {
            return null;
        }

        List<BlockPos> mins = BlockListUtils.resolveStrictBlockList(minObj);
        List<BlockPos> maxs = BlockListUtils.resolveStrictBlockList(maxObj);
        if (mins == null || maxs == null) {
            return "Min Blocks and Max Blocks must be strict BLOCK_LIST of BlockPos entries.";
        }
        if (mins.size() != maxs.size()) {
            return "Min Blocks and Max Blocks must have the same length.";
        }
        for (int i = 0; i < mins.size(); i++) {
            out.add(new RegionData(mins.get(i), maxs.get(i)));
        }
        return null;
    }

    private void publishInvalid(String error) {
        outputValues.put(OUTPUT_REGIONS_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_BOUNDS_REGION_ID, null);
        outputValues.put(OUTPUT_MIN_BLOCK_ID, BlockPos.ORIGIN);
        outputValues.put(OUTPUT_MAX_BLOCK_ID, BlockPos.ORIGIN);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }
}

package com.nodecraft.nodesystem.nodes.world.selection;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "world.selection.multi_region",
    displayName = "Multi-Region Selection",
    description = "Aggregates multiple non-contiguous region selections into a region list.",
    category = "world.selection",
    order = 7
)
public class MultiRegionSelectionNode extends BaseNode {

    private static final String INPUT_REGIONS_ID = "input_regions";
    private static final String INPUT_REGION_A_ID = "input_region_a";
    private static final String INPUT_REGION_B_ID = "input_region_b";
    private static final String INPUT_REGION_C_ID = "input_region_c";
    private static final String INPUT_MIN_POINTS_ID = "input_min_points";
    private static final String INPUT_MAX_POINTS_ID = "input_max_points";

    private static final String OUTPUT_REGIONS_ID = "output_regions";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_BOUNDS_REGION_ID = "output_bounds_region";
    private static final String OUTPUT_MIN_ID = "output_min";
    private static final String OUTPUT_MAX_ID = "output_max";
    private static final String OUTPUT_MIN_BLOCK_ID = "output_min_block";
    private static final String OUTPUT_MAX_BLOCK_ID = "output_max_block";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public MultiRegionSelectionNode() {
        super(UUID.randomUUID(), "world.selection.multi_region");

        addInputPort(new BasePort(INPUT_REGIONS_ID, "Regions", "Optional existing region list", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_REGION_A_ID, "Region A", "Optional region input A", NodeDataType.REGION, this));
        addInputPort(new BasePort(INPUT_REGION_B_ID, "Region B", "Optional region input B", NodeDataType.REGION, this));
        addInputPort(new BasePort(INPUT_REGION_C_ID, "Region C", "Optional region input C", NodeDataType.REGION, this));
        addInputPort(new BasePort(INPUT_MIN_POINTS_ID, "Min Points", "Optional region min corners list", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_MAX_POINTS_ID, "Max Points", "Optional region max corners list", NodeDataType.LIST, this));

        addOutputPort(new BasePort(OUTPUT_REGIONS_ID, "Regions", "Resolved region list", NodeDataType.REGION_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Region count", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDS_REGION_ID, "Bounds Region", "Overall bounds covering all regions", NodeDataType.REGION, this));
        addOutputPort(new BasePort(OUTPUT_MIN_ID, "Bounds Min", "Overall min corner", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_MAX_ID, "Bounds Max", "Overall max corner", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_MIN_BLOCK_ID, "Bounds Min Block", "Overall min corner as a block position", NodeDataType.BLOCK_POS, this));
        addOutputPort(new BasePort(OUTPUT_MAX_BLOCK_ID, "Bounds Max Block", "Overall max corner as a block position", NodeDataType.BLOCK_POS, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether at least one region exists", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Aggregates multiple non-contiguous region selections into a region list.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<RegionData> regions = new ArrayList<>();
        appendRegions(regions, inputValues.get(INPUT_REGIONS_ID));
        appendRegion(regions, inputValues.get(INPUT_REGION_A_ID));
        appendRegion(regions, inputValues.get(INPUT_REGION_B_ID));
        appendRegion(regions, inputValues.get(INPUT_REGION_C_ID));
        appendRegionsFromMinMaxLists(regions, inputValues.get(INPUT_MIN_POINTS_ID), inputValues.get(INPUT_MAX_POINTS_ID));

        List<RegionData> complete = new ArrayList<>();
        for (RegionData region : regions) {
            if (region != null && region.isComplete()) {
                complete.add(region);
            }
        }

        if (complete.isEmpty()) {
            outputValues.put(OUTPUT_REGIONS_ID, List.of());
            outputValues.put(OUTPUT_COUNT_ID, 0);
            outputValues.put(OUTPUT_BOUNDS_REGION_ID, null);
            outputValues.put(OUTPUT_MIN_ID, null);
            outputValues.put(OUTPUT_MAX_ID, null);
            outputValues.put(OUTPUT_MIN_BLOCK_ID, BlockPos.ORIGIN);
            outputValues.put(OUTPUT_MAX_BLOCK_ID, BlockPos.ORIGIN);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        BlockPos min = null;
        BlockPos max = null;
        for (RegionData region : complete) {
            BlockPos rMin = region.getMinCorner();
            BlockPos rMax = region.getMaxCorner();
            if (rMin == null || rMax == null) {
                continue;
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

        RegionData bounds = (min != null && max != null) ? new RegionData(min, max) : null;
        outputValues.put(OUTPUT_REGIONS_ID, List.copyOf(complete));
        outputValues.put(OUTPUT_COUNT_ID, complete.size());
        outputValues.put(OUTPUT_BOUNDS_REGION_ID, bounds);
        outputValues.put(OUTPUT_MIN_ID, min != null ? new Vector3d(min.getX(), min.getY(), min.getZ()) : null);
        outputValues.put(OUTPUT_MAX_ID, max != null ? new Vector3d(max.getX(), max.getY(), max.getZ()) : null);
        outputValues.put(OUTPUT_MIN_BLOCK_ID, min != null ? min : BlockPos.ORIGIN);
        outputValues.put(OUTPUT_MAX_BLOCK_ID, max != null ? max : BlockPos.ORIGIN);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void appendRegions(List<RegionData> out, Object value) {
        if (!(value instanceof List<?> list)) {
            return;
        }
        for (Object entry : list) {
            appendRegion(out, entry);
        }
    }

    private void appendRegion(List<RegionData> out, Object value) {
        if (value instanceof RegionData region) {
            out.add(region);
        }
    }

    private void appendRegionsFromMinMaxLists(List<RegionData> out, Object minObj, Object maxObj) {
        if (!(minObj instanceof List<?> mins) || !(maxObj instanceof List<?> maxs)) {
            return;
        }
        int count = Math.min(mins.size(), maxs.size());
        for (int i = 0; i < count; i++) {
            BlockPos min = WorldSelectionResolveUtils.resolveBlockPos(mins.get(i));
            BlockPos max = WorldSelectionResolveUtils.resolveBlockPos(maxs.get(i));
            if (min != null && max != null) {
                out.add(new RegionData(min, max));
            }
        }
    }
}

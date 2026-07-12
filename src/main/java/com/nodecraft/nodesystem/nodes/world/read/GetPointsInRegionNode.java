package com.nodecraft.nodesystem.nodes.world.read;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "world.read.get_points_in_region",
    displayName = "Get Points In Region",
    description = "Generates or filters block positions inside a region with optional uniform sampling.",
    category = "world.read",
    order = 5
)
public class GetPointsInRegionNode extends BaseNode {

    private boolean filterFromCoordinates = false;

    private static final String INPUT_REGION_ID = "input_region";
    private static final String INPUT_COORDINATES_ID = "input_coordinates";
    private static final String INPUT_MAX_POINTS_ID = "input_max_points";
    private static final String INPUT_USE_INPUT_COORDINATES_ID = "input_use_input_coordinates";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_TOTAL_POSSIBLE_ID = "output_total_possible";
    private static final String OUTPUT_SAMPLED_ID = "output_sampled";
    private static final String OUTPUT_HIT_LIMIT_ID = "output_hit_limit";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public GetPointsInRegionNode() {
        super(UUID.randomUUID(), "world.read.get_points_in_region");

        addInputPort(new BasePort(INPUT_REGION_ID, "Region", "Region to read", NodeDataType.REGION, this));
        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", "Optional coordinates to filter", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_MAX_POINTS_ID, "Max Points", "Maximum returned points; 0 uses the global generation cap", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_USE_INPUT_COORDINATES_ID, "Use Input Coordinates", "Filter input coordinates instead of generating region points", NodeDataType.BOOLEAN, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Block positions inside the region", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Returned point count", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_TOTAL_POSSIBLE_ID, "Total Possible", "Total possible points before Max Points is applied", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_SAMPLED_ID, "Sampled", "Whether generated output was uniformly sampled", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_HIT_LIMIT_ID, "Hit Limit", "Whether Max Points limited the output", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether the region input was valid", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Generates or filters block positions inside a region with optional uniform sampling.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        BlockPosList result = new BlockPosList();
        long totalPossible = 0L;
        boolean sampled = false;
        boolean hitLimit = false;
        boolean valid = false;

        Object regionObj = inputValues.get(INPUT_REGION_ID);
        Object coordinatesObj = inputValues.get(INPUT_COORDINATES_ID);
        int maxPoints = WorldReadUtils.resolveMaxListElements(inputValues.get(INPUT_MAX_POINTS_ID));
        boolean useInputCoordinates = inputValues.get(INPUT_USE_INPUT_COORDINATES_ID) instanceof Boolean value
            ? value
            : filterFromCoordinates;

        if (regionObj instanceof RegionData region && region.isComplete()) {
            totalPossible = WorldReadUtils.volume(region);
            valid = true;
            if (useInputCoordinates && coordinatesObj instanceof BlockPosList coordinates) {
                filterPointsInRegion(coordinates, region, maxPoints, result);
                hitLimit = result.size() >= maxPoints;
            } else {
                sampled = totalPossible > maxPoints;
                generatePointsInRegion(region, maxPoints, sampled, result);
                hitLimit = sampled || result.size() >= maxPoints;
            }
        }

        outputValues.put(OUTPUT_POINTS_ID, result);
        outputValues.put(OUTPUT_COUNT_ID, result.size());
        outputValues.put(OUTPUT_TOTAL_POSSIBLE_ID, (int) Math.min(Integer.MAX_VALUE, totalPossible));
        outputValues.put(OUTPUT_SAMPLED_ID, sampled);
        outputValues.put(OUTPUT_HIT_LIMIT_ID, hitLimit);
        outputValues.put(OUTPUT_VALID_ID, valid);
    }

    private void filterPointsInRegion(BlockPosList coordinates, RegionData region, int maxPoints, BlockPosList result) {
        BlockPos min = region.getMinCorner();
        BlockPos max = region.getMaxCorner();
        if (min == null || max == null) {
            return;
        }
        for (BlockPos pos : coordinates) {
            if (isPointInRegion(pos, min, max)) {
                result.add(pos);
                if (result.size() >= maxPoints) {
                    return;
                }
            }
        }
    }

    private void generatePointsInRegion(RegionData region, int maxPoints, boolean sampled, BlockPosList result) {
        BlockPos min = region.getMinCorner();
        BlockPos max = region.getMaxCorner();
        if (min == null || max == null) {
            return;
        }

        if (sampled) {
            sampleRegion(min, max, maxPoints, result);
            return;
        }

        for (BlockPos pos : BlockPos.iterate(min, max)) {
            result.add(pos);
            if (result.size() >= maxPoints) {
                return;
            }
        }
    }

    private void sampleRegion(BlockPos min, BlockPos max, int maxPoints, BlockPosList result) {
        int sizeX = max.getX() - min.getX() + 1;
        int sizeY = max.getY() - min.getY() + 1;
        int sizeZ = max.getZ() - min.getZ() + 1;
        long totalPoints = (long) sizeX * sizeY * sizeZ;
        double stepFactor = Math.cbrt((double) totalPoints / Math.max(1, maxPoints));
        int stepX = Math.max(1, (int) Math.ceil(stepFactor));
        int stepY = Math.max(1, (int) Math.ceil(stepFactor));
        int stepZ = Math.max(1, (int) Math.ceil(stepFactor));

        for (int x = min.getX(); x <= max.getX(); x += stepX) {
            for (int y = min.getY(); y <= max.getY(); y += stepY) {
                for (int z = min.getZ(); z <= max.getZ(); z += stepZ) {
                    result.add(new BlockPos(x, y, z));
                    if (result.size() >= maxPoints) {
                        return;
                    }
                }
            }
        }
    }

    private boolean isPointInRegion(BlockPos pos, BlockPos min, BlockPos max) {
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
            && pos.getY() >= min.getY() && pos.getY() <= max.getY()
            && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    public boolean isFilterFromCoordinates() {
        return filterFromCoordinates;
    }

    public void setFilterFromCoordinates(boolean filterFromCoordinates) {
        this.filterFromCoordinates = filterFromCoordinates;
        markDirty();
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("filterFromCoordinates", filterFromCoordinates);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Map<?, ?> stateMap && stateMap.get("filterFromCoordinates") instanceof Boolean value) {
            setFilterFromCoordinates(value);
        }
    }
}

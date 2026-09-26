package com.nodecraft.nodesystem.nodes.world.read;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockListUtils;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "world.read.get_block_positions_in_region",
    displayName = "Get Block Positions In Region",
    description = "Generates or filters block positions inside a region. Under budget: complete enumeration. Over budget: uniform deterministic sampling.",
    category = "world.read",
    order = 4
)
public class GetBlockPositionsInRegionNode extends BaseNode {

    @NodeProperty(displayName = "Use Input Coordinates", category = "Mode", order = 1)
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
    private static final String OUTPUT_COMPLETE_ID = "output_complete";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public GetBlockPositionsInRegionNode() {
        super(UUID.randomUUID(), "world.read.get_block_positions_in_region");

        addInputPort(new BasePort(INPUT_REGION_ID, "Region", "Region to enumerate", NodeDataType.REGION, this));
        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", "Optional BLOCK_LIST to filter by region", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_MAX_POINTS_ID, "Max Points", "Maximum returned block positions", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_USE_INPUT_COORDINATES_ID, "Use Input Coordinates", "Filter input coordinates instead of generating region positions", NodeDataType.BOOLEAN, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Block Positions", "Block positions inside the region", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Returned position count", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_TOTAL_POSSIBLE_ID, "Total Possible", "Total possible positions before Max Points is applied", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_SAMPLED_ID, "Sampled", "Whether generated output used uniform sampling", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_HIT_LIMIT_ID, "Hit Limit", "Whether Max Points limited the output", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_COMPLETE_ID, "Complete", "Whether enumeration finished without sampling or truncation", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether the region input was valid", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Error message when enumeration fails", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Generates or filters block positions inside a region. Under budget: complete enumeration. Over budget: uniform deterministic sampling.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Boolean useInputCoordinates = OptionalPortDrive.resolveOptionalBoolean(
                this, INPUT_USE_INPUT_COORDINATES_ID, filterFromCoordinates);
        if (useInputCoordinates == null) {
            publishInvalid("Use Input Coordinates is connected but null or invalid.");
            return;
        }

        Integer maxPoints = WorldReadUtils.resolveBoundedWorldReadCount(
                this, INPUT_MAX_POINTS_ID, GenerationLimits.MAX_WORLD_READ_BLOCKS, WorldReadUtils.DEFAULT_MAX_BLOCKS);
        if (maxPoints == null) {
            publishInvalid("Max Points must be an exact INTEGER between 1 and "
                    + GenerationLimits.MAX_WORLD_READ_BLOCKS + ".");
            return;
        }

        Object regionObj = inputValues.get(INPUT_REGION_ID);
        if (!(regionObj instanceof RegionData region) || !region.isComplete()) {
            publishInvalid("Region input is incomplete.");
            return;
        }

        long totalPossible = WorldReadUtils.volume(region);
        if (totalPossible == WorldReadUtils.OVERFLOW) {
            publishInvalid("Region volume overflows integer coordinate range.");
            return;
        }

        BlockPosList result = new BlockPosList();
        boolean sampled = false;
        boolean hitLimit = false;

        if (useInputCoordinates) {
            if (OptionalPortDrive.isConnected(this, INPUT_COORDINATES_ID)
                    || inputValues.get(INPUT_COORDINATES_ID) != null) {
                List<BlockPos> coordinates = BlockListUtils.resolveStrictBlockList(inputValues.get(INPUT_COORDINATES_ID));
                if (coordinates == null) {
                    publishInvalid("Coordinates must be a strict BLOCK_LIST of BlockPos entries.");
                    return;
                }
                hitLimit = filterPointsInRegion(coordinates, region, maxPoints, result);
            } else {
                publishInvalid("Coordinates are required when Use Input Coordinates is true.");
                return;
            }
        } else {
            sampled = totalPossible > maxPoints;
            generatePointsInRegion(region, maxPoints, sampled, result);
            hitLimit = sampled;
        }

        boolean complete = !sampled && !hitLimit;

        outputValues.put(OUTPUT_POINTS_ID, result);
        outputValues.put(OUTPUT_COUNT_ID, result.size());
        outputValues.put(OUTPUT_TOTAL_POSSIBLE_ID, (int) Math.min(Integer.MAX_VALUE, totalPossible));
        outputValues.put(OUTPUT_SAMPLED_ID, sampled);
        outputValues.put(OUTPUT_HIT_LIMIT_ID, hitLimit);
        outputValues.put(OUTPUT_COMPLETE_ID, complete);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private boolean filterPointsInRegion(List<BlockPos> coordinates, RegionData region, int maxPoints, BlockPosList result) {
        BlockPos min = region.getMinCorner();
        BlockPos max = region.getMaxCorner();
        if (min == null || max == null) {
            return false;
        }
        for (BlockPos pos : coordinates) {
            if (region.contains(pos)) {
                result.add(pos);
                if (result.size() >= maxPoints) {
                    return true;
                }
            }
        }
        return false;
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
            result.add(pos.toImmutable());
            if (result.size() >= maxPoints) {
                return;
            }
        }
    }

    private void sampleRegion(BlockPos min, BlockPos max, int maxPoints, BlockPosList result) {
        long sizeX = WorldReadUtils.axisSpan(min.getX(), max.getX());
        long sizeY = WorldReadUtils.axisSpan(min.getY(), max.getY());
        long sizeZ = WorldReadUtils.axisSpan(min.getZ(), max.getZ());
        if (sizeX < 0 || sizeY < 0 || sizeZ < 0) {
            return;
        }
        long totalPoints;
        try {
            totalPoints = Math.multiplyExact(Math.multiplyExact(sizeX, sizeY), sizeZ);
        } catch (ArithmeticException ignored) {
            return;
        }
        double stepFactor = Math.cbrt((double) totalPoints / Math.max(1, maxPoints));
        int stepX = Math.max(1, (int) Math.ceil(stepFactor));
        int stepY = Math.max(1, (int) Math.ceil(stepFactor));
        int stepZ = Math.max(1, (int) Math.ceil(stepFactor));

        for (long x = min.getX(); x <= max.getX(); ) {
            for (long y = min.getY(); y <= max.getY(); ) {
                for (long z = min.getZ(); z <= max.getZ(); ) {
                    result.add(new BlockPos((int) x, (int) y, (int) z));
                    if (result.size() >= maxPoints) {
                        return;
                    }
                    Integer nextZ = WorldReadUtils.nextAxisCoordinate(z, stepZ, max.getZ());
                    if (nextZ == null) {
                        break;
                    }
                    z = nextZ;
                }
                Integer nextY = WorldReadUtils.nextAxisCoordinate(y, stepY, max.getY());
                if (nextY == null) {
                    break;
                }
                y = nextY;
            }
            Integer nextX = WorldReadUtils.nextAxisCoordinate(x, stepX, max.getX());
            if (nextX == null) {
                break;
            }
            x = nextX;
        }
    }

    private void publishInvalid(String error) {
        outputValues.put(OUTPUT_POINTS_ID, new BlockPosList());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_TOTAL_POSSIBLE_ID, 0);
        outputValues.put(OUTPUT_SAMPLED_ID, false);
        outputValues.put(OUTPUT_HIT_LIMIT_ID, false);
        outputValues.put(OUTPUT_COMPLETE_ID, false);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
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

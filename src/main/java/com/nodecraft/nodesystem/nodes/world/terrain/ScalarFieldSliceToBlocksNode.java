package com.nodecraft.nodesystem.nodes.world.terrain;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.datatypes.ScalarFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.BlockPosList;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "world.terrain.scalar_field_slice_to_blocks",
    displayName = "Scalar Field Slice To Blocks",
    description = "Visualizes scalar field values on a horizontal slice using low/high block thresholds.",
    category = "world.terrain",
    order = 18
)
public class ScalarFieldSliceToBlocksNode extends BaseNode {

    private static final String INPUT_REGION_ID = "input_region";
    private static final String INPUT_SCALAR_FIELD_ID = "input_scalar_field";
    private static final String INPUT_SLICE_Y_ID = "input_slice_y";
    private static final String INPUT_THRESHOLD_ID = "input_threshold";
    private static final String INPUT_LOW_BLOCK_ID = "input_low_block";
    private static final String INPUT_HIGH_BLOCK_ID = "input_high_block";
    private static final String INPUT_STEP_ID = "input_step";
    private static final String INPUT_ONLY_HIGH_BLOCKS_ID = "input_only_high_blocks";
    private static final String INPUT_MAX_PLACEMENTS_ID = "input_max_placements";

    private static final String OUTPUT_BLOCK_PLACEMENTS_ID = "output_block_placements";
    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_STEP_USED_ID = "output_step_used";
    private static final String OUTPUT_HIT_LIMIT_ID = "output_hit_limit";
    private static final String OUTPUT_STOPPED_REASON_ID = "output_stopped_reason";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    @NodeProperty(displayName = "Slice Y", category = "Slice", order = 1)
    private int sliceY = 64;

    @NodeProperty(displayName = "Threshold", category = "Slice", order = 2)
    private double threshold = 0.5d;

    @NodeProperty(displayName = "Low Block", category = "Slice", order = 3)
    private String lowBlock = "minecraft:gray_concrete";

    @NodeProperty(displayName = "High Block", category = "Slice", order = 4)
    private String highBlock = "minecraft:lime_concrete";

    @NodeProperty(displayName = "Step", category = "Sampling", order = 5)
    private int step = 1;

    @NodeProperty(displayName = "Only High Blocks", category = "Sampling", order = 6)
    private boolean onlyHighBlocks = false;

    @NodeProperty(displayName = "Max Placements", category = "Safety", order = 7)
    private int maxPlacements = TerrainNodeUtils.DEFAULT_MAX_PLACEMENTS;

    public ScalarFieldSliceToBlocksNode() {
        super(UUID.randomUUID(), "world.terrain.scalar_field_slice_to_blocks");

        addInputPort(new BasePort(INPUT_REGION_ID, "Region", "Optional region to sample; defaults to a safe 64x64 area when omitted", NodeDataType.REGION, this));
        addInputPort(new BasePort(INPUT_SCALAR_FIELD_ID, "Scalar Field", "Field to visualize", NodeDataType.SCALAR_FIELD, this));
        addInputPort(new BasePort(INPUT_SLICE_Y_ID, "Slice Y", "Y level for field sampling", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_THRESHOLD_ID, "Threshold", "Value threshold for high/low split", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_LOW_BLOCK_ID, "Low Block", "Block below threshold", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_HIGH_BLOCK_ID, "High Block", "Block at or above threshold", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_STEP_ID, "Step", "Sampling stride in blocks", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_ONLY_HIGH_BLOCKS_ID, "Only High Blocks", "When true, skip values below the threshold", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_MAX_PLACEMENTS_ID, "Max Placements", "Maximum slice placements before stopping", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_BLOCK_PLACEMENTS_ID, "Block Placements", "Slice visualization placements", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Sampled slice points", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Generated placement count", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_STEP_USED_ID, "Step Used", "Actual sampling step", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_HIT_LIMIT_ID, "Hit Limit", "True when Max Placements stopped generation", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_STOPPED_REASON_ID, "Stopped Reason", "Reason generation stopped early", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether generation succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Error message when generation failed", NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object regionObj = inputValues.get(INPUT_REGION_ID);
        Object fieldObj = inputValues.get(INPUT_SCALAR_FIELD_ID);

        if (!(fieldObj instanceof ScalarFieldData field)) {
            outputValues.put(OUTPUT_BLOCK_PLACEMENTS_ID, List.of());
            outputValues.put(OUTPUT_POINTS_ID, new BlockPosList());
            outputValues.put(OUTPUT_COUNT_ID, 0);
            outputValues.put(OUTPUT_STEP_USED_ID, Math.max(1, getInputInt(INPUT_STEP_ID, step)));
            outputValues.put(OUTPUT_HIT_LIMIT_ID, false);
            outputValues.put(OUTPUT_STOPPED_REASON_ID, "");
            outputValues.put(OUTPUT_VALID_ID, false);
            outputValues.put(OUTPUT_ERROR_ID, "Missing scalar field input.");
            return;
        }

        RegionBounds bounds = resolveBounds(regionObj instanceof RegionData value ? value : null);

        int y = clamp(getInputInt(INPUT_SLICE_Y_ID, sliceY), bounds.minY, bounds.maxY);
        double cut = getInputDouble(INPUT_THRESHOLD_ID, threshold);
        String low = getInputString(INPUT_LOW_BLOCK_ID, lowBlock);
        String high = getInputString(INPUT_HIGH_BLOCK_ID, highBlock);
        int resolvedStep = Math.max(1, getInputInt(INPUT_STEP_ID, step));
        boolean resolvedOnlyHighBlocks = getInputBoolean(INPUT_ONLY_HIGH_BLOCKS_ID, onlyHighBlocks);
        int resolvedMaxPlacements = Math.max(1, getInputInt(INPUT_MAX_PLACEMENTS_ID, maxPlacements));

        List<BlockPlacementData> placements = new ArrayList<>();
        BlockPosList points = new BlockPosList();
        Vector3d samplePoint = new Vector3d();
        boolean hitLimit = false;
        String stoppedReason = "";

        for (int x = bounds.minX; x <= bounds.maxX; x += resolvedStep) {
            for (int z = bounds.minZ; z <= bounds.maxZ; z += resolvedStep) {
                samplePoint.set(x, y, z);
                double value = sanitizeFinite(field.sampleScalar(samplePoint), 0.0d);
                boolean isHigh = value >= cut;
                if (resolvedOnlyHighBlocks && !isHigh) {
                    continue;
                }
                if (placements.size() >= resolvedMaxPlacements) {
                    hitLimit = true;
                    stoppedReason = "max_placements";
                    break;
                }
                String block = isHigh ? high : low;

                BlockPos pos = new BlockPos(x, y, z);
                placements.add(new BlockPlacementData(pos, block));
                points.add(pos);
            }
            if (hitLimit) {
                break;
            }
        }

        outputValues.put(OUTPUT_BLOCK_PLACEMENTS_ID, placements);
        outputValues.put(OUTPUT_POINTS_ID, points);
        outputValues.put(OUTPUT_COUNT_ID, placements.size());
        outputValues.put(OUTPUT_STEP_USED_ID, resolvedStep);
        outputValues.put(OUTPUT_HIT_LIMIT_ID, hitLimit);
        outputValues.put(OUTPUT_STOPPED_REASON_ID, stoppedReason);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private int getInputInt(String portId, int fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private double getInputDouble(String portId, double fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private String getInputString(String portId, String fallback) {
        Object value = inputValues.get(portId);
        return (value instanceof String text && !text.isBlank()) ? text : fallback;
    }

    private boolean getInputBoolean(String portId, boolean fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Boolean flag ? flag : fallback;
    }

    private double sanitizeFinite(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    private RegionBounds resolveBounds(@Nullable RegionData region) {
        if (region == null || !region.isComplete()) {
            return new RegionBounds(TerrainNodeUtils.DEFAULT_MIN_X, TerrainNodeUtils.DEFAULT_MAX_X,
                TerrainNodeUtils.DEFAULT_MIN_Y, TerrainNodeUtils.DEFAULT_MAX_Y,
                TerrainNodeUtils.DEFAULT_MIN_Z, TerrainNodeUtils.DEFAULT_MAX_Z);
        }

        BlockPos min = region.getMinCorner();
        BlockPos max = region.getMaxCorner();
        if (min == null || max == null) {
            return new RegionBounds(TerrainNodeUtils.DEFAULT_MIN_X, TerrainNodeUtils.DEFAULT_MAX_X,
                TerrainNodeUtils.DEFAULT_MIN_Y, TerrainNodeUtils.DEFAULT_MAX_Y,
                TerrainNodeUtils.DEFAULT_MIN_Z, TerrainNodeUtils.DEFAULT_MAX_Z);
        }

        return new RegionBounds(min.getX(), max.getX(), min.getY(), max.getY(), min.getZ(), max.getZ());
    }

    private int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }

    private record RegionBounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
    }
}

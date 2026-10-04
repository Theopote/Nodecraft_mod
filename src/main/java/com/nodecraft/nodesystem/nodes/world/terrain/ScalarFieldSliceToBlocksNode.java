package com.nodecraft.nodesystem.nodes.world.terrain;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.datatypes.ScalarFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.BlockSpace;
import com.nodecraft.nodesystem.util.GenerationLimits;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "world.terrain.scalar_field_slice_to_blocks",
    displayName = "Scalar Field Slice To Blocks",
    description = "Visualizes scalar field values on a horizontal slice using low/high block thresholds. "
        + "Samples at block cell centers.",
    category = "world.terrain",
    order = 17
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
    private static final String OUTPUT_COMPLETE_ID = "output_complete";
    private static final String OUTPUT_STOPPED_REASON_ID = "output_stopped_reason";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    @NodeProperty(displayName = "Slice Y", category = "Slice", order = 1)
    private int sliceY = 64;

    @NodeProperty(displayName = "Threshold", category = "Slice", order = 2)
    private double threshold = 0.5d;

    @NodeProperty(displayName = "Low Block", category = "Slice", order = 3,
        description = "Required unless Only High Blocks is true")
    private String lowBlock = "";

    @NodeProperty(displayName = "High Block", category = "Slice", order = 4,
        description = "Required block at or above threshold")
    private String highBlock = "";

    @NodeProperty(displayName = "Step", category = "Sampling", order = 5)
    private int step = 1;

    @NodeProperty(displayName = "Only High Blocks", category = "Sampling", order = 6)
    private boolean onlyHighBlocks = false;

    @NodeProperty(displayName = "Max Placements", category = "Safety", order = 7)
    private int maxPlacements = TerrainNodeUtils.DEFAULT_MAX_PLACEMENTS;

    public ScalarFieldSliceToBlocksNode() {
        super(UUID.randomUUID(), "world.terrain.scalar_field_slice_to_blocks");

        addInputPort(new BasePort(INPUT_REGION_ID, "Region",
            "Optional region to sample; defaults to modeling domain (−32..31, Y −64..319) when omitted",
            NodeDataType.REGION, this));
        addInputPort(new BasePort(INPUT_SCALAR_FIELD_ID, "Scalar Field",
            "Field to visualize", NodeDataType.SCALAR_FIELD, this));
        addInputPort(new BasePort(INPUT_SLICE_Y_ID, "Slice Y",
            "Y level for field sampling", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_THRESHOLD_ID, "Threshold",
            "Value threshold for high/low split", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_LOW_BLOCK_ID, "Low Block",
            "Block below threshold; required unless Only High Blocks", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_HIGH_BLOCK_ID, "High Block",
            "Required block at or above threshold", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_STEP_ID, "Step",
            "Sampling stride in blocks", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_ONLY_HIGH_BLOCKS_ID, "Only High Blocks",
            "When true, skip values below the threshold", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_MAX_PLACEMENTS_ID, "Max Placements",
            "Maximum slice placements before stopping (hard-capped)", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_BLOCK_PLACEMENTS_ID, "Block Placements",
            "Slice visualization placements", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points",
            "Sampled slice block positions", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count",
            "Generated placement count", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_STEP_USED_ID, "Step Used",
            "Actual sampling step", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_HIT_LIMIT_ID, "Hit Limit",
            "True when Max Placements stopped generation", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_COMPLETE_ID, "Complete",
            "False when Hit Limit stopped generation early", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_STOPPED_REASON_ID, "Stopped Reason",
            "Reason generation stopped early", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "Whether generation succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error",
            "Error message when generation failed", NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        if (!(inputValues.get(INPUT_SCALAR_FIELD_ID) instanceof ScalarFieldData field)) {
            writeInvalid("Missing scalar field input.", 1);
            return;
        }

        RegionData region = TerrainNodeUtils.resolveOptionalRegion(this, INPUT_REGION_ID);
        if (TerrainNodeUtils.isInvalidRegionMarker(region)) {
            writeInvalid("Region is connected but incomplete or invalid.", 1);
            return;
        }
        RegionBounds bounds = resolveBounds(region);

        Integer resolvedSliceY = TerrainNodeUtils.resolveOptionalExactInteger(this, INPUT_SLICE_Y_ID, sliceY);
        if (resolvedSliceY == null) {
            writeInvalid("Slice Y must be an exact INTEGER.", 1);
            return;
        }
        if (resolvedSliceY < bounds.minY || resolvedSliceY > bounds.maxY) {
            writeInvalid("Slice Y (" + resolvedSliceY + ") is outside region Y bounds ("
                + bounds.minY + ".." + bounds.maxY + ").", 1);
            return;
        }
        int y = resolvedSliceY;

        Double cut = TerrainNodeUtils.resolveOptionalFiniteDouble(this, INPUT_THRESHOLD_ID, threshold);
        if (cut == null) {
            writeInvalid("Threshold must be a finite DOUBLE.", 1);
            return;
        }

        Integer resolvedStep = TerrainNodeUtils.resolveOptionalExactInteger(this, INPUT_STEP_ID, step);
        if (resolvedStep == null || resolvedStep < 1) {
            writeInvalid("Step must be an exact INTEGER >= 1.", 1);
            return;
        }

        Boolean resolvedOnlyHighBlocks = TerrainNodeUtils.resolveOptionalBoolean(
            this, INPUT_ONLY_HIGH_BLOCKS_ID, onlyHighBlocks);
        if (resolvedOnlyHighBlocks == null) {
            writeInvalid("Only High Blocks is connected but null or invalid.", resolvedStep);
            return;
        }

        Integer resolvedMaxPlacements = TerrainNodeUtils.resolveUserBudgetExactInteger(
            this, INPUT_MAX_PLACEMENTS_ID, maxPlacements, GenerationLimits.MAX_TERRAIN_PLACEMENTS);
        if (resolvedMaxPlacements == null) {
            writeInvalid("Max Placements must be an exact INTEGER between 1 and "
                + GenerationLimits.MAX_TERRAIN_PLACEMENTS + ".", resolvedStep);
            return;
        }

        String high = TerrainNodeUtils.resolveOptionalString(this, INPUT_HIGH_BLOCK_ID, highBlock);
        if (high == null) {
            writeInvalid("High Block is required.", resolvedStep);
            return;
        }
        String highError = TerrainNodeUtils.preflightBlockIdError(high);
        if (highError != null) {
            writeInvalid(highError, resolvedStep);
            return;
        }

        String low = TerrainNodeUtils.resolveOptionalString(this, INPUT_LOW_BLOCK_ID, lowBlock);
        if (!resolvedOnlyHighBlocks && low == null) {
            writeInvalid("Low Block is required unless Only High Blocks is true.", resolvedStep);
            return;
        }
        if (low != null) {
            String lowError = TerrainNodeUtils.preflightBlockIdError(low);
            if (lowError != null) {
                writeInvalid(lowError, resolvedStep);
                return;
            }
        }

        List<BlockPlacementData> placements = new ArrayList<>();
        BlockPosList points = new BlockPosList();
        Vector3d samplePoint = new Vector3d();
        boolean hitLimit = false;
        String stoppedReason = "";

        int x = bounds.minX;
        while (true) {
            int z = bounds.minZ;
            while (true) {
                samplePoint.set(
                    x + BlockSpace.CELL_CENTER_OFFSET,
                    y + BlockSpace.CELL_CENTER_OFFSET,
                    z + BlockSpace.CELL_CENTER_OFFSET
                );
                double value = field.sampleScalar(samplePoint);
                if (!Double.isFinite(value)) {
                    writeInvalid("Scalar field returned a non-finite sample.", resolvedStep);
                    return;
                }
                boolean isHigh = value >= cut;
                if (resolvedOnlyHighBlocks && !isHigh) {
                    Integer nextZSkip = TerrainGridDomain.safeNextAxis(z, resolvedStep, bounds.maxZ);
                    if (nextZSkip == null) {
                        break;
                    }
                    z = nextZSkip;
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

                Integer nextZ = TerrainGridDomain.safeNextAxis(z, resolvedStep, bounds.maxZ);
                if (nextZ == null) {
                    break;
                }
                z = nextZ;
            }
            if (hitLimit) {
                break;
            }
            Integer nextX = TerrainGridDomain.safeNextAxis(x, resolvedStep, bounds.maxX);
            if (nextX == null) {
                break;
            }
            x = nextX;
        }

        outputValues.put(OUTPUT_BLOCK_PLACEMENTS_ID, placements);
        outputValues.put(OUTPUT_POINTS_ID, points);
        outputValues.put(OUTPUT_COUNT_ID, placements.size());
        outputValues.put(OUTPUT_STEP_USED_ID, resolvedStep);
        outputValues.put(OUTPUT_HIT_LIMIT_ID, hitLimit);
        outputValues.put(OUTPUT_COMPLETE_ID, !hitLimit);
        outputValues.put(OUTPUT_STOPPED_REASON_ID, stoppedReason);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void writeInvalid(String error, int stepUsed) {
        outputValues.put(OUTPUT_BLOCK_PLACEMENTS_ID, List.of());
        outputValues.put(OUTPUT_POINTS_ID, new BlockPosList());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_STEP_USED_ID, Math.max(1, stepUsed));
        outputValues.put(OUTPUT_HIT_LIMIT_ID, false);
        outputValues.put(OUTPUT_COMPLETE_ID, false);
        outputValues.put(OUTPUT_STOPPED_REASON_ID, "");
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error);
    }

    private RegionBounds resolveBounds(@Nullable RegionData region) {
        if (region == null || !region.isComplete()) {
            return new RegionBounds(
                TerrainNodeUtils.DEFAULT_MIN_X, TerrainNodeUtils.DEFAULT_MAX_X,
                TerrainNodeUtils.DEFAULT_MIN_Y, TerrainNodeUtils.DEFAULT_MAX_Y,
                TerrainNodeUtils.DEFAULT_MIN_Z, TerrainNodeUtils.DEFAULT_MAX_Z);
        }
        BlockPos min = region.getMinCorner();
        BlockPos max = region.getMaxCorner();
        if (min == null || max == null) {
            return new RegionBounds(
                TerrainNodeUtils.DEFAULT_MIN_X, TerrainNodeUtils.DEFAULT_MAX_X,
                TerrainNodeUtils.DEFAULT_MIN_Y, TerrainNodeUtils.DEFAULT_MAX_Y,
                TerrainNodeUtils.DEFAULT_MIN_Z, TerrainNodeUtils.DEFAULT_MAX_Z);
        }
        return new RegionBounds(min.getX(), max.getX(), min.getY(), max.getY(), min.getZ(), max.getZ());
    }

    private record RegionBounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
    }
}

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

    private static final String OUTPUT_BLOCK_PLACEMENTS_ID = "output_block_placements";
    private static final String OUTPUT_POINTS_ID = "output_points";

    private static final int DEFAULT_MIN_X = -512;
    private static final int DEFAULT_MAX_X = 512;
    private static final int DEFAULT_MIN_Z = -512;
    private static final int DEFAULT_MAX_Z = 512;
    private static final int DEFAULT_MIN_Y = -64;
    private static final int DEFAULT_MAX_Y = 320;

    @NodeProperty(displayName = "Slice Y", category = "Slice", order = 1)
    private int sliceY = 64;

    @NodeProperty(displayName = "Threshold", category = "Slice", order = 2)
    private double threshold = 0.5d;

    @NodeProperty(displayName = "Low Block", category = "Slice", order = 3)
    private String lowBlock = "minecraft:gray_concrete";

    @NodeProperty(displayName = "High Block", category = "Slice", order = 4)
    private String highBlock = "minecraft:lime_concrete";

    public ScalarFieldSliceToBlocksNode() {
        super(UUID.randomUUID(), "world.terrain.scalar_field_slice_to_blocks");

        addInputPort(new BasePort(INPUT_REGION_ID, "Region", "Optional region to sample; defaults to broad bounds when omitted", NodeDataType.REGION, this));
        addInputPort(new BasePort(INPUT_SCALAR_FIELD_ID, "Scalar Field", "Field to visualize", NodeDataType.SCALAR_FIELD, this));
        addInputPort(new BasePort(INPUT_SLICE_Y_ID, "Slice Y", "Y level for field sampling", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_THRESHOLD_ID, "Threshold", "Value threshold for high/low split", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_LOW_BLOCK_ID, "Low Block", "Block below threshold", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_HIGH_BLOCK_ID, "High Block", "Block at or above threshold", NodeDataType.BLOCK_TYPE, this));

        addOutputPort(new BasePort(OUTPUT_BLOCK_PLACEMENTS_ID, "Block Placements", "Slice visualization placements", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Sampled slice points", NodeDataType.BLOCK_LIST, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object regionObj = inputValues.get(INPUT_REGION_ID);
        Object fieldObj = inputValues.get(INPUT_SCALAR_FIELD_ID);

        if (!(fieldObj instanceof ScalarFieldData field)) {
            outputValues.put(OUTPUT_BLOCK_PLACEMENTS_ID, List.of());
            outputValues.put(OUTPUT_POINTS_ID, new BlockPosList());
            return;
        }

        RegionBounds bounds = resolveBounds(regionObj instanceof RegionData value ? value : null);

        int y = clamp(getInputInt(INPUT_SLICE_Y_ID, sliceY), bounds.minY, bounds.maxY);
        double cut = getInputDouble(INPUT_THRESHOLD_ID, threshold);
        String low = getInputString(INPUT_LOW_BLOCK_ID, lowBlock);
        String high = getInputString(INPUT_HIGH_BLOCK_ID, highBlock);

        List<BlockPlacementData> placements = new ArrayList<>();
        BlockPosList points = new BlockPosList();
        Vector3d samplePoint = new Vector3d();

        for (int x = bounds.minX; x <= bounds.maxX; x++) {
            for (int z = bounds.minZ; z <= bounds.maxZ; z++) {
                samplePoint.set(x, y, z);
                double value = sanitizeFinite(field.sampleScalar(samplePoint), 0.0d);
                String block = value >= cut ? high : low;

                BlockPos pos = new BlockPos(x, y, z);
                placements.add(new BlockPlacementData(pos, block));
                points.add(pos);
            }
        }

        outputValues.put(OUTPUT_BLOCK_PLACEMENTS_ID, placements);
        outputValues.put(OUTPUT_POINTS_ID, points);
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

    private double sanitizeFinite(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    private RegionBounds resolveBounds(@Nullable RegionData region) {
        if (region == null || !region.isComplete()) {
            return new RegionBounds(DEFAULT_MIN_X, DEFAULT_MAX_X, DEFAULT_MIN_Y, DEFAULT_MAX_Y, DEFAULT_MIN_Z, DEFAULT_MAX_Z);
        }

        BlockPos min = region.getMinCorner();
        BlockPos max = region.getMaxCorner();
        if (min == null || max == null) {
            return new RegionBounds(DEFAULT_MIN_X, DEFAULT_MAX_X, DEFAULT_MIN_Y, DEFAULT_MAX_Y, DEFAULT_MIN_Z, DEFAULT_MAX_Z);
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

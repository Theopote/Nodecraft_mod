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
    id = "world.terrain.heightfield_to_blocks",
    displayName = "Heightfield To Blocks",
    description = "Converts a height field inside a region to terrain block placements.",
    category = "world.terrain",
    order = 16
)
public class HeightfieldToBlocksNode extends BaseNode {

    private static final String INPUT_REGION_ID = "input_region";
    private static final String INPUT_HEIGHT_FIELD_ID = "input_height_field";
    private static final String INPUT_SURFACE_BLOCK_ID = "input_surface_block";
    private static final String INPUT_SUBSURFACE_BLOCK_ID = "input_subsurface_block";
    private static final String INPUT_WATER_LEVEL_ID = "input_water_level";
    private static final String INPUT_STEP_ID = "input_step";

    private static final String OUTPUT_BLOCK_PLACEMENTS_ID = "output_block_placements";
    private static final String OUTPUT_SURFACE_POINTS_ID = "output_surface_points";

    private static final int DEFAULT_MIN_X = -512;
    private static final int DEFAULT_MAX_X = 512;
    private static final int DEFAULT_MIN_Z = -512;
    private static final int DEFAULT_MAX_Z = 512;
    private static final int DEFAULT_MIN_Y = -64;
    private static final int DEFAULT_MAX_Y = 320;

    @NodeProperty(displayName = "Step", category = "Sampling", order = 1,
        description = "Column sampling stride for preview downsampling")
    private int step = 1;

    public HeightfieldToBlocksNode() {
        super(UUID.randomUUID(), "world.terrain.heightfield_to_blocks");

        addInputPort(new BasePort(INPUT_REGION_ID, "Region", "Optional region to rasterize; defaults to broad bounds when omitted", NodeDataType.REGION, this));
        addInputPort(new BasePort(INPUT_HEIGHT_FIELD_ID, "Height Field", "Input terrain height field", NodeDataType.SCALAR_FIELD, this));
        addInputPort(new BasePort(INPUT_SURFACE_BLOCK_ID, "Surface Block", "Top-layer block id", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_SUBSURFACE_BLOCK_ID, "Subsurface Block", "Inner-layer block id", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_WATER_LEVEL_ID, "Water Level", "Absolute water level in world Y", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_STEP_ID, "Step", "Column sampling stride in blocks", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_BLOCK_PLACEMENTS_ID, "Block Placements", "Generated placements for world write nodes", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_SURFACE_POINTS_ID, "Surface Points", "Top point per sampled X/Z column", NodeDataType.BLOCK_LIST, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object regionObj = inputValues.get(INPUT_REGION_ID);
        Object heightObj = inputValues.get(INPUT_HEIGHT_FIELD_ID);
        int resolvedStep = Math.max(1, getInputInt(INPUT_STEP_ID, step));

        if (!(heightObj instanceof ScalarFieldData heightField)) {
            outputValues.put(OUTPUT_BLOCK_PLACEMENTS_ID, List.of());
            outputValues.put(OUTPUT_SURFACE_POINTS_ID, new BlockPosList());
            return;
        }

        RegionBounds bounds = resolveBounds(regionObj instanceof RegionData value ? value : null);

        String surfaceBlock = getInputString(INPUT_SURFACE_BLOCK_ID, "minecraft:grass_block");
        String subsurfaceBlock = getInputString(INPUT_SUBSURFACE_BLOCK_ID, "minecraft:stone");
        int waterLevel = (int) Math.round(getInputDouble(INPUT_WATER_LEVEL_ID, bounds.minY + 62.0d));

        List<BlockPlacementData> placements = new ArrayList<>();
        BlockPosList surfacePoints = new BlockPosList();

        Vector3d samplePoint = new Vector3d();
        for (int x = bounds.minX; x <= bounds.maxX; x += resolvedStep) {
            for (int z = bounds.minZ; z <= bounds.maxZ; z += resolvedStep) {
                samplePoint.set(x, bounds.minY, z);
                double sampled = sanitizeFinite(heightField.sampleScalar(samplePoint), 0.0d);

                int columnTop = toColumnTopY(sampled, bounds.minY, bounds.maxY);
                for (int y = bounds.minY; y <= columnTop; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    String blockId = (y == columnTop) ? surfaceBlock : subsurfaceBlock;
                    placements.add(new BlockPlacementData(pos, blockId));
                }

                if (columnTop < waterLevel) {
                    for (int y = columnTop + 1; y <= Math.min(waterLevel, bounds.maxY); y++) {
                        placements.add(new BlockPlacementData(new BlockPos(x, y, z), "minecraft:water"));
                    }
                }

                surfacePoints.add(new BlockPos(x, columnTop, z));
            }
        }

        outputValues.put(OUTPUT_BLOCK_PLACEMENTS_ID, placements);
        outputValues.put(OUTPUT_SURFACE_POINTS_ID, surfacePoints);
    }

    private int toColumnTopY(double sampledHeight, int minY, int maxY) {
        double normalized = clamp(sampledHeight, -1.0d, 1.0d);
        double t = (normalized + 1.0d) * 0.5d;
        int y = (int) Math.round(minY + t * (maxY - minY));
        return clamp(y, minY, maxY);
    }

    private String getInputString(String portId, String fallback) {
        Object value = inputValues.get(portId);
        return (value instanceof String text && !text.isBlank()) ? text : fallback;
    }

    private double getInputDouble(String portId, double fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private int getInputInt(String portId, int fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.intValue() : fallback;
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

    private double clamp(double value, double min, double max) {
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }

    private record RegionBounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
    }
}

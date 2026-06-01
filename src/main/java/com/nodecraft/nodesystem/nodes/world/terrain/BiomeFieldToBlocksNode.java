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
    id = "world.terrain.biome_field_to_blocks",
    displayName = "Biome Field To Blocks",
    description = "Maps biome id field to surface block placements using a configurable palette.",
    category = "world.terrain",
    order = 17
)
public class BiomeFieldToBlocksNode extends BaseNode {

    private static final String INPUT_REGION_ID = "input_region";
    private static final String INPUT_BIOME_ID_FIELD_ID = "input_biome_id_field";
    private static final String INPUT_HEIGHT_FIELD_ID = "input_height_field";
    private static final String INPUT_STEP_ID = "input_step";

    private static final String OUTPUT_BLOCK_PLACEMENTS_ID = "output_block_placements";
    private static final String OUTPUT_SURFACE_POINTS_ID = "output_surface_points";

    private static final int DEFAULT_MIN_X = -512;
    private static final int DEFAULT_MAX_X = 512;
    private static final int DEFAULT_MIN_Z = -512;
    private static final int DEFAULT_MAX_Z = 512;
    private static final int DEFAULT_MIN_Y = -64;
    private static final int DEFAULT_MAX_Y = 320;

    @NodeProperty(displayName = "Biome Palette", category = "Biome", order = 1,
        description = "Comma-separated block ids by biome index; defaults cover ids 0..8")
    private String biomePalette = "minecraft:sand,minecraft:grass_block,minecraft:coarse_dirt,minecraft:grass_block,minecraft:oak_leaves,minecraft:snow_block,minecraft:spruce_leaves,minecraft:stone,minecraft:moss_block";

    @NodeProperty(displayName = "Step", category = "Sampling", order = 2,
        description = "Surface sampling stride for preview downsampling")
    private int step = 1;

    public BiomeFieldToBlocksNode() {
        super(UUID.randomUUID(), "world.terrain.biome_field_to_blocks");

        addInputPort(new BasePort(INPUT_REGION_ID, "Region", "Optional region to materialize; defaults to broad bounds when omitted", NodeDataType.REGION, this));
        addInputPort(new BasePort(INPUT_BIOME_ID_FIELD_ID, "Biome Id Field", "Biome class id encoded as scalar", NodeDataType.SCALAR_FIELD, this));
        addInputPort(new BasePort(INPUT_HEIGHT_FIELD_ID, "Height Field", "Terrain height field for surface Y", NodeDataType.SCALAR_FIELD, this));
        addInputPort(new BasePort(INPUT_STEP_ID, "Step", "Surface sampling stride in blocks", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_BLOCK_PLACEMENTS_ID, "Block Placements", "Biome-based surface placements", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_SURFACE_POINTS_ID, "Surface Points", "Resolved surface points", NodeDataType.BLOCK_LIST, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object regionObj = inputValues.get(INPUT_REGION_ID);
        Object biomeObj = inputValues.get(INPUT_BIOME_ID_FIELD_ID);
        Object heightObj = inputValues.get(INPUT_HEIGHT_FIELD_ID);
        int resolvedStep = Math.max(1, getInputInt(INPUT_STEP_ID, step));

        if (!(biomeObj instanceof ScalarFieldData biomeField)
            || !(heightObj instanceof ScalarFieldData heightField)) {
            outputValues.put(OUTPUT_BLOCK_PLACEMENTS_ID, List.of());
            outputValues.put(OUTPUT_SURFACE_POINTS_ID, new BlockPosList());
            return;
        }

        RegionBounds bounds = resolveBounds(regionObj instanceof RegionData value ? value : null);

        List<String> palette = parsePalette(biomePalette);
        List<BlockPlacementData> placements = new ArrayList<>();
        BlockPosList surfacePoints = new BlockPosList();
        Vector3d samplePoint = new Vector3d();

        for (int x = bounds.minX; x <= bounds.maxX; x += resolvedStep) {
            for (int z = bounds.minZ; z <= bounds.maxZ; z += resolvedStep) {
                samplePoint.set(x, bounds.minY, z);
                double h = sanitizeFinite(heightField.sampleScalar(samplePoint), 0.0d);
                int y = toColumnTopY(h, bounds.minY, bounds.maxY);

                samplePoint.set(x, y, z);
                int biomeId = (int) Math.round(Math.max(0.0d, sanitizeFinite(biomeField.sampleScalar(samplePoint), 0.0d)));
                String blockId = palette.get(Math.min(palette.size() - 1, biomeId));

                BlockPos pos = new BlockPos(x, y, z);
                placements.add(new BlockPlacementData(pos, blockId));
                surfacePoints.add(pos);
            }
        }

        outputValues.put(OUTPUT_BLOCK_PLACEMENTS_ID, placements);
        outputValues.put(OUTPUT_SURFACE_POINTS_ID, surfacePoints);
    }

    private List<String> parsePalette(String text) {
        List<String> blocks = new ArrayList<>();
        if (text != null) {
            for (String token : text.split(",")) {
                String value = token.trim();
                if (!value.isEmpty()) {
                    blocks.add(value.contains(":") ? value : "minecraft:" + value);
                }
            }
        }
        if (blocks.isEmpty()) {
            blocks.add("minecraft:grass_block");
        }
        return blocks;
    }

    private int toColumnTopY(double sampledHeight, int minY, int maxY) {
        double normalized = clamp(sampledHeight, -1.0d, 1.0d);
        double t = (normalized + 1.0d) * 0.5d;
        int y = (int) Math.round(minY + t * (maxY - minY));
        return clamp(y, minY, maxY);
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

package com.nodecraft.nodesystem.nodes.material.gradient_mapping;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Applies deterministic 3D value noise to material assignments across placements or voxelized geometry.
 */
@NodeInfo(
    id = "material.gradient_mapping.noise_material",
    displayName = "Noise Material",
    description = "Assigns block types across placements or geometry using deterministic 3D noise bands",
    category = "material.gradient_mapping",
    order = 1
)
public class NoiseMaterialNode extends BaseNode {

    @NodeProperty(displayName = "Scale", category = "Noise", order = 1)
    private double scale = 0.12d;

    @NodeProperty(displayName = "Detail Octaves", category = "Noise", order = 2)
    private int octaves = 3;

    @NodeProperty(displayName = "Persistence", category = "Noise", order = 3)
    private double persistence = 0.5d;

    @NodeProperty(displayName = "Lacunarity", category = "Noise", order = 4)
    private double lacunarity = 2.0d;

    private static final String INPUT_PLACEMENTS_ID = "input_placements";
    private static final String INPUT_COORDINATES_ID = "input_coordinates";
    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_BOX_GEOMETRY_ID = "input_box_geometry";
    private static final String INPUT_CYLINDER_GEOMETRY_ID = "input_cylinder_geometry";
    private static final String INPUT_SPHERE_GEOMETRY_ID = "input_sphere_geometry";
    private static final String INPUT_TORUS_GEOMETRY_ID = "input_torus_geometry";
    private static final String INPUT_PALETTE_ID = "input_palette";
    private static final String INPUT_FALLBACK_BLOCK_ID = "input_fallback_block";
    private static final String INPUT_SEED_ID = "input_seed";
    private static final String INPUT_THRESHOLD_LOW_ID = "input_threshold_low";
    private static final String INPUT_THRESHOLD_HIGH_ID = "input_threshold_high";

    private static final String OUTPUT_POSITIONS_ID = "output_positions";
    private static final String OUTPUT_BLOCK_IDS_ID = "output_block_ids";
    private static final String OUTPUT_PLACEMENTS_ID = "output_placements";
    private static final String OUTPUT_NOISE_VALUES_ID = "output_noise_values";
    private static final String OUTPUT_PALETTE_SIZE_ID = "output_palette_size";

    public NoiseMaterialNode() {
        super(UUID.randomUUID(), "material.gradient_mapping.noise_material");

        addInputPort(new BasePort(INPUT_PLACEMENTS_ID, "Block Placements", "Optional incoming placements to remap with noise", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", "Block coordinate list", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Unified abstract geometry input", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BOX_GEOMETRY_ID, "Box Geometry", "Box geometry data to materialize", NodeDataType.BOX_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_CYLINDER_GEOMETRY_ID, "Cylinder Geometry", "Cylinder geometry data to materialize", NodeDataType.CYLINDER_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_SPHERE_GEOMETRY_ID, "Sphere Geometry", "Sphere geometry data to materialize", NodeDataType.SPHERE, this));
        addInputPort(new BasePort(INPUT_TORUS_GEOMETRY_ID, "Torus Geometry", "Torus geometry data to materialize", NodeDataType.TORUS_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_PALETTE_ID, "Palette", "Ordered block id list used as noise bands", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_FALLBACK_BLOCK_ID, "Fallback Block", "Fallback block when palette is empty", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_SEED_ID, "Seed", "Seed used for deterministic material noise", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_THRESHOLD_LOW_ID, "Low Threshold", "Lower threshold remapped into the noise range", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_THRESHOLD_HIGH_ID, "High Threshold", "Upper threshold remapped into the noise range", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_POSITIONS_ID, "Positions", "Resolved block positions", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_BLOCK_IDS_ID, "Block IDs", "Block ids aligned with the positions list", NodeDataType.BLOCK_INFO_LIST, this));
        addOutputPort(new BasePort(OUTPUT_PLACEMENTS_ID, "Block Placements", "Noise-mapped placements for baking", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_NOISE_VALUES_ID, "Noise Values", "Noise sample aligned with the positions list", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_PALETTE_SIZE_ID, "Palette Size", "Number of usable palette entries", NodeDataType.INTEGER, this));
    }

    @Override
    public String getDescription() {
        return "Assigns block types across placements or geometry using deterministic 3D noise bands";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<String> palette = resolvePalette();
        List<BlockPlacementData> sourcePlacements = resolvePlacements();
        int seed = getInputInt(INPUT_SEED_ID, 0);
        double lowThreshold = getInputDouble(INPUT_THRESHOLD_LOW_ID, 0.0d);
        double highThreshold = getInputDouble(INPUT_THRESHOLD_HIGH_ID, 1.0d);
        double minThreshold = Math.min(lowThreshold, highThreshold);
        double maxThreshold = Math.max(lowThreshold, highThreshold);

        BlockPosList positions = new BlockPosList();
        List<String> blockIds = new ArrayList<>(sourcePlacements.size());
        List<BlockPlacementData> placements = new ArrayList<>(sourcePlacements.size());
        List<Double> noiseValues = new ArrayList<>(sourcePlacements.size());

        for (BlockPlacementData placement : sourcePlacements) {
            BlockPos pos = placement.pos();
            if (pos == null) {
                continue;
            }

            double noise = sampleNoise(pos.getX(), pos.getY(), pos.getZ(), seed);
            double normalized = remapNoise(noise, minThreshold, maxThreshold);
            String fallbackBlockId = placement.blockId() != null && !placement.blockId().isBlank()
                ? placement.blockId()
                : palette.getFirst();
            String blockId = selectPaletteBlock(palette, normalized, fallbackBlockId);

            positions.add(pos);
            blockIds.add(blockId);
            noiseValues.add(normalized);
            placements.add(new BlockPlacementData(pos, blockId, placement.stateData()));
        }

        outputValues.put(OUTPUT_POSITIONS_ID, positions);
        outputValues.put(OUTPUT_BLOCK_IDS_ID, blockIds);
        outputValues.put(OUTPUT_PLACEMENTS_ID, placements);
        outputValues.put(OUTPUT_NOISE_VALUES_ID, List.copyOf(noiseValues));
        outputValues.put(OUTPUT_PALETTE_SIZE_ID, palette.size());
    }

    private List<BlockPlacementData> resolvePlacements() {
        Object placementsObj = inputValues.get(INPUT_PLACEMENTS_ID);
        if (placementsObj instanceof List<?> placementList && !placementList.isEmpty()) {
            List<BlockPlacementData> resolved = new ArrayList<>();
            for (Object entry : placementList) {
                if (entry instanceof BlockPlacementData placement && placement.pos() != null) {
                    resolved.add(placement);
                }
            }
            if (!resolved.isEmpty()) {
                return resolved;
            }
        }

        String fallbackBlockId = getInputString(INPUT_FALLBACK_BLOCK_ID, "minecraft:stone");
        BlockPosList positions = GeometryVoxelizer.resolveBlocks(
            inputValues.get(INPUT_COORDINATES_ID),
            inputValues.get(INPUT_GEOMETRY_ID),
            inputValues.get(INPUT_BOX_GEOMETRY_ID),
            inputValues.get(INPUT_CYLINDER_GEOMETRY_ID),
            inputValues.get(INPUT_SPHERE_GEOMETRY_ID),
            inputValues.get(INPUT_TORUS_GEOMETRY_ID),
            true
        );

        List<BlockPlacementData> generated = new ArrayList<>(positions.size());
        for (BlockPos pos : positions) {
            generated.add(new BlockPlacementData(pos, fallbackBlockId));
        }
        return generated;
    }

    private List<String> resolvePalette() {
        Object paletteObj = inputValues.get(INPUT_PALETTE_ID);
        List<String> palette = new ArrayList<>();
        if (paletteObj instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof String blockId && !blockId.isBlank()) {
                    palette.add(blockId);
                }
            }
        }

        if (palette.isEmpty()) {
            palette.add(getInputString(INPUT_FALLBACK_BLOCK_ID, "minecraft:stone"));
        }
        return List.copyOf(palette);
    }

    private String selectPaletteBlock(List<String> palette, double normalizedNoise, String fallback) {
        if (palette.isEmpty()) {
            return fallback;
        }
        int index = Math.min(palette.size() - 1, (int) Math.floor(clamp01(normalizedNoise) * palette.size()));
        String blockId = palette.get(index);
        return blockId == null || blockId.isBlank() ? fallback : blockId;
    }

    private double sampleNoise(int x, int y, int z, int seed) {
        double amplitude = 1.0d;
        double frequency = Math.max(1.0e-6d, scale);
        double total = 0.0d;
        double amplitudeSum = 0.0d;

        for (int octave = 0; octave < Math.max(1, octaves); octave++) {
            double sampleX = x * frequency;
            double sampleY = y * frequency;
            double sampleZ = z * frequency;
            total += sampleValueNoise(sampleX, sampleY, sampleZ, seed + octave * 1013) * amplitude;
            amplitudeSum += amplitude;
            amplitude *= Math.max(0.0d, persistence);
            frequency *= Math.max(1.0d, lacunarity);
        }

        if (amplitudeSum <= 1.0e-9d) {
            return 0.5d;
        }
        return clamp01((total / amplitudeSum + 1.0d) * 0.5d);
    }

    private double sampleValueNoise(double x, double y, double z, int seed) {
        int x0 = fastFloor(x);
        int y0 = fastFloor(y);
        int z0 = fastFloor(z);
        int x1 = x0 + 1;
        int y1 = y0 + 1;
        int z1 = z0 + 1;

        double tx = smoothStep(x - x0);
        double ty = smoothStep(y - y0);
        double tz = smoothStep(z - z0);

        double c000 = randomValue(x0, y0, z0, seed);
        double c100 = randomValue(x1, y0, z0, seed);
        double c010 = randomValue(x0, y1, z0, seed);
        double c110 = randomValue(x1, y1, z0, seed);
        double c001 = randomValue(x0, y0, z1, seed);
        double c101 = randomValue(x1, y0, z1, seed);
        double c011 = randomValue(x0, y1, z1, seed);
        double c111 = randomValue(x1, y1, z1, seed);

        double x00 = lerp(c000, c100, tx);
        double x10 = lerp(c010, c110, tx);
        double x01 = lerp(c001, c101, tx);
        double x11 = lerp(c011, c111, tx);

        double y0v = lerp(x00, x10, ty);
        double y1v = lerp(x01, x11, ty);
        return lerp(y0v, y1v, tz);
    }

    private double randomValue(int x, int y, int z, int seed) {
        long hash = 1469598103934665603L;
        hash = mix(hash, x);
        hash = mix(hash, y);
        hash = mix(hash, z);
        hash = mix(hash, seed);
        double normalized = (double) (hash & 0x7fffffffL) / (double) 0x7fffffffL;
        return normalized * 2.0d - 1.0d;
    }

    private long mix(long current, int value) {
        long mixed = current ^ value;
        return mixed * 1099511628211L;
    }

    private int fastFloor(double value) {
        int truncated = (int) value;
        return value < truncated ? truncated - 1 : truncated;
    }

    private double smoothStep(double t) {
        double clamped = clamp01(t);
        return clamped * clamped * (3.0d - 2.0d * clamped);
    }

    private double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private double remapNoise(double value, double lowThreshold, double highThreshold) {
        double clamped = clamp01(value);
        double span = Math.max(1.0e-9d, highThreshold - lowThreshold);
        return clamp01((clamped - lowThreshold) / span);
    }

    private double clamp01(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    public double getScale() {
        return scale;
    }

    public void setScale(double scale) {
        double resolved = Math.max(1.0e-6d, scale);
        if (Double.compare(this.scale, resolved) != 0) {
            this.scale = resolved;
            markDirty();
        }
    }

    public int getOctaves() {
        return octaves;
    }

    public void setOctaves(int octaves) {
        int resolved = Math.max(1, octaves);
        if (this.octaves != resolved) {
            this.octaves = resolved;
            markDirty();
        }
    }

    public double getPersistence() {
        return persistence;
    }

    public void setPersistence(double persistence) {
        double resolved = Math.max(0.0d, persistence);
        if (Double.compare(this.persistence, resolved) != 0) {
            this.persistence = resolved;
            markDirty();
        }
    }

    public double getLacunarity() {
        return lacunarity;
    }

    public void setLacunarity(double lacunarity) {
        double resolved = Math.max(1.0d, lacunarity);
        if (Double.compare(this.lacunarity, resolved) != 0) {
            this.lacunarity = resolved;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        return new java.util.HashMap<String, Object>() {{
            put("scale", scale);
            put("octaves", octaves);
            put("persistence", persistence);
            put("lacunarity", lacunarity);
        }};
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof java.util.Map<?, ?> map)) {
            return;
        }
        if (map.get("scale") instanceof Number value) {
            setScale(value.doubleValue());
        }
        if (map.get("octaves") instanceof Number value) {
            setOctaves(value.intValue());
        }
        if (map.get("persistence") instanceof Number value) {
            setPersistence(value.doubleValue());
        }
        if (map.get("lacunarity") instanceof Number value) {
            setLacunarity(value.doubleValue());
        }
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
}

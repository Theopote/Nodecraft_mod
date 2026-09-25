package com.nodecraft.nodesystem.nodes.material.gradient_mapping;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.math.RandomOps;
import com.nodecraft.nodesystem.util.BlockPaletteData;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.MaterialMappingSupport;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Applies deterministic 3D value noise (RandomOps) to material assignments.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
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

    private static final String OUTPUT_PLACEMENTS_ID = "output_placements";
    private static final String OUTPUT_NOISE_VALUES_ID = "output_noise_values";
    private static final String OUTPUT_PALETTE_SIZE_ID = "output_palette_size";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public NoiseMaterialNode() {
        super(UUID.randomUUID(), "material.gradient_mapping.noise_material");

        addInputPort(new BasePort(INPUT_PLACEMENTS_ID, "Block Placements", "Optional incoming placements to remap with noise", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", "Block coordinate list", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Unified abstract geometry input", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BOX_GEOMETRY_ID, "Box Geometry", "Box geometry data to materialize", NodeDataType.BOX_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_CYLINDER_GEOMETRY_ID, "Cylinder Geometry", "Cylinder geometry data to materialize", NodeDataType.CYLINDER_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_SPHERE_GEOMETRY_ID, "Sphere Geometry", "Sphere geometry data to materialize", NodeDataType.SPHERE, this));
        addInputPort(new BasePort(INPUT_TORUS_GEOMETRY_ID, "Torus Geometry", "Torus geometry data to materialize", NodeDataType.TORUS_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_PALETTE_ID, "Palette", "Typed block palette (BLOCK_PALETTE)", NodeDataType.BLOCK_PALETTE, this));
        addInputPort(new BasePort(INPUT_FALLBACK_BLOCK_ID, "Fallback Block", "Geometry voxelization base block when palette is empty", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_SEED_ID, "Seed", "Integer seed for deterministic material noise", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_THRESHOLD_LOW_ID, "Low Threshold", "Lower threshold remapped into the noise range", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_THRESHOLD_HIGH_ID, "High Threshold", "Upper threshold remapped into the noise range", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_PLACEMENTS_ID, "Block Placements", "Noise-mapped placements for baking", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_NOISE_VALUES_ID, "Noise Values", "Normalized noise samples aligned with placements", NodeDataType.DOUBLE_LIST, this));
        addOutputPort(new BasePort(OUTPUT_PALETTE_SIZE_ID, "Palette Size", "Number of usable palette entries", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when noise parameters are usable", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Validation error when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Assigns block types across placements or geometry using deterministic 3D noise bands";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        GradientMaterialUtils.Validation params = validateParams();
        if (!params.valid()) {
            emitFail(params.message());
            return;
        }

        BlockPaletteData palette = GradientMaterialUtils.resolvePalette(inputValues.get(INPUT_PALETTE_ID));
        String fallbackMapped = MaterialMappingSupport.optionalBlockType(inputValues.get(INPUT_FALLBACK_BLOCK_ID));

        List<BlockPlacementData> fromPlacements = MaterialMappingSupport.extractPlacements(inputValues.get(INPUT_PLACEMENTS_ID));
        boolean placementSource = !fromPlacements.isEmpty();

        List<BlockPlacementData> sources = placementSource
            ? fromPlacements
            : MaterialMappingSupport.resolveSourcePlacements(
                null,
                inputValues.get(INPUT_COORDINATES_ID),
                inputValues.get(INPUT_GEOMETRY_ID),
                inputValues.get(INPUT_BOX_GEOMETRY_ID),
                inputValues.get(INPUT_CYLINDER_GEOMETRY_ID),
                inputValues.get(INPUT_SPHERE_GEOMETRY_ID),
                inputValues.get(INPUT_TORUS_GEOMETRY_ID),
                MaterialMappingSupport.firstMappedBlockType(
                    fallbackMapped,
                    palette.isEmpty() ? null : palette.entries().getFirst().blockId()
                )
            );

        if (!placementSource
            && sources.isEmpty()
            && GradientMaterialUtils.hasNonPlacementSource(
                inputValues.get(INPUT_COORDINATES_ID),
                inputValues.get(INPUT_GEOMETRY_ID),
                inputValues.get(INPUT_BOX_GEOMETRY_ID),
                inputValues.get(INPUT_CYLINDER_GEOMETRY_ID),
                inputValues.get(INPUT_SPHERE_GEOMETRY_ID),
                inputValues.get(INPUT_TORUS_GEOMETRY_ID)
            )
            && palette.isEmpty()
            && fallbackMapped == null) {
            emitFail("Palette or fallback block required for geometry or coordinates input");
            return;
        }

        int seed = RandomOps.resolveSeed(inputValues.get(INPUT_SEED_ID));
        double lowThreshold = readDouble(INPUT_THRESHOLD_LOW_ID, 0.0d);
        double highThreshold = readDouble(INPUT_THRESHOLD_HIGH_ID, 1.0d);

        List<BlockPlacementData> placements = new ArrayList<>(sources.size());
        List<Double> noiseValues = new ArrayList<>(sources.size());

        for (BlockPlacementData source : sources) {
            BlockPos pos = source.pos();
            if (pos == null) {
                continue;
            }
            double noise = sampleNoise(pos.getX(), pos.getY(), pos.getZ(), seed);
            double normalized = remapNoise(noise, lowThreshold, highThreshold);
            if (!Double.isFinite(normalized)) {
                emitFail("Noise sample produced a non-finite value");
                return;
            }
            String blockId = GradientMaterialUtils.pickByNormalized(palette, normalized, source.blockId());
            placements.add(MaterialMappingSupport.remapBlockId(source, blockId));
            noiseValues.add(normalized);
        }

        outputValues.put(OUTPUT_PLACEMENTS_ID, placements);
        outputValues.put(OUTPUT_NOISE_VALUES_ID, List.copyOf(noiseValues));
        outputValues.put(OUTPUT_PALETTE_SIZE_ID, palette.size());
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private GradientMaterialUtils.Validation validateParams() {
        GradientMaterialUtils.Validation scaleOk = GradientMaterialUtils.requirePositive(scale, "Scale");
        if (!scaleOk.valid()) {
            return scaleOk;
        }
        if (octaves < 1) {
            return GradientMaterialUtils.Validation.fail("Octaves must be at least 1");
        }
        GradientMaterialUtils.Validation persistenceOk = GradientMaterialUtils.requireFinite(persistence, "Persistence");
        if (!persistenceOk.valid()) {
            return persistenceOk;
        }
        GradientMaterialUtils.Validation lacunarityOk = GradientMaterialUtils.requireFinite(lacunarity, "Lacunarity");
        if (!lacunarityOk.valid()) {
            return lacunarityOk;
        }

        Object lowObj = inputValues.get(INPUT_THRESHOLD_LOW_ID);
        Object highObj = inputValues.get(INPUT_THRESHOLD_HIGH_ID);
        double low = lowObj instanceof Number number ? number.doubleValue() : 0.0d;
        double high = highObj instanceof Number number ? number.doubleValue() : 1.0d;
        GradientMaterialUtils.Validation lowOk = GradientMaterialUtils.requireFinite(low, "Low Threshold");
        if (!lowOk.valid()) {
            return lowOk;
        }
        GradientMaterialUtils.Validation highOk = GradientMaterialUtils.requireFinite(high, "High Threshold");
        if (!highOk.valid()) {
            return highOk;
        }
        if (!(low < high)) {
            return GradientMaterialUtils.Validation.fail("Low Threshold must be less than High Threshold");
        }
        return GradientMaterialUtils.Validation.ok();
    }

    private double sampleNoise(int x, int y, int z, int seed) {
        double amplitude = 1.0d;
        double frequency = scale;
        double total = 0.0d;
        double amplitudeSum = 0.0d;

        for (int octave = 0; octave < octaves; octave++) {
            double sample = RandomOps.valueNoise3(
                x * frequency,
                y * frequency,
                z * frequency,
                seed + octave * 1013
            );
            total += sample * amplitude;
            amplitudeSum += amplitude;
            amplitude *= persistence;
            frequency *= lacunarity;
        }

        if (!(amplitudeSum > 0.0d) || !Double.isFinite(amplitudeSum)) {
            return Double.NaN;
        }
        return GradientMaterialUtils.clamp01((total / amplitudeSum + 1.0d) * 0.5d);
    }

    private double remapNoise(double value, double lowThreshold, double highThreshold) {
        double clamped = GradientMaterialUtils.clamp01(value);
        if (!Double.isFinite(clamped)) {
            return Double.NaN;
        }
        return GradientMaterialUtils.clamp01((clamped - lowThreshold) / (highThreshold - lowThreshold));
    }

    private void emitFail(String message) {
        Map<String, Object> fail = GradientMaterialUtils.failResult(message, OUTPUT_NOISE_VALUES_ID);
        outputValues.putAll(fail);
        outputValues.put(OUTPUT_PALETTE_SIZE_ID, 0);
    }

    private double readDouble(String portId, double fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    public double getScale() {
        return scale;
    }

    public void setScale(double scale) {
        if (Double.compare(this.scale, scale) != 0) {
            this.scale = scale;
            markDirty();
        }
    }

    public int getOctaves() {
        return octaves;
    }

    public void setOctaves(int octaves) {
        if (this.octaves != octaves) {
            this.octaves = octaves;
            markDirty();
        }
    }

    public double getPersistence() {
        return persistence;
    }

    public void setPersistence(double persistence) {
        if (Double.compare(this.persistence, persistence) != 0) {
            this.persistence = persistence;
            markDirty();
        }
    }

    public double getLacunarity() {
        return lacunarity;
    }

    public void setLacunarity(double lacunarity) {
        if (Double.compare(this.lacunarity, lacunarity) != 0) {
            this.lacunarity = lacunarity;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("scale", scale);
        state.put("octaves", octaves);
        state.put("persistence", persistence);
        state.put("lacunarity", lacunarity);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("scale") instanceof Number value) {
            this.scale = value.doubleValue();
        }
        if (map.get("octaves") instanceof Number value) {
            this.octaves = value.intValue();
        }
        if (map.get("persistence") instanceof Number value) {
            this.persistence = value.doubleValue();
        }
        if (map.get("lacunarity") instanceof Number value) {
            this.lacunarity = value.doubleValue();
        }
    }
}

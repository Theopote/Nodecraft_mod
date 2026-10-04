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
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.MaterialMappingSupport;
import com.nodecraft.nodesystem.util.MaterialSourceResolver;
import com.nodecraft.nodesystem.util.RandomInputResolver;
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

    private static final MaterialSourceResolver.SourcePorts SOURCE_PORTS = new MaterialSourceResolver.SourcePorts(
        null,
        null,
        INPUT_PLACEMENTS_ID,
        INPUT_COORDINATES_ID,
        INPUT_GEOMETRY_ID,
        INPUT_BOX_GEOMETRY_ID,
        INPUT_CYLINDER_GEOMETRY_ID,
        INPUT_SPHERE_GEOMETRY_ID,
        INPUT_TORUS_GEOMETRY_ID
    );

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
        GradientMaterialUtils.Validation params = validateNoiseProperties();
        if (!params.valid()) {
            emitFail(params.message());
            return;
        }

        GradientMaterialUtils.OptionalDoubleResult lowResult =
            GradientMaterialUtils.resolveOptionalStrictDouble(this, INPUT_THRESHOLD_LOW_ID, 0.0d, "Low Threshold");
        if (!lowResult.valid()) {
            emitFail(lowResult.error());
            return;
        }
        GradientMaterialUtils.OptionalDoubleResult highResult =
            GradientMaterialUtils.resolveOptionalStrictDouble(this, INPUT_THRESHOLD_HIGH_ID, 1.0d, "High Threshold");
        if (!highResult.valid()) {
            emitFail(highResult.error());
            return;
        }
        double lowThreshold = lowResult.value();
        double highThreshold = highResult.value();
        if (!(lowThreshold < highThreshold)) {
            emitFail("Low Threshold must be less than High Threshold");
            return;
        }

        GradientMaterialUtils.OptionalPaletteResult paletteResult =
            GradientMaterialUtils.resolveOptionalPalette(this, INPUT_PALETTE_ID);
        if (!paletteResult.valid()) {
            emitFail(paletteResult.error());
            return;
        }
        BlockPaletteData palette = paletteResult.palette();
        MaterialMappingSupport.MappedBlockType fallbackType =
            MaterialMappingSupport.requireKnownBlockType(
                inputValues.get(INPUT_FALLBACK_BLOCK_ID),
                MaterialSourceResolver.isDriven(this, INPUT_FALLBACK_BLOCK_ID));
        if (!fallbackType.valid()) {
            emitFail(fallbackType.error());
            return;
        }
        String fallback = MaterialMappingSupport.firstMappedBlockType(
            fallbackType.blockId(),
            palette.isEmpty() ? null : palette.entries().getFirst().blockId()
        );

        MaterialSourceResolver.SourceResolution source =
            MaterialSourceResolver.resolve(this, SOURCE_PORTS, fallback);
        if (!source.valid()) {
            emitFail(source.error());
            return;
        }
        if (source.kind() == MaterialSourceResolver.SourceKind.NONE) {
            emitOk(List.of(), List.of(), palette.size());
            return;
        }
        if ((source.kind() == MaterialSourceResolver.SourceKind.COORDINATES
            || source.kind() == MaterialSourceResolver.SourceKind.GEOMETRY)
            && fallback == null) {
            emitFail("Palette or fallback block required for geometry or coordinates input");
            return;
        }

        RandomInputResolver.IntegerResolveResult seedResult = RandomInputResolver.resolveSeed(
            inputValues.get(INPUT_SEED_ID),
            MaterialSourceResolver.isDriven(this, INPUT_SEED_ID));
        if (!seedResult.valid()) {
            emitFail("Seed must be an exact Integer");
            return;
        }
        int seed = seedResult.value();
        List<BlockPlacementData> sources = source.placements();
        GradientMaterialUtils.Validation workOk = requireSampleWorkBudget(sources.size());
        if (!workOk.valid()) {
            emitFail(workOk.message());
            return;
        }
        List<BlockPlacementData> placements = new ArrayList<>(sources.size());
        List<Double> noiseValues = new ArrayList<>(sources.size());

        for (BlockPlacementData sourcePlacement : sources) {
            BlockPos pos = sourcePlacement.pos();
            double noise = 0;
            if (pos != null) {
                noise = sampleNoise(pos.getX(), pos.getY(), pos.getZ(), seed);
            }
            double normalized = remapNoise(noise, lowThreshold, highThreshold);
            if (!Double.isFinite(normalized)) {
                emitFail("Noise sample produced a non-finite value");
                return;
            }
            GradientMaterialUtils.PickResult pick =
                GradientMaterialUtils.pickByNormalized(palette, normalized, sourcePlacement.blockId());
            if (!pick.valid()) {
                emitFail(pick.error());
                return;
            }
            MaterialMappingSupport.RemapResult remap =
                MaterialMappingSupport.remapValidated(sourcePlacement, pick.blockId());
            if (!remap.valid()) {
                emitFail(remap.error());
                return;
            }
            placements.add(remap.placement());
            noiseValues.add(normalized);
        }

        emitOk(placements, noiseValues, palette.size());
    }

    private GradientMaterialUtils.Validation validateNoiseProperties() {
        GradientMaterialUtils.Validation scaleOk = GradientMaterialUtils.requirePositive(scale, "Scale");
        if (!scaleOk.valid()) {
            return scaleOk;
        }
        if (octaves < 1 || octaves > GenerationLimits.MAX_MATERIAL_NOISE_OCTAVES) {
            return GradientMaterialUtils.Validation.fail(
                "Octaves must be in [1, " + GenerationLimits.MAX_MATERIAL_NOISE_OCTAVES + "]"
            );
        }
        GradientMaterialUtils.Validation persistenceOk = GradientMaterialUtils.requireFinite(persistence, "Persistence");
        if (!persistenceOk.valid()) {
            return persistenceOk;
        }
        return GradientMaterialUtils.requireFinite(lacunarity, "Lacunarity");
    }

    private GradientMaterialUtils.Validation requireSampleWorkBudget(int placementCount) {
        long placements = Math.max(0, placementCount);
        long octaveCount = octaves;
        if (placements > 0 && octaveCount > Long.MAX_VALUE / placements) {
            return GradientMaterialUtils.Validation.fail("Noise sample work exceeds budget");
        }
        long work = placements * octaveCount;
        if (work > GenerationLimits.MAX_MATERIAL_SAMPLE_WORK) {
            return GradientMaterialUtils.Validation.fail(
                "Noise sample work exceeds MAX_MATERIAL_SAMPLE_WORK (" + work + ")"
            );
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

    private void emitOk(List<BlockPlacementData> placements, List<Double> noiseValues, int paletteSize) {
        outputValues.put(OUTPUT_PLACEMENTS_ID, placements);
        outputValues.put(OUTPUT_NOISE_VALUES_ID, List.copyOf(noiseValues));
        outputValues.put(OUTPUT_PALETTE_SIZE_ID, paletteSize);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
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
        int clamped = Math.max(1, Math.min(GenerationLimits.MAX_MATERIAL_NOISE_OCTAVES, octaves));
        if (this.octaves != clamped) {
            this.octaves = clamped;
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

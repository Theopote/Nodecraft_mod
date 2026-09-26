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
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "world.terrain.height_seed_field",
    displayName = "Height Seed Field",
    description = "Builds a deterministic continental-scale seed height field over X/Z (normalized [-1,1]). "
        + "Unconnected Region defaults to continental soft domain ±4096.",
    category = "world.terrain",
    order = 0
)
public class HeightSeedFieldNode extends BaseNode {

    private static final String INPUT_REGION_ID = "input_region";
    private static final String INPUT_SEED_ID = "input_seed";
    private static final String INPUT_SCALE_KM_ID = "input_scale_km";
    private static final String INPUT_CONTINENT_BIAS_ID = "input_continent_bias";

    private static final String OUTPUT_HEIGHT_FIELD_ID = "output_height_field";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    @NodeProperty(displayName = "Seed", category = "Terrain", order = 1)
    private int seed = 1337;

    @NodeProperty(displayName = "Scale Km", category = "Terrain", order = 2)
    private double scaleKm = 180.0d;

    @NodeProperty(displayName = "Continent Bias", category = "Terrain", order = 3,
        description = "0.0 gives more ocean, 1.0 gives more land")
    private double continentBias = 0.58d;

    public HeightSeedFieldNode() {
        super(UUID.randomUUID(), "world.terrain.height_seed_field");

        addInputPort(new BasePort(INPUT_REGION_ID, "Region", "Optional bounds used for broad-scale shaping; unconnected → continental ±4096", NodeDataType.REGION, this));
        addInputPort(new BasePort(INPUT_SEED_ID, "Seed", "Deterministic exact INTEGER seed", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_SCALE_KM_ID, "Scale Km", "Macro terrain wavelength in pseudo-kilometers", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_CONTINENT_BIAS_ID, "Continent Bias", "Land/ocean ratio control in [0,1]", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_HEIGHT_FIELD_ID, "Height Field", "Seed scalar field for downstream terrain simulation (normalized [-1,1])", NodeDataType.SCALAR_FIELD, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether the seed field was created", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Error message when field creation failed", NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        RegionData region = TerrainNodeUtils.resolveOptionalRegion(this, INPUT_REGION_ID);
        if (TerrainNodeUtils.isInvalidRegionMarker(region)) {
            publishInvalid("Connected Region is incomplete or invalid.");
            return;
        }

        Integer resolvedSeed = TerrainNodeUtils.resolveOptionalExactInteger(this, INPUT_SEED_ID, seed);
        if (resolvedSeed == null) {
            publishInvalid("Seed must be an exact INTEGER.");
            return;
        }

        Double resolvedScaleKmRaw = TerrainNodeUtils.resolveOptionalFiniteDouble(this, INPUT_SCALE_KM_ID, scaleKm);
        if (resolvedScaleKmRaw == null) {
            publishInvalid("Scale Km must be a finite DOUBLE.");
            return;
        }

        Double resolvedBiasRaw = TerrainNodeUtils.resolveOptionalFiniteDouble(this, INPUT_CONTINENT_BIAS_ID, continentBias);
        if (resolvedBiasRaw == null) {
            publishInvalid("Continent Bias must be a finite DOUBLE.");
            return;
        }

        double resolvedScaleKm = Math.max(1.0d, resolvedScaleKmRaw);
        double resolvedBias = clamp01(resolvedBiasRaw);

        // Higher bias means a lower sea level threshold, producing larger landmasses.
        double seaLevel = lerp(0.62d, 0.38d, resolvedBias);

        Bounds bounds = Bounds.fromRegion(region);
        int seedValue = resolvedSeed;
        ScalarFieldData field = point -> {
            double nx = (point.x - bounds.centerX) / resolvedScaleKm;
            double nz = (point.z - bounds.centerZ) / resolvedScaleKm;

            double continents = sampleFbm2d(nx * 0.55d, nz * 0.55d, seedValue, 5, 0.5d, 2.0d);
            double ridges = 1.0d - Math.abs(sampleFbm2d(nx * 1.35d, nz * 1.35d, seedValue + 991, 3, 0.55d, 2.1d));

            double blended = 0.72d * normalizeSigned(continents) + 0.28d * clamp01(ridges);

            // Softly favor larger connected continents near region center if bounds are known.
            double radial = 1.0d - Math.min(1.0d, distanceToCenter01(point.x, point.z, bounds));
            double continentalMask = lerp(0.6d, 1.0d, radial);

            return TerrainNodeUtils.clampNormalizedHeight(blended * continentalMask - seaLevel);
        };

        outputValues.put(OUTPUT_HEIGHT_FIELD_ID, field);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void publishInvalid(String error) {
        outputValues.put(OUTPUT_HEIGHT_FIELD_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error);
    }

    private double sampleFbm2d(double x, double z, int seed, int octaves, double persistence, double lacunarity) {
        double amplitude = 1.0d;
        double frequency = 1.0d;
        double sum = 0.0d;
        double amplitudeSum = 0.0d;

        int resolvedOctaves = Math.max(1, octaves);
        for (int octave = 0; octave < resolvedOctaves; octave++) {
            double value = sampleValueNoise2d(x * frequency, z * frequency, seed + octave * 1013);
            sum += value * amplitude;
            amplitudeSum += amplitude;
            amplitude *= persistence;
            frequency *= lacunarity;

            if (!Double.isFinite(sum) || !Double.isFinite(amplitudeSum) || !Double.isFinite(amplitude) || !Double.isFinite(frequency)) {
                break;
            }
        }

        if (amplitudeSum <= 1.0e-9d) {
            return 0.0d;
        }
        double normalized = sum / amplitudeSum;
        return Double.isFinite(normalized) ? normalized : 0.0d;
    }

    private double sampleValueNoise2d(double x, double z, int seed) {
        int x0 = fastFloor(x);
        int z0 = fastFloor(z);
        int x1 = x0 + 1;
        int z1 = z0 + 1;

        double tx = smoothStep(finiteOrZero(x - x0));
        double tz = smoothStep(finiteOrZero(z - z0));

        double c00 = randomSigned(x0, z0, seed);
        double c10 = randomSigned(x1, z0, seed);
        double c01 = randomSigned(x0, z1, seed);
        double c11 = randomSigned(x1, z1, seed);

        double a = lerp(c00, c10, tx);
        double b = lerp(c01, c11, tx);
        return finiteOrZero(lerp(a, b, tz));
    }

    private double randomSigned(int x, int z, int seed) {
        long hash = 1469598103934665603L;
        hash = mix(hash, x);
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

    private static double normalizeSigned(double value) {
        return clamp01((value + 1.0d) * 0.5d);
    }

    private static double clamp01(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    private static double finiteOrZero(double value) {
        return Double.isFinite(value) ? value : 0.0d;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double distanceToCenter01(double x, double z, Bounds bounds) {
        double dx = (x - bounds.centerX) / Math.max(1.0d, bounds.halfWidth);
        double dz = (z - bounds.centerZ) / Math.max(1.0d, bounds.halfDepth);
        return Math.sqrt(dx * dx + dz * dz);
    }

    private record Bounds(double centerX, double centerZ, double halfWidth, double halfDepth) {
        private static Bounds fromRegion(@Nullable RegionData region) {
            if (region == null || !region.isComplete()) {
                return continentalDefault();
            }

            BlockPos min = region.getMinCorner();
            BlockPos max = region.getMaxCorner();
            if (min == null || max == null) {
                return continentalDefault();
            }

            double centerX = ((double) min.getX() + (double) max.getX()) * 0.5d;
            double centerZ = ((double) min.getZ() + (double) max.getZ()) * 0.5d;
            double halfWidth = Math.max(1.0d, Math.abs((long) max.getX() - (long) min.getX()) * 0.5d);
            double halfDepth = Math.max(1.0d, Math.abs((long) max.getZ() - (long) min.getZ()) * 0.5d);
            return new Bounds(centerX, centerZ, halfWidth, halfDepth);
        }

        private static Bounds continentalDefault() {
            double half = Math.abs((long) TerrainNodeUtils.CONTINENTAL_MAX_XZ - (long) TerrainNodeUtils.CONTINENTAL_MIN_XZ) * 0.5d;
            double center = ((double) TerrainNodeUtils.CONTINENTAL_MIN_XZ + (double) TerrainNodeUtils.CONTINENTAL_MAX_XZ) * 0.5d;
            return new Bounds(center, center, Math.max(1.0d, half), Math.max(1.0d, half));
        }
    }
}

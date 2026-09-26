package com.nodecraft.nodesystem.nodes.pattern.surface_volume_distribution;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.SphereData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.DeterministicSeedUtils;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import com.nodecraft.nodesystem.util.SphereSurfaceSampling;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "pattern.surface_volume_distribution.sample_sphere_surface",
    displayName = "Sample Sphere Surface",
    description = "Samples points and normals on a sphere surface for scattering and growth workflows",
    category = "pattern.surface_volume_distribution",
    order = 1
)
public class SampleSphereSurfaceNode extends BaseNode {

    public enum SampleMode {
        FIBONACCI_UNIFORM,
        RANDOM_UNIFORM,
        LAT_LONG_GRID
    }

    @NodeProperty(displayName = "Sample Mode", category = "Sampling", order = 1)
    private SampleMode sampleMode = SampleMode.FIBONACCI_UNIFORM;

    @NodeProperty(displayName = "Sample Count", category = "Sampling", order = 2)
    private int sampleCount = 64;

    @NodeProperty(displayName = "Seed", category = "Sampling", order = 3)
    private int seed = DeterministicSeedUtils.DEFAULT_SEED;

    private static final String INPUT_SPHERE_ID = "input_sphere";
    private static final String INPUT_COUNT_ID = "input_count";
    private static final String INPUT_SEED_ID = "input_seed";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_NORMALS_ID = "output_normals";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public SampleSphereSurfaceNode() {
        super(UUID.randomUUID(), "pattern.surface_volume_distribution.sample_sphere_surface");

        addInputPort(new BasePort(INPUT_SPHERE_ID, "Sphere", "Sphere geometry to sample", NodeDataType.SPHERE, this));
        addInputPort(new BasePort(INPUT_COUNT_ID, "Count", "Optional sample count override", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_SEED_ID, "Seed", "Optional random seed override", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Sampled surface points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_NORMALS_ID, "Normals", "Outward normals aligned with the sampled points", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of generated samples", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when the sphere input is valid", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Samples points and normals on a sphere surface for scattering and growth workflows";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object sphereObj = inputValues.get(INPUT_SPHERE_ID);
        if (!(sphereObj instanceof SphereData sphere)) {
            writeEmpty();
            return;
        }

        int requestedCount = DeterministicSeedUtils.resolveStrictInteger(inputValues.get(INPUT_COUNT_ID), sampleCount);
        if (requestedCount <= 0) {
            writeEmpty();
            return;
        }

        int resolvedCount = GenerationLimits.clampLayoutInstanceCount(requestedCount);
        if (resolvedCount <= 0) {
            writeEmpty();
            return;
        }

        int resolvedSeed = DeterministicSeedUtils.resolveSeed(inputValues.get(INPUT_SEED_ID), seed);
        SphereSurfaceSampling.Mode mode = switch (sampleMode) {
            case RANDOM_UNIFORM -> SphereSurfaceSampling.Mode.RANDOM_UNIFORM;
            case LAT_LONG_GRID -> SphereSurfaceSampling.Mode.LAT_LONG_GRID;
            case FIBONACCI_UNIFORM -> SphereSurfaceSampling.Mode.FIBONACCI_UNIFORM;
        };

        List<Vector3d> normals = SphereSurfaceSampling.sampleUnitNormals(mode, resolvedCount, resolvedSeed);
        Vector3d center = sphere.center();
        double radius = sphere.radius();
        List<Vector3d> points = new ArrayList<>(normals.size());
        List<Vector3d> outwardNormals = new ArrayList<>(normals.size());

        for (Vector3d normal : normals) {
            Vector3d outward = SphereSurfaceSampling.normalizeOrUp(normal);
            outwardNormals.add(outward);
            points.add(new Vector3d(outward).mul(radius).add(center));
        }

        outputValues.put(OUTPUT_POINTS_ID, SpatialValueResolver.toPointDataList(points));
        outputValues.put(OUTPUT_NORMALS_ID, List.copyOf(outwardNormals));
        outputValues.put(OUTPUT_COUNT_ID, points.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeEmpty() {
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_NORMALS_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    public SampleMode getSampleMode() {
        return sampleMode;
    }

    public void setSampleMode(SampleMode sampleMode) {
        this.sampleMode = sampleMode == null ? SampleMode.FIBONACCI_UNIFORM : sampleMode;
        markDirty();
    }

    public void setSampleModeString(String mode) {
        if (mode == null || mode.isBlank()) {
            setSampleMode(SampleMode.FIBONACCI_UNIFORM);
            return;
        }
        try {
            setSampleMode(SampleMode.valueOf(mode.trim().toUpperCase()));
        } catch (IllegalArgumentException ignored) {
            setSampleMode(SampleMode.FIBONACCI_UNIFORM);
        }
    }

    public int getSampleCount() {
        return sampleCount;
    }

    public void setSampleCount(int sampleCount) {
        this.sampleCount = Math.max(1, sampleCount);
        markDirty();
    }

    public int getSeed() {
        return seed;
    }

    public void setSeed(int seed) {
        this.seed = seed;
        markDirty();
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("sampleMode", sampleMode.name());
        state.put("sampleCount", sampleCount);
        state.put("seed", seed);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("sampleMode") instanceof String sampleModeValue) {
            setSampleModeString(sampleModeValue);
        }
        if (map.get("sampleCount") instanceof Number sampleCountValue) {
            setSampleCount(sampleCountValue.intValue());
        }
        if (map.get("seed") instanceof Number seedValue) {
            setSeed(seedValue.intValue());
        }
    }
}

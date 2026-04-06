package com.nodecraft.nodesystem.nodes.spatial.analysis;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.SphereData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@NodeInfo(
    id = "spatial.analysis.sample_sphere_surface",
    displayName = "Sample Sphere Surface",
    description = "Samples points and normals on a sphere surface for scattering and growth workflows",
    category = "spatial.analysis"
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
    private int seed = 12345;

    private static final String INPUT_SPHERE_ID = "input_sphere";
    private static final String INPUT_COUNT_ID = "input_count";
    private static final String INPUT_SEED_ID = "input_seed";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_NORMALS_ID = "output_normals";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public SampleSphereSurfaceNode() {
        super(UUID.randomUUID(), "spatial.analysis.sample_sphere_surface");

        addInputPort(new BasePort(INPUT_SPHERE_ID, "Sphere", "Sphere geometry to sample", NodeDataType.SPHERE, this));
        addInputPort(new BasePort(INPUT_COUNT_ID, "Count", "Optional sample count override", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_SEED_ID, "Seed", "Optional random seed override", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Sampled surface points", NodeDataType.VECTOR_LIST, this));
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
            outputValues.put(OUTPUT_POINTS_ID, List.of());
            outputValues.put(OUTPUT_NORMALS_ID, List.of());
            outputValues.put(OUTPUT_COUNT_ID, 0);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        int resolvedCount = resolvePositiveInt(inputValues.get(INPUT_COUNT_ID), sampleCount, 1, 100000);
        int resolvedSeed = resolveInt(inputValues.get(INPUT_SEED_ID), seed);

        List<Vector3d> normals = switch (sampleMode) {
            case RANDOM_UNIFORM -> sampleRandomUniform(resolvedCount, resolvedSeed);
            case LAT_LONG_GRID -> sampleLatLongGrid(resolvedCount);
            case FIBONACCI_UNIFORM -> sampleFibonacci(resolvedCount);
        };

        Vector3d center = sphere.getCenter();
        double radius = sphere.getRadius();
        List<Vector3d> points = new ArrayList<>(normals.size());
        List<Vector3d> outwardNormals = new ArrayList<>(normals.size());

        for (Vector3d normal : normals) {
            Vector3d outward = normalizeOrUp(normal);
            outwardNormals.add(outward);
            points.add(new Vector3d(outward).mul(radius).add(center));
        }

        outputValues.put(OUTPUT_POINTS_ID, List.copyOf(points));
        outputValues.put(OUTPUT_NORMALS_ID, List.copyOf(outwardNormals));
        outputValues.put(OUTPUT_COUNT_ID, points.size());
        outputValues.put(OUTPUT_VALID_ID, true);
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

    private int resolvePositiveInt(Object value, int fallback, int min, int max) {
        int resolved = fallback;
        if (value instanceof Number number) {
            resolved = number.intValue();
        }
        return Math.max(min, Math.min(max, resolved));
    }

    private int resolveInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return fallback;
    }

    private List<Vector3d> sampleFibonacci(int count) {
        List<Vector3d> normals = new ArrayList<>(count);
        double goldenAngle = Math.PI * (3.0d - Math.sqrt(5.0d));

        for (int i = 0; i < count; i++) {
            double y = 1.0d - (2.0d * (i + 0.5d) / count);
            double radial = Math.sqrt(Math.max(0.0d, 1.0d - (y * y)));
            double theta = goldenAngle * i;
            normals.add(new Vector3d(
                Math.cos(theta) * radial,
                y,
                Math.sin(theta) * radial
            ));
        }

        return normals;
    }

    private List<Vector3d> sampleRandomUniform(int count, int seed) {
        List<Vector3d> normals = new ArrayList<>(count);
        Random random = new Random(seed);

        for (int i = 0; i < count; i++) {
            double u = random.nextDouble();
            double v = random.nextDouble();
            double theta = 2.0d * Math.PI * u;
            double z = 1.0d - 2.0d * v;
            double radial = Math.sqrt(Math.max(0.0d, 1.0d - (z * z)));
            normals.add(new Vector3d(
                Math.cos(theta) * radial,
                z,
                Math.sin(theta) * radial
            ));
        }

        return normals;
    }

    private List<Vector3d> sampleLatLongGrid(int count) {
        int latSteps = Math.max(2, (int) Math.round(Math.sqrt(count / 2.0d)));
        int lonSteps = Math.max(4, (int) Math.ceil((double) count / latSteps));
        List<Vector3d> normals = new ArrayList<>(latSteps * lonSteps);

        for (int latIndex = 0; latIndex < latSteps; latIndex++) {
            double v = latSteps == 1 ? 0.5d : (double) latIndex / (latSteps - 1);
            double phi = Math.PI * v;
            double y = Math.cos(phi);
            double radial = Math.sin(phi);

            for (int lonIndex = 0; lonIndex < lonSteps; lonIndex++) {
                double u = (double) lonIndex / lonSteps;
                double theta = u * Math.PI * 2.0d;
                normals.add(new Vector3d(
                    Math.cos(theta) * radial,
                    y,
                    Math.sin(theta) * radial
                ));
                if (normals.size() >= count) {
                    return normals;
                }
            }
        }

        return normals;
    }

    private Vector3d normalizeOrUp(Vector3d value) {
        Vector3d normalized = new Vector3d(value);
        if (normalized.lengthSquared() < 1.0e-12d) {
            return new Vector3d(0.0d, 1.0d, 0.0d);
        }
        return normalized.normalize();
    }
}

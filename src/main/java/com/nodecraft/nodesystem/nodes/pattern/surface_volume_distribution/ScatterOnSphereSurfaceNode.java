package com.nodecraft.nodesystem.nodes.pattern.surface_volume_distribution;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.SphereData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

@NodeInfo(
    id = "pattern.surface_volume_distribution.surface_scatter",
    displayName = "Scatter On Sphere Surface",
    description = "Scatters points on a sphere surface and outputs matching normals and optional snapped block coordinates",
    category = "pattern.surface_volume_distribution"
)
public class ScatterOnSphereSurfaceNode extends BaseNode {

    public enum DistributionMode {
        FIBONACCI_UNIFORM,
        RANDOM_UNIFORM,
        LAT_LONG_GRID
    }

    public enum SnapMode {
        NONE,
        FLOOR,
        NEAREST,
        CEIL
    }

    @NodeProperty(displayName = "Distribution", category = "Scatter", order = 1)
    private DistributionMode distributionMode = DistributionMode.FIBONACCI_UNIFORM;

    @NodeProperty(displayName = "Count", category = "Scatter", order = 2)
    private int count = 64;

    @NodeProperty(displayName = "Seed", category = "Scatter", order = 3)
    private int seed = 12345;

    @NodeProperty(displayName = "Snap Mode", category = "Output", order = 4)
    private SnapMode snapMode = SnapMode.NEAREST;

    @NodeProperty(displayName = "Unique Blocks Only", category = "Output", order = 5)
    private boolean uniqueBlocksOnly = true;

    private static final String INPUT_SPHERE_ID = "input_sphere";
    private static final String INPUT_COUNT_ID = "input_count";
    private static final String INPUT_SEED_ID = "input_seed";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_NORMALS_ID = "output_normals";
    private static final String OUTPUT_BLOCKS_ID = "output_blocks";
    private static final String OUTPUT_POINT_COUNT_ID = "output_point_count";
    private static final String OUTPUT_BLOCK_COUNT_ID = "output_block_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ScatterOnSphereSurfaceNode() {
        super(UUID.randomUUID(), "pattern.surface_volume_distribution.surface_scatter");

        addInputPort(new BasePort(INPUT_SPHERE_ID, "Sphere", "Sphere geometry to scatter on", NodeDataType.SPHERE, this));
        addInputPort(new BasePort(INPUT_COUNT_ID, "Count", "Optional scatter count override", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_SEED_ID, "Seed", "Optional seed override", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Scattered surface points", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_NORMALS_ID, "Normals", "Outward normals matched by point index", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_BLOCKS_ID, "Blocks", "Scattered points snapped to block coordinates when enabled", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_POINT_COUNT_ID, "Point Count", "Number of scattered geometric points", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_BLOCK_COUNT_ID, "Block Count", "Number of scattered snapped block coordinates", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when sphere input is valid", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Scatters points on a sphere surface and outputs matching normals and optional snapped block coordinates";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object sphereObj = inputValues.get(INPUT_SPHERE_ID);
        if (!(sphereObj instanceof SphereData sphere)) {
            outputValues.put(OUTPUT_POINTS_ID, List.of());
            outputValues.put(OUTPUT_NORMALS_ID, List.of());
            outputValues.put(OUTPUT_BLOCKS_ID, new BlockPosList());
            outputValues.put(OUTPUT_POINT_COUNT_ID, 0);
            outputValues.put(OUTPUT_BLOCK_COUNT_ID, 0);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        int resolvedCount = resolvePositiveInt(inputValues.get(INPUT_COUNT_ID), count, 1, 100000);
        int resolvedSeed = resolveInt(inputValues.get(INPUT_SEED_ID), seed);

        List<Vector3d> normals = switch (distributionMode) {
            case RANDOM_UNIFORM -> sampleRandomUniform(resolvedCount, resolvedSeed);
            case LAT_LONG_GRID -> sampleLatLongGrid(resolvedCount);
            case FIBONACCI_UNIFORM -> sampleFibonacci(resolvedCount);
        };

        Vector3d center = sphere.getCenter();
        double radius = sphere.getRadius();
        List<Vector3d> points = new ArrayList<>(normals.size());
        List<Vector3d> outwardNormals = new ArrayList<>(normals.size());
        BlockPosList blocks = new BlockPosList();
        Set<BlockPos> uniquePositions = uniqueBlocksOnly ? new LinkedHashSet<>() : null;

        for (Vector3d normal : normals) {
            Vector3d outward = normalizeOrUp(normal);
            Vector3d point = new Vector3d(outward).mul(radius).add(center);
            points.add(point);
            outwardNormals.add(outward);

            BlockPos snapped = snap(point);
            if (snapped == null) {
                continue;
            }
            if (uniquePositions != null) {
                if (uniquePositions.add(snapped)) {
                    blocks.add(snapped);
                }
            } else {
                blocks.add(snapped);
            }
        }

        outputValues.put(OUTPUT_POINTS_ID, List.copyOf(points));
        outputValues.put(OUTPUT_NORMALS_ID, List.copyOf(outwardNormals));
        outputValues.put(OUTPUT_BLOCKS_ID, blocks);
        outputValues.put(OUTPUT_POINT_COUNT_ID, points.size());
        outputValues.put(OUTPUT_BLOCK_COUNT_ID, blocks.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    public DistributionMode getDistributionMode() {
        return distributionMode;
    }

    public void setDistributionMode(DistributionMode distributionMode) {
        this.distributionMode = distributionMode == null ? DistributionMode.FIBONACCI_UNIFORM : distributionMode;
        markDirty();
    }

    public void setDistributionModeString(String mode) {
        if (mode == null || mode.isBlank()) {
            setDistributionMode(DistributionMode.FIBONACCI_UNIFORM);
            return;
        }
        try {
            setDistributionMode(DistributionMode.valueOf(mode.trim().toUpperCase()));
        } catch (IllegalArgumentException ignored) {
            setDistributionMode(DistributionMode.FIBONACCI_UNIFORM);
        }
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = Math.max(1, count);
        markDirty();
    }

    public int getSeed() {
        return seed;
    }

    public void setSeed(int seed) {
        this.seed = seed;
        markDirty();
    }

    public SnapMode getSnapMode() {
        return snapMode;
    }

    public void setSnapMode(SnapMode snapMode) {
        this.snapMode = snapMode == null ? SnapMode.NEAREST : snapMode;
        markDirty();
    }

    public void setSnapModeString(String mode) {
        if (mode == null || mode.isBlank()) {
            setSnapMode(SnapMode.NEAREST);
            return;
        }
        try {
            setSnapMode(SnapMode.valueOf(mode.trim().toUpperCase()));
        } catch (IllegalArgumentException ignored) {
            setSnapMode(SnapMode.NEAREST);
        }
    }

    public boolean isUniqueBlocksOnly() {
        return uniqueBlocksOnly;
    }

    public void setUniqueBlocksOnly(boolean uniqueBlocksOnly) {
        this.uniqueBlocksOnly = uniqueBlocksOnly;
        markDirty();
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("distributionMode", distributionMode.name());
        state.put("count", count);
        state.put("seed", seed);
        state.put("snapMode", snapMode.name());
        state.put("uniqueBlocksOnly", uniqueBlocksOnly);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("distributionMode") instanceof String distributionModeValue) {
            setDistributionModeString(distributionModeValue);
        }
        if (map.get("count") instanceof Number countValue) {
            setCount(countValue.intValue());
        }
        if (map.get("seed") instanceof Number seedValue) {
            setSeed(seedValue.intValue());
        }
        if (map.get("snapMode") instanceof String snapModeValue) {
            setSnapModeString(snapModeValue);
        }
        if (map.get("uniqueBlocksOnly") instanceof Boolean uniqueBlocksOnlyValue) {
            setUniqueBlocksOnly(uniqueBlocksOnlyValue);
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

    private BlockPos snap(Vector3d point) {
        return switch (snapMode) {
            case NONE -> null;
            case FLOOR -> new BlockPos(
                (int) Math.floor(point.x),
                (int) Math.floor(point.y),
                (int) Math.floor(point.z)
            );
            case CEIL -> new BlockPos(
                (int) Math.ceil(point.x),
                (int) Math.ceil(point.y),
                (int) Math.ceil(point.z)
            );
            case NEAREST -> new BlockPos(
                (int) Math.round(point.x),
                (int) Math.round(point.y),
                (int) Math.round(point.z)
            );
        };
    }
}

package com.nodecraft.nodesystem.nodes.pattern.surface_volume_distribution;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.DeterministicSeedUtils;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.MinDistanceScatterSelector;
import com.nodecraft.nodesystem.util.PrimitiveGeometrySurfaceSampler;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "pattern.surface_volume_distribution.scatter_surface",
    displayName = "Scatter On Surface",
    description = "Scatters points on supported primitive geometry surfaces with random or blue-noise distribution",
    category = "pattern.surface_volume_distribution",
    order = 2
)
public class ScatterOnSurfaceNode extends BaseNode {

    public enum DistributionMode {
        RANDOM,
        BLUE_NOISE
    }

    @NodeProperty(displayName = "Target Count", category = "Scatter", order = 1)
    private int targetCount = 128;

    @NodeProperty(displayName = "Seed", category = "Scatter", order = 2)
    private int seed = DeterministicSeedUtils.DEFAULT_SEED;

    @NodeProperty(displayName = "Min Distance", category = "Scatter", order = 3)
    private double minDistance = 0.0d;

    @NodeProperty(displayName = "Distribution", category = "Scatter", order = 4)
    private DistributionMode distributionMode = DistributionMode.BLUE_NOISE;

    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_TARGET_COUNT_ID = "input_target_count";
    private static final String INPUT_SEED_ID = "input_seed";
    private static final String INPUT_MIN_DISTANCE_ID = "input_min_distance";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_NORMALS_ID = "output_normals";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ScatterOnSurfaceNode() {
        super(UUID.randomUUID(), "pattern.surface_volume_distribution.scatter_surface");
        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Primitive geometry to scatter on", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_TARGET_COUNT_ID, "Target Count", "Maximum requested scatter count", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_SEED_ID, "Seed", "Optional seed override", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_MIN_DISTANCE_ID, "Min Distance", "Minimum Euclidean spacing between accepted points", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Scattered surface points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_NORMALS_ID, "Normals", "Outward surface normals aligned with points", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Actual number of accepted points", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when geometry input is valid and supported", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Scatters points on supported primitive geometry surfaces with random or blue-noise distribution";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object geometryObj = inputValues.get(INPUT_GEOMETRY_ID);
        if (!(geometryObj instanceof GeometryData geometry) || !PrimitiveGeometrySurfaceSampler.isSupported(geometry)) {
            writeEmpty();
            return;
        }

        int requestedCount = DeterministicSeedUtils.resolveStrictInteger(inputValues.get(INPUT_TARGET_COUNT_ID), targetCount);
        if (requestedCount <= 0) {
            writeEmpty();
            return;
        }

        double minDist = inputValues.get(INPUT_MIN_DISTANCE_ID) instanceof Number n ? n.doubleValue() : minDistance;
        if (!Double.isFinite(minDist) || minDist < 0.0d) {
            writeEmpty();
            return;
        }

        int resolvedCount = GenerationLimits.clampLayoutInstanceCount(requestedCount);
        if (resolvedCount <= 0) {
            writeEmpty();
            return;
        }

        int resolvedSeed = DeterministicSeedUtils.resolveSeed(inputValues.get(INPUT_SEED_ID), seed);
        MinDistanceScatterSelector.DistributionMode mode = distributionMode == DistributionMode.RANDOM
            ? MinDistanceScatterSelector.DistributionMode.RANDOM
            : MinDistanceScatterSelector.DistributionMode.BLUE_NOISE_APPROX;

        List<PrimitiveGeometrySurfaceSampler.SurfaceSample> samples = PrimitiveGeometrySurfaceSampler.scatter(
            geometry,
            resolvedCount,
            resolvedSeed,
            minDist,
            mode
        );
        if (samples.isEmpty()) {
            writeEmpty();
            return;
        }

        List<Vector3d> points = new ArrayList<>(samples.size());
        List<Vector3d> normals = new ArrayList<>(samples.size());
        for (PrimitiveGeometrySurfaceSampler.SurfaceSample sample : samples) {
            points.add(sample.point());
            normals.add(sample.normal());
        }

        outputValues.put(OUTPUT_POINTS_ID, SpatialValueResolver.toPointDataList(points));
        outputValues.put(OUTPUT_NORMALS_ID, List.copyOf(normals));
        outputValues.put(OUTPUT_COUNT_ID, points.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeEmpty() {
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_NORMALS_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    public DistributionMode getDistributionMode() {
        return distributionMode;
    }

    public void setDistributionMode(DistributionMode distributionMode) {
        this.distributionMode = distributionMode == null ? DistributionMode.BLUE_NOISE : distributionMode;
        markDirty();
    }

    public void setDistributionModeString(String mode) {
        if (mode == null || mode.isBlank()) {
            setDistributionMode(DistributionMode.BLUE_NOISE);
            return;
        }
        try {
            String normalized = mode.trim().toUpperCase().replace("BLUE_NOISE_APPROX", "BLUE_NOISE");
            setDistributionMode(DistributionMode.valueOf(normalized));
        } catch (IllegalArgumentException ignored) {
            setDistributionMode(DistributionMode.BLUE_NOISE);
        }
    }

    public int getTargetCount() {
        return targetCount;
    }

    public void setTargetCount(int targetCount) {
        this.targetCount = Math.max(1, targetCount);
        markDirty();
    }

    public int getSeed() {
        return seed;
    }

    public void setSeed(int seed) {
        this.seed = seed;
        markDirty();
    }

    public double getMinDistance() {
        return minDistance;
    }

    public void setMinDistance(double minDistance) {
        this.minDistance = minDistance;
        markDirty();
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("targetCount", targetCount);
        state.put("seed", seed);
        state.put("minDistance", minDistance);
        state.put("distributionMode", distributionMode.name());
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("targetCount") instanceof Number countValue) {
            setTargetCount(countValue.intValue());
        }
        if (map.get("seed") instanceof Number seedValue) {
            setSeed(seedValue.intValue());
        }
        if (map.get("minDistance") instanceof Number minDistanceValue) {
            setMinDistance(minDistanceValue.doubleValue());
        }
        if (map.get("distributionMode") instanceof String distributionModeValue) {
            setDistributionModeString(distributionModeValue);
        }
    }
}

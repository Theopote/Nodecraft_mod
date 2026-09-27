package com.nodecraft.nodesystem.nodes.pattern.surface_volume_distribution;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.DeterministicSeedUtils;
import com.nodecraft.nodesystem.util.MinDistanceScatterSelector;
import com.nodecraft.nodesystem.util.PrimitiveVolumeSampler;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "pattern.surface_volume_distribution.scatter_volume",
    displayName = "Scatter In Volume",
    description = "Scatters points inside supported primitive geometry volumes with random or approximate blue-noise distribution",
    category = "pattern.surface_volume_distribution",
    order = 4
)
public class ScatterInVolumeNode extends AbstractSurfaceVolumeDistributionNode {

    public enum DistributionMode {
        RANDOM,
        BLUE_NOISE
    }

    @NodeProperty(displayName = "Target Count", category = "Scatter", order = 1)
    private int targetCount = 256;

    @NodeProperty(displayName = "Seed", category = "Scatter", order = 2)
    private int seed = DeterministicSeedUtils.DEFAULT_SEED;

    @NodeProperty(displayName = "Min Distance", category = "Scatter", order = 3)
    private double minDistance = 0.0d;

    @NodeProperty(displayName = "Distribution", category = "Scatter", order = 4,
        description = "BLUE_NOISE uses an approximate blue-noise selector (not Bridson Poisson-disk)")
    private DistributionMode distributionMode = DistributionMode.BLUE_NOISE;

    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_TARGET_COUNT_ID = "input_target_count";
    private static final String INPUT_SEED_ID = "input_seed";
    private static final String INPUT_MIN_DISTANCE_ID = "input_min_distance";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_COUNT_ID = "output_count";

    public ScatterInVolumeNode() {
        super(UUID.randomUUID(), "pattern.surface_volume_distribution.scatter_volume");
        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Primitive geometry volume to scatter inside", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_TARGET_COUNT_ID, "Target Count", "Maximum requested scatter count", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_SEED_ID, "Seed", "Optional seed override", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_MIN_DISTANCE_ID, "Min Distance", "Minimum Euclidean spacing between accepted points (>= 0)", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Scattered volume points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Actual number of accepted points", NodeDataType.INTEGER, this));
        addValidErrorAndCompleteOutputs();
    }

    @Override
    public String getDescription() {
        return "Scatters points inside supported primitive geometry volumes with random or approximate blue-noise distribution";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object geometryObj = inputValues.get(INPUT_GEOMETRY_ID);
        if (!(geometryObj instanceof GeometryData geometry)) {
            writeFail("Missing or invalid Geometry");
            return;
        }
        String validation = PrimitiveVolumeSampler.validatePrimitive(geometry);
        if (validation != null) {
            writeFail(validation);
            return;
        }

        Integer resolvedCount = resolveLayoutCount(INPUT_TARGET_COUNT_ID, targetCount);
        if (resolvedCount == null) {
            writeFail(countFailureReason(INPUT_TARGET_COUNT_ID, targetCount));
            return;
        }

        Double minDist = resolveNonNegativeFinite(INPUT_MIN_DISTANCE_ID, minDistance);
        if (minDist == null) {
            writeFail("Min Distance connected but invalid (must be finite and >= 0)");
            return;
        }

        Integer resolvedSeed = resolveSeed(INPUT_SEED_ID, seed);
        if (resolvedSeed == null) {
            writeFail("Seed connected but invalid");
            return;
        }

        MinDistanceScatterSelector.DistributionMode mode = distributionMode == DistributionMode.RANDOM
            ? MinDistanceScatterSelector.DistributionMode.RANDOM
            : MinDistanceScatterSelector.DistributionMode.BLUE_NOISE_APPROX;

        List<Vector3d> points = PrimitiveVolumeSampler.scatter(
            geometry, resolvedCount, resolvedSeed, minDist, mode);
        if (points == null) {
            writeFail("Volume scatter failed");
            return;
        }

        boolean complete = points.size() == resolvedCount;
        commitPointList(OUTPUT_POINTS_ID, OUTPUT_COUNT_ID, points, complete);
    }

    private void writeFail(String error) {
        markInvalid(error);
        putEmptyListOutputs(OUTPUT_POINTS_ID);
        putIntOutputs(0, OUTPUT_COUNT_ID);
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
        this.targetCount = targetCount;
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

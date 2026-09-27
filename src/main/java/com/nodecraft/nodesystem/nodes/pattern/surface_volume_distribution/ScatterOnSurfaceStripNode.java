package com.nodecraft.nodesystem.nodes.pattern.surface_volume_distribution;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.SurfaceStripData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.DeterministicSeedUtils;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.MinDistanceScatterSelector;
import com.nodecraft.nodesystem.util.SurfaceStripSampling;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "pattern.surface_volume_distribution.scatter_surface_strip",
    displayName = "Scatter On Surface Strip",
    description = "Scatters points on a surface strip by area-weighted quad sampling with optional spacing",
    category = "pattern.surface_volume_distribution",
    order = 3
)
public class ScatterOnSurfaceStripNode extends AbstractSurfaceVolumeDistributionNode {

    @NodeProperty(displayName = "Target Count", category = "Scatter", order = 1)
    private int targetCount = 128;

    @NodeProperty(displayName = "Seed", category = "Scatter", order = 2)
    private int seed = DeterministicSeedUtils.DEFAULT_SEED;

    @NodeProperty(displayName = "Min Distance", category = "Scatter", order = 3)
    private double minDistance = 0.0d;

    private static final String INPUT_SURFACE_STRIP_ID = "input_surface_strip";
    private static final String INPUT_TARGET_COUNT_ID = "input_target_count";
    private static final String INPUT_SEED_ID = "input_seed";
    private static final String INPUT_MIN_DISTANCE_ID = "input_min_distance";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_COUNT_ID = "output_count";

    public ScatterOnSurfaceStripNode() {
        super(UUID.randomUUID(), "pattern.surface_volume_distribution.scatter_surface_strip");
        addInputPort(new BasePort(INPUT_SURFACE_STRIP_ID, "Surface Strip", "Surface strip to scatter points on", NodeDataType.SURFACE_STRIP, this));
        addInputPort(new BasePort(INPUT_TARGET_COUNT_ID, "Target Count", "Maximum requested scatter count", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_SEED_ID, "Seed", "Optional seed override", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_MIN_DISTANCE_ID, "Min Distance", "Minimum Euclidean spacing between accepted points (>= 0)", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Scattered points on the strip surface", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Actual number of accepted points", NodeDataType.INTEGER, this));
        addValidErrorAndCompleteOutputs();
    }

    @Override
    public String getDescription() {
        return "Scatters points on a surface strip by area-weighted quad sampling with optional spacing";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object stripObj = inputValues.get(INPUT_SURFACE_STRIP_ID);
        if (!(stripObj instanceof SurfaceStripData strip)) {
            writeFail("Missing or invalid Surface Strip");
            return;
        }

        if (!SurfaceStripSampling.hasValidTopology(strip.sections())) {
            writeFail("Surface Strip topology is invalid");
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

        String budgetError = GenerationLimits.validateScatterCandidateBudget(
            resolvedCount, GenerationLimits.SCATTER_CANDIDATES_PER_TARGET);
        if (budgetError != null) {
            writeFail(budgetError);
            return;
        }

        Random random = new Random(resolvedSeed);
        SurfaceStripSampling.QuadCatalog quadCatalog = SurfaceStripSampling.QuadCatalog.from(strip);
        if (quadCatalog.size() == 0) {
            writeFail("Surface Strip has no sampleable quads");
            return;
        }

        long maxAttemptsLong = Math.max((long) resolvedCount * GenerationLimits.SCATTER_CANDIDATES_PER_TARGET, 128L);
        int maxAttempts = (int) Math.min(maxAttemptsLong, Integer.MAX_VALUE);
        long fillCapLong = (long) resolvedCount * 8L;
        int fillCap = (int) Math.min(fillCapLong, Integer.MAX_VALUE);

        List<Vector3d> candidates = new ArrayList<>(Math.min(maxAttempts, fillCap));
        for (int attempt = 0; attempt < maxAttempts && candidates.size() < fillCap; attempt++) {
            candidates.add(quadCatalog.sample(random));
        }

        List<Vector3d> points = MinDistanceScatterSelector.select(
            candidates,
            resolvedCount,
            minDist,
            MinDistanceScatterSelector.DistributionMode.RANDOM,
            random
        );

        boolean complete = points.size() == resolvedCount;
        commitPointList(OUTPUT_POINTS_ID, OUTPUT_COUNT_ID, points, complete);
    }

    private void writeFail(String error) {
        markInvalid(error);
        putEmptyListOutputs(OUTPUT_POINTS_ID);
        putIntOutputs(0, OUTPUT_COUNT_ID);
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
    }
}

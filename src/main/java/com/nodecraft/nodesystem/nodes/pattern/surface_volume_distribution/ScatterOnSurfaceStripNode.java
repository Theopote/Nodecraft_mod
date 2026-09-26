package com.nodecraft.nodesystem.nodes.pattern.surface_volume_distribution;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.SurfaceStripData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.DeterministicSeedUtils;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.MinDistanceScatterSelector;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
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
    order = 4
)
public class ScatterOnSurfaceStripNode extends BaseNode {

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
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ScatterOnSurfaceStripNode() {
        super(UUID.randomUUID(), "pattern.surface_volume_distribution.scatter_surface_strip");
        addInputPort(new BasePort(INPUT_SURFACE_STRIP_ID, "Surface Strip", "Surface strip to scatter points on", NodeDataType.SURFACE_STRIP, this));
        addInputPort(new BasePort(INPUT_TARGET_COUNT_ID, "Target Count", "Maximum requested scatter count", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_SEED_ID, "Seed", "Optional seed override", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_MIN_DISTANCE_ID, "Min Distance", "Minimum Euclidean spacing between accepted points", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Scattered points on the strip surface", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Actual number of accepted points", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when the surface strip is valid", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Scatters points on a surface strip by area-weighted quad sampling with optional spacing";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object stripObj = inputValues.get(INPUT_SURFACE_STRIP_ID);
        if (!(stripObj instanceof SurfaceStripData strip)) {
            writeEmpty();
            return;
        }

        if (!SurfaceStripSampling.hasValidTopology(strip.sections())) {
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
        Random random = new Random(resolvedSeed);
        SurfaceStripSampling.QuadCatalog quadCatalog = SurfaceStripSampling.QuadCatalog.from(strip);
        if (quadCatalog.size() == 0) {
            writeEmpty();
            return;
        }

        List<Vector3d> candidates = new ArrayList<>(Math.max(resolvedCount * 32, resolvedCount));
        int maxAttempts = Math.max(resolvedCount * 32, 128);
        for (int attempt = 0; attempt < maxAttempts && candidates.size() < resolvedCount * 8; attempt++) {
            candidates.add(quadCatalog.sample(random));
        }

        List<Vector3d> points = MinDistanceScatterSelector.select(
            candidates,
            resolvedCount,
            minDist,
            MinDistanceScatterSelector.DistributionMode.RANDOM,
            random
        );

        if (points.isEmpty()) {
            writeEmpty();
            return;
        }

        outputValues.put(OUTPUT_POINTS_ID, SpatialValueResolver.toPointDataList(points));
        outputValues.put(OUTPUT_COUNT_ID, points.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeEmpty() {
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
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

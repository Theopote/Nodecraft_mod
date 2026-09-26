package com.nodecraft.nodesystem.nodes.pattern.surface_volume_distribution;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PlaneProjectionUtils;
import com.nodecraft.nodesystem.util.DeterministicSeedUtils;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.MinDistanceScatterSelector;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2d;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "pattern.surface_volume_distribution.poisson_disk_plane",
    displayName = "Poisson Disk On Plane",
    description = "Samples points on a plane inside a UV rectangle with minimum separation using rejection sampling",
    category = "pattern.surface_volume_distribution",
    order = 3
)
public class PoissonDiskOnPlaneNode extends BaseNode {

    @NodeProperty(displayName = "Max Attempts", category = "Sampling", order = 1,
        description = "Maximum random proposals before stopping (may return fewer than Target Count)")
    private int maxAttempts = 20_000;

    @NodeProperty(displayName = "Seed", category = "Sampling", order = 2)
    private int seed = DeterministicSeedUtils.DEFAULT_SEED;

    private static final String INPUT_PLANE_ID = "input_plane";
    private static final String INPUT_ORIGIN_ID = "input_origin";
    private static final String INPUT_HALF_U_ID = "input_half_u";
    private static final String INPUT_HALF_V_ID = "input_half_v";
    private static final String INPUT_TARGET_COUNT_ID = "input_target_count";
    private static final String INPUT_MIN_DISTANCE_ID = "input_min_distance";
    private static final String INPUT_SEED_ID = "input_seed";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_ATTEMPTS_ID = "output_attempts";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public PoissonDiskOnPlaneNode() {
        super(UUID.randomUUID(), "pattern.surface_volume_distribution.poisson_disk_plane");

        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Plane defining UV basis and projection", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_ORIGIN_ID, "Origin", "Rectangle center on the plane. When disconnected, the plane reference point is used.", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_HALF_U_ID, "Half U", "Half extent along the plane U axis", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_HALF_V_ID, "Half V", "Half extent along the plane V axis", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_TARGET_COUNT_ID, "Target Count", "Target number of samples", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_MIN_DISTANCE_ID, "Min Distance", "Minimum Euclidean distance between accepted samples", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SEED_ID, "Seed", "Optional RNG seed for reproducibility", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Accepted sample positions", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of accepted samples", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_ATTEMPTS_ID, "Attempts", "Number of random proposals tried", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when inputs are valid", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDisplayName() {
        return "Poisson Disk On Plane";
    }

    @Override
    public String getDescription() {
        return "Samples points on a plane inside a UV rectangle with minimum separation using rejection sampling";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object planeObj = inputValues.get(INPUT_PLANE_ID);
        if (!(planeObj instanceof PlaneData plane)) {
            writeInvalid();
            return;
        }

        double halfU = inputValues.get(INPUT_HALF_U_ID) instanceof Number n ? n.doubleValue() : Double.NaN;
        double halfV = inputValues.get(INPUT_HALF_V_ID) instanceof Number n ? n.doubleValue() : Double.NaN;
        double minDist = inputValues.get(INPUT_MIN_DISTANCE_ID) instanceof Number n ? n.doubleValue() : Double.NaN;
        int requestedTarget = DeterministicSeedUtils.resolveStrictInteger(inputValues.get(INPUT_TARGET_COUNT_ID), 1);

        if (requestedTarget <= 0
            || !Double.isFinite(halfU) || !Double.isFinite(halfV) || !Double.isFinite(minDist)
            || halfU <= 0.0d || halfV <= 0.0d || minDist < 0.0d) {
            writeInvalid();
            return;
        }

        int targetCount = GenerationLimits.clampLayoutInstanceCount(requestedTarget);
        if (targetCount <= 0) {
            writeInvalid();
            return;
        }

        Vector3d origin = resolveOrigin(inputValues.get(INPUT_ORIGIN_ID), plane);
        PlaneProjectionUtils.PlaneAxes axes = PlaneProjectionUtils.PlaneAxes.from(plane);
        Vector3d projectedOrigin = plane.projectPoint(origin);
        Vector2d originUv = axes.to2d(projectedOrigin);

        int resolvedSeed = DeterministicSeedUtils.resolveSeed(inputValues.get(INPUT_SEED_ID), seed);
        Random rng = new Random(resolvedSeed);

        double minDistSq = minDist * minDist;
        List<Vector3d> accepted = new ArrayList<>(targetCount);
        int attempts = 0;
        int cap = GenerationLimits.clampAttemptBudget(maxAttempts, targetCount);

        while (accepted.size() < targetCount && attempts < cap) {
            attempts++;
            double u = originUv.x + (rng.nextDouble() * 2.0d - 1.0d) * halfU;
            double v = originUv.y + (rng.nextDouble() * 2.0d - 1.0d) * halfV;
            Vector3d candidate = axes.from2d(new Vector2d(u, v));

            if (MinDistanceScatterSelector.isFarEnough(candidate, accepted, minDistSq)) {
                accepted.add(candidate);
            }
        }

        outputValues.put(OUTPUT_POINTS_ID, SpatialValueResolver.toPointDataList(accepted));
        outputValues.put(OUTPUT_COUNT_ID, accepted.size());
        outputValues.put(OUTPUT_ATTEMPTS_ID, attempts);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_ATTEMPTS_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private static Vector3d resolveOrigin(Object value, PlaneData plane) {
        Vector3d resolved = SpatialValueResolver.resolveVector3d(value);
        if (resolved != null) {
            return plane.projectPoint(resolved);
        }
        return new Vector3d(plane.getPoint());
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        int resolved = Math.max(100, maxAttempts);
        if (this.maxAttempts != resolved) {
            this.maxAttempts = resolved;
            markDirty();
        }
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
        return java.util.Map.of("maxAttempts", maxAttempts, "seed", seed);
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map<?, ?> map) {
            Object ma = map.get("maxAttempts");
            if (ma instanceof Number n) {
                setMaxAttempts(n.intValue());
            }
            Object seedValue = map.get("seed");
            if (seedValue instanceof Number n) {
                setSeed(n.intValue());
            }
        }
    }
}

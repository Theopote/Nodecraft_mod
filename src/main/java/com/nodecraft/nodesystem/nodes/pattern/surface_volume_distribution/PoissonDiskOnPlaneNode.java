package com.nodecraft.nodesystem.nodes.pattern.surface_volume_distribution;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PlaneProjectionUtils;
import com.nodecraft.nodesystem.util.DeterministicSeedUtils;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.MinDistanceScatterSelector;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2d;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "pattern.surface_volume_distribution.poisson_disk_plane",
    displayName = "Poisson Disk On Plane",
    description = "Samples points on a plane inside a UV rectangle with minimum separation using rejection sampling",
    category = "pattern.surface_volume_distribution",
    order = 2
)
public class PoissonDiskOnPlaneNode extends AbstractSurfaceVolumeDistributionNode {

    @NodeProperty(displayName = "Max Attempts", category = "Sampling", order = 1,
        description = "Maximum random proposals before stopping (may return fewer than Target Count)")
    private int maxAttempts = 20_000;

    @NodeProperty(displayName = "Seed", category = "Sampling", order = 2)
    private int seed = DeterministicSeedUtils.DEFAULT_SEED;

    @NodeProperty(displayName = "Target Count", category = "Sampling", order = 3)
    private int targetCount = 64;

    @NodeProperty(displayName = "Half U", category = "Sampling", order = 4)
    private double halfU = 1.0d;

    @NodeProperty(displayName = "Half V", category = "Sampling", order = 5)
    private double halfV = 1.0d;

    @NodeProperty(displayName = "Min Distance", category = "Sampling", order = 6)
    private double minDistance = 0.0d;

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

    public PoissonDiskOnPlaneNode() {
        super(UUID.randomUUID(), "pattern.surface_volume_distribution.poisson_disk_plane");

        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Plane defining UV basis and projection", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_ORIGIN_ID, "Origin", "Rectangle center on the plane. When disconnected, the plane reference point is used.", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_HALF_U_ID, "Half U", "Half extent along the plane U axis (> 0)", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_HALF_V_ID, "Half V", "Half extent along the plane V axis (> 0)", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_TARGET_COUNT_ID, "Target Count", "Target number of samples", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_MIN_DISTANCE_ID, "Min Distance", "Minimum Euclidean distance between accepted samples (>= 0)", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SEED_ID, "Seed", "Optional RNG seed for reproducibility", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Accepted sample positions", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of accepted samples", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_ATTEMPTS_ID, "Attempts", "Number of random proposals tried", NodeDataType.INTEGER, this));
        addValidErrorAndCompleteOutputs();
    }

    @Override
    public String getDescription() {
        return "Samples points on a plane inside a UV rectangle with minimum separation using rejection sampling";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        if (!OptionalPortDrive.isConnected(this, INPUT_PLANE_ID)) {
            writeFail("Plane is required");
            return;
        }
        PlaneData planeRaw = OptionalPortDrive.resolveOptionalPlane(this, INPUT_PLANE_ID, null);
        if (planeRaw == null) {
            writeFail("Plane connected but invalid");
            return;
        }
        PlaneData plane = planeRaw.normalized();
        if (plane == null) {
            writeFail("Plane is degenerate");
            return;
        }

        Integer resolvedTarget = resolveLayoutCount(INPUT_TARGET_COUNT_ID, targetCount);
        if (resolvedTarget == null) {
            writeFail(countFailureReason(INPUT_TARGET_COUNT_ID, targetCount));
            return;
        }

        Double resolvedHalfU = resolvePositiveFinite(INPUT_HALF_U_ID, halfU);
        Double resolvedHalfV = resolvePositiveFinite(INPUT_HALF_V_ID, halfV);
        if (resolvedHalfU == null || resolvedHalfV == null) {
            writeFail("Half U/V must be finite and > 0");
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

        Vector3d origin;
        if (OptionalPortDrive.isConnected(this, INPUT_ORIGIN_ID)) {
            Vector3d resolved = OptionalPortDrive.resolveOptionalPoint(this, INPUT_ORIGIN_ID, null);
            if (resolved == null) {
                writeFail("Origin connected but invalid");
                return;
            }
            origin = plane.projectPoint(resolved);
        } else {
            origin = new Vector3d(plane.getPoint());
        }

        PlaneProjectionUtils.PlaneAxes axes = PlaneProjectionUtils.PlaneAxes.from(plane);
        Vector2d originUv = axes.to2d(origin);

        if (maxAttempts < 100) {
            writeFail("Max Attempts must be >= 100");
            return;
        }

        int attemptCap = GenerationLimits.clampAttemptBudget(maxAttempts, resolvedTarget);
        String distanceBudget = GenerationLimits.validateScatterDistanceTests(attemptCap, resolvedTarget);
        if (distanceBudget != null) {
            writeFail(distanceBudget);
            return;
        }

        Random rng = new Random(resolvedSeed);
        double minDistSq = minDist * minDist;
        List<Vector3d> accepted = new ArrayList<>(resolvedTarget);
        int attempts = 0;

        while (accepted.size() < resolvedTarget && attempts < attemptCap) {
            attempts++;
            double u = originUv.x + (rng.nextDouble() * 2.0d - 1.0d) * resolvedHalfU;
            double v = originUv.y + (rng.nextDouble() * 2.0d - 1.0d) * resolvedHalfV;
            Vector3d candidate = axes.from2d(new Vector2d(u, v));

            if (MinDistanceScatterSelector.isFarEnough(candidate, accepted, minDistSq)) {
                accepted.add(candidate);
            }
        }

        boolean complete = accepted.size() == resolvedTarget;
        if (!commitPointList(OUTPUT_POINTS_ID, OUTPUT_COUNT_ID, accepted, complete)) {
            putIntOutputs(0, OUTPUT_ATTEMPTS_ID);
            return;
        }
        putIntOutputs(attempts, OUTPUT_ATTEMPTS_ID);
    }

    private void writeFail(String error) {
        markInvalid(error);
        putEmptyListOutputs(OUTPUT_POINTS_ID);
        putIntOutputs(0, OUTPUT_COUNT_ID, OUTPUT_ATTEMPTS_ID);
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        if (this.maxAttempts != maxAttempts) {
            this.maxAttempts = maxAttempts;
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

    public int getTargetCount() {
        return targetCount;
    }

    public void setTargetCount(int targetCount) {
        this.targetCount = targetCount;
        markDirty();
    }

    public double getHalfU() {
        return halfU;
    }

    public void setHalfU(double halfU) {
        this.halfU = halfU;
        markDirty();
    }

    public double getHalfV() {
        return halfV;
    }

    public void setHalfV(double halfV) {
        this.halfV = halfV;
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
        state.put("maxAttempts", maxAttempts);
        state.put("seed", seed);
        state.put("targetCount", targetCount);
        state.put("halfU", halfU);
        state.put("halfV", halfV);
        state.put("minDistance", minDistance);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("maxAttempts") instanceof Number n) {
            setMaxAttempts(n.intValue());
        }
        if (map.get("seed") instanceof Number n) {
            setSeed(n.intValue());
        }
        if (map.get("targetCount") instanceof Number n) {
            setTargetCount(n.intValue());
        }
        if (map.get("halfU") instanceof Number n) {
            setHalfU(n.doubleValue());
        }
        if (map.get("halfV") instanceof Number n) {
            setHalfV(n.doubleValue());
        }
        if (map.get("minDistance") instanceof Number n) {
            setMinDistance(n.doubleValue());
        }
    }
}

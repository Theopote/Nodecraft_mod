package com.nodecraft.nodesystem.nodes.pattern.surface_volume_distribution;

import com.nodecraft.nodesystem.nodes.geometry.curves.util.PlaneProjectionUtils;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2d;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Poisson-disk style sampling on a plane rectangle in UV space (bridson-like rejection sampling).
 */
@NodeInfo(
    id = "pattern.surface_volume_distribution.poisson_disk_plane",
    displayName = "Poisson Disk On Plane",
    description = "Samples points on a plane inside a UV rectangle with minimum separation using rejection sampling",
    category = "pattern.surface_volume_distribution",
    order = 3
)
public class PoissonDiskOnPlaneNode extends BaseNode {

    private static final double EPS = 1.0e-9d;

    @NodeProperty(displayName = "Max Attempts", category = "Sampling", order = 1,
        description = "Maximum random proposals before stopping (may return fewer than Count)")
    private int maxAttempts = 20000;

    private static final String INPUT_PLANE_ID = "input_plane";
    private static final String INPUT_ORIGIN_ID = "input_origin";
    private static final String INPUT_HALF_U_ID = "input_half_u";
    private static final String INPUT_HALF_V_ID = "input_half_v";
    private static final String INPUT_COUNT_ID = "input_count";
    private static final String INPUT_MIN_DISTANCE_ID = "input_min_distance";
    private static final String INPUT_SEED_ID = "input_seed";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_ATTEMPTS_ID = "output_attempts";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public PoissonDiskOnPlaneNode() {
        super(UUID.randomUUID(), "pattern.surface_volume_distribution.poisson_disk_plane");

        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane",
            "Plane defining UV basis and projection",
            NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_ORIGIN_ID, "Origin",
            "Rectangle center on the plane (Point, Vector, or BlockPos). When disconnected, the plane reference point is used.",
            NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_HALF_U_ID, "Half U",
            "Half extent along the plane U axis",
            NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_HALF_V_ID, "Half V",
            "Half extent along the plane V axis",
            NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_COUNT_ID, "Count",
            "Target number of samples",
            NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_MIN_DISTANCE_ID, "Min Distance",
            "Minimum Euclidean distance between accepted samples",
            NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SEED_ID, "Seed",
            "Optional RNG seed for reproducibility",
            NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points",
            "Accepted sample positions as Vector3d list",
            NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count",
            "Number of accepted samples",
            NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_ATTEMPTS_ID, "Attempts",
            "Number of random proposals tried",
            NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when the requested count was reached",
            NodeDataType.BOOLEAN, this));
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
        Object halfUObj = inputValues.get(INPUT_HALF_U_ID);
        Object halfVObj = inputValues.get(INPUT_HALF_V_ID);
        Object countObj = inputValues.get(INPUT_COUNT_ID);
        Object minDistObj = inputValues.get(INPUT_MIN_DISTANCE_ID);
        if (!(planeObj instanceof PlaneData plane)
            || !(halfUObj instanceof Number huNum)
            || !(halfVObj instanceof Number hvNum)
            || !(countObj instanceof Number cNum)
            || !(minDistObj instanceof Number mdNum)) {
            writeInvalid();
            return;
        }

        double halfU = huNum.doubleValue();
        double halfV = hvNum.doubleValue();
        int targetCount = GenerationLimits.clampPositiveCount(cNum.intValue());
        double minDist = mdNum.doubleValue();

        if (targetCount < 1 || halfU <= EPS || halfV <= EPS || minDist < EPS) {
            writeInvalid();
            return;
        }

        Vector3d origin = resolveOrigin(inputValues.get(INPUT_ORIGIN_ID), plane);
        com.nodecraft.nodesystem.nodes.geometry.curves.util.PlaneProjectionUtils.PlaneAxes axes =
            com.nodecraft.nodesystem.nodes.geometry.curves.util.PlaneProjectionUtils.PlaneAxes.from(plane);
        Vector3d projectedOrigin = plane.projectPoint(origin);
        Vector2d originUv = axes.to2d(projectedOrigin);

        Object seedObj = inputValues.get(INPUT_SEED_ID);
        long seed = seedObj instanceof Number n ? n.longValue() : System.nanoTime();
        Random rng = new Random(seed);

        double minDistSq = minDist * minDist;
        List<Vector3d> accepted = new ArrayList<>(targetCount);
        int attempts = 0;
        int cap = GenerationLimits.clampAttemptBudget(maxAttempts, targetCount);

        while (accepted.size() < targetCount && attempts < cap) {
            attempts++;
            double u = originUv.x + (rng.nextDouble() * 2.0d - 1.0d) * halfU;
            double v = originUv.y + (rng.nextDouble() * 2.0d - 1.0d) * halfV;
            Vector3d candidate = axes.from2d(new Vector2d(u, v));

            boolean ok = true;
            for (Vector3d p : accepted) {
                if (p.distanceSquared(candidate) < minDistSq) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                accepted.add(candidate);
            }
        }

        outputValues.put(OUTPUT_POINTS_ID, accepted);
        outputValues.put(OUTPUT_COUNT_ID, accepted.size());
        outputValues.put(OUTPUT_ATTEMPTS_ID, attempts);
        outputValues.put(OUTPUT_VALID_ID, accepted.size() == targetCount);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_ATTEMPTS_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private static Vector3d resolveOrigin(Object value, PlaneData plane) {
        if (value instanceof PointData pd) {
            return plane.projectPoint(new Vector3d(pd.getPosition()));
        }
        if (value instanceof Vector3d v) {
            return plane.projectPoint(new Vector3d(v));
        }
        if (value instanceof BlockPos bp) {
            return plane.projectPoint(new Vector3d(bp.getX(), bp.getY(), bp.getZ()));
        }
        return new Vector3d(plane.getPoint());
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = GenerationLimits.clampAttemptBudget(Math.max(100, maxAttempts), 1);
        markDirty();
    }

    @Override
    public Object getNodeState() {
        return java.util.Map.of("maxAttempts", maxAttempts);
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map<?, ?> map) {
            Object ma = map.get("maxAttempts");
            if (ma instanceof Number n) {
                setMaxAttempts(n.intValue());
            }
        }
    }
}

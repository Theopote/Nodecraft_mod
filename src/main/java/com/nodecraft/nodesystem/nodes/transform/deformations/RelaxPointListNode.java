package com.nodecraft.nodesystem.nodes.transform.deformations;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.PointListKnn3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "transform.deformations.relax_points",
    displayName = "Relax Point List",
    description = "Laplacian-style smoothing using k nearest neighbors (uniform grid hash for speed; capped point count)",
    category = "transform.deformations",
    order = 5
)
public class RelaxPointListNode extends BaseNode {

    @NodeProperty(displayName = "Neighbors K", category = "Relax", order = 1,
        description = "Number of nearest neighbors to average (excluding self)")
    private int neighborsK = 6;

    @NodeProperty(displayName = "Iterations", category = "Relax", order = 2)
    private int iterations = 4;

    @NodeProperty(displayName = "Blend", category = "Relax", order = 3,
        description = "How much each step moves toward the neighbor centroid (0-1)")
    private double blend = 0.35d;

    @NodeProperty(displayName = "Max Points", category = "Relax", order = 4,
        description = "Safety cap; lists larger than this are rejected")
    private int maxPoints = 2048;

    private static final String INPUT_POINTS_ID = "input_points";
    private static final String INPUT_K_ID = "input_k";
    private static final String INPUT_ITERATIONS_ID = "input_iterations";
    private static final String INPUT_BLEND_ID = "input_blend";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public RelaxPointListNode() {
        super(UUID.randomUUID(), "transform.deformations.relax_points");

        addInputPort(new BasePort(INPUT_POINTS_ID, "Points", "Point list to smooth", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_K_ID, "K", "Neighbor count override", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_ITERATIONS_ID, "Iterations", "Smoothing iterations override", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_BLEND_ID, "Blend", "Blend factor override", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Smoothed point list", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of output points", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when smoothing succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Laplacian-style smoothing using k nearest neighbors (uniform grid hash for speed; capped point count)";
    }

    @Override
    public String getDisplayName() {
        return "Relax Point List";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object pointsObj = inputValues.get(INPUT_POINTS_ID);
        if (!(pointsObj instanceof List<?> pointsInput)) {
            writeEmpty();
            return;
        }

        List<Vector3d> pts = new ArrayList<>();
        for (Object entry : pointsInput) {
            Vector3d p = resolvePoint(entry);
            if (p != null) {
                pts.add(new Vector3d(p));
            }
        }
        if (pts.size() < 2) {
            writeEmpty();
            return;
        }

        int cap = Math.max(8, Math.min(8192, maxPoints));
        if (pts.size() > cap) {
            writeEmpty();
            return;
        }

        int k = resolveInt(inputValues.get(INPUT_K_ID), neighborsK);
        k = Math.max(1, Math.min(pts.size() - 1, k));
        int iters = resolveInt(inputValues.get(INPUT_ITERATIONS_ID), iterations);
        iters = Math.max(1, Math.min(64, iters));
        double lambda = resolveDouble(inputValues.get(INPUT_BLEND_ID), blend);
        lambda = Math.max(0.0d, Math.min(1.0d, lambda));

        int[] idxBuf = new int[k];

        List<Vector3d> current = new ArrayList<>(pts);
        for (int it = 0; it < iters; it++) {
            List<Vector3d> next = new ArrayList<>(current.size());
            for (int i = 0; i < current.size(); i++) {
                Vector3d p = current.get(i);
                PointListKnn3d.fillKNearest(current, i, k, idxBuf);
                Vector3d centroid = new Vector3d();
                int used = 0;
                for (int t = 0; t < k; t++) {
                    int j = idxBuf[t];
                    if (j < 0) {
                        continue;
                    }
                    centroid.add(current.get(j));
                    used++;
                }
                if (used == 0) {
                    next.add(new Vector3d(p));
                    continue;
                }
                centroid.div(used);
                Vector3d out = new Vector3d(p).lerp(centroid, lambda);
                next.add(out);
            }
            current = next;
        }

        outputValues.put(OUTPUT_POINTS_ID, List.copyOf(current));
        outputValues.put(OUTPUT_COUNT_ID, current.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeEmpty() {
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private static int resolveInt(Object value, int fallback) {
        return value instanceof Number n ? n.intValue() : fallback;
    }

    private static double resolveDouble(Object value, double fallback) {
        return DeformationUtils.resolveFiniteDouble(value, fallback);
    }

    private static Vector3d resolvePoint(Object value) {
        Vector3d point = DeformationUtils.resolvePoint(value);
        return DeformationUtils.isFinite(point) ? point : null;
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("neighborsK", neighborsK);
        state.put("iterations", iterations);
        state.put("blend", blend);
        state.put("maxPoints", maxPoints);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        neighborsK = Math.max(1, DeformationUtils.intOrCurrent(map.get("neighborsK"), neighborsK));
        iterations = Math.max(1, DeformationUtils.intOrCurrent(map.get("iterations"), iterations));
        blend = Math.max(0.0d, Math.min(1.0d, DeformationUtils.finiteOrCurrent(map.get("blend"), blend)));
        maxPoints = Math.max(8, Math.min(8192, DeformationUtils.intOrCurrent(map.get("maxPoints"), maxPoints)));
    }
}

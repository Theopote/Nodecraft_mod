package com.nodecraft.nodesystem.nodes.transform.deformations;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import com.nodecraft.nodesystem.util.PointListKnn3d;
import com.nodecraft.nodesystem.util.PointUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "transform.deformations.relax_points",
    displayName = "Relax Point List",
    description = "Laplacian-style smoothing using k nearest neighbors (uniform grid hash for speed)",
    category = "transform.deformations",
    order = 7
)
public class RelaxPointListNode extends AbstractDeformationNode {

    @NodeProperty(displayName = "Neighbors K", category = "Relax", order = 1,
        description = "Number of nearest neighbors to average (excluding self)")
    private int neighborsK = 6;

    @NodeProperty(displayName = "Iterations", category = "Relax", order = 2)
    private int iterations = 4;

    @NodeProperty(displayName = "Blend", category = "Relax", order = 3,
        description = "How much each step moves toward the neighbor centroid (0-1)")
    private double blend = 0.35d;

    private static final String INPUT_POINTS_ID = "input_points";
    private static final String INPUT_K_ID = "input_k";
    private static final String INPUT_ITERATIONS_ID = "input_iterations";
    private static final String INPUT_BLEND_ID = "input_blend";

    public RelaxPointListNode() {
        super("transform.deformations.relax_points");

        addInputPort(new BasePort(INPUT_POINTS_ID, "Points", "Point list to smooth", NodeDataType.POINT_LIST, this));
        addInputPort(new BasePort(INPUT_K_ID, "K", "Neighbor count override", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_ITERATIONS_ID, "Iterations", "Smoothing iterations override", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_BLEND_ID, "Blend", "Blend factor override", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Smoothed point list", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of output points", NodeDataType.INTEGER, this));
        addValidAndErrorOutputs();
    }

    @Override
    public String getDescription() {
        return "Laplacian-style smoothing using k nearest neighbors (uniform grid hash for speed)";
    }

    @Override
    public String getDisplayName() {
        return "Relax Point List";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> pts = PointUtils.resolveStrictPointList(inputValues.get(INPUT_POINTS_ID));
        if (pts == null || pts.size() < 2) {
            failPointList("Invalid point list");
            return;
        }
        if (pts.size() > GenerationLimits.MAX_RELAX_POINTS) {
            failPointList("Point list exceeds relax budget");
            return;
        }

        Integer k = OptionalPortDrive.resolveOptionalInteger(this, INPUT_K_ID, neighborsK);
        Integer iters = OptionalPortDrive.resolveOptionalInteger(this, INPUT_ITERATIONS_ID, iterations);
        Double lambda = OptionalPortDrive.resolveOptionalDouble(this, INPUT_BLEND_ID, blend);

        if (k == null || iters == null || lambda == null) {
            failPointList("Invalid relax parameters");
            return;
        }
        if (k < 1 || k > pts.size() - 1) {
            failPointList("K out of range");
            return;
        }
        if (iters < 1 || iters > GenerationLimits.MAX_RELAX_ITERATIONS) {
            failPointList("Iterations out of range");
            return;
        }
        if (lambda < 0.0d || lambda > 1.0d) {
            failPointList("Blend must be between 0 and 1");
            return;
        }

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
                next.add(new Vector3d(p).lerp(centroid, lambda));
            }
            current = next;
        }

        commitPointList(current);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("neighborsK", neighborsK);
        state.put("iterations", iterations);
        state.put("blend", blend);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("neighborsK") instanceof Integer value && value >= 1) {
            neighborsK = value;
        }
        if (map.get("iterations") instanceof Integer value && value >= 1 && value <= GenerationLimits.MAX_RELAX_ITERATIONS) {
            iterations = value;
        }
        if (map.get("blend") instanceof Number value) {
            double v = value.doubleValue();
            if (Double.isFinite(v) && v >= 0.0d && v <= 1.0d) {
                blend = v;
            }
        }
    }
}

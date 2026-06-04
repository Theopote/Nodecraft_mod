package com.nodecraft.nodesystem.nodes.geometry.solids;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.SurfaceStripData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Projects query points onto the piecewise-triangle mesh of a {@link SurfaceStripData} strip.
 */
@NodeInfo(
    id = "geometry.solids.shrinkwrap_points_surface_strip",
    displayName = "Shrinkwrap Points On Surface Strip",
    description = "Projects each query point to the closest location on the surface strip triangle mesh",
    category = "geometry.solids",
    order = 20
)
public class ShrinkwrapPointsOnSurfaceStripNode extends BaseNode {

    private static final double EPS = 1.0e-12d;

    private static final String INPUT_POINTS_ID = "input_points";
    private static final String INPUT_SURFACE_STRIP_ID = "input_surface_strip";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_DISTANCES_ID = "output_distances";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ShrinkwrapPointsOnSurfaceStripNode() {
        super(UUID.randomUUID(), "geometry.solids.shrinkwrap_points_surface_strip");

        addInputPort(new BasePort(INPUT_POINTS_ID, "Points",
            "Point list (Point, Vector, BlockPos, etc.) or a single point value",
            NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_SURFACE_STRIP_ID, "Surface Strip",
            "Surface strip whose quad strips are triangulated for projection",
            NodeDataType.SURFACE_STRIP, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Projected Points",
            "Closest points on the strip as Vector3d list",
            NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_DISTANCES_ID, "Distances",
            "Per-point distances from query to projected location",
            NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when projection succeeded",
            NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDisplayName() {
        return "Shrinkwrap Points On Surface Strip";
    }

    @Override
    public String getDescription() {
        return "Projects each query point to the closest location on the surface strip triangle mesh";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object stripObj = inputValues.get(INPUT_SURFACE_STRIP_ID);
        if (!(stripObj instanceof SurfaceStripData strip)) {
            writeInvalid();
            return;
        }

        List<Vector3d> queries = SolidNodeUtils.resolvePointList(inputValues.get(INPUT_POINTS_ID));
        if (queries.isEmpty()) {
            writeInvalid();
            return;
        }

        List<Triangle> triangles = buildTriangles(strip);
        if (triangles.isEmpty()) {
            writeInvalid();
            return;
        }

        List<Vector3d> projected = new ArrayList<>(queries.size());
        List<Double> distances = new ArrayList<>(queries.size());
        for (Vector3d q : queries) {
            double bestSq = Double.MAX_VALUE;
            Vector3d best = null;
            for (Triangle tri : triangles) {
                Vector3d c = closestOnTriangle(q, tri.a, tri.b, tri.c);
                double dSq = q.distanceSquared(c);
                if (dSq < bestSq) {
                    bestSq = dSq;
                    best = c;
                }
            }
            if (best == null) {
                writeInvalid();
                return;
            }
            projected.add(best);
            distances.add(Math.sqrt(bestSq));
        }

        outputValues.put(OUTPUT_POINTS_ID, projected);
        outputValues.put(OUTPUT_DISTANCES_ID, distances);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_DISTANCES_ID, List.of());
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private static List<Triangle> buildTriangles(SurfaceStripData strip) {
        List<List<Vector3d>> sections = strip.getSections();
        List<Boolean> closedFlags = strip.getSectionClosedFlags();
        List<Triangle> tris = new ArrayList<>();
        if (sections.size() < 2) {
            return tris;
        }
        int pointCount = sections.get(0).size();
        if (pointCount < 2) {
            return tris;
        }

        for (int u = 0; u < sections.size() - 1; u++) {
            List<Vector3d> lower = sections.get(u);
            List<Vector3d> upper = sections.get(u + 1);
            boolean wrap = Boolean.TRUE.equals(closedFlags.get(u))
                && Boolean.TRUE.equals(closedFlags.get(u + 1));
            int segCount = wrap ? pointCount : pointCount - 1;
            for (int j = 0; j < segCount; j++) {
                Vector3d a = lower.get(j);
                Vector3d b = lower.get((j + 1) % pointCount);
                Vector3d c = upper.get(j);
                Vector3d d = upper.get((j + 1) % pointCount);
                tris.add(new Triangle(a, b, c));
                tris.add(new Triangle(b, d, c));
            }
        }
        return tris;
    }

    private record Triangle(Vector3d a, Vector3d b, Vector3d c) {
    }

    /**
     * Closest point on triangle ABC to point P (Real-Time Collision Detection, Christer Ericson).
     */
    private static Vector3d closestOnTriangle(Vector3d p, Vector3d a, Vector3d b, Vector3d c) {
        Vector3d ab = new Vector3d(b).sub(a);
        Vector3d ac = new Vector3d(c).sub(a);
        Vector3d ap = new Vector3d(p).sub(a);
        double d1 = ab.dot(ap);
        double d2 = ac.dot(ap);
        if (d1 <= 0.0d && d2 <= 0.0d) {
            return new Vector3d(a);
        }

        Vector3d bp = new Vector3d(p).sub(b);
        double d3 = ab.dot(bp);
        double d4 = ac.dot(bp);
        if (d3 >= 0.0d && d4 <= d3) {
            return new Vector3d(b);
        }

        double vc = d1 * d4 - d3 * d2;
        if (vc <= 0.0d && d1 >= 0.0d && d3 <= 0.0d) {
            double denom = d1 - d3;
            double v = Math.abs(denom) < EPS ? 0.0d : d1 / denom;
            return new Vector3d(a).add(ab.mul(v, new Vector3d()));
        }

        Vector3d cp = new Vector3d(p).sub(c);
        double d5 = ab.dot(cp);
        double d6 = ac.dot(cp);
        if (d6 >= 0.0d && d5 <= d6) {
            return new Vector3d(c);
        }

        double vb = d5 * d2 - d1 * d6;
        if (vb <= 0.0d && d2 >= 0.0d && d6 <= 0.0d) {
            double denom = d2 - d6;
            double w = Math.abs(denom) < EPS ? 0.0d : d2 / denom;
            return new Vector3d(a).add(ac.mul(w, new Vector3d()));
        }

        double va = d3 * d6 - d5 * d4;
        if (va <= 0.0d && (d4 - d3) >= 0.0d && (d5 - d6) >= 0.0d) {
            double denom = (d4 - d3) + (d5 - d6);
            double w = Math.abs(denom) < EPS ? 0.0d : (d4 - d3) / denom;
            return new Vector3d(b).add(new Vector3d(c).sub(b).mul(w, new Vector3d()));
        }

        double denom = va + vb + vc;
        if (Math.abs(denom) < EPS) {
            return new Vector3d(a);
        }
        double v = vb / denom;
        double w = vc / denom;
        return new Vector3d(a).add(ab.mul(v, new Vector3d())).add(ac.mul(w, new Vector3d()));
    }
}

package com.nodecraft.nodesystem.nodes.geometry.solids;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.SurfaceStripData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import com.nodecraft.nodesystem.util.SurfaceInputUtils;
import com.nodecraft.nodesystem.util.VectorUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Projects query points onto the piecewise-triangle mesh of a {@link SurfaceStripData} strip.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.solids.shrinkwrap_points_surface_strip",
    displayName = "Shrinkwrap Points On Surface Strip",
    description = "Projects each query point to the closest location on the surface strip triangle mesh",
    category = "geometry.solids",
    order = 20
)
public class ShrinkwrapPointsOnSurfaceStripNode extends AbstractSolidNode {

    private static final double EPS = 1.0e-12d;

    private static final String INPUT_POINTS_ID = "input_points";
    private static final String INPUT_SURFACE_STRIP_ID = "input_surface_strip";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_DISTANCES_ID = "output_distances";

    public ShrinkwrapPointsOnSurfaceStripNode() {
        super(UUID.randomUUID(), "geometry.solids.shrinkwrap_points_surface_strip");

        addInputPort(new BasePort(INPUT_POINTS_ID, "Points",
            "Query point list to project onto the surface strip",
            NodeDataType.POINT_LIST, this));
        addInputPort(new BasePort(INPUT_SURFACE_STRIP_ID, "Surface Strip",
            "Surface strip whose quad strips are triangulated for projection",
            NodeDataType.SURFACE_STRIP, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Projected Points",
            "Closest points on the strip as point list",
            NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_DISTANCES_ID, "Distances",
            "Per-point distances from query to projected location",
            NodeDataType.DOUBLE_LIST, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when projection succeeded",
            NodeDataType.BOOLEAN, this));
        addErrorOutputPort();
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
            invalidate("Surface strip is missing");
            return;
        }

        String stripError = validateSurfaceStrip(strip);
        if (stripError != null) {
            invalidate(stripError);
            return;
        }

        List<Vector3d> queries = SolidNodeUtils.resolveStrictPointList(inputValues.get(INPUT_POINTS_ID));
        if (queries == null) {
            invalidate("Query point list is missing or contains non-PointData / non-finite entries");
            return;
        }
        if (queries.isEmpty()) {
            putEmptyListOutputs(OUTPUT_POINTS_ID, OUTPUT_DISTANCES_ID);
            markSuccess();
            return;
        }

        List<Triangle> triangles = buildTriangles(strip);
        if (triangles.isEmpty()) {
            invalidate("Surface strip triangle mesh is empty");
            return;
        }

        long queryCount = queries.size();
        long triangleCount = triangles.size();
        if (!SurfaceInputUtils.isWithinProjectionWorkload(queryCount, triangleCount)) {
            invalidate("Projection workload exceeds limit (" + GenerationLimits.MAX_NEAREST_PROJECTION_WORK + ")");
            return;
        }

        List<Vector3d> projected = new ArrayList<>(queries.size());
        List<Double> distances = new ArrayList<>(queries.size());
        for (Vector3d q : queries) {
            double bestDist = Double.POSITIVE_INFINITY;
            Vector3d best = null;
            for (Triangle tri : triangles) {
                Vector3d c = closestOnTriangle(q, tri.a, tri.b, tri.c);
                if (c == null) {
                    continue;
                }
                double dist = VectorUtils.safeDistance(q, c);
                if (!Double.isFinite(dist)) {
                    invalidate("Nearest surface distance is non-finite");
                    return;
                }
                if (dist < bestDist) {
                    bestDist = dist;
                    best = c;
                }
            }
            if (best == null) {
                invalidate("Nearest surface point could not be resolved");
                return;
            }
            projected.add(best);
            distances.add(bestDist);
        }

        outputValues.put(OUTPUT_POINTS_ID, SpatialValueResolver.toPointDataList(projected));
        outputValues.put(OUTPUT_DISTANCES_ID, List.copyOf(distances));
        markSuccess();
    }

    private void invalidate(String error) {
        putEmptyListOutputs(OUTPUT_POINTS_ID, OUTPUT_DISTANCES_ID);
        markInvalid(error);
    }

    private static List<Triangle> buildTriangles(SurfaceStripData strip) {
        List<List<Vector3d>> sections = strip.sections();
        List<Boolean> closedFlags = strip.sectionClosedFlags();
        List<Triangle> tris = new ArrayList<>();
        if (sections.size() < 2) {
            return tris;
        }
        int pointCount = sections.getFirst().size();
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

    private static @Nullable Vector3d closestOnTriangle(Vector3d p, Vector3d a, Vector3d b, Vector3d c) {
        Vector3d ab = VectorUtils.safeSubtract(b, a);
        Vector3d ac = VectorUtils.safeSubtract(c, a);
        Vector3d ap = VectorUtils.safeSubtract(p, a);
        if (ab == null || ac == null || ap == null) {
            return null;
        }
        double d1 = VectorUtils.safeDot(ab, ap);
        double d2 = VectorUtils.safeDot(ac, ap);
        if (!Double.isFinite(d1) || !Double.isFinite(d2)) {
            return null;
        }
        if (d1 <= 0.0d && d2 <= 0.0d) {
            return new Vector3d(a);
        }

        Vector3d bp = VectorUtils.safeSubtract(p, b);
        if (bp == null) {
            return null;
        }
        double d3 = VectorUtils.safeDot(ab, bp);
        double d4 = VectorUtils.safeDot(ac, bp);
        if (!Double.isFinite(d3) || !Double.isFinite(d4)) {
            return null;
        }
        if (d3 >= 0.0d && d4 <= d3) {
            return new Vector3d(b);
        }

        double vc = d1 * d4 - d3 * d2;
        if (!Double.isFinite(vc)) {
            return null;
        }
        if (vc <= 0.0d && d1 >= 0.0d && d3 <= 0.0d) {
            double denom = d1 - d3;
            if (!Double.isFinite(denom)) {
                return null;
            }
            double v = Math.abs(denom) < EPS ? 0.0d : d1 / denom;
            return VectorUtils.safeAdd(a, VectorUtils.safeScale(ab, v));
        }

        Vector3d cp = VectorUtils.safeSubtract(p, c);
        if (cp == null) {
            return null;
        }
        double d5 = VectorUtils.safeDot(ab, cp);
        double d6 = VectorUtils.safeDot(ac, cp);
        if (!Double.isFinite(d5) || !Double.isFinite(d6)) {
            return null;
        }
        if (d6 >= 0.0d && d5 <= d6) {
            return new Vector3d(c);
        }

        double vb = d5 * d2 - d1 * d6;
        if (!Double.isFinite(vb)) {
            return null;
        }
        if (vb <= 0.0d && d2 >= 0.0d && d6 <= 0.0d) {
            double denom = d2 - d6;
            if (!Double.isFinite(denom)) {
                return null;
            }
            double w = Math.abs(denom) < EPS ? 0.0d : d2 / denom;
            return VectorUtils.safeAdd(a, VectorUtils.safeScale(ac, w));
        }

        double va = d3 * d6 - d5 * d4;
        if (!Double.isFinite(va)) {
            return null;
        }
        if (va <= 0.0d && (d4 - d3) >= 0.0d && (d5 - d6) >= 0.0d) {
            double denom = (d4 - d3) + (d5 - d6);
            if (!Double.isFinite(denom)) {
                return null;
            }
            double w = Math.abs(denom) < EPS ? 0.0d : (d4 - d3) / denom;
            Vector3d bc = VectorUtils.safeSubtract(c, b);
            return VectorUtils.safeAdd(b, VectorUtils.safeScale(bc, w));
        }

        double denom = va + vb + vc;
        if (!Double.isFinite(denom) || Math.abs(denom) < EPS) {
            return null;
        }
        double v = vb / denom;
        double w = vc / denom;
        if (!Double.isFinite(v) || !Double.isFinite(w)) {
            return null;
        }
        Vector3d alongAb = VectorUtils.safeScale(ab, v);
        Vector3d alongAc = VectorUtils.safeScale(ac, w);
        return VectorUtils.safeAdd(a, VectorUtils.safeAdd(alongAb, alongAc));
    }
}

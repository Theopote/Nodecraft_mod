package com.nodecraft.nodesystem.nodes.reference.points;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.datatypes.SurfaceStripData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.Curve;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Finds the closest point from one query point to path, strip, or voxelized geometry inputs.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "reference.points.closest_point_to_object",
    displayName = "Closest Point To Object",
    description = "Finds the nearest point from a query point to a curve, path, surface strip, or voxelized geometry",
    category = "reference.points",
    order = 16
)
public class ClosestPointToObjectNode extends BaseNode {

    private static final double EPS = 1.0e-12d;

    @NodeProperty(displayName = "Fill Geometry", category = "Geometry", order = 1,
        description = "When enabled, voxelized geometry uses solid voxels; when disabled, it uses the shell where supported")
    private boolean fillGeometry = false;

    @NodeProperty(displayName = "Max Geometry Voxels", category = "Geometry", order = 2,
        description = "Safety cap for geometry voxel nearest-point search")
    private int maxGeometryVoxels = 65536;

    private static final String INPUT_POINT_ID = "input_point";
    private static final String INPUT_CURVE_ID = "input_curve";
    private static final String INPUT_POLYLINE_ID = "input_polyline";
    private static final String INPUT_LINE_ID = "input_line";
    private static final String INPUT_SURFACE_STRIP_ID = "input_surface_strip";
    private static final String INPUT_GEOMETRY_ID = "input_geometry";

    private static final String OUTPUT_POINT_ID = "output_point";
    private static final String OUTPUT_VECTOR_ID = "output_vector";
    private static final String OUTPUT_DISTANCE_ID = "output_distance";
    private static final String OUTPUT_OBJECT_TYPE_ID = "output_object_type";
    private static final String OUTPUT_SEGMENT_INDEX_ID = "output_segment_index";
    private static final String OUTPUT_SEGMENT_T_ID = "output_segment_t";
    private static final String OUTPUT_ARC_LENGTH_ID = "output_arc_length";
    private static final String OUTPUT_APPROXIMATE_ID = "output_approximate";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ClosestPointToObjectNode() {
        super(UUID.randomUUID(), "reference.points.closest_point_to_object");

        addInputPort(new BasePort(INPUT_POINT_ID, "Point",
            "Query point. Supports Point, Vector, Position, or Block Coordinate.",
            NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_CURVE_ID, "Curve",
            "Curve to search using its sampled points",
            NodeDataType.CURVE, this));
        addInputPort(new BasePort(INPUT_POLYLINE_ID, "Polyline",
            "Polyline to search",
            NodeDataType.POLYLINE, this));
        addInputPort(new BasePort(INPUT_LINE_ID, "Line",
            "Line segment to search",
            NodeDataType.LINE, this));
        addInputPort(new BasePort(INPUT_SURFACE_STRIP_ID, "Surface Strip",
            "Surface strip to search by triangulating its quads",
            NodeDataType.SURFACE_STRIP, this));
        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry",
            "Geometry to search by voxelizing it and testing block centers",
            NodeDataType.GEOMETRY, this));

        addOutputPort(new BasePort(OUTPUT_POINT_ID, "Closest Point",
            "Closest point as PointData",
            NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_VECTOR_ID, "Closest Vector",
            "Closest point as Vector3d",
            NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_DISTANCE_ID, "Distance",
            "Distance from the query point to the closest point",
            NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_OBJECT_TYPE_ID, "Object Type",
            "Object input that produced the closest point",
            NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_SEGMENT_INDEX_ID, "Segment Index",
            "Closest path segment index, or -1 for non-path objects",
            NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_SEGMENT_T_ID, "Segment T",
            "Interpolation parameter on the closest path segment, or NaN for non-path objects",
            NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_ARC_LENGTH_ID, "Arc Length",
            "Path length from the start to the closest point, or NaN for non-path objects",
            NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_APPROXIMATE_ID, "Approximate",
            "True when the result came from voxelized geometry or sampled curve data",
            NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when a closest point was found",
            NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDisplayName() {
        return "Closest Point To Object";
    }

    @Override
    public String getDescription() {
        return "Finds the nearest point from a query point to a curve, path, surface strip, or voxelized geometry";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d query = PointUtils.resolvePoint(inputValues.get(INPUT_POINT_ID));
        if (!PointUtils.isFinite(query)) {
            writeInvalid();
            return;
        }

        ClosestResult best = null;
        best = chooseBetter(best, closestOnCurve(query, inputValues.get(INPUT_CURVE_ID)));
        best = chooseBetter(best, closestOnPolyline(query, inputValues.get(INPUT_POLYLINE_ID), "Polyline", false));
        best = chooseBetter(best, closestOnLine(query, inputValues.get(INPUT_LINE_ID)));
        best = chooseBetter(best, closestOnSurfaceStrip(query, inputValues.get(INPUT_SURFACE_STRIP_ID)));
        best = chooseBetter(best, closestOnGeometry(query, inputValues.get(INPUT_GEOMETRY_ID)));

        if (best == null) {
            writeInvalid();
            return;
        }

        outputValues.put(OUTPUT_POINT_ID, new PointData(new Vector3d(best.point)));
        outputValues.put(OUTPUT_VECTOR_ID, new Vector3d(best.point));
        outputValues.put(OUTPUT_DISTANCE_ID, Math.sqrt(best.distanceSq));
        outputValues.put(OUTPUT_OBJECT_TYPE_ID, best.objectType);
        outputValues.put(OUTPUT_SEGMENT_INDEX_ID, best.segmentIndex);
        outputValues.put(OUTPUT_SEGMENT_T_ID, best.segmentT);
        outputValues.put(OUTPUT_ARC_LENGTH_ID, best.arcLength);
        outputValues.put(OUTPUT_APPROXIMATE_ID, best.approximate);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private static ClosestResult chooseBetter(@Nullable ClosestResult current, @Nullable ClosestResult candidate) {
        if (candidate == null) {
            return current;
        }
        if (current == null || candidate.distanceSq < current.distanceSq) {
            return candidate;
        }
        return current;
    }

    private @Nullable ClosestResult closestOnCurve(Vector3d query, @Nullable Object curveObj) {
        if (!(curveObj instanceof Curve curve)) {
            return null;
        }
        List<Vector3d> points = new ArrayList<>();
        for (Vec3d sample : curve.getSamplePoints()) {
            points.add(fromVec3d(sample));
        }
        return closestOnPolyline(query, points, "Curve", true);
    }

    private @Nullable ClosestResult closestOnPolyline(Vector3d query, @Nullable Object polylineObj, String type, boolean approximate) {
        if (!(polylineObj instanceof PolylineData polyline)) {
            return null;
        }
        List<Vector3d> points = new ArrayList<>();
        for (Vec3d point : polyline.getPoints()) {
            points.add(fromVec3d(point));
        }
        return closestOnPolyline(query, points, type, approximate);
    }

    private @Nullable ClosestResult closestOnLine(Vector3d query, @Nullable Object lineObj) {
        if (!(lineObj instanceof LineData line)) {
            return null;
        }
        return closestOnPolyline(query, List.of(fromVec3d(line.getStart()), fromVec3d(line.getEnd())), "Line", false);
    }

    private @Nullable ClosestResult closestOnPolyline(Vector3d query, List<Vector3d> rawPoints, String type, boolean approximate) {
        List<Vector3d> verts = new ArrayList<>(rawPoints.size());
        for (Vector3d point : rawPoints) {
            if (PointUtils.isFinite(point)) {
                verts.add(new Vector3d(point));
            }
        }
        if (verts.size() < 2) {
            return null;
        }

        boolean closed = isClosed(verts);
        List<Vector3d> unique = closed ? verts.subList(0, verts.size() - 1) : verts;
        if (unique.size() < 2) {
            return null;
        }

        int segCount = closed ? unique.size() : unique.size() - 1;
        double bestDistSq = Double.MAX_VALUE;
        Vector3d bestPoint = null;
        int bestSeg = -1;
        double bestT = Double.NaN;

        for (int i = 0; i < segCount; i++) {
            Vector3d a = unique.get(i);
            Vector3d b = unique.get((i + 1) % unique.size());
            SegmentClosest sc = closestOnSegment(query, a, b);
            if (sc.distanceSq < bestDistSq) {
                bestDistSq = sc.distanceSq;
                bestPoint = sc.point;
                bestSeg = i;
                bestT = sc.t;
            }
        }

        if (bestPoint == null) {
            return null;
        }

        double arcLen = 0.0d;
        for (int i = 0; i < bestSeg; i++) {
            Vector3d a = unique.get(i);
            Vector3d b = unique.get((i + 1) % unique.size());
            arcLen += a.distance(b);
        }
        arcLen += unique.get(bestSeg).distance(unique.get((bestSeg + 1) % unique.size())) * bestT;

        return new ClosestResult(bestPoint, bestDistSq, type, bestSeg, bestT, arcLen, approximate);
    }

    private @Nullable ClosestResult closestOnSurfaceStrip(Vector3d query, @Nullable Object stripObj) {
        if (!(stripObj instanceof SurfaceStripData strip)) {
            return null;
        }
        List<Triangle> triangles = buildTriangles(strip);
        if (triangles.isEmpty()) {
            return null;
        }

        double bestSq = Double.MAX_VALUE;
        Vector3d best = null;
        for (Triangle tri : triangles) {
            Vector3d candidate = closestOnTriangle(query, tri.a, tri.b, tri.c);
            double dSq = query.distanceSquared(candidate);
            if (dSq < bestSq) {
                bestSq = dSq;
                best = candidate;
            }
        }
        return best == null ? null : new ClosestResult(best, bestSq, "Surface Strip", -1, Double.NaN, Double.NaN, false);
    }

    private @Nullable ClosestResult closestOnGeometry(Vector3d query, @Nullable Object geometryObj) {
        if (!(geometryObj instanceof GeometryData geometry)) {
            return null;
        }
        BlockPosList voxels = GeometryVoxelizer.voxelize(geometry, fillGeometry);
        if (voxels.isEmpty()) {
            return null;
        }

        int cap = Math.max(1, maxGeometryVoxels);
        double bestSq = Double.MAX_VALUE;
        Vector3d best = null;
        int i = 0;
        for (BlockPos voxel : voxels) {
            if (i++ >= cap) {
                break;
            }
            Vector3d center = blockCenter(voxel);
            double dSq = query.distanceSquared(center);
            if (dSq < bestSq) {
                bestSq = dSq;
                best = center;
            }
        }
        return best == null ? null : new ClosestResult(best, bestSq, "Geometry", -1, Double.NaN, Double.NaN, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_POINT_ID, null);
        outputValues.put(OUTPUT_VECTOR_ID, null);
        outputValues.put(OUTPUT_DISTANCE_ID, Double.NaN);
        outputValues.put(OUTPUT_OBJECT_TYPE_ID, "");
        outputValues.put(OUTPUT_SEGMENT_INDEX_ID, -1);
        outputValues.put(OUTPUT_SEGMENT_T_ID, Double.NaN);
        outputValues.put(OUTPUT_ARC_LENGTH_ID, Double.NaN);
        outputValues.put(OUTPUT_APPROXIMATE_ID, false);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private static boolean isClosed(List<Vector3d> verts) {
        return verts.size() >= 3 && verts.getFirst().distanceSquared(verts.getLast()) < 1.0e-12d;
    }

    private static SegmentClosest closestOnSegment(Vector3d p, Vector3d a, Vector3d b) {
        Vector3d ab = new Vector3d(b).sub(a);
        double abLenSq = ab.lengthSquared();
        if (abLenSq < EPS * EPS) {
            return new SegmentClosest(new Vector3d(a), p.distanceSquared(a), 0.0d);
        }
        double t = new Vector3d(p).sub(a).dot(ab) / abLenSq;
        double clamped = Math.max(0.0d, Math.min(1.0d, t));
        Vector3d closest = new Vector3d(a).lerp(b, clamped);
        return new SegmentClosest(closest, p.distanceSquared(closest), clamped);
    }

    private static List<Triangle> buildTriangles(SurfaceStripData strip) {
        List<List<Vector3d>> sections = strip.getSections();
        List<Boolean> closedFlags = strip.getSectionClosedFlags();
        List<Triangle> triangles = new ArrayList<>();
        if (sections.size() < 2) {
            return triangles;
        }
        int pointCount = sections.getFirst().size();
        if (pointCount < 2) {
            return triangles;
        }

        for (int u = 0; u < sections.size() - 1; u++) {
            List<Vector3d> lower = sections.get(u);
            List<Vector3d> upper = sections.get(u + 1);
            if (lower.size() != pointCount || upper.size() != pointCount) {
                continue;
            }
            boolean wrap = Boolean.TRUE.equals(closedFlags.get(u))
                && Boolean.TRUE.equals(closedFlags.get(u + 1));
            int segCount = wrap ? pointCount : pointCount - 1;
            for (int j = 0; j < segCount; j++) {
                Vector3d a = lower.get(j);
                Vector3d b = lower.get((j + 1) % pointCount);
                Vector3d c = upper.get(j);
                Vector3d d = upper.get((j + 1) % pointCount);
                triangles.add(new Triangle(a, b, c));
                triangles.add(new Triangle(b, d, c));
            }
        }
        return triangles;
    }

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

    private static Vector3d blockCenter(BlockPos pos) {
        return new Vector3d(pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d);
    }

    private static Vector3d fromVec3d(Vec3d point) {
        return new Vector3d(point.x, point.y, point.z);
    }

    public boolean isFillGeometry() {
        return fillGeometry;
    }

    public void setFillGeometry(boolean fillGeometry) {
        if (this.fillGeometry != fillGeometry) {
            this.fillGeometry = fillGeometry;
            markDirty();
        }
    }

    public int getMaxGeometryVoxels() {
        return maxGeometryVoxels;
    }

    public void setMaxGeometryVoxels(int maxGeometryVoxels) {
        int value = Math.max(1, maxGeometryVoxels);
        if (this.maxGeometryVoxels != value) {
            this.maxGeometryVoxels = value;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        return java.util.Map.of(
            "fillGeometry", fillGeometry,
            "maxGeometryVoxels", maxGeometryVoxels
        );
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof java.util.Map<?, ?> map)) {
            return;
        }
        if (map.get("fillGeometry") instanceof Boolean value) {
            setFillGeometry(value);
        }
        if (map.get("maxGeometryVoxels") instanceof Number value) {
            setMaxGeometryVoxels(value.intValue());
        }
    }

    private record SegmentClosest(Vector3d point, double distanceSq, double t) {
    }

    private record Triangle(Vector3d a, Vector3d b, Vector3d c) {
    }

    private record ClosestResult(
        Vector3d point,
        double distanceSq,
        String objectType,
        int segmentIndex,
        double segmentT,
        double arcLength,
        boolean approximate
    ) {
    }
}

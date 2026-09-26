package com.nodecraft.nodesystem.nodes.geometry.curves.util;

import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.util.Curve;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

public final class PathUtils {

    private static final double EPS = 1.0e-9d;

    /** Distance tolerance for treating polyline endpoints as closed (matches {@link #isClosed}). */
    public static final double CLOSED_DISTANCE_EPSILON = 1.0e-6d;

    private PathUtils() {
    }

    /**
     * Unique polyline vertices for frame/sampling: when closed, drops the duplicate seam vertex
     * using the same tolerance as {@link #isClosed(List)}.
     */
    public record ClosedVertices(List<Vector3d> vertices, boolean closed) {
    }

    /**
     * Returns closed unique vertices. For a near-closed polyline {@code A-B-C-A'} where
     * {@code distance(A, A') < CLOSED_DISTANCE_EPSILON}, the result is {@code [A, B, C]} with
     * {@code closed=true}.
     */
    public static ClosedVertices closedUniqueVertices(List<Vector3d> samples) {
        List<Vector3d> verts = new ArrayList<>(samples);
        boolean closed = isClosed(verts);
        if (closed && verts.size() > 1
                && verts.getFirst().distance(verts.getLast()) < CLOSED_DISTANCE_EPSILON) {
            verts = new ArrayList<>(verts.subList(0, verts.size() - 1));
        }
        return new ClosedVertices(List.copyOf(verts), closed);
    }

    /**
     * Resolves vertices from a single path value ({@link PathData}, {@link LineData},
     * {@link PolylineData}, or {@link Curve}).
     */
    public static @Nullable List<Vector3d> resolvePath(@Nullable Object pathValue) {
        PathData path = PathData.wrap(pathValue);
        if (path == null) {
            return null;
        }
        return switch (path.getKind()) {
            case LINE -> verticesFromLine(path.getLine());
            case POLYLINE -> toVector3dList(path.getPolyline().points());
            case CURVE -> verticesFromCurve(path.getCurve());
        };
    }

    /**
     * Resolves a path from {@code PATH} (or line/polyline/curve) with {@code POINT_LIST} fallback.
     * Precedence: path value &gt; point list.
     */
    public static List<Vector3d> resolvePathOrPointList(@Nullable Object pathValue,
                                                        @Nullable Object pointListValue) {
        List<Vector3d> path = resolvePath(pathValue);
        if (path != null && path.size() >= 2) {
            return path;
        }
        return SpatialValueResolver.resolvePointList(pointListValue);
    }

    /**
     * Legacy triple-input resolver. Precedence: CURVE &gt; POLYLINE &gt; LINE.
     */
    public static @Nullable List<Vector3d> resolveVertices(@Nullable Object curveObj,
                                                           @Nullable Object polyObj,
                                                           @Nullable Object lineObj) {
        if (curveObj instanceof Curve curve) {
            return verticesFromCurve(curve);
        }
        if (polyObj instanceof PolylineData poly) {
            return toVector3dList(poly.points());
        }
        if (lineObj instanceof LineData line) {
            return verticesFromLine(line);
        }
        return resolvePath(curveObj != null ? curveObj : (polyObj != null ? polyObj : lineObj));
    }

    public static boolean isClosed(List<Vector3d> verts) {
        if (verts.size() < 3) {
            return false;
        }
        Vector3d first = verts.getFirst();
        Vector3d last = verts.getLast();
        return first.distance(last) < CLOSED_DISTANCE_EPSILON;
    }

    public static double @Nullable [] buildCumulative(List<Vector3d> unique, boolean closed) {
        int segCount = closed ? unique.size() : unique.size() - 1;
        if (segCount < 1) {
            return null;
        }
        double[] cumulative = new double[segCount + 1];
        cumulative[0] = 0.0d;
        double acc = 0.0d;
        for (int i = 0; i < segCount; i++) {
            Vector3d a = unique.get(i);
            Vector3d b = unique.get((i + 1) % unique.size());
            acc += a.distance(b);
            cumulative[i + 1] = acc;
        }
        return cumulative;
    }

    public static Vector3d sampleAtDistance(List<Vector3d> unique,
                                            boolean closed,
                                            double[] cumulative,
                                            double targetDistance) {
        double clamped = Math.max(0.0d, Math.min(targetDistance, cumulative[cumulative.length - 1]));
        for (int i = 0; i < cumulative.length - 1; i++) {
            double s0 = cumulative[i];
            double s1 = cumulative[i + 1];
            if (clamped <= s1 || i == cumulative.length - 2) {
                Vector3d p0 = unique.get(i);
                Vector3d p1 = unique.get((i + 1) % unique.size());
                double segLen = s1 - s0;
                if (segLen <= EPS) {
                    return new Vector3d(p0);
                }
                double t = (clamped - s0) / segLen;
                return new Vector3d(p0).lerp(p1, t);
            }
        }
        return new Vector3d(unique.getFirst());
    }

    public static List<Vec3d> toVec3dList(List<Vector3d> points, boolean closed) {
        List<Vec3d> out = new ArrayList<>(points.size() + (closed ? 1 : 0));
        for (Vector3d point : points) {
            out.add(new Vec3d(point.x, point.y, point.z));
        }
        if (closed && !out.isEmpty()) {
            out.add(out.getFirst());
        }
        return out;
    }

    public static @Nullable PolylineData createPolylineOrNull(List<Vec3d> points) {
        if (points.size() < 2) {
            return null;
        }
        for (Vec3d point : points) {
            if (point == null) {
                return null;
            }
        }
        return new PolylineData(points);
    }

    public static @Nullable PathData toPathData(@Nullable List<Vector3d> verts) {
        if (verts == null || verts.size() < 2) {
            return null;
        }
        boolean closed = isClosed(verts);
        PolylineData polyline = createPolylineOrNull(toVec3dList(verts, closed));
        if (polyline == null) {
            return null;
        }
        if (!closed && verts.size() == 2) {
            Vec3d a = polyline.points().get(0);
            Vec3d b = polyline.points().get(1);
            return PathData.fromLine(new LineData(a, b));
        }
        return PathData.fromPolyline(polyline);
    }

    /**
     * Joins two paths only when Path A end and Path B start are within tolerance.
     * Does not reverse paths or bridge disconnected endpoints.
     */
    public static @Nullable List<Vector3d> joinPathsStrict(@Nullable List<Vector3d> first,
                                                           @Nullable List<Vector3d> second,
                                                           double tolerance) {
        if (first == null || second == null || first.size() < 2 || second.size() < 2) {
            return null;
        }
        if (!Double.isFinite(tolerance) || tolerance < 0.0d) {
            return null;
        }

        Vector3d endA = first.getLast();
        Vector3d startB = second.getFirst();
        if (endA.distance(startB) > tolerance) {
            return null;
        }

        List<Vector3d> joined = new ArrayList<>(first.size() + second.size());
        appendVerticesFar(joined, first);
        int startIndex = endA.distance(startB) <= tolerance ? 1 : 0;
        for (int i = startIndex; i < second.size(); i++) {
            Vector3d point = second.get(i);
            if (joined.isEmpty() || joined.getLast().distanceSquared(point) > EPS * EPS) {
                joined.add(new Vector3d(point));
            }
        }
        return joined.size() >= 2 ? joined : null;
    }

    public static @Nullable List<Vector3d> reversePath(@Nullable List<Vector3d> verts) {
        if (verts == null || verts.size() < 2) {
            return null;
        }
        List<Vector3d> reversed = new ArrayList<>(verts.size());
        for (int i = verts.size() - 1; i >= 0; i--) {
            reversed.add(new Vector3d(verts.get(i)));
        }
        return reversed;
    }

    /**
     * Extracts a directed sub-path between normalized arc-length parameters.
     * Open paths require start &lt; end after clamping; closed paths allow seam wrap when start &gt; end.
     */
    public static @Nullable List<Vector3d> trimPathByParameter(@Nullable List<Vector3d> verts,
                                                               double startT,
                                                               double endT) {
        if (verts == null || verts.size() < 2) {
            return null;
        }
        if (!Double.isFinite(startT) || !Double.isFinite(endT)) {
            return null;
        }

        double t0 = clamp01(startT);
        double t1 = clamp01(endT);
        if (Math.abs(t1 - t0) <= EPS) {
            return null;
        }

        boolean closed = isClosed(verts);
        List<Vector3d> unique = closed ? verts.subList(0, verts.size() - 1) : verts;
        if (unique.size() < 2) {
            return null;
        }
        double[] cumulative = buildCumulative(unique, closed);
        if (cumulative == null) {
            return null;
        }
        double total = cumulative[cumulative.length - 1];
        if (total <= EPS) {
            return null;
        }

        if (closed) {
            if (t0 < t1) {
                return extractDirectedSegment(unique, closed, cumulative, t0 * total, t1 * total);
            }
            List<Vector3d> head = extractDirectedSegment(unique, closed, cumulative, t0 * total, total);
            List<Vector3d> tail = extractDirectedSegment(unique, closed, cumulative, 0.0d, t1 * total);
            return mergeSegments(head, tail);
        }

        if (t0 >= t1) {
            return null;
        }
        return extractDirectedSegment(unique, false, cumulative, t0 * total, t1 * total);
    }

    public static @Nullable PathSplitResult splitPathByParameter(@Nullable List<Vector3d> verts, double parameter) {
        if (!Double.isFinite(parameter)) {
            return null;
        }
        double clamped = clamp01(parameter);
        List<Vector3d> pathA = trimPathByParameter(verts, 0.0d, clamped);
        List<Vector3d> pathB = trimPathByParameter(verts, clamped, 1.0d);
        if (pathA == null || pathB == null) {
            return null;
        }
        return new PathSplitResult(pathA, pathB);
    }

    /**
     * Decomposes a path into per-segment vertex pairs (open: n-1 segments; closed: n segments).
     */
    public static List<List<Vector3d>> explodePath(@Nullable List<Vector3d> verts) {
        if (verts == null || verts.size() < 2) {
            return List.of();
        }

        boolean closed = isClosed(verts);
        List<Vector3d> unique = closed ? verts.subList(0, verts.size() - 1) : verts;
        if (unique.size() < 2) {
            return List.of();
        }

        int segCount = closed ? unique.size() : unique.size() - 1;
        List<List<Vector3d>> segments = new ArrayList<>(segCount);
        for (int i = 0; i < segCount; i++) {
            Vector3d a = unique.get(i);
            Vector3d b = unique.get((i + 1) % unique.size());
            if (a.distanceSquared(b) <= EPS * EPS) {
                continue;
            }
            segments.add(List.of(new Vector3d(a), new Vector3d(b)));
        }
        return segments;
    }

    /**
     * Linearly extends an open path along start/end tangents. Closed paths are rejected.
     */
    public static @Nullable List<Vector3d> extendPath(@Nullable List<Vector3d> verts,
                                                      double startLength,
                                                      double endLength) {
        if (verts == null || verts.size() < 2) {
            return null;
        }
        if (!Double.isFinite(startLength) || !Double.isFinite(endLength)) {
            return null;
        }
        if (startLength < 0.0d || endLength < 0.0d) {
            return null;
        }
        if (isClosed(verts)) {
            return null;
        }

        Vector3d startTangent = findStartTangent(verts);
        Vector3d endTangent = findEndTangent(verts);
        if (startTangent == null || endTangent == null) {
            return null;
        }

        List<Vector3d> extended = new ArrayList<>(verts.size() + 2);
        if (startLength > 0.0d) {
            Vector3d startPoint = verts.getFirst();
            extended.add(new Vector3d(startPoint).sub(new Vector3d(startTangent).mul(startLength)));
        }
        appendVerticesFar(extended, verts);
        if (endLength > 0.0d) {
            Vector3d endPoint = verts.getLast();
            extended.add(new Vector3d(endPoint).add(new Vector3d(endTangent).mul(endLength)));
        }
        return extended.size() >= 2 ? extended : null;
    }

    public static @Nullable ClosestPointResult closestPointOnPath(@Nullable List<Vector3d> verts,
                                                                  @Nullable Vector3d query) {
        if (query == null || verts == null || verts.size() < 2) {
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
        int bestSeg = 0;
        double bestT = 0.0d;

        for (int i = 0; i < segCount; i++) {
            Vector3d a = unique.get(i);
            Vector3d b = unique.get((i + 1) % unique.size());
            SegmentClosest sc = closestOnSegment(query, a, b);
            if (sc.distSq() < bestDistSq) {
                bestDistSq = sc.distSq();
                bestPoint = sc.closest();
                bestSeg = i;
                bestT = sc.t();
            }
        }

        if (bestPoint == null) {
            return null;
        }

        double[] cumulative = buildCumulative(unique, closed);
        if (cumulative == null) {
            return null;
        }
        double total = cumulative[cumulative.length - 1];
        if (total <= EPS) {
            return null;
        }

        double arcLen = cumulative[bestSeg];
        Vector3d segStart = unique.get(bestSeg);
        Vector3d segEnd = unique.get((bestSeg + 1) % unique.size());
        arcLen += segStart.distance(segEnd) * bestT;

        return new ClosestPointResult(
            new Vector3d(bestPoint),
            Math.sqrt(bestDistSq),
            arcLen / total,
            arcLen
        );
    }

    public record ClosestPointResult(Vector3d point, double distance, double parameter, double arcLength) {
    }

    public record PathSplitResult(List<Vector3d> pathA, List<Vector3d> pathB) {
    }

    private static @Nullable List<Vector3d> extractDirectedSegment(List<Vector3d> unique,
                                                                 boolean closed,
                                                                 double[] cumulative,
                                                                 double dist0,
                                                                 double dist1) {
        if (dist1 - dist0 <= EPS) {
            return null;
        }

        List<Vector3d> samples = new ArrayList<>();
        samples.add(sampleAtDistance(unique, closed, cumulative, dist0));

        for (int i = 0; i < cumulative.length - 1; i++) {
            double s0 = cumulative[i];
            double s1 = cumulative[i + 1];
            if (s1 <= dist0 + EPS || s0 >= dist1 - EPS) {
                continue;
            }
            if (s0 > dist0 + EPS && s0 < dist1 - EPS) {
                samples.add(new Vector3d(unique.get(i)));
            }
            if (s1 > dist0 + EPS && s1 < dist1 - EPS) {
                samples.add(new Vector3d(unique.get((i + 1) % unique.size())));
            }
        }

        Vector3d endPoint = sampleAtDistance(unique, closed, cumulative, dist1);
        if (samples.getLast().distanceSquared(endPoint) > EPS * EPS) {
            samples.add(endPoint);
        }
        return samples.size() >= 2 ? samples : null;
    }

    private static @Nullable List<Vector3d> mergeSegments(@Nullable List<Vector3d> first,
                                                          @Nullable List<Vector3d> second) {
        if (first == null || second == null) {
            return null;
        }
        if (first.isEmpty()) {
            return second.size() >= 2 ? second : null;
        }
        if (second.isEmpty()) {
            return first.size() >= 2 ? first : null;
        }

        List<Vector3d> merged = new ArrayList<>(first.size() + second.size());
        appendVerticesFar(merged, first);
        int startIndex = merged.getLast().distanceSquared(second.getFirst()) <= EPS * EPS ? 1 : 0;
        for (int i = startIndex; i < second.size(); i++) {
            Vector3d point = second.get(i);
            if (merged.isEmpty() || merged.getLast().distanceSquared(point) > EPS * EPS) {
                merged.add(new Vector3d(point));
            }
        }
        return merged.size() >= 2 ? merged : null;
    }

    private static @Nullable Vector3d findStartTangent(List<Vector3d> verts) {
        for (int i = 0; i < verts.size() - 1; i++) {
            Vector3d dir = new Vector3d(verts.get(i + 1)).sub(verts.get(i));
            if (dir.lengthSquared() > EPS * EPS) {
                return dir.normalize();
            }
        }
        return null;
    }

    private static @Nullable Vector3d findEndTangent(List<Vector3d> verts) {
        for (int i = verts.size() - 1; i > 0; i--) {
            Vector3d dir = new Vector3d(verts.get(i)).sub(verts.get(i - 1));
            if (dir.lengthSquared() > EPS * EPS) {
                return dir.normalize();
            }
        }
        return null;
    }

    private static void appendVerticesFar(List<Vector3d> target, List<Vector3d> points) {
        for (Vector3d point : points) {
            if (target.isEmpty() || target.getLast().distanceSquared(point) > EPS * EPS) {
                target.add(new Vector3d(point));
            }
        }
    }

    private static double clamp01(double value) {
        if (!Double.isFinite(value)) {
            return 0.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    private static SegmentClosest closestOnSegment(Vector3d p, Vector3d a, Vector3d b) {
        Vector3d ab = new Vector3d(b).sub(a);
        double abLenSq = ab.lengthSquared();
        if (abLenSq < EPS * EPS) {
            return new SegmentClosest(new Vector3d(a), p.distanceSquared(a), 0.0d);
        }
        double t = new Vector3d(p).sub(a).dot(ab) / abLenSq;
        double tClamped = Math.max(0.0d, Math.min(1.0d, t));
        Vector3d closest = new Vector3d(a).lerp(b, tClamped);
        return new SegmentClosest(closest, p.distanceSquared(closest), tClamped);
    }

    private record SegmentClosest(Vector3d closest, double distSq, double t) {
    }

    private static @Nullable List<Vector3d> verticesFromCurve(@Nullable Curve curve) {
        if (curve == null) {
            return null;
        }
        List<Vec3d> pts = curve.getSamplePoints();
        if (pts.size() < 2) {
            return null;
        }
        return toVector3dList(pts);
    }

    private static List<Vector3d> verticesFromLine(@Nullable LineData line) {
        Vec3d a = line.getStart();
        Vec3d b = line.getEnd();
        return List.of(new Vector3d(a.x, a.y, a.z), new Vector3d(b.x, b.y, b.z));
    }

    private static List<Vector3d> toVector3dList(List<Vec3d> pts) {
        List<Vector3d> out = new ArrayList<>(pts.size());
        for (Vec3d v : pts) {
            out.add(new Vector3d(v.x, v.y, v.z));
        }
        return out;
    }
}

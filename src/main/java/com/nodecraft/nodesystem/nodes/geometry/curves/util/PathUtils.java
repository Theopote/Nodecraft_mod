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

    private PathUtils() {
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
            case POLYLINE -> toVector3dList(path.getPolyline().getPoints());
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
            return toVector3dList(poly.getPoints());
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
        return first.distance(last) < 1.0e-6d;
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
            Vec3d a = polyline.getPoints().get(0);
            Vec3d b = polyline.getPoints().get(1);
            return PathData.fromLine(new LineData(a, b));
        }
        return PathData.fromPolyline(polyline);
    }

    public static @Nullable List<Vector3d> joinPaths(@Nullable List<Vector3d> first,
                                                       @Nullable List<Vector3d> second) {
        if (first == null || second == null || first.size() < 2 || second.size() < 2) {
            return null;
        }
        List<Vector3d> joined = new ArrayList<>(first.size() + second.size());
        appendVerticesFar(joined, first);
        appendVerticesFar(joined, second);
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

    public static @Nullable List<Vector3d> trimPathByParameter(@Nullable List<Vector3d> verts,
                                                               double startT,
                                                               double endT) {
        PathSegment segment = resolveParameterSegment(verts, startT, endT);
        return segment == null ? null : segment.points();
    }

    public static @Nullable PathSplitResult splitPathByParameter(@Nullable List<Vector3d> verts, double parameter) {
        PathSegment segment = resolveParameterSegment(verts, 0.0d, parameter);
        if (segment == null) {
            return null;
        }
        List<Vector3d> pathA = segment.points();
        PathSegment tail = resolveParameterSegment(verts, parameter, 1.0d);
        if (tail == null) {
            return null;
        }
        return new PathSplitResult(pathA, tail.points());
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

    private static @Nullable PathSegment resolveParameterSegment(@Nullable List<Vector3d> verts,
                                                                 double startT,
                                                                 double endT) {
        if (verts == null || verts.size() < 2) {
            return null;
        }
        double t0 = clamp01(startT);
        double t1 = clamp01(endT);
        if (t1 < t0) {
            double swap = t0;
            t0 = t1;
            t1 = swap;
        }
        if (t1 - t0 <= EPS) {
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

        double dist0 = t0 * total;
        double dist1 = t1 * total;
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
        return samples.size() >= 2 ? new PathSegment(samples) : null;
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

    private record PathSegment(List<Vector3d> points) {
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

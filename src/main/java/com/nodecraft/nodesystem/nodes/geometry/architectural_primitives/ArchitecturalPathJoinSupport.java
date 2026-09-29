package com.nodecraft.nodesystem.nodes.geometry.architectural_primitives;

import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PrismGeometryData;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.InPlanePathOffset;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PathUtils;
import com.nodecraft.nodesystem.util.GeometryOutputUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2d;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * In-plane path offset with miter/bevel/butt joins for architectural path consumers (Graph V97).
 */
final class ArchitecturalPathJoinSupport {

    static final double EPS = 1.0e-9d;
    private static final double MITER_LIMIT = 1.0e6d;
    private static final double BEVEL_MITER_LIMIT = 4.0d;
    private static final double MAX_PLANAR_DEVIATION = 0.05d;

    private ArchitecturalPathJoinSupport() {
    }

    enum JoinMode {
        MITER,
        BEVEL,
        BUTT;

        static @Nullable JoinMode fromString(@Nullable String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "miter" -> MITER;
                case "bevel" -> BEVEL;
                case "butt" -> BUTT;
                default -> null;
            };
        }
    }

    record FlatPath(List<Vector3d> points, boolean closed, double baseY) {
    }

    static @Nullable FlatPath flattenToHorizontalPlane(ArchitecturalPathSupport.PathGeometry path) {
        if (path == null || path.unique().isEmpty()) {
            return null;
        }
        double baseY = path.unique().getFirst().y;
        double maxDeviation = 0.0d;
        for (Vector3d point : path.unique()) {
            maxDeviation = Math.max(maxDeviation, Math.abs(point.y - baseY));
        }
        if (maxDeviation > MAX_PLANAR_DEVIATION) {
            return null;
        }
        List<Vector3d> flattened = new ArrayList<>(path.unique().size());
        for (Vector3d point : path.unique()) {
            flattened.add(new Vector3d(point.x, baseY, point.z));
        }
        return new FlatPath(flattened, path.closed(), baseY);
    }

    static @Nullable ArchitecturalPathSupport.PathGeometry offsetPath(
            ArchitecturalPathSupport.PathGeometry path,
            double offset,
            JoinMode join
    ) {
        if (path == null || join == null) {
            return null;
        }
        if (Math.abs(offset) < EPS) {
            return path;
        }
        FlatPath flat = flattenToHorizontalPlane(path);
        if (flat == null) {
            return null;
        }

        List<Vector3d> offsetPoints = switch (join) {
            case BUTT -> offsetButt(flat.points(), flat.closed(), offset);
            case MITER, BEVEL -> offsetMiterOrBevel(flat.points(), flat.closed(), offset, join);
        };
        if (offsetPoints == null || offsetPoints.size() < 2) {
            return null;
        }
        if (flat.closed()) {
            offsetPoints = new ArrayList<>(offsetPoints);
            offsetPoints.add(new Vector3d(offsetPoints.getFirst()));
        }
        return ArchitecturalPathSupport.resolve(PathUtils.createPolylineOrNull(
                PathUtils.toVec3dList(offsetPoints, flat.closed())));
    }

    static @Nullable GeometryData extrudeWallFootprint(
            ArchitecturalPathSupport.PathGeometry path,
            double thickness,
            double height,
            double centerOffset,
            JoinMode join
    ) {
        if (path == null || join == null || thickness <= EPS || height <= EPS) {
            return null;
        }
        FlatPath flat = flattenToHorizontalPlane(path);
        if (flat == null) {
            return null;
        }

        double half = thickness / 2.0d;
        List<Vector3d> centerline = offsetPolyline(flat.points(), flat.closed(), centerOffset, join);
        if (centerline == null || centerline.size() < 2) {
            return null;
        }
        List<Vector3d> left = offsetPolyline(flat.points(), flat.closed(), centerOffset - half, join);
        List<Vector3d> right = offsetPolyline(flat.points(), flat.closed(), centerOffset + half, join);
        if (left == null || right == null || left.size() < 2 || right.size() < 2) {
            return null;
        }

        List<Vector3d> footprint = buildClosedFootprint(left, right, flat.closed());
        if (footprint.size() < 3) {
            return null;
        }

        List<List<Vector3d>> convexParts = decomposeToConvex(footprint);
        if (convexParts.isEmpty()) {
            return null;
        }

        Vector3d extrusion = new Vector3d(0.0d, height, 0.0d);
        List<GeometryData> pieces = new ArrayList<>(convexParts.size());
        for (List<Vector3d> part : convexParts) {
            if (part.size() < 3) {
                return null;
            }
            pieces.add(new PrismGeometryData(part, extrusion));
        }
        return GeometryOutputUtils.packGeometry(pieces);
    }

    private static @Nullable List<Vector3d> offsetMiterOrBevel(
            List<Vector3d> points,
            boolean closed,
            double offset,
            JoinMode join
    ) {
        double miterLimit = join == JoinMode.BEVEL ? BEVEL_MITER_LIMIT : MITER_LIMIT;
        InPlanePathOffset.Result result = InPlanePathOffset.offset(points, PlaneData.XZ_PLANE, offset, miterLimit);
        return result == null ? null : result.points();
    }

    private static @Nullable List<Vector3d> offsetButt(List<Vector3d> points, boolean closed, double offset) {
        int n = points.size();
        if (n < 2) {
            return null;
        }
        if (closed) {
            return offsetMiterOrBevel(points, true, offset, JoinMode.BEVEL);
        }

        List<Vector2d> pts2d = new ArrayList<>(n);
        for (Vector3d point : points) {
            pts2d.add(new Vector2d(point.x, point.z));
        }

        List<Vector3d> out = new ArrayList<>();
        for (int seg = 0; seg < n - 1; seg++) {
            Vector2d a = pts2d.get(seg);
            Vector2d b = pts2d.get(seg + 1);
            Vector2d d = new Vector2d(b).sub(a);
            double len = d.length();
            if (len < EPS) {
                return null;
            }
            d.mul(1.0d / len);
            Vector2d normal = new Vector2d(-d.y, d.x).mul(offset);
            Vector3d start = points.get(seg);
            Vector3d end = points.get(seg + 1);
            if (seg == 0) {
                out.add(new Vector3d(start.x + normal.x, start.y, start.z + normal.y));
            } else {
                out.add(new Vector3d(start.x + normal.x, start.y, start.z + normal.y));
            }
            if (seg == n - 2) {
                out.add(new Vector3d(end.x + normal.x, end.y, end.z + normal.y));
            }
        }
        return out.size() >= 2 ? out : null;
    }

    private static @Nullable List<Vector3d> offsetPolyline(
            List<Vector3d> points,
            boolean closed,
            double offset,
            JoinMode join
    ) {
        if (Math.abs(offset) < EPS) {
            return new ArrayList<>(points);
        }
        return switch (join) {
            case BUTT -> offsetButt(points, closed, offset);
            case MITER, BEVEL -> offsetMiterOrBevel(points, closed, offset, join);
        };
    }

    private static List<Vector3d> buildClosedFootprint(List<Vector3d> left, List<Vector3d> right, boolean closed) {
        List<Vector3d> footprint = new ArrayList<>(left.size() + right.size());
        footprint.addAll(left);
        for (int i = right.size() - 1; i >= 0; i--) {
            footprint.add(new Vector3d(right.get(i)));
        }
        if (closed && !footprint.isEmpty()) {
            footprint.add(new Vector3d(footprint.getFirst()));
        }
        return dedupeAdjacent(footprint);
    }

    private static List<Vector3d> dedupeAdjacent(List<Vector3d> points) {
        if (points.isEmpty()) {
            return List.of();
        }
        List<Vector3d> out = new ArrayList<>();
        Vector3d prev = null;
        for (Vector3d point : points) {
            if (prev == null || prev.distanceSquared(point) > EPS * EPS) {
                out.add(new Vector3d(point));
                prev = point;
            }
        }
        if (out.size() > 1 && out.getFirst().distanceSquared(out.getLast()) <= EPS * EPS) {
            out.removeLast();
        }
        return out;
    }

    private static List<List<Vector3d>> decomposeToConvex(List<Vector3d> polygon) {
        List<Vector3d> ring = dedupeAdjacent(polygon);
        if (ring.size() < 3) {
            return List.of();
        }
        if (isConvex(ring)) {
            return List.of(ring);
        }
        int reflex = findReflexIndex(ring);
        if (reflex < 0) {
            return List.of(ring);
        }
        int split = farthestVisibleVertex(ring, reflex);
        if (split < 0 || split == reflex) {
            return List.of(ring);
        }
        List<Vector3d> partA = extractChain(ring, reflex, split);
        List<Vector3d> partB = extractChain(ring, split, reflex);
        List<List<Vector3d>> result = new ArrayList<>();
        result.addAll(decomposeToConvex(partA));
        result.addAll(decomposeToConvex(partB));
        return result;
    }

    private static List<Vector3d> extractChain(List<Vector3d> ring, int start, int end) {
        List<Vector3d> chain = new ArrayList<>();
        int n = ring.size();
        int index = start;
        chain.add(new Vector3d(ring.get(index)));
        while (index != end) {
            index = (index + 1) % n;
            chain.add(new Vector3d(ring.get(index)));
        }
        return chain;
    }

    private static int findReflexIndex(List<Vector3d> ring) {
        int n = ring.size();
        for (int i = 0; i < n; i++) {
            Vector3d prev = ring.get((i - 1 + n) % n);
            Vector3d curr = ring.get(i);
            Vector3d next = ring.get((i + 1) % n);
            if (crossSign(prev, curr, next) < 0.0d) {
                return i;
            }
        }
        return -1;
    }

    private static int farthestVisibleVertex(List<Vector3d> ring, int reflex) {
        Vector3d apex = ring.get(reflex);
        double bestDist = -1.0d;
        int best = -1;
        for (int i = 0; i < ring.size(); i++) {
            if (i == reflex) {
                continue;
            }
            double dist = apex.distanceSquared(ring.get(i));
            if (dist > bestDist) {
                bestDist = dist;
                best = i;
            }
        }
        return best;
    }

    private static boolean isConvex(List<Vector3d> ring) {
        int n = ring.size();
        if (n < 3) {
            return false;
        }
        double sign = 0.0d;
        for (int i = 0; i < n; i++) {
            double cross = crossSign(ring.get((i - 1 + n) % n), ring.get(i), ring.get((i + 1) % n));
            if (Math.abs(cross) <= EPS) {
                continue;
            }
            if (sign == 0.0d) {
                sign = Math.signum(cross);
            } else if (Math.signum(cross) != sign) {
                return false;
            }
        }
        return true;
    }

    private static double crossSign(Vector3d a, Vector3d b, Vector3d c) {
        double abx = b.x - a.x;
        double abz = b.z - a.z;
        double bcx = c.x - b.x;
        double bcz = c.z - b.z;
        return abx * bcz - abz * bcx;
    }
}

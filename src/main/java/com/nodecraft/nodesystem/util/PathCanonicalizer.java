package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PathUtils;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Canonicalizes {@link PolylineData} before {@link com.nodecraft.nodesystem.datatypes.PathData} construction.
 * Open polylines forbid adjacent zero-length segments. Closed polylines require an exact seam
 * ({@code last == first}) and at least three unique vertices.
 */
public final class PathCanonicalizer {

    private PathCanonicalizer() {
    }

    public static @Nullable PolylineData canonicalize(@Nullable PolylineData polyline) {
        if (polyline == null) {
            return null;
        }
        List<Vec3d> raw = polyline.points();
        if (raw.size() < 2) {
            return null;
        }

        List<Vec3d> collapsed = new ArrayList<>(raw.size());
        for (Vec3d point : raw) {
            if (!isFinite(point)) {
                return null;
            }
            if (!collapsed.isEmpty() && samePoint(collapsed.getLast(), point)) {
                continue;
            }
            collapsed.add(point);
        }
        if (collapsed.size() < 2) {
            return null;
        }

        Vec3d first = collapsed.getFirst();
        Vec3d last = collapsed.getLast();
        double closureEpsSq = PathUtils.CLOSED_DISTANCE_EPSILON * PathUtils.CLOSED_DISTANCE_EPSILON;
        boolean exactClosed = samePoint(first, last);
        boolean nearClosed = !exactClosed && distanceSquared(first, last) <= closureEpsSq;

        if (exactClosed || nearClosed) {
            while (collapsed.size() > 1
                    && !samePoint(collapsed.getFirst(), collapsed.getLast())
                    && distanceSquared(collapsed.getFirst(), collapsed.getLast()) <= closureEpsSq) {
                collapsed.removeLast();
            }

            List<Vec3d> unique = new ArrayList<>();
            for (int i = 0; i < collapsed.size(); i++) {
                Vec3d point = collapsed.get(i);
                if (i == collapsed.size() - 1 && samePoint(point, collapsed.getFirst())) {
                    continue;
                }
                if (!unique.isEmpty() && samePoint(unique.getLast(), point)) {
                    continue;
                }
                unique.add(point);
            }
            if (unique.size() < 3) {
                return null;
            }
            for (int i = 0; i < unique.size(); i++) {
                Vec3d a = unique.get(i);
                Vec3d b = unique.get((i + 1) % unique.size());
                if (samePoint(a, b)) {
                    return null;
                }
            }

            List<Vec3d> closed = new ArrayList<>(unique.size() + 1);
            closed.addAll(unique);
            closed.add(new Vec3d(first.x, first.y, first.z));
            return new PolylineData(closed);
        }

        for (int i = 0; i < collapsed.size() - 1; i++) {
            if (samePoint(collapsed.get(i), collapsed.get(i + 1))) {
                return null;
            }
        }
        return new PolylineData(List.copyOf(collapsed));
    }

    private static boolean isFinite(Vec3d point) {
        return Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
    }

    private static boolean samePoint(Vec3d a, Vec3d b) {
        return a.x == b.x && a.y == b.y && a.z == b.z;
    }

    private static double distanceSquared(Vec3d a, Vec3d b) {
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        double dz = a.z - b.z;
        return dx * dx + dy * dy + dz * dz;
    }
}

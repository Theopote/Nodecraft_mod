package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PlaneProjectionUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2d;
import org.joml.Vector3d;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared validation for {@link PolygonProfileData} topology and budgets (Graph V73).
 * <p>
 * Canonical invariant: POLYGON_PROFILE = simple, closed, planar polygon loop without holes.
 */
public final class PolygonProfileValidator {

    public static final double CLOSURE_EPS = 1.0e-6d;
    public static final double COPLANAR_EPS = 1.0e-5d;
    private static final double EDGE_EPS_SQ = 1.0e-18d;
    private static final double AREA_EPS = 1.0e-12d;
    private static final double DISTINCT_EPS_SQ = 1.0e-12d;

    private PolygonProfileValidator() {
    }

    /**
     * @return null when valid; otherwise an actionable error message
     */
    public static @Nullable String validate(@Nullable PolygonProfileData profile) {
        if (profile == null) {
            return "Polygon profile is missing";
        }
        return validateConstruction(profile.closedPoints(), profile.plane());
    }

    /**
     * @return null when valid; otherwise an actionable error message
     */
    public static @Nullable String validateConstruction(
            @Nullable List<Vector3d> closedPoints,
            @Nullable PlaneData plane
    ) {
        if (closedPoints == null || plane == null) {
            return "Polygon profile requires points and a plane";
        }
        if (closedPoints.size() < 4) {
            return "Polygon profile requires at least 3 unique vertices plus closure";
        }

        List<Vector3d> canonical = canonicalizeClosedPoints(closedPoints);
        if (canonical.size() < 4) {
            return "Polygon profile requires at least 3 unique vertices plus closure";
        }

        PlaneData normalizedPlane = plane.normalized();
        if (normalizedPlane == null) {
            return "Polygon profile plane is invalid";
        }

        for (Vector3d point : canonical) {
            if (!FrameUtils.isFinite(point)) {
                return "Polygon profile contains non-finite coordinates";
            }
            if (Math.abs(normalizedPlane.signedDistanceTo(point)) > COPLANAR_EPS) {
                return "Polygon profile vertices are not coplanar with the plane";
            }
        }

        int uniqueCount = canonical.size() - 1;
        if (uniqueCount < 3) {
            return "Polygon profile requires at least 3 unique vertices";
        }

        for (int i = 0; i < uniqueCount; i++) {
            Vector3d current = canonical.get(i);
            Vector3d next = canonical.get(i + 1);
            if (current.distanceSquared(next) <= EDGE_EPS_SQ) {
                return "Polygon profile contains zero-length edges";
            }
            if (i > 0 && current.distanceSquared(canonical.get(i - 1)) <= DISTINCT_EPS_SQ) {
                return "Polygon profile contains consecutive duplicate vertices";
            }
        }
        if (canonical.get(uniqueCount - 1).distanceSquared(canonical.getFirst()) <= DISTINCT_EPS_SQ) {
            return "Polygon profile contains consecutive duplicate vertices";
        }

        if (!GenerationLimits.isWithinProfileVertices(uniqueCount)) {
            return "Polygon profile vertex count exceeds limit (" + GenerationLimits.MAX_PROFILE_VERTICES + ")";
        }

        double signedArea = signedArea2d(canonical, normalizedPlane);
        if (Math.abs(signedArea) <= AREA_EPS) {
            return "Polygon profile has zero area";
        }

        if (!isSimpleLoop(canonical, normalizedPlane)) {
            return "Polygon profile is self-intersecting";
        }

        return null;
    }

    /**
     * Returns a defensive copy with canonical exact repeated first vertex at closure.
     */
    public static List<Vector3d> canonicalizeClosedPoints(List<Vector3d> closedPoints) {
        List<Vector3d> copied = new ArrayList<>(closedPoints.size());
        for (Vector3d point : closedPoints) {
            if (point == null) {
                return List.of();
            }
            copied.add(new Vector3d(point));
        }
        if (copied.size() < 2) {
            return copied;
        }
        Vector3d first = copied.getFirst();
        Vector3d last = copied.getLast();
        if (first.distance(last) > CLOSURE_EPS) {
            return copied;
        }
        copied.set(copied.size() - 1, new Vector3d(first));
        return List.copyOf(copied);
    }

    public static boolean profilesCoplanar(PolygonProfileData a, PolygonProfileData b) {
        PlaneData planeA = a.plane().normalized();
        if (planeA == null) {
            return false;
        }
        PlaneData planeB = b.plane().normalized();
        if (planeB == null) {
            return false;
        }
        Vector3d normalA = planeA.getNormal();
        Vector3d normalB = planeB.getNormal();
        if (Math.abs(normalA.dot(normalB)) < 0.999999d) {
            return false;
        }
        double offsetDelta = Math.abs(planeA.signedDistanceTo(planeB.getPoint()));
        if (offsetDelta > COPLANAR_EPS) {
            return false;
        }
        for (Vector3d point : b.getUniquePoints()) {
            if (Math.abs(planeA.signedDistanceTo(point)) > COPLANAR_EPS) {
                return false;
            }
        }
        return true;
    }

    private static double signedArea2d(List<Vector3d> closedPoints, PlaneData plane) {
        PlaneProjectionUtils.PlaneAxes axes = PlaneProjectionUtils.PlaneAxes.from(plane);
        double area2 = 0.0d;
        for (int i = 0; i < closedPoints.size() - 1; i++) {
            Vector2d a = axes.to2d(closedPoints.get(i));
            Vector2d b = axes.to2d(closedPoints.get(i + 1));
            area2 += (a.x * b.y - b.x * a.y);
        }
        return area2 * 0.5d;
    }

    private static boolean isSimpleLoop(List<Vector3d> closedPoints, PlaneData plane) {
        PlaneProjectionUtils.PlaneAxes axes = PlaneProjectionUtils.PlaneAxes.from(plane);
        Coordinate[] coords = new Coordinate[closedPoints.size()];
        for (int i = 0; i < closedPoints.size(); i++) {
            Vector2d uv = axes.to2d(closedPoints.get(i));
            coords[i] = new Coordinate(uv.x, uv.y);
        }
        Polygon polygon = new GeometryFactory().createPolygon(coords);
        return polygon.isSimple();
    }
}

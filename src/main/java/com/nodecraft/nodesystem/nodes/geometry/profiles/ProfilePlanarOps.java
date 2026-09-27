package com.nodecraft.nodesystem.nodes.geometry.profiles;

import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PlaneProjectionUtils;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.PolygonProfileValidator;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2d;
import org.joml.Vector3d;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared planar JTS operations for profile boolean/offset nodes (Graph V73).
 */
final class ProfilePlanarOps {

    private ProfilePlanarOps() {
    }

    static @Nullable Polygon toJtsPolygon(
            PolygonProfileData profile,
            PlaneProjectionUtils.PlaneAxes axes,
            GeometryFactory gf
    ) {
        List<Vector3d> closed = profile.closedPoints();
        if (closed.size() < 4) {
            return null;
        }
        Coordinate[] coords = new Coordinate[closed.size()];
        for (int i = 0; i < closed.size(); i++) {
            Vector2d uv = axes.to2d(closed.get(i));
            coords[i] = new Coordinate(uv.x, uv.y);
        }
        return gf.createPolygon(coords);
    }

    /**
     * @return null when conversion succeeds; otherwise an actionable error message
     */
    static @Nullable String fromJtsPolygon(
            Polygon polygon,
            PlaneProjectionUtils.PlaneAxes axes,
            PlaneData plane,
            List<PolygonProfileData> out
    ) {
        if (polygon.getNumInteriorRing() > 0) {
            return "Result contains holes not representable by POLYGON_PROFILE";
        }
        Coordinate[] coords = polygon.getExteriorRing().getCoordinates();
        if (coords.length < 4) {
            return "Boolean result polygon is degenerate";
        }
        List<Vector3d> closed = new ArrayList<>(coords.length);
        for (Coordinate c : coords) {
            closed.add(axes.from2d(new Vector2d(c.x, c.y)));
        }
        List<Vector3d> canonical = PolygonProfileValidator.canonicalizeClosedPoints(closed);
        try {
            PolygonProfileData profile = new PolygonProfileData(canonical, plane);
            String error = PolygonProfileValidator.validate(profile);
            if (error != null) {
                return error;
            }
            out.add(profile);
            return null;
        } catch (IllegalArgumentException ex) {
            return ex.getMessage() == null ? "Failed to convert polygon profile" : ex.getMessage();
        }
    }

    static @Nullable String appendSimplePolygons(
            Geometry geometry,
            PlaneProjectionUtils.PlaneAxes axes,
            PlaneData plane,
            List<PolygonProfileData> out
    ) {
        if (geometry == null || geometry.isEmpty()) {
            return null;
        }
        if (geometry instanceof Polygon polygon) {
            return fromJtsPolygon(polygon, axes, plane, out);
        }
        if (geometry instanceof GeometryCollection collection) {
            for (int i = 0; i < collection.getNumGeometries(); i++) {
                String error = appendSimplePolygons(collection.getGeometryN(i), axes, plane, out);
                if (error != null) {
                    return error;
                }
            }
            return null;
        }
        return "Boolean result is not a polygon";
    }

    static double area2d(PolygonProfileData profile, PlaneProjectionUtils.PlaneAxes axes) {
        List<Vector3d> pts = profile.closedPoints();
        double area2 = 0.0d;
        for (int i = 0; i < pts.size() - 1; i++) {
            Vector2d a = axes.to2d(pts.get(i));
            Vector2d b = axes.to2d(pts.get(i + 1));
            area2 += (a.x * b.y - b.x * a.y);
        }
        return area2 * 0.5d;
    }

    static PolygonProfileData selectPrimaryProfile(
            List<PolygonProfileData> profiles,
            PlaneProjectionUtils.PlaneAxes axes
    ) {
        PolygonProfileData primary = profiles.getFirst();
        for (PolygonProfileData profile : profiles) {
            if (Math.abs(area2d(profile, axes)) > Math.abs(area2d(primary, axes))) {
                primary = profile;
            }
        }
        return primary;
    }

    static @Nullable String validateOutputBudget(List<PolygonProfileData> profiles) {
        if (!GenerationLimits.isWithinProfileOutputCount(profiles.size())) {
            return "Profile output count exceeds limit (" + GenerationLimits.MAX_PROFILE_OUTPUT_PROFILES + ")";
        }
        long totalVertices = 0L;
        for (PolygonProfileData profile : profiles) {
            totalVertices += profile.getEdgeCount();
        }
        if (!GenerationLimits.isWithinProfileTotalVertices(totalVertices)) {
            return "Total profile vertex workload exceeds limit (" + GenerationLimits.MAX_PROFILE_TOTAL_VERTICES + ")";
        }
        return null;
    }
}

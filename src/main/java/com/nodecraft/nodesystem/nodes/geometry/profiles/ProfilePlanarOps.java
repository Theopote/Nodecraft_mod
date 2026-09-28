package com.nodecraft.nodesystem.nodes.geometry.profiles;

import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PlanarRegionData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PlaneProjectionUtils;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.PlanarRegionValidator;
import com.nodecraft.nodesystem.util.PolygonProfileValidator;
import com.nodecraft.nodesystem.util.ProfileConstructionUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2d;
import org.joml.Vector3d;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared planar JTS operations for profile boolean/offset nodes (Graph V73/V91).
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

    static @Nullable Polygon toJtsPolygon(
            PlanarRegionData region,
            PlaneProjectionUtils.PlaneAxes axes,
            GeometryFactory gf
    ) {
        return PlanarRegionValidator.toJtsPolygonWithHoles(region.outer(), region.holes(), axes, gf);
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
        StringBuilder error = new StringBuilder();
        PolygonProfileData profile = ProfileConstructionUtils.tryCreateProfile(closed, plane, error);
        if (profile == null) {
            return error.isEmpty() ? "Failed to convert polygon profile" : error.toString();
        }
        out.add(profile);
        return null;
    }

    /**
     * Converts a JTS polygon (with optional holes) into a {@link PlanarRegionData}.
     *
     * @return null on success; otherwise an error message
     */
    static @Nullable String fromJtsPolygonToRegion(
            Polygon polygon,
            PlaneProjectionUtils.PlaneAxes axes,
            PlaneData plane,
            List<PlanarRegionData> out
    ) {
        Coordinate[] exterior = polygon.getExteriorRing().getCoordinates();
        if (exterior.length < 4) {
            return "Boolean result polygon is degenerate";
        }
        List<Vector3d> outerClosed = ringToClosed(exterior, axes);
        StringBuilder error = new StringBuilder();
        PolygonProfileData outer = ProfileConstructionUtils.tryCreateProfile(outerClosed, plane, error);
        if (outer == null) {
            return error.isEmpty() ? "Failed to convert outer ring" : error.toString();
        }

        List<PolygonProfileData> holes = new ArrayList<>();
        for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
            Coordinate[] holeCoords = polygon.getInteriorRingN(i).getCoordinates();
            if (holeCoords.length < 4) {
                return "Boolean result hole is degenerate";
            }
            error.setLength(0);
            PolygonProfileData hole = ProfileConstructionUtils.tryCreateProfile(
                ringToClosed(holeCoords, axes), plane, error);
            if (hole == null) {
                return error.isEmpty() ? "Failed to convert hole ring" : error.toString();
            }
            holes.add(hole);
        }

        error.setLength(0);
        PlanarRegionData region = PlanarRegionData.tryCreate(outer, holes, plane, error);
        if (region == null) {
            return error.isEmpty() ? "Failed to create planar region" : error.toString();
        }
        out.add(region);
        return null;
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

    static @Nullable String appendPlanarRegions(
            Geometry geometry,
            PlaneProjectionUtils.PlaneAxes axes,
            PlaneData plane,
            List<PlanarRegionData> out
    ) {
        if (geometry == null || geometry.isEmpty()) {
            return null;
        }
        if (geometry instanceof Polygon polygon) {
            return fromJtsPolygonToRegion(polygon, axes, plane, out);
        }
        if (geometry instanceof GeometryCollection collection) {
            for (int i = 0; i < collection.getNumGeometries(); i++) {
                String error = appendPlanarRegions(collection.getGeometryN(i), axes, plane, out);
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

    static double regionArea2d(PlanarRegionData region, PlaneProjectionUtils.PlaneAxes axes) {
        double area = Math.abs(area2d(region.outer(), axes));
        for (PolygonProfileData hole : region.holes()) {
            area -= Math.abs(area2d(hole, axes));
        }
        return Math.max(0.0d, area);
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

    static PlanarRegionData selectPrimaryRegion(
            List<PlanarRegionData> regions,
            PlaneProjectionUtils.PlaneAxes axes
    ) {
        PlanarRegionData primary = regions.getFirst();
        for (PlanarRegionData region : regions) {
            if (regionArea2d(region, axes) > regionArea2d(primary, axes)) {
                primary = region;
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

    static @Nullable String validateRegionOutputBudget(List<PlanarRegionData> regions) {
        if (!GenerationLimits.isWithinProfileOutputCount(regions.size())) {
            return "Region output count exceeds limit (" + GenerationLimits.MAX_PROFILE_OUTPUT_PROFILES + ")";
        }
        long totalVertices = 0L;
        for (PlanarRegionData region : regions) {
            totalVertices += region.outer().getEdgeCount();
            for (PolygonProfileData hole : region.holes()) {
                totalVertices += hole.getEdgeCount();
            }
        }
        if (!GenerationLimits.isWithinProfileTotalVertices(totalVertices)) {
            return "Total region vertex workload exceeds limit (" + GenerationLimits.MAX_PROFILE_TOTAL_VERTICES + ")";
        }
        return null;
    }

    private static List<Vector3d> ringToClosed(Coordinate[] coords, PlaneProjectionUtils.PlaneAxes axes) {
        List<Vector3d> closed = new ArrayList<>(coords.length);
        for (Coordinate c : coords) {
            closed.add(axes.from2d(new Vector2d(c.x, c.y)));
        }
        return PolygonProfileValidator.canonicalizeClosedPoints(closed);
    }
}

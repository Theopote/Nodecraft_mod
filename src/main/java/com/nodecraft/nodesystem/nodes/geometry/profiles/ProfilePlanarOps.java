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
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.operation.buffer.BufferOp;
import org.locationtech.jts.operation.buffer.BufferParameters;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared planar JTS operations for profile/region boolean/offset nodes (Graph V73/V91/V92).
 */
final class ProfilePlanarOps {

    enum BooleanOp {
        UNION,
        INTERSECTION,
        DIFFERENCE
    }

    /**
     * Outcome of a region boolean or offset. {@code error != null} means failure;
     * empty {@code regions} with null error is a successful empty result.
     */
    record RegionOpOutcome(
        @Nullable String error,
        List<PlanarRegionData> regions,
        @Nullable PlaneData plane
    ) {
        static RegionOpOutcome fail(String error) {
            return new RegionOpOutcome(error, List.of(), null);
        }

        static RegionOpOutcome empty(PlaneData plane) {
            return new RegionOpOutcome(null, List.of(), plane);
        }

        static RegionOpOutcome ok(List<PlanarRegionData> regions, PlaneData plane) {
            return new RegionOpOutcome(null, List.copyOf(regions), plane);
        }

        boolean failed() {
            return error != null;
        }
    }

    private ProfilePlanarOps() {
    }

    static int regionVertexCount(PlanarRegionData region) {
        int total = region.outer().getEdgeCount();
        for (PolygonProfileData hole : region.holes()) {
            total += hole.getEdgeCount();
        }
        return total;
    }

    static boolean regionsCoplanar(PlanarRegionData a, PlanarRegionData b) {
        return PolygonProfileValidator.profilesCoplanar(a.outer(), b.outer());
    }

    static RegionOpOutcome booleanRegions(
            PlanarRegionData a,
            PlanarRegionData b,
            BooleanOp operation
    ) {
        String aError = PlanarRegionValidator.validate(a);
        if (aError != null) {
            return RegionOpOutcome.fail("Region A: " + aError);
        }
        String bError = PlanarRegionValidator.validate(b);
        if (bError != null) {
            return RegionOpOutcome.fail("Region B: " + bError);
        }

        int totalVertices = regionVertexCount(a) + regionVertexCount(b);
        if (!GenerationLimits.isWithinProfileBooleanVertices(totalVertices)) {
            return RegionOpOutcome.fail(
                "Combined region vertex count exceeds limit ("
                    + GenerationLimits.MAX_PROFILE_BOOLEAN_VERTICES + ")");
        }

        if (!regionsCoplanar(a, b)) {
            return RegionOpOutcome.fail("Regions must lie on the same plane");
        }

        PlaneData plane = a.plane();
        PlaneProjectionUtils.PlaneAxes axes = PlaneProjectionUtils.PlaneAxes.from(plane);
        GeometryFactory gf = new GeometryFactory();

        Polygon pa = toJtsPolygon(a, axes, gf);
        Polygon pb = toJtsPolygon(b, axes, gf);
        if (pa == null || pb == null) {
            return RegionOpOutcome.fail("Failed to convert regions for boolean operation");
        }

        Geometry out = switch (operation) {
            case INTERSECTION -> pa.intersection(pb);
            case DIFFERENCE -> pa.difference(pb);
            case UNION -> pa.union(pb);
        };

        return finalizeRegionGeometry(out, axes, plane);
    }

    static RegionOpOutcome offsetRegion(
            PlanarRegionData region,
            double distance,
            int quadrantSegments,
            int joinStyle,
            double miterLimit
    ) {
        String regionError = PlanarRegionValidator.validate(region);
        if (regionError != null) {
            return RegionOpOutcome.fail(regionError);
        }

        if (Math.abs(distance) < 1.0e-12d) {
            return RegionOpOutcome.ok(List.of(region), region.plane());
        }

        PlaneData plane = region.plane();
        PlaneProjectionUtils.PlaneAxes axes = PlaneProjectionUtils.PlaneAxes.from(plane);
        GeometryFactory gf = new GeometryFactory();
        Polygon polygon = toJtsPolygon(region, axes, gf);
        if (polygon == null) {
            return RegionOpOutcome.fail("Failed to convert region for offset operation");
        }

        BufferParameters params = new BufferParameters();
        params.setQuadrantSegments(Math.max(1, quadrantSegments));
        params.setJoinStyle(joinStyle);
        params.setMitreLimit(Math.max(1.0d, miterLimit));

        Geometry out = BufferOp.bufferOp(polygon, distance, params);
        return finalizeRegionGeometry(out, axes, plane);
    }

    private static RegionOpOutcome finalizeRegionGeometry(
            Geometry out,
            PlaneProjectionUtils.PlaneAxes axes,
            PlaneData plane
    ) {
        List<PlanarRegionData> regions = new ArrayList<>();
        String conversionError = appendPlanarRegions(out, axes, plane, regions);
        if (conversionError != null) {
            return RegionOpOutcome.fail(conversionError);
        }
        if (regions.isEmpty()) {
            return RegionOpOutcome.empty(plane);
        }
        String budgetError = validateRegionOutputBudget(regions);
        if (budgetError != null) {
            return RegionOpOutcome.fail(budgetError);
        }
        return RegionOpOutcome.ok(regions, plane);
    }

    static BooleanOp parseBooleanOp(@Nullable String raw) {
        if (raw == null) {
            return BooleanOp.UNION;
        }
        return switch (raw.trim().toUpperCase()) {
            case "INTERSECTION" -> BooleanOp.INTERSECTION;
            case "DIFFERENCE" -> BooleanOp.DIFFERENCE;
            default -> BooleanOp.UNION;
        };
    }

    static int parseJoinStyle(@Nullable String raw) {
        if (raw == null) {
            return BufferParameters.JOIN_ROUND;
        }
        return switch (raw.trim().toUpperCase()) {
            case "MITER", "MITRE" -> BufferParameters.JOIN_MITRE;
            case "BEVEL" -> BufferParameters.JOIN_BEVEL;
            default -> BufferParameters.JOIN_ROUND;
        };
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

package com.nodecraft.nodesystem.nodes.geometry.profiles;

import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PlanarRegionData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PlaneProjectionUtils;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.PlanarRegionValidator;
import com.nodecraft.nodesystem.util.PolygonProfileMetrics;
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
 * Shared planar JTS operations for profile/region boolean/offset nodes.
 * Historical Graph V73/V91/V92 residue.
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
        PlaneProjectionUtils.PlaneProjectionContext ctx =
            PlaneProjectionUtils.PlaneProjectionContext.from(plane, a.outer().closedPoints().getFirst());
        GeometryFactory gf = new GeometryFactory();

        Polygon pa = toJtsPolygon(a, ctx, gf);
        Polygon pb = toJtsPolygon(b, ctx, gf);
        if (pa == null || pb == null) {
            return RegionOpOutcome.fail("Failed to convert regions for boolean operation");
        }

        Geometry out = switch (operation) {
            case INTERSECTION -> pa.intersection(pb);
            case DIFFERENCE -> pa.difference(pb);
            case UNION -> pa.union(pb);
        };

        return finalizeRegionGeometry(out, ctx, plane);
    }

    static RegionOpOutcome offsetRegion(
            PlanarRegionData region,
            double distance,
            int quadrantSegments,
            @Nullable Integer joinStyle,
            double miterLimit
    ) {
        String paramError = validateOffsetParams(quadrantSegments, joinStyle, miterLimit);
        if (paramError != null) {
            return RegionOpOutcome.fail(paramError);
        }

        String regionError = PlanarRegionValidator.validate(region);
        if (regionError != null) {
            return RegionOpOutcome.fail(regionError);
        }

        if (Math.abs(distance) < 1.0e-12d) {
            return RegionOpOutcome.ok(List.of(region), region.plane());
        }

        PlaneData plane = region.plane();
        PlaneProjectionUtils.PlaneProjectionContext ctx =
            PlaneProjectionUtils.PlaneProjectionContext.from(plane, region.outer().closedPoints().getFirst());
        GeometryFactory gf = new GeometryFactory();
        Polygon polygon = toJtsPolygon(region, ctx, gf);
        if (polygon == null) {
            return RegionOpOutcome.fail("Failed to convert region for offset operation");
        }

        BufferParameters params = new BufferParameters();
        params.setQuadrantSegments(quadrantSegments);
        params.setJoinStyle(joinStyle);
        params.setMitreLimit(miterLimit);

        Geometry out = BufferOp.bufferOp(polygon, distance, params);
        return finalizeRegionGeometry(out, ctx, plane);
    }

    static @Nullable String validateOffsetParams(
            int quadrantSegments,
            @Nullable Integer joinStyle,
            double miterLimit
    ) {
        if (quadrantSegments < 1 || quadrantSegments > GenerationLimits.MAX_PROFILE_VERTICES) {
            return "Quadrant segments must be an integer from 1 to "
                + GenerationLimits.MAX_PROFILE_VERTICES;
        }
        if (joinStyle == null) {
            return "Join style must be ROUND, MITER, or BEVEL";
        }
        if (!Double.isFinite(miterLimit) || miterLimit < 1.0d) {
            return "Miter limit must be a finite number >= 1";
        }
        return null;
    }

    private static RegionOpOutcome finalizeRegionGeometry(
            Geometry out,
            PlaneProjectionUtils.PlaneProjectionContext ctx,
            PlaneData plane
    ) {
        List<PlanarRegionData> regions = new ArrayList<>();
        String conversionError = appendPlanarRegions(out, ctx, plane, regions);
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

    static @Nullable BooleanOp parseBooleanOp(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.trim().toUpperCase()) {
            case "UNION" -> BooleanOp.UNION;
            case "INTERSECTION" -> BooleanOp.INTERSECTION;
            case "DIFFERENCE" -> BooleanOp.DIFFERENCE;
            default -> null;
        };
    }

    static @Nullable Integer parseJoinStyle(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.trim().toUpperCase()) {
            case "ROUND" -> BufferParameters.JOIN_ROUND;
            case "MITER", "MITRE" -> BufferParameters.JOIN_MITRE;
            case "BEVEL" -> BufferParameters.JOIN_BEVEL;
            default -> null;
        };
    }

    static @Nullable Polygon toJtsPolygon(
            PolygonProfileData profile,
            PlaneProjectionUtils.PlaneProjectionContext ctx,
            GeometryFactory gf
    ) {
        List<Vector3d> closed = profile.closedPoints();
        if (closed.size() < 4) {
            return null;
        }
        Coordinate[] coords = new Coordinate[closed.size()];
        for (int i = 0; i < closed.size(); i++) {
            Vector2d uv = ctx.toLocal(closed.get(i));
            if (!Double.isFinite(uv.x) || !Double.isFinite(uv.y)) {
                return null;
            }
            coords[i] = new Coordinate(uv.x, uv.y);
        }
        return gf.createPolygon(coords);
    }

    static @Nullable Polygon toJtsPolygon(
            PlanarRegionData region,
            PlaneProjectionUtils.PlaneProjectionContext ctx,
            GeometryFactory gf
    ) {
        return PlanarRegionValidator.toJtsPolygonWithHoles(region.outer(), region.holes(), ctx, gf);
    }

    /**
     * @return null when conversion succeeds; otherwise an actionable error message
     */
    static @Nullable String fromJtsPolygon(
            Polygon polygon,
            PlaneProjectionUtils.PlaneProjectionContext ctx,
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
            closed.add(ctx.fromLocal(new Vector2d(c.x, c.y)));
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
            PlaneProjectionUtils.PlaneProjectionContext ctx,
            PlaneData plane,
            List<PlanarRegionData> out
    ) {
        Coordinate[] exterior = polygon.getExteriorRing().getCoordinates();
        if (exterior.length < 4) {
            return "Boolean result polygon is degenerate";
        }
        List<Vector3d> outerClosed = ringToClosed(exterior, ctx);
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
                ringToClosed(holeCoords, ctx), plane, error);
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
            PlaneProjectionUtils.PlaneProjectionContext ctx,
            PlaneData plane,
            List<PolygonProfileData> out
    ) {
        if (geometry == null || geometry.isEmpty()) {
            return null;
        }
        if (geometry instanceof Polygon polygon) {
            return fromJtsPolygon(polygon, ctx, plane, out);
        }
        if (geometry instanceof GeometryCollection collection) {
            for (int i = 0; i < collection.getNumGeometries(); i++) {
                String error = appendSimplePolygons(collection.getGeometryN(i), ctx, plane, out);
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
            PlaneProjectionUtils.PlaneProjectionContext ctx,
            PlaneData plane,
            List<PlanarRegionData> out
    ) {
        if (geometry == null || geometry.isEmpty()) {
            return null;
        }
        if (geometry instanceof Polygon polygon) {
            return fromJtsPolygonToRegion(polygon, ctx, plane, out);
        }
        if (geometry instanceof GeometryCollection collection) {
            for (int i = 0; i < collection.getNumGeometries(); i++) {
                String error = appendPlanarRegions(collection.getGeometryN(i), ctx, plane, out);
                if (error != null) {
                    return error;
                }
            }
            return null;
        }
        return "Boolean result is not a polygon";
    }

    static double area2d(PolygonProfileData profile) {
        return PolygonProfileMetrics.signedArea(profile);
    }

    static double regionArea2d(PlanarRegionData region) {
        double area = PolygonProfileMetrics.area(region.outer());
        if (!Double.isFinite(area)) {
            return Double.NaN;
        }
        for (PolygonProfileData hole : region.holes()) {
            double holeArea = PolygonProfileMetrics.area(hole);
            if (!Double.isFinite(holeArea)) {
                return Double.NaN;
            }
            area -= holeArea;
            if (!Double.isFinite(area)) {
                return Double.NaN;
            }
        }
        return Math.max(0.0d, area);
    }

    static PolygonProfileData selectPrimaryProfile(List<PolygonProfileData> profiles) {
        PolygonProfileData primary = profiles.getFirst();
        for (PolygonProfileData profile : profiles) {
            if (Math.abs(area2d(profile)) > Math.abs(area2d(primary))) {
                primary = profile;
            }
        }
        return primary;
    }

    static PlanarRegionData selectPrimaryRegion(List<PlanarRegionData> regions) {
        PlanarRegionData primary = regions.getFirst();
        for (PlanarRegionData region : regions) {
            if (regionArea2d(region) > regionArea2d(primary)) {
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

    private static List<Vector3d> ringToClosed(
            Coordinate[] coords,
            PlaneProjectionUtils.PlaneProjectionContext ctx
    ) {
        List<Vector3d> closed = new ArrayList<>(coords.length);
        for (Coordinate c : coords) {
            closed.add(ctx.fromLocal(new Vector2d(c.x, c.y)));
        }
        return PolygonProfileValidator.canonicalizeClosedPoints(closed);
    }
}

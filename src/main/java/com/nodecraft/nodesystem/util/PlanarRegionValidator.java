package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PlanarRegionData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PlaneProjectionUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2d;
import org.joml.Vector3d;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.operation.valid.IsValidOp;
import org.locationtech.jts.operation.valid.TopologyValidationError;

import java.util.List;

/**
 * Validation for {@link PlanarRegionData}: outer + holes on one plane.
 * Holes must be interior-disjoint from the outer boundary and from each other;
 * JTS {@code touches} / boundary intersection is invalid. Concentric annuli remain valid.
 */
public final class PlanarRegionValidator {

    private PlanarRegionValidator() {
    }

    public static @Nullable String validate(@Nullable PlanarRegionData region) {
        if (region == null) {
            return "Planar region is missing";
        }
        return validateConstruction(region.outer(), region.holes(), region.plane());
    }

    public static @Nullable String validateConstruction(
            @Nullable PolygonProfileData outer,
            @Nullable List<PolygonProfileData> holes,
            @Nullable PlaneData plane
    ) {
        if (outer == null) {
            return "Planar region requires an outer profile";
        }
        String outerError = PolygonProfileValidator.validate(outer);
        if (outerError != null) {
            return "Outer profile: " + outerError;
        }

        PlaneData resolvedPlane = plane != null ? plane.normalized() : outer.plane().normalized();
        if (resolvedPlane == null) {
            return "Planar region plane is invalid";
        }

        List<PolygonProfileData> holeList = holes == null ? List.of() : holes;
        long totalVertices = outer.getEdgeCount();
        for (int i = 0; i < holeList.size(); i++) {
            PolygonProfileData hole = holeList.get(i);
            if (hole == null) {
                return "Planar region hole " + i + " is missing";
            }
            String holeError = PolygonProfileValidator.validate(hole);
            if (holeError != null) {
                return "Hole " + i + ": " + holeError;
            }
            if (!PolygonProfileValidator.profilesCoplanar(outer, hole)) {
                return "Hole " + i + " is not coplanar with the outer profile";
            }
            totalVertices += hole.getEdgeCount();
        }

        if (!GenerationLimits.isWithinProfileTotalVertices(totalVertices)) {
            return "Planar region vertex workload exceeds limit ("
                + GenerationLimits.MAX_PROFILE_TOTAL_VERTICES + ")";
        }

        GeometryFactory gf = new GeometryFactory();
        Vector3d anchor = outer.closedPoints().getFirst();
        PlaneProjectionUtils.PlaneProjectionContext ctx =
            PlaneProjectionUtils.PlaneProjectionContext.from(resolvedPlane, anchor);
        Polygon jtsPolygon = toJtsPolygonWithHoles(outer, holeList, ctx, gf);
        if (jtsPolygon == null) {
            return "Failed to build planar region topology";
        }

        IsValidOp validOp = new IsValidOp(jtsPolygon);
        if (!validOp.isValid()) {
            TopologyValidationError error = validOp.getValidationError();
            if (error != null && error.getMessage() != null && !error.getMessage().isBlank()) {
                return "Planar region is invalid: " + error.getMessage();
            }
            return "Planar region topology is invalid";
        }

        Polygon outerOnly = toJtsLoop(outer, ctx, gf);
        if (outerOnly == null) {
            return "Failed to convert outer profile";
        }
        for (int i = 0; i < holeList.size(); i++) {
            Polygon holePoly = toJtsLoop(holeList.get(i), ctx, gf);
            if (holePoly == null) {
                return "Failed to convert hole " + i;
            }
            if (holePoly.touches(outerOnly) || holePoly.intersects(outerOnly.getBoundary())) {
                return "Hole " + i + " touches the outer profile";
            }
            if (!outerOnly.contains(holePoly.getInteriorPoint())
                    && !outerOnly.covers(holePoly)) {
                if (!outerOnly.contains(holePoly.getCentroid())) {
                    return "Hole " + i + " is not inside the outer profile";
                }
            }
            for (int j = i + 1; j < holeList.size(); j++) {
                Polygon other = toJtsLoop(holeList.get(j), ctx, gf);
                if (other == null) {
                    return "Failed to convert hole " + j;
                }
                if (holePoly.intersects(other)) {
                    return "Holes " + i + " and " + j + " overlap or touch";
                }
            }
        }

        return null;
    }

    public static @Nullable Polygon toJtsPolygonWithHoles(
            PolygonProfileData outer,
            List<PolygonProfileData> holes,
            PlaneProjectionUtils.PlaneProjectionContext ctx,
            GeometryFactory gf
    ) {
        LinearRing shell = toLinearRing(outer, ctx, gf);
        if (shell == null) {
            return null;
        }
        LinearRing[] holeRings = new LinearRing[holes.size()];
        for (int i = 0; i < holes.size(); i++) {
            holeRings[i] = toLinearRing(holes.get(i), ctx, gf);
            if (holeRings[i] == null) {
                return null;
            }
        }
        try {
            return gf.createPolygon(shell, holeRings);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static @Nullable Polygon toJtsLoop(
            PolygonProfileData profile,
            PlaneProjectionUtils.PlaneProjectionContext ctx,
            GeometryFactory gf
    ) {
        LinearRing ring = toLinearRing(profile, ctx, gf);
        if (ring == null) {
            return null;
        }
        try {
            return gf.createPolygon(ring);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static @Nullable LinearRing toLinearRing(
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
        try {
            return gf.createLinearRing(coords);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}

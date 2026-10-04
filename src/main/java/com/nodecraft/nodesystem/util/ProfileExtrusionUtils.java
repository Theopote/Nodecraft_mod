package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.datatypes.PrismGeometryData;
import com.nodecraft.nodesystem.datatypes.SurfaceStripData;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared profile extrusion helpers for Extrude / Extrude Region.
 * Historical Graph V91 residue.
 */
public final class ProfileExtrusionUtils {

    private ProfileExtrusionUtils() {
    }

    public record ExtrusionResult(
        PrismGeometryData prism,
        PolygonProfileData topProfile,
        SurfaceStripData sideSurface,
        List<Vector3d> baseClosedPoints,
        List<Vector3d> topClosedPoints,
        double height
    ) {
    }

    /**
     * Extrudes a simple polygon profile along {@code direction}.
     * Returns {@code null} and writes {@code errorOut} on failure.
     */
    public static @Nullable ExtrusionResult extrudeProfile(
            PolygonProfileData baseProfile,
            Vector3d direction,
            @Nullable StringBuilder errorOut
    ) {
        if (baseProfile == null || direction == null) {
            writeError(errorOut, "Profile and direction are required");
            return null;
        }
        double height = direction.length();
        if (!(height > 1.0e-12d) || !Double.isFinite(height)) {
            writeError(errorOut, "Extrusion direction must have non-zero finite length");
            return null;
        }

        List<Vector3d> baseUniquePoints = baseProfile.getUniquePoints();
        if (baseUniquePoints.size() < 3) {
            writeError(errorOut, "Profile must have at least 3 vertices");
            return null;
        }

        List<Vector3d> baseClosedPoints = baseProfile.closedPoints();
        List<Vector3d> topClosedPoints = new ArrayList<>(baseClosedPoints.size());
        for (Vector3d point : baseClosedPoints) {
            topClosedPoints.add(new Vector3d(point).add(direction));
        }

        PlaneData basePlane = baseProfile.plane();
        PlaneData topPlane = PlaneData.canonical(
            new Vector3d(basePlane.getPoint()).add(direction),
            basePlane.getNormal()
        );
        if (topPlane == null) {
            writeError(errorOut, "Failed to create top plane");
            return null;
        }

        PolygonProfileData topProfile = ProfileConstructionUtils.tryCreateProfile(
            topClosedPoints, topPlane, errorOut);
        if (topProfile == null) {
            return null;
        }

        PrismGeometryData prism = new PrismGeometryData(baseUniquePoints, direction);
        SurfaceStripData sideSurface;
        try {
            sideSurface = new SurfaceStripData(
                List.of(baseProfile.getUniquePoints(), topProfile.getUniquePoints()),
                List.of(true, true)
            );
        } catch (IllegalArgumentException ex) {
            writeError(errorOut, ex.getMessage() == null ? "Side surface is invalid" : ex.getMessage());
            return null;
        }

        return new ExtrusionResult(
            prism, topProfile, sideSurface, baseClosedPoints, topClosedPoints, height);
    }

    private static void writeError(@Nullable StringBuilder errorOut, String message) {
        if (errorOut != null) {
            errorOut.setLength(0);
            errorOut.append(message);
        }
    }
}

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
 * Historical Graph V91 residue; {@code GraphFormatVersion.CURRENT} is stamp-only 1.
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
        if (!VectorUtils.isFinite(direction)) {
            writeError(errorOut, "Extrusion direction must have non-zero finite length");
            return null;
        }
        double height = VectorUtils.safeLength(direction);
        if (!(height > VectorUtils.EPS) || !Double.isFinite(height)) {
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
            Vector3d top = VectorUtils.safeAdd(point, direction);
            if (top == null) {
                writeError(errorOut, "Extruded vertices are non-finite");
                return null;
            }
            topClosedPoints.add(top);
        }

        PlaneData basePlane = baseProfile.plane();
        Vector3d topOrigin = VectorUtils.safeAdd(basePlane.getPoint(), direction);
        if (topOrigin == null) {
            writeError(errorOut, "Failed to create top plane");
            return null;
        }
        PlaneData topPlane = PlaneData.canonical(topOrigin, basePlane.getNormal());
        if (topPlane == null) {
            writeError(errorOut, "Failed to create top plane");
            return null;
        }

        PolygonProfileData topProfile = ProfileConstructionUtils.tryCreateProfile(
            topClosedPoints, topPlane, errorOut);
        if (topProfile == null) {
            return null;
        }

        PrismGeometryData prism;
        try {
            prism = new PrismGeometryData(baseUniquePoints, direction);
        } catch (IllegalArgumentException ex) {
            writeError(errorOut, ex.getMessage() == null ? "Failed to create prism" : ex.getMessage());
            return null;
        }
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

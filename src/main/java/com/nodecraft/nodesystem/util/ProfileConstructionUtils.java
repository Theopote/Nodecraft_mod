package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;

/**
 * Fail-closed construction helpers for {@link PolygonProfileData} (Graph V91).
 */
public final class ProfileConstructionUtils {

    private ProfileConstructionUtils() {
    }

    /**
     * Builds a validated profile, returning {@code null} instead of throwing.
     * When {@code errorOut} is non-null, it is cleared and filled with the failure reason.
     */
    public static @Nullable PolygonProfileData tryCreateProfile(
            @Nullable List<Vector3d> points,
            @Nullable PlaneData plane,
            @Nullable StringBuilder errorOut
    ) {
        if (points == null || plane == null) {
            writeError(errorOut, "Polygon profile requires points and a plane");
            return null;
        }
        List<Vector3d> canonical = PolygonProfileValidator.canonicalizeClosedPoints(points);
        String validationError = PolygonProfileValidator.validateConstruction(canonical, plane);
        if (validationError != null) {
            writeError(errorOut, validationError);
            return null;
        }
        try {
            return new PolygonProfileData(canonical, plane);
        } catch (IllegalArgumentException ex) {
            writeError(errorOut, ex.getMessage() == null ? "Failed to create polygon profile" : ex.getMessage());
            return null;
        }
    }

    private static void writeError(@Nullable StringBuilder errorOut, String message) {
        if (errorOut != null) {
            errorOut.setLength(0);
            errorOut.append(message);
        }
    }
}

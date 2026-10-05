package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;

/**
 * Fail-closed construction helpers for {@link PolygonProfileData}.
 */
public final class ProfileConstructionUtils {

    private ProfileConstructionUtils() {
    }

    /**
     * Unique-vertex budget used by {@link PolygonProfileValidator} ({@code canonical.size() - 1}).
     */
    public static boolean requireUniqueVertices(int uniqueCount) {
        return GenerationLimits.isWithinProfileVertices(uniqueCount);
    }

    public static int uniqueCircleVertices(int segments) {
        return segments;
    }

    public static int uniqueCapsuleVertices(int capSegments) {
        return 2 * capSegments + 1;
    }

    public static int uniqueRoundedRectangleVertices(int cornerSegments, boolean sharp) {
        return sharp ? 4 : 4 * cornerSegments;
    }

    public static int uniqueSemiCircleVertices(int arcSegments) {
        return arcSegments + 1;
    }

    public static int uniqueSectorVertices(int arcSegments) {
        return arcSegments + 2;
    }

    public static int uniqueAnnularSectorVertices(int arcSegments) {
        return arcSegments * 2 + 2;
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

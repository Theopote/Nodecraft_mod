package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;

/**
 * Shared invariants for {@link BoxFaceData} consumed by graph nodes (Graph V75).
 * <p>
 * Canonical BOX_FACE: exactly 4 corners/indices, finite geometry, usable normal, coplanar corners.
 */
public final class BoxFaceValidator {

    public static final double COPLANAR_EPS = SpatialTolerance.EPS;

    private BoxFaceValidator() {
    }

    /**
     * @return null when valid; otherwise an actionable error message
     */
    public static @Nullable String validate(@Nullable BoxFaceData face) {
        if (face == null) {
            return "Box face is missing";
        }
        return validateConstruction(
            face.getName(),
            face.getCornerIndices(),
            face.getCorners(),
            face.getCenter(),
            face.getNormal()
        );
    }

    public static @Nullable String validateConstruction(
            @Nullable String name,
            @Nullable List<Integer> cornerIndices,
            @Nullable List<Vector3d> corners,
            @Nullable Vector3d center,
            @Nullable Vector3d normal
    ) {
        if (name == null || name.isBlank()) {
            return "Box face name is required";
        }
        if (cornerIndices == null || corners == null) {
            return "Box face requires corner indices and corners";
        }
        if (cornerIndices.size() != 4 || corners.size() != 4) {
            return "Box face must contain exactly 4 corners";
        }
        if (!FrameUtils.isFinite(center)) {
            return "Box face center must be finite";
        }
        if (!FrameUtils.isUsableAxis(normal)) {
            return "Box face normal must be finite and non-zero";
        }
        for (Vector3d corner : corners) {
            if (!FrameUtils.isFinite(corner)) {
                return "Box face corners must be finite";
            }
        }

        PlaneData plane = new PlaneData(center, normal).normalized();
        if (plane == null) {
            return "Box face plane is invalid";
        }
        Vector3d unitNormal = plane.getNormal();
        Vector3d planePoint = plane.getPoint();
        for (Vector3d corner : corners) {
            double distance = new Vector3d(corner).sub(planePoint).dot(unitNormal);
            if (Math.abs(distance) > COPLANAR_EPS) {
                return "Box face corners must be coplanar";
            }
        }
        return null;
    }
}

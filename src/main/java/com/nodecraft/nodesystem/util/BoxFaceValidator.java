package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;

/**
 * Shared invariants for {@link BoxFaceData} consumed by graph nodes (Graph V75, V103).
 * <p>
 * Canonical BOX_FACE: exactly 4 corners/indices in CCW ring order, finite geometry,
 * usable normal, coplanar corners, and an ordered rectangular face (adjacent edges
 * orthogonal, opposite edges parallel and equal length).
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

        return validateRectangularRing(corners, center, normal);
    }

    private static @Nullable String validateRectangularRing(
            List<Vector3d> corners,
            Vector3d center,
            Vector3d normal
    ) {
        for (int i = 0; i < 4; i++) {
            for (int j = i + 1; j < 4; j++) {
                if (corners.get(i).distanceSquared(corners.get(j)) <= COPLANAR_EPS * COPLANAR_EPS) {
                    return "Box face corners must be unique";
                }
            }
        }

        Vector3d c0 = corners.get(0);
        Vector3d c1 = corners.get(1);
        Vector3d c2 = corners.get(2);
        Vector3d c3 = corners.get(3);

        Vector3d e01 = edge(c1, c0);
        Vector3d e12 = edge(c2, c1);
        Vector3d e23 = edge(c3, c2);
        Vector3d e30 = edge(c0, c3);
        Vector3d e03 = edge(c3, c0);

        if (!FrameUtils.isUsableAxis(e01) || !FrameUtils.isUsableAxis(e12)
                || !FrameUtils.isUsableAxis(e23) || !FrameUtils.isUsableAxis(e30)) {
            return "Box face edges must be non-zero";
        }

        Vector3d e01n = VectorUtils.safeNormalize(e01);
        Vector3d e03n = VectorUtils.safeNormalize(e03);
        if (e01n == null || e03n == null) {
            return "Box face edges must be finite";
        }
        double adjacentDot = VectorUtils.safeDot(e01n, e03n);
        if (!Double.isFinite(adjacentDot) || Math.abs(adjacentDot) > COPLANAR_EPS) {
            return "Box face adjacent edges must be perpendicular";
        }

        if (!FrameUtils.areParallel(e01, e23)) {
            return "Box face opposite edges must be parallel";
        }
        if (!FrameUtils.areParallel(e03, e12)) {
            return "Box face opposite edges must be parallel";
        }

        double len01 = VectorUtils.safeLength(e01);
        double len23 = VectorUtils.safeLength(e23);
        double len03 = VectorUtils.safeLength(e03);
        double len12 = VectorUtils.safeLength(e12);
        if (!Double.isFinite(len01) || !Double.isFinite(len23)
                || !Double.isFinite(len03) || !Double.isFinite(len12)
                || !lengthsApproximatelyEqual(len01, len23)
                || !lengthsApproximatelyEqual(len03, len12)) {
            return "Box face opposite edges must have equal length";
        }

        Vector3d average = new Vector3d(c0).add(c1).add(c2).add(c3).mul(0.25d);
        if (center.distanceSquared(average) > COPLANAR_EPS * COPLANAR_EPS) {
            return "Box face center must match corner average";
        }

        Vector3d edgeNormal = VectorUtils.safeNormalize(VectorUtils.safeCross(e01, e03));
        Vector3d storedNormal = VectorUtils.safeNormalize(normal);
        if (edgeNormal == null || storedNormal == null) {
            return "Box face must form a rectangle";
        }
        double aligned = VectorUtils.safeDot(storedNormal, edgeNormal);
        if (!Double.isFinite(aligned) || Math.abs(aligned) < 1.0d - COPLANAR_EPS) {
            return "Box face normal must align with edge cross product";
        }

        return null;
    }

    private static Vector3d edge(Vector3d to, Vector3d from) {
        return new Vector3d(to).sub(from);
    }

    private static boolean lengthsApproximatelyEqual(double a, double b) {
        double max = Math.max(a, b);
        if (max <= COPLANAR_EPS) {
            return false;
        }
        return Math.abs(a - b) <= COPLANAR_EPS * max;
    }
}

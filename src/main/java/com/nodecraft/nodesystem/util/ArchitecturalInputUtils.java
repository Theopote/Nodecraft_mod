package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.ArchitecturalPrimitiveSupport;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PathUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Connection-aware architectural input resolution (Graph V68).
 * <p>
 * unconnected → property/default; connected valid → override; connected invalid → fail closed.
 */
public final class ArchitecturalInputUtils {

    private ArchitecturalInputUtils() {
    }

    public static @Nullable Integer resolveOptionalExactPositiveInteger(
            BaseNode node,
            String portId,
            int propertyFallback
    ) {
        Integer value = OptionalPortDrive.resolveOptionalInteger(node, portId, propertyFallback);
        if (value == null || value <= 0) {
            return null;
        }
        return value;
    }

    /**
     * Exact INTEGER in {@code [minInclusive, maxInclusive]}. Fail closed outside range.
     */
    public static @Nullable Integer resolveOptionalBoundedExactInteger(
            BaseNode node,
            String portId,
            int propertyFallback,
            int minInclusive,
            int maxInclusive
    ) {
        Integer value = OptionalPortDrive.resolveOptionalInteger(node, portId, propertyFallback);
        if (value == null || value < minInclusive || value > maxInclusive) {
            return null;
        }
        return value;
    }

    public static @Nullable Double resolveOptionalPositiveFiniteDouble(
            BaseNode node,
            String portId,
            double propertyFallback
    ) {
        Double value = OptionalPortDrive.resolveOptionalDouble(node, portId, propertyFallback);
        if (value == null || !(value > 0.0d) || !Double.isFinite(value)) {
            return null;
        }
        return value;
    }

    public static @Nullable Double resolveOptionalNonNegativeFiniteDouble(
            BaseNode node,
            String portId,
            double propertyFallback
    ) {
        Double value = OptionalPortDrive.resolveOptionalDouble(node, portId, propertyFallback);
        if (value == null || value < 0.0d || !Double.isFinite(value)) {
            return null;
        }
        return value;
    }

    /**
     * Any finite DOUBLE (signed). Used for lateral path offsets.
     */
    public static @Nullable Double resolveOptionalFiniteDouble(
            BaseNode node,
            String portId,
            double propertyFallback
    ) {
        Double value = OptionalPortDrive.resolveOptionalDouble(node, portId, propertyFallback);
        if (value == null || !Double.isFinite(value)) {
            return null;
        }
        return value;
    }

    /** Path join policies for wall/railing offset centerlines. Historical Graph V97 residue. */
    public static final Set<String> PATH_JOIN_MODES = Set.of("miter", "bevel", "butt");

    /**
     * Known string enum (case-insensitive, trimmed). Connected unknown/null → fail closed.
     */
    public static @Nullable String resolveKnownStringEnum(
            BaseNode node,
            String portId,
            @Nullable String propertyFallback,
            Set<String> allowedLowerCase
    ) {
        if (allowedLowerCase == null || allowedLowerCase.isEmpty()) {
            return null;
        }
        if (OptionalPortDrive.isConnected(node, portId)) {
            Object value = node.getInput(portId);
            if (!(value instanceof String text) || text.isBlank()) {
                return null;
            }
            String key = text.trim().toLowerCase(Locale.ROOT);
            return allowedLowerCase.contains(key) ? key : null;
        }
        if (propertyFallback == null || propertyFallback.isBlank()) {
            return null;
        }
        String key = propertyFallback.trim().toLowerCase(Locale.ROOT);
        return allowedLowerCase.contains(key) ? key : null;
    }

    public static @Nullable BoxFaceData resolveRequiredFace(BaseNode node, String portId) {
        Object value = node.getInput(portId);
        if (!(value instanceof BoxFaceData face)) {
            return null;
        }
        ArchitecturalPrimitiveSupport.FaceFrame frame = ArchitecturalPrimitiveSupport.resolveFaceFrame(face);
        return frame == null ? null : face;
    }

    /**
     * Resolves a PATH-compatible input to finite polyline points (size &gt;= 2).
     */
    public static @Nullable List<Vector3d> resolveRequiredPathPoints(BaseNode node, String portId) {
        List<Vector3d> points = PathUtils.resolvePath(node.getInput(portId));
        if (points == null || points.size() < 2) {
            return null;
        }
        for (Vector3d point : points) {
            if (!PointUtils.isFinite(point)) {
                return null;
            }
        }
        return points;
    }

    /**
     * Required POINT port (PointData only — no bare Vector3d).
     */
    public static @Nullable Vector3d resolveRequiredPointData(BaseNode node, String portId) {
        Object value = node.getInput(portId);
        if (value instanceof PointData point) {
            Vector3d position = point.position();
            return PointUtils.isFinite(position) ? position : null;
        }
        return null;
    }

    public static boolean isConnected(BaseNode node, String portId) {
        return OptionalPortDrive.isConnected(node, portId);
    }
}

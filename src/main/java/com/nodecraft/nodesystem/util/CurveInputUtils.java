package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.VectorData;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Connection-aware curve input resolution.
 * <p>
 * unconnected → property/default; connected valid → override; connected invalid → fail closed.
 */
public final class CurveInputUtils {

    private CurveInputUtils() {
    }

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

    public static @Nullable Double resolveOptionalFiniteDouble(
            BaseNode node,
            String portId,
            double propertyFallback
    ) {
        return OptionalPortDrive.resolveOptionalDouble(node, portId, propertyFallback);
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
     * Normalized arc-length parameter for open paths: finite in {@code [0, 1]}.
     */
    public static @Nullable Double resolveNormalizedTOpen(
            BaseNode node,
            String portId,
            double propertyFallback
    ) {
        Double value = OptionalPortDrive.resolveOptionalDouble(node, portId, propertyFallback);
        if (value == null || value < 0.0d || value > 1.0d) {
            return null;
        }
        return value;
    }

    /**
     * Split parameter: finite in {@code (0, 1)} — endpoints produce zero-length sides.
     */
    public static @Nullable Double resolveNormalizedTSplit(
            BaseNode node,
            String portId,
            double propertyFallback
    ) {
        Double value = OptionalPortDrive.resolveOptionalDouble(node, portId, propertyFallback);
        if (value == null || value <= 0.0d || value >= 1.0d) {
            return null;
        }
        return value;
    }

    /**
     * Closed-path parameter: finite, wrapped to {@code [0, 1)} via modulo.
     */
    public static @Nullable Double resolveNormalizedTClosed(
            BaseNode node,
            String portId,
            double propertyFallback
    ) {
        Double value = OptionalPortDrive.resolveOptionalDouble(node, portId, propertyFallback);
        if (value == null) {
            return null;
        }
        double wrapped = value % 1.0d;
        if (wrapped < 0.0d) {
            wrapped += 1.0d;
        }
        return wrapped;
    }

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

    public static @Nullable Vector3d resolveOptionalFiniteVector(
            BaseNode node,
            String portId,
            @Nullable Vector3d propertyFallback
    ) {
        return OptionalPortDrive.resolveOptionalVector(node, portId, propertyFallback);
    }

    public static @Nullable Vector3d resolveOptionalNonZeroVector(
            BaseNode node,
            String portId,
            @Nullable Vector3d propertyFallback
    ) {
        Vector3d value = OptionalPortDrive.resolveOptionalVector(node, portId, propertyFallback);
        if (value == null || value.lengthSquared() <= 1.0e-24d) {
            return null;
        }
        return value;
    }

    /**
     * Strict POINT_LIST: Collection of {@link PointData} only, all finite, no filtering.
     * Empty / oversize / mixed types → {@code null}.
     */
    public static @Nullable List<Vector3d> resolveStrictPointListBounded(@Nullable Object value, int maxElements) {
        return PointUtils.resolveStrictPointListBounded(value, maxElements);
    }

    /**
     * Connected POINT must be finite {@link PointData}. Call only when the port is connected.
     */
    public static @Nullable Vector3d requireConnectedPointData(BaseNode node, String portId) {
        Object value = node.getInput(portId);
        if (!(value instanceof PointData pointData)) {
            return null;
        }
        Vector3d position = pointData.position();
        return PointUtils.isFinite(position) ? new Vector3d(position) : null;
    }

    /**
     * Connected VECTOR must be finite {@link VectorData}. Call only when the port is connected.
     */
    public static @Nullable Vector3d requireConnectedVectorData(BaseNode node, String portId) {
        Object value = node.getInput(portId);
        if (!(value instanceof VectorData vectorData)) {
            return null;
        }
        Vector3d components = vectorData.components();
        return VectorUtils.isFinite(components) ? new Vector3d(components) : null;
    }

    /**
     * Strict DOUBLE_LIST: exact length, every entry is an exact finite {@link Double} {@code > 0}.
     */
    public static @Nullable List<Double> resolveStrictPositiveDoubleList(
            @Nullable Object value,
            int requiredLength
    ) {
        if (!(value instanceof Collection<?> collection) || collection.size() != requiredLength) {
            return null;
        }
        List<Double> weights = new ArrayList<>(requiredLength);
        for (Object entry : collection) {
            if (!(entry instanceof Double resolved)) {
                return null;
            }
            if (!Double.isFinite(resolved) || !(resolved > 0.0d)) {
                return null;
            }
            weights.add(resolved);
        }
        return List.copyOf(weights);
    }

    public static boolean isWithinControlCount(int count) {
        return count >= 1 && count <= GenerationLimits.MAX_CURVE_CONTROL_POINTS;
    }

    public static boolean isWithinEvaluationWork(long controlCount, long sampleCount) {
        if (controlCount <= 0L || sampleCount <= 0L) {
            return false;
        }
        if (controlCount > GenerationLimits.MAX_CURVE_CONTROL_POINTS) {
            return false;
        }
        if (sampleCount > GenerationLimits.MAX_CURVE_SAMPLES) {
            return false;
        }
        long work = controlCount * sampleCount;
        return work >= 0L && work <= GenerationLimits.MAX_CURVE_EVALUATION_WORK;
    }

    /**
     * NURBS weights: unconnected → uniform default; connected → strict DOUBLE_LIST aligned to control count.
     */
    public static @Nullable List<Double> resolveOptionalNurbsWeights(
            BaseNode node,
            String portId,
            int controlCount,
            double defaultWeight
    ) {
        if (controlCount <= 0 || !Double.isFinite(defaultWeight) || !(defaultWeight > 0.0d)) {
            return null;
        }
        if (!OptionalPortDrive.isConnected(node, portId)) {
            List<Double> uniform = new ArrayList<>(controlCount);
            for (int i = 0; i < controlCount; i++) {
                uniform.add(defaultWeight);
            }
            return List.copyOf(uniform);
        }
        return resolveStrictPositiveDoubleList(node.getInput(portId), controlCount);
    }

    public static boolean isWithinCurveSamples(int count) {
        return count >= 2 && count <= GenerationLimits.MAX_CURVE_SAMPLES;
    }

    public static boolean isWithinCurveOutputPaths(int count) {
        return count >= 1 && count <= GenerationLimits.MAX_CURVE_OUTPUT_PATHS;
    }

    public static boolean isWithinCurveWorkload(long pathCount, long samplesPerPath) {
        if (pathCount <= 0L || samplesPerPath <= 0L) {
            return false;
        }
        if (pathCount > GenerationLimits.MAX_CURVE_OUTPUT_PATHS) {
            return false;
        }
        if (samplesPerPath > GenerationLimits.MAX_CURVE_SAMPLES) {
            return false;
        }
        return pathCount * samplesPerPath <= GenerationLimits.MAX_CURVE_TOTAL_SAMPLES;
    }

    public static boolean isConnected(BaseNode node, String portId) {
        return OptionalPortDrive.isConnected(node, portId);
    }
}

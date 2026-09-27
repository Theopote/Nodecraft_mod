package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.datatypes.SurfaceStripData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Connection-aware surface/solid input resolution (Graph V72).
 */
public final class SurfaceInputUtils {

    private SurfaceInputUtils() {
    }

    public static @Nullable Integer resolveOptionalBoundedExactInteger(
            BaseNode node,
            String portId,
            int propertyFallback,
            int minInclusive,
            int maxInclusive
    ) {
        return CurveInputUtils.resolveOptionalBoundedExactInteger(
            node, portId, propertyFallback, minInclusive, maxInclusive);
    }

    public static @Nullable Integer resolveOptionalExactPositiveInteger(
            BaseNode node,
            String portId,
            int propertyFallback
    ) {
        return CurveInputUtils.resolveOptionalExactPositiveInteger(node, portId, propertyFallback);
    }

    public static @Nullable Double resolveOptionalFiniteDouble(
            BaseNode node,
            String portId,
            double propertyFallback
    ) {
        return CurveInputUtils.resolveOptionalFiniteDouble(node, portId, propertyFallback);
    }

    public static @Nullable Double resolveOptionalPositiveFiniteDouble(
            BaseNode node,
            String portId,
            double propertyFallback
    ) {
        return CurveInputUtils.resolveOptionalPositiveFiniteDouble(node, portId, propertyFallback);
    }

    public static @Nullable Double resolveOptionalNonNegativeFiniteDouble(
            BaseNode node,
            String portId,
            double propertyFallback
    ) {
        return CurveInputUtils.resolveOptionalNonNegativeFiniteDouble(node, portId, propertyFallback);
    }

    /** Normalized surface parameter U or V in {@code [0, 1]}. */
    public static @Nullable Double resolveNormalizedU(
            BaseNode node,
            String portId,
            double propertyFallback
    ) {
        return CurveInputUtils.resolveNormalizedTOpen(node, portId, propertyFallback);
    }

    /** Normalized range end: {@code 0 <= start < end <= 1}. */
    public static @Nullable Double resolveNormalizedRangeEnd(
            BaseNode node,
            String portId,
            double propertyFallback
    ) {
        return CurveInputUtils.resolveNormalizedTOpen(node, portId, propertyFallback);
    }

    public static @Nullable String resolveKnownStringEnum(
            BaseNode node,
            String portId,
            @Nullable String propertyFallback,
            Set<String> allowedLowerCase
    ) {
        return CurveInputUtils.resolveKnownStringEnum(node, portId, propertyFallback, allowedLowerCase);
    }

    public static @Nullable List<Double> resolveStrictFiniteDoubleList(@Nullable Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return null;
        }
        List<Double> resolved = new ArrayList<>(collection.size());
        for (Object entry : collection) {
            if (!(entry instanceof Number number)) {
                return null;
            }
            double d = number.doubleValue();
            if (!Double.isFinite(d)) {
                return null;
            }
            resolved.add(d);
        }
        return List.copyOf(resolved);
    }

    public static @Nullable List<Double> resolveStrictPositiveDoubleList(@Nullable Object value) {
        List<Double> values = resolveStrictFiniteDoubleList(value);
        if (values == null) {
            return null;
        }
        for (double d : values) {
            if (!(d > 0.0d)) {
                return null;
            }
        }
        return values;
    }

    public static @Nullable List<PolygonProfileData> resolveStrictProfileList(@Nullable Object value) {
        if (!(value instanceof Collection<?> collection) || collection.isEmpty()) {
            return null;
        }
        List<PolygonProfileData> profiles = new ArrayList<>(collection.size());
        for (Object item : collection) {
            if (!(item instanceof PolygonProfileData profile)) {
                return null;
            }
            profiles.add(profile);
        }
        return List.copyOf(profiles);
    }

    public static @Nullable List<PlaneData> resolveStrictPlaneList(@Nullable Object value) {
        if (!(value instanceof Collection<?> collection) || collection.isEmpty()) {
            return null;
        }
        List<PlaneData> planes = new ArrayList<>(collection.size());
        for (Object item : collection) {
            if (!(item instanceof PlaneData plane)) {
                return null;
            }
            planes.add(plane.normalized());
        }
        return List.copyOf(planes);
    }

    public static @Nullable List<SurfaceStripData> resolveStrictSurfaceStripList(@Nullable Object value) {
        if (!(value instanceof Collection<?> collection) || collection.isEmpty()) {
            return null;
        }
        List<SurfaceStripData> strips = new ArrayList<>(collection.size());
        for (Object item : collection) {
            if (!(item instanceof SurfaceStripData strip)) {
                return null;
            }
            if (SurfaceStripValidator.validate(strip) != null) {
                return null;
            }
            strips.add(strip);
        }
        return List.copyOf(strips);
    }

    /**
     * Resample or broadcast a scalar field along U: 0 values → defaults; 1 → broadcast; N → linear resample.
     */
    public static List<Double> sampleFieldAlongU(
            @Nullable List<Double> values,
            int sectionCount,
            double defaultValue
    ) {
        if (sectionCount <= 0) {
            return List.of();
        }
        if (values == null || values.isEmpty()) {
            List<Double> defaults = new ArrayList<>(sectionCount);
            for (int i = 0; i < sectionCount; i++) {
                defaults.add(defaultValue);
            }
            return List.copyOf(defaults);
        }
        if (values.size() == 1) {
            List<Double> broadcast = new ArrayList<>(sectionCount);
            for (int i = 0; i < sectionCount; i++) {
                broadcast.add(values.getFirst());
            }
            return List.copyOf(broadcast);
        }
        if (values.size() == sectionCount) {
            return values;
        }
        List<Double> sampled = new ArrayList<>(sectionCount);
        for (int i = 0; i < sectionCount; i++) {
            double t = sectionCount == 1 ? 0.0d : (double) i / (sectionCount - 1);
            double index = t * (values.size() - 1);
            int lo = (int) Math.floor(index);
            int hi = Math.min(values.size() - 1, lo + 1);
            double frac = index - lo;
            sampled.add(values.get(lo) * (1.0d - frac) + values.get(hi) * frac);
        }
        return List.copyOf(sampled);
    }

    public static boolean isWithinSurfaceSections(int sectionCount) {
        return sectionCount >= 2 && sectionCount <= GenerationLimits.MAX_SURFACE_SECTIONS;
    }

    public static boolean isWithinSurfacePointsPerSection(int pointsPerSection) {
        return pointsPerSection >= 2 && pointsPerSection <= GenerationLimits.MAX_SURFACE_POINTS_PER_SECTION;
    }

    public static boolean isWithinSurfaceWorkload(int sectionCount, int pointsPerSection) {
        if (sectionCount < 2 || pointsPerSection < 2) {
            return false;
        }
        return (long) sectionCount * pointsPerSection <= GenerationLimits.MAX_SURFACE_TOTAL_POINTS;
    }

    public static boolean isWithinProjectionWorkload(long queryCount, long triangleCount) {
        if (queryCount <= 0L || triangleCount <= 0L) {
            return false;
        }
        if (queryCount > GenerationLimits.MAX_SURFACE_PROJECTION_QUERIES) {
            return false;
        }
        return queryCount * triangleCount <= GenerationLimits.MAX_SURFACE_PROJECTION_WORK;
    }

    public static boolean isConnected(BaseNode node, String portId) {
        return OptionalPortDrive.isConnected(node, portId);
    }
}

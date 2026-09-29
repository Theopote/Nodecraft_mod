package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import org.jetbrains.annotations.Nullable;

/**
 * Geometry structure helpers for placement workload budgeting (Graph V76 / V100).
 */
public final class GeometryStructureUtils {

    private GeometryStructureUtils() {
    }

    /**
     * Counts leaf geometries: composites are flattened; any other non-null geometry counts as one.
     */
    public static long countLeaves(@Nullable GeometryData geometry) {
        return countLeavesBounded(geometry, Long.MAX_VALUE);
    }

    /**
     * Counts leaf geometries without allocating a leaf list. If the count would exceed
     * {@code limit}, returns {@code limit + 1} (or {@link Long#MAX_VALUE} when limit is already
     * max) and stops early (Graph V100).
     */
    public static long countLeavesBounded(@Nullable GeometryData geometry, long limit) {
        if (geometry == null) {
            return 0;
        }
        if (limit < 0) {
            return 1;
        }
        return countLeavesBoundedRecursive(geometry, 0L, limit);
    }

    private static long countLeavesBoundedRecursive(GeometryData geometry, long count, long limit) {
        if (geometry instanceof CompositeGeometryData composite) {
            for (GeometryData child : composite.geometries()) {
                if (child == null) {
                    continue;
                }
                count = countLeavesBoundedRecursive(child, count, limit);
                if (count > limit) {
                    return overLimit(limit);
                }
            }
            return count;
        }
        if (count == Long.MAX_VALUE) {
            return overLimit(limit);
        }
        count++;
        return count > limit ? overLimit(limit) : count;
    }

    private static long overLimit(long limit) {
        return limit == Long.MAX_VALUE ? Long.MAX_VALUE : limit + 1;
    }
}

package com.nodecraft.nodesystem.util;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Spacing samples along a path. Closed paths omit the seam (distance = length).
 */
public final class PathSpacingPlan {

    private PathSpacingPlan() {
    }

    /**
     * Sample distances, or {@code null} when the plan is invalid or would exceed {@code maxCount}.
     */
    public static @Nullable List<Double> distances(
        double total,
        boolean closed,
        boolean includeEnds,
        double spacing,
        int maxCount
    ) {
        if (!Double.isFinite(total) || total < 0.0d
            || !Double.isFinite(spacing) || !(spacing > 0.0d)
            || maxCount < 1) {
            return null;
        }
        long estimated = estimateCount(total, closed, includeEnds, spacing);
        if (estimated < 0L || estimated > maxCount) {
            return null;
        }
        List<Double> distances = new ArrayList<>((int) estimated);
        double eps = SpatialTolerance.EPS;
        for (double d = includeEnds ? 0.0d : spacing; d <= total + eps; d += spacing) {
            if (closed && d >= total - eps) {
                break;
            }
            distances.add(Math.min(d, total));
            if (distances.size() > maxCount) {
                return null;
            }
        }
        if (!closed && includeEnds
            && (distances.isEmpty() || distances.getLast() < total - eps)
            && distances.size() < maxCount) {
            distances.add(total);
        }
        if (distances.size() > maxCount) {
            return null;
        }
        return distances;
    }

    static long estimateCount(double total, boolean closed, boolean includeEnds, double spacing) {
        double eps = SpatialTolerance.EPS;
        double start = includeEnds ? 0.0d : spacing;
        double span = closed ? (total - eps - start) : (total + eps - start);
        if (!Double.isFinite(span / spacing) || span / spacing > (double) Integer.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        if (closed) {
            if (start >= total - eps) {
                return 0L;
            }
            return 1L + (long) Math.floor(span / spacing);
        }
        if (start > total + eps) {
            return includeEnds ? 1L : 0L;
        }
        long stepped = 1L + (long) Math.floor(span / spacing);
        if (includeEnds) {
            double last = start + (stepped - 1L) * spacing;
            if (last < total - eps) {
                stepped += 1L;
            }
        }
        return stepped;
    }
}

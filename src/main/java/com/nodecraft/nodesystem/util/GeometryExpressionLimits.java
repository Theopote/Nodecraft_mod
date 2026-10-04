package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.DifferenceGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.IntersectionGeometryData;
import org.jetbrains.annotations.Nullable;

/**
 * Structural depth of deferred geometry expressions (composite / boolean wrappers).
 * Leaf geometry has depth 1.
 */
public final class GeometryExpressionLimits {

    public static final String DEPTH_EXCEEDED = "geometry_expression_depth_exceeded";

    private GeometryExpressionLimits() {
    }

    public static int depth(@Nullable GeometryData geometry) {
        if (geometry == null) {
            return 0;
        }
        if (geometry instanceof CompositeGeometryData composite) {
            int maxChild = 0;
            for (GeometryData child : composite.geometries()) {
                maxChild = Math.max(maxChild, depth(child));
            }
            return 1 + maxChild;
        }
        if (geometry instanceof DifferenceGeometryData difference) {
            return 1 + Math.max(depth(difference.getMinuend()), depth(difference.getSubtrahend()));
        }
        if (geometry instanceof IntersectionGeometryData intersection) {
            return 1 + Math.max(depth(intersection.left()), depth(intersection.right()));
        }
        return 1;
    }

    public static boolean exceedsMax(@Nullable GeometryData geometry) {
        return depth(geometry) > GenerationLimits.MAX_GEOMETRY_EXPRESSION_DEPTH;
    }

    /** True when wrapping two operands as Difference/Intersection stays within the depth cap. */
    public static boolean canWrap(@Nullable GeometryData left, @Nullable GeometryData right) {
        return 1 + Math.max(depth(left), depth(right)) <= GenerationLimits.MAX_GEOMETRY_EXPRESSION_DEPTH;
    }
}

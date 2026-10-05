package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.DifferenceGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.IntersectionGeometryData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;

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
        int maxDepth = 0;
        Deque<Frame> stack = new ArrayDeque<>();
        stack.push(new Frame(geometry, 1));
        while (!stack.isEmpty()) {
            Frame frame = stack.pop();
            if (frame.depth > GenerationLimits.MAX_GEOMETRY_EXPRESSION_DEPTH) {
                return frame.depth;
            }
            maxDepth = Math.max(maxDepth, frame.depth);
            for (GeometryData child : children(frame.geometry)) {
                stack.push(new Frame(child, frame.depth + 1));
            }
        }
        return maxDepth;
    }

    public static boolean exceedsMax(@Nullable GeometryData geometry) {
        return depth(geometry) > GenerationLimits.MAX_GEOMETRY_EXPRESSION_DEPTH;
    }

    /** True when wrapping two operands as Difference/Intersection stays within the depth cap. */
    public static boolean canWrap(@Nullable GeometryData left, @Nullable GeometryData right) {
        return 1 + Math.max(depth(left), depth(right)) <= GenerationLimits.MAX_GEOMETRY_EXPRESSION_DEPTH;
    }

    private static Iterable<GeometryData> children(GeometryData geometry) {
        if (geometry instanceof CompositeGeometryData composite) {
            return composite.geometries();
        }
        if (geometry instanceof DifferenceGeometryData difference) {
            return java.util.List.of(difference.getMinuend(), difference.getSubtrahend());
        }
        if (geometry instanceof IntersectionGeometryData intersection) {
            return java.util.List.of(intersection.left(), intersection.right());
        }
        return java.util.List.of();
    }

    private record Frame(GeometryData geometry, int depth) {
    }
}

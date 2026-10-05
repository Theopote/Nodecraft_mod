package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.BentSdfData;
import com.nodecraft.nodesystem.datatypes.BooleanSdfData;
import com.nodecraft.nodesystem.datatypes.DomainWarpedSdfData;
import com.nodecraft.nodesystem.datatypes.MirroredSdfData;
import com.nodecraft.nodesystem.datatypes.NoiseDisplacedSdfData;
import com.nodecraft.nodesystem.datatypes.SignedDistanceFieldData;
import com.nodecraft.nodesystem.datatypes.TransformedSdfData;
import com.nodecraft.nodesystem.datatypes.TwistedSdfData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Structural depth and node-count limits for deferred SDF expression trees.
 * Leaf SDF primitives have depth 1.
 */
public final class SdfExpressionLimits {

    public static final String BUDGET_EXCEEDED = "sdf_expression_budget_exceeded";

    private SdfExpressionLimits() {
    }

    public static int depth(@Nullable SignedDistanceFieldData sdf) {
        if (sdf == null) {
            return 0;
        }
        int maxDepth = 0;
        Deque<Frame> stack = new ArrayDeque<>();
        stack.push(new Frame(sdf, 1));
        while (!stack.isEmpty()) {
            Frame frame = stack.pop();
            if (frame.depth > GenerationLimits.MAX_SDF_EXPRESSION_DEPTH) {
                return frame.depth;
            }
            maxDepth = Math.max(maxDepth, frame.depth);
            for (SignedDistanceFieldData child : children(frame.sdf)) {
                stack.push(new Frame(child, frame.depth + 1));
            }
        }
        return maxDepth;
    }

    public static int nodeCount(@Nullable SignedDistanceFieldData sdf) {
        if (sdf == null) {
            return 0;
        }
        int count = 0;
        Deque<SignedDistanceFieldData> stack = new ArrayDeque<>();
        stack.push(sdf);
        while (!stack.isEmpty()) {
            if (++count > GenerationLimits.MAX_SDF_NODE_COUNT) {
                return count;
            }
            SignedDistanceFieldData current = stack.pop();
            for (SignedDistanceFieldData child : children(current)) {
                stack.push(child);
            }
        }
        return count;
    }

    public static boolean exceedsMax(@Nullable SignedDistanceFieldData sdf) {
        return depth(sdf) > GenerationLimits.MAX_SDF_EXPRESSION_DEPTH
            || nodeCount(sdf) > GenerationLimits.MAX_SDF_NODE_COUNT;
    }

    public static boolean validate(@Nullable SignedDistanceFieldData sdf) {
        return sdf != null && !exceedsMax(sdf);
    }

    public static boolean canWrapUnary(@Nullable SignedDistanceFieldData source) {
        if (source == null) {
            return false;
        }
        return depth(source) + 1 <= GenerationLimits.MAX_SDF_EXPRESSION_DEPTH
            && nodeCount(source) + 1 <= GenerationLimits.MAX_SDF_NODE_COUNT;
    }

    public static boolean canWrapBinary(
            @Nullable SignedDistanceFieldData left,
            @Nullable SignedDistanceFieldData right
    ) {
        if (left == null || right == null) {
            return false;
        }
        int wrappedDepth = 1 + Math.max(depth(left), depth(right));
        int wrappedCount = nodeCount(left) + nodeCount(right) + 1;
        return wrappedDepth <= GenerationLimits.MAX_SDF_EXPRESSION_DEPTH
            && wrappedCount <= GenerationLimits.MAX_SDF_NODE_COUNT;
    }

    private static Iterable<SignedDistanceFieldData> children(SignedDistanceFieldData sdf) {
        return switch (sdf) {
            case BooleanSdfData booleanSdf -> java.util.List.of(booleanSdf.getLeft(), booleanSdf.getRight());
            case TransformedSdfData transformed -> java.util.List.of(transformed.getSource());
            case NoiseDisplacedSdfData noise -> java.util.List.of(noise.getSource());
            case DomainWarpedSdfData warp -> java.util.List.of(warp.getSource());
            case TwistedSdfData twisted -> java.util.List.of(twisted.getSource());
            case BentSdfData bent -> java.util.List.of(bent.getSource());
            case MirroredSdfData mirrored -> java.util.List.of(mirrored.source());
            default -> java.util.List.of();
        };
    }

    private record Frame(SignedDistanceFieldData sdf, int depth) {
    }
}

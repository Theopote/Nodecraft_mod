package com.nodecraft.nodesystem.contract.support;

import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.PathData;
import org.joml.Vector3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Geometry-level assertions for architectural primitives (local extents, path elevation).
 */
public final class ArchitecturalGeometryAssert {

    private static final double TOL = 1.0e-6d;

    private ArchitecturalGeometryAssert() {
    }

    public static void assertLocalHalfExtents(
        BoxGeometryData box,
        double expectedX,
        double expectedY,
        double expectedZ
    ) {
        Vector3d half = box.getHalfExtents();
        assertEquals(expectedX, half.x, TOL, "local X half extent");
        assertEquals(expectedY, half.y, TOL, "local Y half extent");
        assertEquals(expectedZ, half.z, TOL, "local Z half extent");
    }

    public static double pathMidpointAlongNormal(PathData path) {
        if (path.getLine() == null) {
            return Double.NaN;
        }
        var start = path.getLine().start();
        var end = path.getLine().end();
        return (start.z + end.z) * 0.5d;
    }

    public static void assertPathHigherThan(PathData higher, PathData lower, double minDelta) {
        double delta = pathMidpointAlongNormal(higher) - pathMidpointAlongNormal(lower);
        assertTrue(delta >= minDelta - TOL,
            "expected higher path elevation along face normal; delta=" + delta + " minDelta=" + minDelta);
    }

    public static double xzArea(BoxGeometryData box) {
        Bounds bounds = xzBounds(box);
        return (bounds.maxX - bounds.minX) * (bounds.maxZ - bounds.minZ);
    }

    public static double xzOverlapArea(BoxGeometryData a, BoxGeometryData b) {
        Bounds left = xzBounds(a);
        Bounds right = xzBounds(b);
        double overlapX = Math.min(left.maxX, right.maxX) - Math.max(left.minX, right.minX);
        double overlapZ = Math.min(left.maxZ, right.maxZ) - Math.max(left.minZ, right.minZ);
        if (overlapX <= 0.0d || overlapZ <= 0.0d) {
            return 0.0d;
        }
        return overlapX * overlapZ;
    }

    public static void assertCornerStepContinuity(
        BoxGeometryData beforeCorner,
        BoxGeometryData afterCorner,
        double minOverlapArea,
        double maxOverlapRatio
    ) {
        double overlap = xzOverlapArea(beforeCorner, afterCorner);
        double minArea = Math.min(xzArea(beforeCorner), xzArea(afterCorner));
        assertTrue(overlap >= minOverlapArea - TOL,
            "corner steps should overlap or touch in XZ; overlapArea=" + overlap
                + " minOverlapArea=" + minOverlapArea);
        if (minArea > TOL) {
            assertTrue(overlap / minArea <= maxOverlapRatio + TOL,
                "corner steps should not overlap excessively; ratio=" + (overlap / minArea)
                    + " maxOverlapRatio=" + maxOverlapRatio);
        }
    }

    private static Bounds xzBounds(BoxGeometryData box) {
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (Vector3d corner : box.getCorners()) {
            minX = Math.min(minX, corner.x);
            maxX = Math.max(maxX, corner.x);
            minZ = Math.min(minZ, corner.z);
            maxZ = Math.max(maxZ, corner.z);
        }
        return new Bounds(minX, maxX, minZ, maxZ);
    }

    private record Bounds(double minX, double maxX, double minZ, double maxZ) {
    }
}

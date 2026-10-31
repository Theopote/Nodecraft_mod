package com.nodecraft.nodesystem.preview;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreviewCurveSmoothingTest {

    private static List<Vec3d> squareCorners() {
        return List.of(
            new Vec3d(0.0d, 0.0d, 0.0d),
            new Vec3d(4.0d, 0.0d, 0.0d),
            new Vec3d(4.0d, 0.0d, 4.0d),
            new Vec3d(0.0d, 0.0d, 4.0d)
        );
    }

    @Test
    void linearMode_keepsControlPointsUnchanged() {
        List<Vec3d> corners = squareCorners();
        List<Vec3d> result = PreviewCurveSmoothing.buildRenderPoints(corners, false, false);

        assertEquals(corners.size(), result.size());
        for (int i = 0; i < corners.size(); i++) {
            assertEquals(corners.get(i), result.get(i));
        }
    }

    @Test
    void smoothMode_subdividesSegments() {
        List<Vec3d> corners = squareCorners();
        List<Vec3d> result = PreviewCurveSmoothing.buildRenderPoints(corners, false, true);

        assertTrue(result.size() > corners.size(),
            "Smooth mode must subdivide the polyline into more render points");
        // Every original control point must still be present in the sampled output.
        for (Vec3d corner : corners) {
            assertTrue(containsNear(result, corner, 1.0e-6d),
                "Spline must pass through control point " + corner);
        }
    }

    @Test
    void linearMode_keepsTheSharpKinkForContrast() {
        List<Vec3d> corners = squareCorners();
        List<Vec3d> result = PreviewCurveSmoothing.buildRenderPoints(corners, false, false);

        int cornerIndex = 1;
        Vec3d before = result.get(cornerIndex).subtract(result.get(cornerIndex - 1)).normalize();
        Vec3d after = result.get(cornerIndex + 1).subtract(result.get(cornerIndex)).normalize();
        double turnDegrees = Math.toDegrees(Math.acos(Math.min(1.0d, before.dotProduct(after))));
        assertEquals(90.0d, turnDegrees, 1.0e-6d);
    }

    @Test
    void smoothMode_cornersAreRoundedInsteadOfSharpKinks() {
        List<Vec3d> corners = squareCorners();
        List<Vec3d> result = PreviewCurveSmoothing.buildRenderPoints(corners, false, true);

        // The polyline corner at (4,0,0) is a hard 90 degree kink in linear mode.
        // In smooth mode the tangent must turn gradually, so the local turn angle is
        // clearly smaller than 90 degrees and the curve bulges past the corner.
        int cornerIndex = -1;
        for (int i = 0; i < result.size(); i++) {
            if (result.get(i).distanceTo(new Vec3d(4.0d, 0.0d, 0.0d)) < 1.0e-6d) {
                cornerIndex = i;
                break;
            }
        }
        assertTrue(cornerIndex > 0 && cornerIndex < result.size() - 1,
            "The control point must be present in the sampled spline");

        Vec3d before = result.get(cornerIndex).subtract(result.get(cornerIndex - 1)).normalize();
        Vec3d after = result.get(cornerIndex + 1).subtract(result.get(cornerIndex)).normalize();
        double turnDegrees = Math.toDegrees(Math.acos(Math.min(1.0d, before.dotProduct(after))));
        assertTrue(turnDegrees < 60.0d,
            "Smooth mode must round the corner instead of keeping a 90 degree kink, was " + turnDegrees);

        boolean bulgesPastCorner = false;
        for (Vec3d p : result) {
            if (p.x > 4.0d + 1.0e-6d) {
                bulgesPastCorner = true;
                break;
            }
        }
        assertTrue(bulgesPastCorner, "Smoothed curve should round outward past the sharp corner");
    }

    @Test
    void smoothMode_isNotIdenticalToLinearOutput() {
        List<Vec3d> corners = squareCorners();
        List<Vec3d> linear = PreviewCurveSmoothing.buildRenderPoints(corners, false, false);
        List<Vec3d> smooth = PreviewCurveSmoothing.buildRenderPoints(corners, false, true);

        assertNotEquals(linear.size(), smooth.size());
    }

    @Test
    void closedPath_linearMode_appendsSeamPoint() {
        List<Vec3d> corners = squareCorners();
        List<Vec3d> result = PreviewCurveSmoothing.buildRenderPoints(corners, true, false);

        assertEquals(corners.size() + 1, result.size());
        assertEquals(corners.getFirst(), result.getLast());
    }

    @Test
    void closedPath_withDuplicatedEndPoint_doesNotDoubleClose() {
        List<Vec3d> corners = new ArrayList<>(squareCorners());
        corners.add(corners.getFirst());

        List<Vec3d> result = PreviewCurveSmoothing.buildRenderPoints(corners, true, false);

        // 4 unique corners + 1 seam point, not 4 + 2.
        assertEquals(squareCorners().size() + 1, result.size());
        assertEquals(corners.getFirst(), result.getLast());
    }

    @Test
    void closedPath_smoothMode_closesWithoutDuplicateSeam() {
        List<Vec3d> corners = squareCorners();
        List<Vec3d> result = PreviewCurveSmoothing.buildRenderPoints(corners, true, true);

        assertTrue(result.size() > corners.size());
        // The loop must close back onto the first point.
        assertTrue(result.getFirst().distanceTo(result.getLast()) < 1.0e-6d,
            "Smoothed closed path must end at the first point");
    }

    @Test
    void twoPointPath_isUnchangedBySmoothing() {
        List<Vec3d> line = List.of(new Vec3d(0.0d, 0.0d, 0.0d), new Vec3d(1.0d, 0.0d, 0.0d));
        List<Vec3d> result = PreviewCurveSmoothing.buildRenderPoints(line, false, true);

        assertEquals(2, result.size());
        assertEquals(line.getFirst(), result.getFirst());
        assertEquals(line.getLast(), result.getLast());
    }

    @Test
    void emptyInput_returnsEmptyList() {
        assertTrue(PreviewCurveSmoothing.buildRenderPoints(List.of(), false, true).isEmpty());
    }

    private static boolean containsNear(List<Vec3d> points, Vec3d target, double tolerance) {
        for (Vec3d p : points) {
            if (p.distanceTo(target) <= tolerance) {
                return true;
            }
        }
        return false;
    }
}

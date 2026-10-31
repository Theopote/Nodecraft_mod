package com.nodecraft.nodesystem.preview.gizmo;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the transform gizmo drag model.
 *
 * <p>Two properties matter for a stable world-space drag:
 * <ul>
 *   <li>the drag must be measured against a frozen press-time baseline, so the reported
 *       displacement is independent of how far the cursor has already travelled; and</li>
 *   <li>rotation steps must be seam-aware, so crossing the +/-180 degree seam does not
 *       produce a ~360 degree spin.</li>
 * </ul>
 */
class GizmoDragBaselineTest {

    private static final double EPSILON = 1.0e-6d;

    /**
     * The camera-facing drag plane contains the drag axis, so translating the plane along that
     * axis does not change the ray/plane intersection. The drag therefore stays stable even
     * though the gizmo origin moves while the node re-executes.
     */
    @Test
    void translationIsInvariantToOriginSlidingAlongDragAxis() {
        Vec3d axis = new Vec3d(1.0d, 0.0d, 0.0d);
        // Camera-facing plane normal +Y => the plane contains the X drag axis.
        Vec3d planeNormal = new Vec3d(0.0d, 1.0d, 0.0d);
        Vec3d frozenPlanePoint = new Vec3d(0.0d, 64.0d, 0.0d);
        Vec3d dragStartPoint = new Vec3d(0.0d, 64.0d, 0.0d);

        // Cursor 4 blocks along +X, ray points down -Y onto the plane.
        Vec3d current = GizmoMath.intersectRayWithPlane(
            new Vec3d(4.0d, 74.0d, 0.0d),
            new Vec3d(0.0d, -1.0d, 0.0d),
            frozenPlanePoint,
            planeNormal
        );
        assertNotNull(current);
        double projected = current.subtract(dragStartPoint).dotProduct(axis.normalize());
        assertEquals(4.0d, projected, EPSILON);

        // The origin slides 10 blocks along the drag axis (node re-executed mid-drag).
        Vec3d movedPlanePoint = new Vec3d(10.0d, 64.0d, 0.0d);
        assertTrue(movedPlanePoint.distanceTo(frozenPlanePoint) > 1.0d);

        Vec3d currentAfterMove = GizmoMath.intersectRayWithPlane(
            new Vec3d(4.0d, 74.0d, 0.0d),
            new Vec3d(0.0d, -1.0d, 0.0d),
            movedPlanePoint,
            planeNormal
        );
        assertNotNull(currentAfterMove);
        double projectedAfterMove = currentAfterMove.subtract(dragStartPoint).dotProduct(axis.normalize());

        assertEquals(projected, projectedAfterMove, EPSILON);
    }

    /**
     * A drag measured from a frozen baseline reports the total displacement since the press,
     * independent of how many frames it took to get there.
     */
    @Test
    void frozenBaselineReportsTotalDisplacement() {
        Vec3d axis = new Vec3d(1.0d, 0.0d, 0.0d);
        Vec3d planeNormal = new Vec3d(0.0d, 1.0d, 0.0d);
        Vec3d planePoint = new Vec3d(0.0d, 64.0d, 0.0d);
        Vec3d dragStartPoint = new Vec3d(0.0d, 64.0d, 0.0d);

        double total = 0.0d;
        for (double cursorX : new double[] {1.0d, 2.0d, 3.0d, 4.0d}) {
            Vec3d hit = GizmoMath.intersectRayWithPlane(
                new Vec3d(cursorX, 74.0d, 0.0d),
                new Vec3d(0.0d, -1.0d, 0.0d),
                planePoint,
                planeNormal
            );
            assertNotNull(hit);
            double absolute = hit.subtract(dragStartPoint).dotProduct(axis.normalize());
            // Absolute displacement is measured from the frozen baseline, so it grows
            // monotonically with the cursor and never depends on the previous frame.
            assertTrue(absolute > total - EPSILON);
            total = absolute;
        }
        assertEquals(4.0d, total, EPSILON);
    }

    /**
     * Crossing the +/-180 degree seam must produce a small step, not a ~360 degree spin.
     */
    @Test
    void rotationStepIsSeamSafe() {
        // Angle jumps from +179 to -179 across the seam.
        double step = GizmoMath.normalizeDegrees(-179.0d - 179.0d);
        assertEquals(2.0d, step, EPSILON);
    }

    @Test
    void rotationStepHandlesFullTurn() {
        assertEquals(-1.0d, GizmoMath.normalizeDegrees(359.0d), EPSILON);
        assertEquals(1.0d, GizmoMath.normalizeDegrees(-359.0d), EPSILON);
        assertEquals(0.0d, GizmoMath.normalizeDegrees(0.0d), EPSILON);
        assertEquals(180.0d, GizmoMath.normalizeDegrees(180.0d), EPSILON);
    }

    /** The seam-aware step must keep the accumulated total continuous. */
    @Test
    void accumulatedRotationStaysContinuousAcrossSeam() {
        double previous = 179.0d;
        double accumulated = 0.0d;
        for (double angle : new double[] {-179.0d, -178.0d, -177.0d}) {
            accumulated += GizmoMath.normalizeDegrees(angle - previous);
            previous = angle;
        }
        // +179 -> -179 -> -178 -> -177 is a continuous 4 degree sweep, not a 358 degree jump.
        assertEquals(4.0d, accumulated, EPSILON);
    }
}

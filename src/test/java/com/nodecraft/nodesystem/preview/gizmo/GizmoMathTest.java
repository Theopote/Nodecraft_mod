package com.nodecraft.nodesystem.preview.gizmo;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GizmoMathTest {

    @Test
    void rayCylinderHit_detectsAxisAlignedCylinder() {
        Vec3d origin = new Vec3d(0.0d, 64.0d, 0.0d);
        Vec3d direction = new Vec3d(0.0d, 0.0d, 1.0d);
        Vec3d rayStart = new Vec3d(0.0d, 64.0d, -10.0d);
        Vec3d rayDirection = new Vec3d(0.0d, 0.0d, 1.0d);

        GizmoMath.RayHit hit = GizmoMath.rayCylinderHit(rayStart, rayDirection, origin, direction, 4.0d, 0.25d);
        assertNotNull(hit);
        assertTrue(hit.distance() > 0.0d);
    }

    @Test
    void rayCylinderHit_missesWhenRayIsTooFarFromAxis() {
        Vec3d origin = new Vec3d(0.0d, 64.0d, 0.0d);
        Vec3d direction = new Vec3d(0.0d, 0.0d, 1.0d);
        Vec3d rayStart = new Vec3d(5.0d, 64.0d, -10.0d);
        Vec3d rayDirection = new Vec3d(0.0d, 0.0d, 1.0d);

        assertNull(GizmoMath.rayCylinderHit(rayStart, rayDirection, origin, direction, 4.0d, 0.25d));
    }
}

package com.nodecraft.nodesystem.preview.gizmo;

import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3d;
import org.joml.Vector3d;

/**
 * Builds local gizmo axes from Euler XYZ rotation in degrees.
 */
public final class GizmoOrientation {

    private GizmoOrientation() {
    }

    public static LocalAxes fromEulerDegrees(double rotationXDeg, double rotationYDeg, double rotationZDeg) {
        Matrix3d rotation = new Matrix3d().rotateXYZ(
            Math.toRadians(rotationXDeg),
            Math.toRadians(rotationYDeg),
            Math.toRadians(rotationZDeg)
        );
        Vector3d x = new Vector3d(1.0d, 0.0d, 0.0d);
        Vector3d y = new Vector3d(0.0d, 1.0d, 0.0d);
        Vector3d z = new Vector3d(0.0d, 0.0d, 1.0d);
        rotation.transform(x);
        rotation.transform(y);
        rotation.transform(z);
        return new LocalAxes(
            new Vec3d(x.x, x.y, x.z),
            new Vec3d(y.x, y.y, y.z),
            new Vec3d(z.x, z.y, z.z)
        );
    }

    public record LocalAxes(Vec3d xAxis, Vec3d yAxis, Vec3d zAxis) {
    }
}

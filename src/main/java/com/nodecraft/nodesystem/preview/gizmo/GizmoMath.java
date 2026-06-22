package com.nodecraft.nodesystem.preview.gizmo;

import net.minecraft.util.math.Vec3d;

/**
 * Math helpers for gizmo ray picking and constrained dragging.
 */
public final class GizmoMath {

    private GizmoMath() {
    }

    public record RayHit(double distance, Vec3d point) {
    }

    public static Vec3d normalizeOr(Vec3d vector, Vec3d fallback) {
        if (vector == null || vector.lengthSquared() < 1.0e-12d) {
            return fallback;
        }
        return vector.normalize();
    }

    public static double rayToSegmentDistance(Vec3d rayOrigin, Vec3d rayDirection, Vec3d segStart, Vec3d segEnd) {
        Vec3d direction = normalizeOr(rayDirection, new Vec3d(0.0d, 0.0d, -1.0d));
        Vec3d segment = segEnd.subtract(segStart);
        Vec3d w0 = rayOrigin.subtract(segStart);

        double a = direction.dotProduct(direction);
        double b = direction.dotProduct(segment);
        double c = segment.dotProduct(segment);
        double d = direction.dotProduct(w0);
        double e = segment.dotProduct(w0);
        double denominator = a * c - b * b;

        double sc;
        double tc;
        if (denominator < 1.0e-12d) {
            Vec3d segmentDir = segment.lengthSquared() < 1.0e-12d ? direction : segment.normalize();
            Vec3d offset = rayOrigin.subtract(segStart);
            Vec3d perpendicular = offset.subtract(segmentDir.multiply(offset.dotProduct(segmentDir)));
            return perpendicular.length();
        } else {
            sc = (b * e - c * d) / denominator;
            tc = (a * e - b * d) / denominator;
            sc = Math.max(0.0d, sc);
            tc = Math.max(0.0d, Math.min(1.0d, tc));

            Vec3d pointOnRay = rayOrigin.add(direction.multiply(sc));
            Vec3d pointOnSegment = segStart.add(segment.multiply(tc));
            return pointOnRay.distanceTo(pointOnSegment);
        }
    }

    public static RayHit rayCylinderHit(
        Vec3d rayOrigin,
        Vec3d rayDirection,
        Vec3d axisOrigin,
        Vec3d axisDirection,
        double length,
        double radius
    ) {
        Vec3d axis = normalizeOr(axisDirection, new Vec3d(0.0d, 1.0d, 0.0d));
        Vec3d direction = normalizeOr(rayDirection, new Vec3d(0.0d, 0.0d, -1.0d));
        Vec3d oc = rayOrigin.subtract(axisOrigin);

        double axisDotDir = axis.dotProduct(direction);
        Vec3d directionPerp = direction.subtract(axis.multiply(axisDotDir));
        Vec3d ocPerp = oc.subtract(axis.multiply(oc.dotProduct(axis)));

        double a = directionPerp.dotProduct(directionPerp);
        if (a < 1.0e-12d) {
            Vec3d axisEnd = axisOrigin.add(axis.multiply(length));
            double distance = rayToSegmentDistance(rayOrigin, rayDirection, axisOrigin, axisEnd);
            if (distance > radius) {
                return null;
            }
            double t = axisOrigin.subtract(rayOrigin).dotProduct(direction);
            if (t < 0.0d) {
                return null;
            }
            return new RayHit(t, rayOrigin.add(direction.multiply(t)));
        }

        double b = 2.0d * ocPerp.dotProduct(directionPerp);
        double c = ocPerp.dotProduct(ocPerp) - radius * radius;
        double discriminant = b * b - 4.0d * a * c;
        if (discriminant < 0.0d) {
            return null;
        }

        double sqrt = Math.sqrt(discriminant);
        double t1 = (-b - sqrt) / (2.0d * a);
        double t2 = (-b + sqrt) / (2.0d * a);
        double t = t1 >= 0.0d ? t1 : (t2 >= 0.0d ? t2 : -1.0d);
        if (t < 0.0d) {
            return null;
        }

        Vec3d hitPoint = rayOrigin.add(direction.multiply(t));
        double axisProjection = hitPoint.subtract(axisOrigin).dotProduct(axis);
        if (axisProjection < 0.0d || axisProjection > length) {
            return null;
        }

        return new RayHit(t, hitPoint);
    }

    public static RayHit rayRingHit(
        Vec3d rayOrigin,
        Vec3d rayDirection,
        Vec3d center,
        Vec3d ringNormal,
        double radius,
        double thickness
    ) {
        Vec3d normal = normalizeOr(ringNormal, new Vec3d(0.0d, 1.0d, 0.0d));
        Vec3d direction = normalizeOr(rayDirection, new Vec3d(0.0d, 0.0d, -1.0d));
        double denominator = normal.dotProduct(direction);
        if (Math.abs(denominator) < 1.0e-8d) {
            return null;
        }

        double t = center.subtract(rayOrigin).dotProduct(normal) / denominator;
        if (t < 0.0d) {
            return null;
        }

        Vec3d hitPoint = rayOrigin.add(direction.multiply(t));
        double radialDistance = hitPoint.subtract(center).subtract(normal.multiply(hitPoint.subtract(center).dotProduct(normal))).length();
        if (Math.abs(radialDistance - radius) > thickness) {
            return null;
        }

        return new RayHit(t, hitPoint);
    }

    public static Vec3d intersectRayWithPlane(Vec3d rayOrigin, Vec3d rayDirection, Vec3d planePoint, Vec3d planeNormal) {
        Vec3d direction = normalizeOr(rayDirection, new Vec3d(0.0d, 0.0d, -1.0d));
        Vec3d normal = normalizeOr(planeNormal, new Vec3d(0.0d, 1.0d, 0.0d));
        double denominator = normal.dotProduct(direction);
        if (Math.abs(denominator) < 1.0e-8d) {
            return null;
        }
        double t = planePoint.subtract(rayOrigin).dotProduct(normal) / denominator;
        if (t < 0.0d) {
            return null;
        }
        return rayOrigin.add(direction.multiply(t));
    }

    public static Vec3d cameraFacingPlaneNormal(Vec3d axisDirection, Vec3d cameraDirection) {
        Vec3d axis = normalizeOr(axisDirection, new Vec3d(0.0d, 1.0d, 0.0d));
        Vec3d camera = normalizeOr(cameraDirection, new Vec3d(0.0d, 0.0d, -1.0d));
        Vec3d normal = camera.crossProduct(axis);
        if (normal.lengthSquared() < 1.0e-8d) {
            normal = axis.crossProduct(new Vec3d(0.0d, 1.0d, 0.0d));
        }
        if (normal.lengthSquared() < 1.0e-8d) {
            normal = new Vec3d(0.0d, 0.0d, 1.0d);
        }
        return normal.normalize();
    }

    public static double signedAngleOnPlane(Vec3d center, Vec3d planeNormal, Vec3d reference, Vec3d point) {
        Vec3d normal = normalizeOr(planeNormal, new Vec3d(0.0d, 1.0d, 0.0d));
        Vec3d ref = reference.subtract(center);
        ref = ref.subtract(normal.multiply(ref.dotProduct(normal)));
        Vec3d target = point.subtract(center);
        target = target.subtract(normal.multiply(target.dotProduct(normal)));
        if (ref.lengthSquared() < 1.0e-8d || target.lengthSquared() < 1.0e-8d) {
            return 0.0d;
        }
        ref = ref.normalize();
        target = target.normalize();
        double sin = normal.dotProduct(ref.crossProduct(target));
        double cos = ref.dotProduct(target);
        return Math.toDegrees(Math.atan2(sin, cos));
    }
}

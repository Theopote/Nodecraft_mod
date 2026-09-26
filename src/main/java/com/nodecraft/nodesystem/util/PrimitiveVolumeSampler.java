package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.ConeGeometryData;
import com.nodecraft.nodesystem.datatypes.CylinderGeometryData;
import com.nodecraft.nodesystem.datatypes.EllipsoidGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.HemisphereGeometryData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.datatypes.SphereData;
import com.nodecraft.nodesystem.datatypes.TorusGeometryData;
import org.joml.Matrix3d;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Continuous volume sampling for supported primitive geometry types.
 */
public final class PrimitiveVolumeSampler {

    private static final int MAX_REJECTION_ATTEMPTS = 256;

    private PrimitiveVolumeSampler() {
    }

    public static boolean isSupported(GeometryData geometry) {
        return PrimitiveGeometrySurfaceSampler.isSupported(geometry);
    }

    public static List<Vector3d> scatter(
        GeometryData geometry,
        int targetCount,
        int seed,
        double minDistance,
        MinDistanceScatterSelector.DistributionMode mode
    ) {
        if (!isSupported(geometry) || targetCount <= 0) {
            return List.of();
        }

        Random random = new Random(seed);
        int candidateBudget = Math.max(targetCount * MAX_REJECTION_ATTEMPTS, targetCount);
        List<Vector3d> candidates = new ArrayList<>(candidateBudget);

        for (int attempt = 0; attempt < candidateBudget && candidates.size() < targetCount * 8; attempt++) {
            Vector3d sample = sampleRandomInteriorPoint(geometry, random);
            if (sample != null) {
                candidates.add(sample);
            }
        }

        return MinDistanceScatterSelector.select(candidates, targetCount, minDistance, mode, random);
    }

    private static Vector3d sampleRandomInteriorPoint(GeometryData geometry, Random random) {
        if (geometry instanceof SphereData sphere) {
            Vector3d direction = SphereSurfaceSampling.sampleRandomUnitNormal(random);
            double r = sphere.getRadius() * Math.cbrt(random.nextDouble());
            return new Vector3d(direction).mul(r).add(sphere.getCenter());
        }
        if (geometry instanceof BoxGeometryData box) {
            return sampleBoxInterior(box, random);
        }
        if (geometry instanceof CylinderGeometryData cylinder) {
            return sampleCylinderInterior(cylinder, random);
        }
        if (geometry instanceof TorusGeometryData torus) {
            return sampleTorusInterior(torus, random);
        }
        if (geometry instanceof ConeGeometryData cone) {
            return sampleConeInterior(cone, random);
        }
        if (geometry instanceof EllipsoidGeometryData ellipsoid) {
            return sampleEllipsoidInterior(ellipsoid, random);
        }
        if (geometry instanceof HemisphereGeometryData hemisphere) {
            return sampleHemisphereInterior(hemisphere, random);
        }
        return null;
    }

    private static Vector3d sampleBoxInterior(BoxGeometryData box, Random random) {
        Vector3d half = box.getHalfExtents();
        Vector3d local = new Vector3d(
            (random.nextDouble() * 2.0d - 1.0d) * half.x,
            (random.nextDouble() * 2.0d - 1.0d) * half.y,
            (random.nextDouble() * 2.0d - 1.0d) * half.z
        );
        Matrix3d orientation = box.getOrientationMatrix();
        orientation.transform(local);
        local.add(box.getCenter());
        return local;
    }

    private static Vector3d sampleCylinderInterior(CylinderGeometryData cylinder, Random random) {
        Vector3d start = cylinder.getStart();
        Vector3d end = cylinder.getEnd();
        Vector3d axis = new Vector3d(end).sub(start);
        double height = axis.length();
        if (height <= 1.0e-12d) {
            return new Vector3d(start);
        }
        axis.div(height);
        double t = random.nextDouble();
        double r = cylinder.getRadius() * Math.sqrt(random.nextDouble());
        Vector3d tangent = orthonormalTangent(axis, random);
        Vector3d bitangent = new Vector3d(axis).cross(tangent).normalize();
        double angle = random.nextDouble() * Math.PI * 2.0d;
        Vector3d radial = new Vector3d(tangent).mul(Math.cos(angle)).add(new Vector3d(bitangent).mul(Math.sin(angle)));
        return new Vector3d(start).add(new Vector3d(axis).mul(height * t)).add(radial.mul(r));
    }

    private static Vector3d sampleTorusInterior(TorusGeometryData torus, Random random) {
        RegionData region = GeometryVoxelizer.createBoundingRegion(torus);
        if (region == null || !region.isComplete()) {
            return null;
        }
        Vector3d min = vectorFromBlockPos(Objects.requireNonNull(region.getMinCorner()));
        Vector3d max = vectorFromBlockPos(Objects.requireNonNull(region.getMaxCorner()));
        for (int attempt = 0; attempt < MAX_REJECTION_ATTEMPTS; attempt++) {
            Vector3d candidate = new Vector3d(
                min.x + random.nextDouble() * (max.x - min.x),
                min.y + random.nextDouble() * (max.y - min.y),
                min.z + random.nextDouble() * (max.z - min.z)
            );
            if (containsPoint(torus, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static Vector3d sampleConeInterior(ConeGeometryData cone, Random random) {
        Vector3d apex = cone.getApex();
        Vector3d baseCenter = cone.getBaseCenter();
        Vector3d axis = new Vector3d(baseCenter).sub(apex);
        double height = axis.length();
        if (height <= 1.0e-12d) {
            return new Vector3d(apex);
        }
        axis.div(height);
        double t = random.nextDouble();
        double r = cone.getBaseRadius() * t * Math.sqrt(random.nextDouble());
        Vector3d tangent = orthonormalTangent(axis, random);
        Vector3d bitangent = new Vector3d(axis).cross(tangent).normalize();
        double angle = random.nextDouble() * Math.PI * 2.0d;
        Vector3d radial = new Vector3d(tangent).mul(Math.cos(angle)).add(new Vector3d(bitangent).mul(Math.sin(angle)));
        return new Vector3d(apex).add(new Vector3d(axis).mul(height * t)).add(radial.mul(r));
    }

    private static Vector3d sampleEllipsoidInterior(EllipsoidGeometryData ellipsoid, Random random) {
        Vector3d direction = SphereSurfaceSampling.sampleRandomUnitNormal(random);
        double r = Math.cbrt(random.nextDouble());
        Vector3d radii = ellipsoid.getRadii();
        Vector3d local = new Vector3d(direction.x * radii.x * r, direction.y * radii.y * r, direction.z * radii.z * r);
        Matrix3d orientation = ellipsoid.getOrientationMatrix();
        orientation.transform(local);
        local.add(ellipsoid.getCenter());
        return local;
    }

    private static Vector3d sampleHemisphereInterior(HemisphereGeometryData hemisphere, Random random) {
        for (int attempt = 0; attempt < MAX_REJECTION_ATTEMPTS; attempt++) {
            Vector3d direction = SphereSurfaceSampling.sampleRandomUnitNormal(random);
            double r = hemisphere.getRadius() * Math.cbrt(random.nextDouble());
            Vector3d point = new Vector3d(direction).mul(r).add(hemisphere.getCenter());
            if (containsPoint(hemisphere, point)) {
                return point;
            }
        }
        return null;
    }

    public static boolean containsPoint(GeometryData geometry, Vector3d point) {
        if (geometry instanceof SphereData sphere) {
            return point.distanceSquared(sphere.getCenter()) <= sphere.getRadius() * sphere.getRadius() + 1.0e-9d;
        }
        if (geometry instanceof BoxGeometryData box) {
            return containsBoxPoint(box, point);
        }
        if (geometry instanceof CylinderGeometryData cylinder) {
            return containsCylinderPoint(cylinder, point);
        }
        if (geometry instanceof TorusGeometryData torus) {
            return containsTorusPoint(torus, point);
        }
        if (geometry instanceof ConeGeometryData cone) {
            return containsConePoint(cone, point);
        }
        if (geometry instanceof EllipsoidGeometryData ellipsoid) {
            return containsEllipsoidPoint(ellipsoid, point);
        }
        if (geometry instanceof HemisphereGeometryData hemisphere) {
            Vector3d offset = new Vector3d(point).sub(hemisphere.getCenter());
            if (offset.lengthSquared() > hemisphere.getRadius() * hemisphere.getRadius() + 1.0e-9d) {
                return false;
            }
            return offset.dot(hemisphere.getAxis()) >= 0.0d;
        }
        return false;
    }

    private static boolean containsBoxPoint(BoxGeometryData box, Vector3d point) {
        Vector3d local = new Vector3d(point).sub(box.getCenter());
        Matrix3d inverse = new Matrix3d(box.getOrientationMatrix()).invert();
        inverse.transform(local);
        Vector3d half = box.getHalfExtents();
        return Math.abs(local.x) <= half.x + 1.0e-9d
            && Math.abs(local.y) <= half.y + 1.0e-9d
            && Math.abs(local.z) <= half.z + 1.0e-9d;
    }

    private static boolean containsCylinderPoint(CylinderGeometryData cylinder, Vector3d point) {
        Vector3d start = cylinder.getStart();
        Vector3d axis = new Vector3d(cylinder.getEnd()).sub(start);
        double height = axis.length();
        if (height <= 1.0e-12d) {
            return point.distanceSquared(start) <= cylinder.getRadius() * cylinder.getRadius() + 1.0e-9d;
        }
        axis.div(height);
        Vector3d offset = new Vector3d(point).sub(start);
        double along = offset.dot(axis);
        if (along < 0.0d || along > height) {
            return false;
        }
        Vector3d radial = new Vector3d(offset).sub(new Vector3d(axis).mul(along));
        return radial.lengthSquared() <= cylinder.getRadius() * cylinder.getRadius() + 1.0e-9d;
    }

    private static boolean containsTorusPoint(TorusGeometryData torus, Vector3d point) {
        Vector3d axis = torus.getAxis();
        Vector3d offset = new Vector3d(point).sub(torus.getCenter());
        Vector3d axial = new Vector3d(axis).mul(offset.dot(axis));
        Vector3d radial = new Vector3d(offset).sub(axial);
        double ringDistance = radial.length();
        double tubeDistance = axial.length();
        double major = torus.getMajorRadius();
        double minor = torus.getMinorRadius();
        double dx = ringDistance - major;
        return (dx * dx + tubeDistance * tubeDistance) <= minor * minor + 1.0e-9d;
    }

    private static boolean containsConePoint(ConeGeometryData cone, Vector3d point) {
        Vector3d apex = cone.getApex();
        Vector3d axis = new Vector3d(cone.getBaseCenter()).sub(apex);
        double height = axis.length();
        if (height <= 1.0e-12d) {
            return point.distanceSquared(apex) <= 1.0e-9d;
        }
        axis.div(height);
        Vector3d offset = new Vector3d(point).sub(apex);
        double along = offset.dot(axis);
        if (along < 0.0d || along > height) {
            return false;
        }
        Vector3d radial = new Vector3d(offset).sub(new Vector3d(axis).mul(along));
        double allowedRadius = cone.getBaseRadius() * (along / height);
        return radial.lengthSquared() <= allowedRadius * allowedRadius + 1.0e-9d;
    }

    private static boolean containsEllipsoidPoint(EllipsoidGeometryData ellipsoid, Vector3d point) {
        Vector3d local = new Vector3d(point).sub(ellipsoid.getCenter());
        Matrix3d inverse = new Matrix3d(ellipsoid.getOrientationMatrix()).invert();
        inverse.transform(local);
        Vector3d radii = ellipsoid.getRadii();
        double nx = local.x / Math.max(radii.x, 1.0e-9d);
        double ny = local.y / Math.max(radii.y, 1.0e-9d);
        double nz = local.z / Math.max(radii.z, 1.0e-9d);
        return (nx * nx + ny * ny + nz * nz) <= 1.0d + 1.0e-9d;
    }

    private static Vector3d orthonormalTangent(Vector3d axis, Random random) {
        Vector3d reference = Math.abs(axis.y) < 0.9d ? new Vector3d(0.0d, 1.0d, 0.0d) : new Vector3d(1.0d, 0.0d, 0.0d);
        Vector3d tangent = new Vector3d(axis).cross(reference);
        if (tangent.lengthSquared() < 1.0e-12d) {
            tangent.set(random.nextDouble(), random.nextDouble(), random.nextDouble());
        }
        return tangent.normalize();
    }

    private static Vector3d vectorFromBlockPos(net.minecraft.util.math.BlockPos pos) {
        return new Vector3d(pos.getX(), pos.getY(), pos.getZ());
    }
}

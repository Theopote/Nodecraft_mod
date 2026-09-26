package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.ConeGeometryData;
import com.nodecraft.nodesystem.datatypes.CylinderGeometryData;
import com.nodecraft.nodesystem.datatypes.EllipsoidGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.HemisphereGeometryData;
import com.nodecraft.nodesystem.datatypes.SphereData;
import com.nodecraft.nodesystem.datatypes.TorusGeometryData;
import org.joml.Matrix3d;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Continuous analytic surface sampling for supported primitive geometry types.
 */
public final class PrimitiveGeometrySurfaceSampler {

    public record SurfaceSample(Vector3d point, Vector3d normal) {
    }

    private PrimitiveGeometrySurfaceSampler() {
    }

    public static boolean isSupported(GeometryData geometry) {
        return geometry instanceof SphereData
            || geometry instanceof BoxGeometryData
            || geometry instanceof CylinderGeometryData
            || geometry instanceof TorusGeometryData
            || geometry instanceof ConeGeometryData
            || geometry instanceof EllipsoidGeometryData
            || geometry instanceof HemisphereGeometryData;
    }

    public static List<SurfaceSample> scatter(
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
        int candidateCount = Math.max(targetCount * 32, targetCount);
        List<Vector3d> candidates = new ArrayList<>(candidateCount);
        List<Vector3d> candidateNormals = new ArrayList<>(candidateCount);

        for (int i = 0; i < candidateCount; i++) {
            SurfaceSample sample = sampleRandomSurfacePoint(geometry, random);
            if (sample == null) {
                return List.of();
            }
            candidates.add(sample.point());
            candidateNormals.add(sample.normal());
        }

        List<Vector3d> selectedPoints = MinDistanceScatterSelector.select(
            candidates,
            targetCount,
            minDistance,
            mode,
            random
        );

        List<SurfaceSample> result = new ArrayList<>(selectedPoints.size());
        for (Vector3d point : selectedPoints) {
            int index = indexOfPoint(candidates, point);
            Vector3d normal = index >= 0 ? candidateNormals.get(index) : new Vector3d(0.0d, 1.0d, 0.0d);
            result.add(new SurfaceSample(new Vector3d(point), new Vector3d(normal)));
        }
        return result;
    }

    private static int indexOfPoint(List<Vector3d> candidates, Vector3d point) {
        for (int i = 0; i < candidates.size(); i++) {
            if (candidates.get(i).distanceSquared(point) < 1.0e-12d) {
                return i;
            }
        }
        return -1;
    }

    private static SurfaceSample sampleRandomSurfacePoint(GeometryData geometry, Random random) {
        if (geometry instanceof SphereData sphere) {
            Vector3d normal = SphereSurfaceSampling.sampleRandomUnitNormal(random);
            Vector3d point = new Vector3d(normal).mul(sphere.radius()).add(sphere.center());
            return new SurfaceSample(point, normal);
        }
        if (geometry instanceof BoxGeometryData box) {
            return sampleBoxSurface(box, random);
        }
        if (geometry instanceof CylinderGeometryData cylinder) {
            return sampleCylinderSurface(cylinder, random);
        }
        if (geometry instanceof TorusGeometryData torus) {
            return sampleTorusSurface(torus, random);
        }
        if (geometry instanceof ConeGeometryData cone) {
            return sampleConeSurface(cone, random);
        }
        if (geometry instanceof EllipsoidGeometryData ellipsoid) {
            return sampleEllipsoidSurface(ellipsoid, random);
        }
        if (geometry instanceof HemisphereGeometryData hemisphere) {
            return sampleHemisphereSurface(hemisphere, random);
        }
        return null;
    }

    private static SurfaceSample sampleBoxSurface(BoxGeometryData box, Random random) {
        List<BoxFaceData> faces = box.getFaces();
        double totalArea = 0.0d;
        for (BoxFaceData face : faces) {
            totalArea += quadArea(face.getCorners());
        }
        if (totalArea <= 1.0e-12d) {
            return new SurfaceSample(new Vector3d(box.getCenter()), new Vector3d(0.0d, 1.0d, 0.0d));
        }

        double pick = random.nextDouble() * totalArea;
        double acc = 0.0d;
        for (BoxFaceData face : faces) {
            acc += quadArea(face.getCorners());
            if (pick <= acc) {
                List<Vector3d> corners = face.getCorners();
                Vector3d a = corners.get(0);
                Vector3d b = corners.get(1);
                Vector3d c = corners.get(2);
                Vector3d d = corners.get(3);
                Vector3d point = random.nextDouble() < 0.5d
                    ? sampleTriangle(a, b, c, random)
                    : sampleTriangle(b, d, c, random);
                return new SurfaceSample(point, new Vector3d(face.getNormal()));
            }
        }
        BoxFaceData last = faces.getLast();
        return new SurfaceSample(new Vector3d(last.getCenter()), new Vector3d(last.getNormal()));
    }

    private static SurfaceSample sampleCylinderSurface(CylinderGeometryData cylinder, Random random) {
        Vector3d start = cylinder.getStart();
        Vector3d end = cylinder.getEnd();
        Vector3d axis = new Vector3d(end).sub(start);
        double height = axis.length();
        if (height <= 1.0e-12d) {
            Vector3d normal = SphereSurfaceSampling.sampleRandomUnitNormal(random);
            return new SurfaceSample(new Vector3d(start).add(normal.mul(cylinder.getRadius())), normal);
        }
        axis.div(height);

        Vector3d tangent = orthonormalTangent(axis, random);
        Vector3d bitangent = new Vector3d(axis).cross(tangent).normalize();
        double angle = random.nextDouble() * Math.PI * 2.0d;
        Vector3d radial = new Vector3d(tangent).mul(Math.cos(angle)).add(new Vector3d(bitangent).mul(Math.sin(angle)));
        double t = random.nextDouble();
        Vector3d axisPoint = new Vector3d(start).add(new Vector3d(axis).mul(height * t));
        Vector3d point = new Vector3d(axisPoint).add(radial.mul(cylinder.getRadius()));
        return new SurfaceSample(point, new Vector3d(radial));
    }

    private static SurfaceSample sampleTorusSurface(TorusGeometryData torus, Random random) {
        Vector3d axis = new Vector3d(torus.axis()).normalize();
        Vector3d tangent = orthonormalTangent(axis, random);
        Vector3d bitangent = new Vector3d(axis).cross(tangent).normalize();

        double u = random.nextDouble() * Math.PI * 2.0d;
        double v = random.nextDouble() * Math.PI * 2.0d;
        double major = torus.majorRadius();
        double minor = torus.minorRadius();

        Vector3d ring = new Vector3d(tangent).mul(Math.cos(u)).add(new Vector3d(bitangent).mul(Math.sin(u)));
        Vector3d centerOnRing = new Vector3d(torus.center()).add(ring.mul(major));
        Vector3d normal = new Vector3d(ring).mul(Math.cos(v)).add(new Vector3d(axis).mul(Math.sin(v)));
        Vector3d point = new Vector3d(centerOnRing).add(normal.mul(minor));
        if (normal.lengthSquared() > 1.0e-12d) {
            normal.normalize();
        }
        return new SurfaceSample(point, normal);
    }

    private static SurfaceSample sampleConeSurface(ConeGeometryData cone, Random random) {
        Vector3d apex = cone.getApex();
        Vector3d baseCenter = cone.getBaseCenter();
        Vector3d axis = new Vector3d(baseCenter).sub(apex);
        double height = axis.length();
        if (height <= 1.0e-12d) {
            Vector3d normal = SphereSurfaceSampling.sampleRandomUnitNormal(random);
            return new SurfaceSample(new Vector3d(apex), normal);
        }
        axis.div(height);

        if (random.nextDouble() < 0.5d) {
            Vector3d tangent = orthonormalTangent(axis, random);
            Vector3d bitangent = new Vector3d(axis).cross(tangent).normalize();
            double angle = random.nextDouble() * Math.PI * 2.0d;
            Vector3d radial = new Vector3d(tangent).mul(Math.cos(angle)).add(new Vector3d(bitangent).mul(Math.sin(angle)));
            Vector3d point = new Vector3d(baseCenter).add(radial.mul(cone.getBaseRadius()));
            Vector3d slant = new Vector3d(point).sub(apex);
            Vector3d normal = new Vector3d(slant).cross(radial);
            if (normal.lengthSquared() > 1.0e-12d) {
                normal.normalize();
            } else {
                normal.set(radial);
            }
            return new SurfaceSample(point, normal);
        }

        double t = random.nextDouble();
        double r = cone.getBaseRadius() * (1.0d - t);
        Vector3d tangent = orthonormalTangent(axis, random);
        Vector3d bitangent = new Vector3d(axis).cross(tangent).normalize();
        double angle = random.nextDouble() * Math.PI * 2.0d;
        Vector3d radial = new Vector3d(tangent).mul(Math.cos(angle)).add(new Vector3d(bitangent).mul(Math.sin(angle)));
        Vector3d point = new Vector3d(apex).add(new Vector3d(axis).mul(height * t)).add(radial.mul(r));
        return new SurfaceSample(point, new Vector3d(radial));
    }

    private static SurfaceSample sampleEllipsoidSurface(EllipsoidGeometryData ellipsoid, Random random) {
        Vector3d normal = SphereSurfaceSampling.sampleRandomUnitNormal(random);
        Vector3d radii = ellipsoid.getRadii();
        Vector3d local = new Vector3d(
            normal.x / Math.max(radii.x, 1.0e-9d),
            normal.y / Math.max(radii.y, 1.0e-9d),
            normal.z / Math.max(radii.z, 1.0e-9d)
        );
        if (local.lengthSquared() > 1.0e-12d) {
            local.normalize();
        }
        Matrix3d orientation = ellipsoid.getOrientationMatrix();
        Vector3d orientedNormal = new Vector3d(local);
        orientation.transform(orientedNormal);
        if (orientedNormal.lengthSquared() > 1.0e-12d) {
            orientedNormal.normalize();
        }
        Vector3d scaled = new Vector3d(local.x * radii.x, local.y * radii.y, local.z * radii.z);
        orientation.transform(scaled);
        scaled.add(ellipsoid.getCenter());
        return new SurfaceSample(scaled, orientedNormal);
    }

    private static SurfaceSample sampleHemisphereSurface(HemisphereGeometryData hemisphere, Random random) {
        Vector3d normal = SphereSurfaceSampling.sampleRandomUnitNormal(random);
        Vector3d axis = new Vector3d(hemisphere.axis()).normalize();
        if (normal.dot(axis) < 0.0d) {
            normal.negate();
        }
        Vector3d point = new Vector3d(normal).mul(hemisphere.radius()).add(hemisphere.center());
        return new SurfaceSample(point, new Vector3d(normal));
    }

    private static Vector3d orthonormalTangent(Vector3d axis, Random random) {
        Vector3d reference = Math.abs(axis.y) < 0.9d ? new Vector3d(0.0d, 1.0d, 0.0d) : new Vector3d(1.0d, 0.0d, 0.0d);
        Vector3d tangent = new Vector3d(axis).cross(reference);
        if (tangent.lengthSquared() < 1.0e-12d) {
            tangent.set(random.nextDouble(), random.nextDouble(), random.nextDouble());
        }
        return tangent.normalize();
    }

    private static double quadArea(List<Vector3d> corners) {
        return triangleArea(corners.get(0), corners.get(1), corners.get(2))
            + triangleArea(corners.get(1), corners.get(3), corners.get(2));
    }

    private static Vector3d sampleTriangle(Vector3d a, Vector3d b, Vector3d c, Random random) {
        double u = random.nextDouble();
        double v = random.nextDouble();
        if (u + v > 1.0d) {
            u = 1.0d - u;
            v = 1.0d - v;
        }
        return new Vector3d(a)
            .add(new Vector3d(b).sub(a).mul(u))
            .add(new Vector3d(c).sub(a).mul(v));
    }

    private static double triangleArea(Vector3d a, Vector3d b, Vector3d c) {
        Vector3d ab = new Vector3d(b).sub(a);
        Vector3d ac = new Vector3d(c).sub(a);
        return ab.cross(ac).length() * 0.5d;
    }
}

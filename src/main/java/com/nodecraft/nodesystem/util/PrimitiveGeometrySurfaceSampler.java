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
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Continuous analytic surface sampling for supported primitive geometry types (Graph V82).
 * <p>
 * Cylinder/Cone sample the lateral surface only. Ellipsoid sampling is analytic continuous
 * (not area-uniform). Degenerate primitives fail closed — no world-axis repair.
 */
public final class PrimitiveGeometrySurfaceSampler {

    public record SurfaceSample(Vector3d point, Vector3d normal) {
    }

    private static final double EPS = 1.0e-12d;

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

    /**
     * @return null on invalid/degenerate geometry or sampler failure;
     *         otherwise selected samples (may be under-target / empty)
     */
    public static @Nullable List<SurfaceSample> scatter(
        GeometryData geometry,
        int targetCount,
        int seed,
        double minDistance,
        MinDistanceScatterSelector.DistributionMode mode
    ) {
        String validation = validatePrimitive(geometry);
        if (validation != null || targetCount <= 0) {
            return null;
        }
        String budgetError = GenerationLimits.validateScatterCandidateBudget(
            targetCount, GenerationLimits.SCATTER_CANDIDATES_PER_TARGET);
        if (budgetError != null) {
            return null;
        }

        Random random = new Random(seed);
        long candidateCountLong = (long) targetCount * GenerationLimits.SCATTER_CANDIDATES_PER_TARGET;
        int candidateCount = (int) Math.min(Math.max(candidateCountLong, targetCount), Integer.MAX_VALUE);
        List<Vector3d> candidates = new ArrayList<>(candidateCount);
        List<Vector3d> candidateNormals = new ArrayList<>(candidateCount);

        for (int i = 0; i < candidateCount; i++) {
            SurfaceSample sample = sampleRandomSurfacePoint(geometry, random);
            if (sample == null) {
                return null;
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
            if (index < 0) {
                return null;
            }
            Vector3d normal = SphereSurfaceSampling.normalizeStrict(candidateNormals.get(index));
            if (normal == null) {
                return null;
            }
            result.add(new SurfaceSample(new Vector3d(point), normal));
        }
        return result;
    }

    public static @Nullable String validatePrimitive(@Nullable GeometryData geometry) {
        if (geometry == null || !isSupported(geometry)) {
            return "Unsupported or missing surface geometry";
        }
        if (geometry instanceof SphereData sphere) {
            if (!PointUtils.isFinite(sphere.center()) || !Double.isFinite(sphere.radius()) || sphere.radius() <= 0.0d) {
                return "Sphere surface is degenerate";
            }
            return null;
        }
        if (geometry instanceof BoxGeometryData box) {
            List<BoxFaceData> faces = box.getFaces();
            double totalArea = 0.0d;
            for (BoxFaceData face : faces) {
                totalArea += quadArea(face.getCorners());
            }
            if (!PointUtils.isFinite(box.getCenter()) || totalArea <= EPS) {
                return "Box surface is degenerate";
            }
            return null;
        }
        if (geometry instanceof CylinderGeometryData cylinder) {
            Vector3d axis = new Vector3d(cylinder.getEnd()).sub(cylinder.getStart());
            if (!PointUtils.isFinite(cylinder.getStart()) || !PointUtils.isFinite(cylinder.getEnd())
                    || !Double.isFinite(cylinder.getRadius()) || cylinder.getRadius() <= 0.0d
                    || axis.lengthSquared() <= EPS) {
                return "Cylinder surface is degenerate";
            }
            return null;
        }
        if (geometry instanceof TorusGeometryData torus) {
            if (!PointUtils.isFinite(torus.center()) || !VectorUtils.isNonZero(torus.axis())
                    || !Double.isFinite(torus.majorRadius()) || !Double.isFinite(torus.minorRadius())
                    || torus.majorRadius() <= 0.0d || torus.minorRadius() <= 0.0d) {
                return "Torus surface is degenerate";
            }
            return null;
        }
        if (geometry instanceof ConeGeometryData cone) {
            Vector3d axis = new Vector3d(cone.getBaseCenter()).sub(cone.getApex());
            if (!PointUtils.isFinite(cone.getApex()) || !PointUtils.isFinite(cone.getBaseCenter())
                    || !Double.isFinite(cone.getBaseRadius()) || cone.getBaseRadius() <= 0.0d
                    || axis.lengthSquared() <= EPS) {
                return "Cone surface is degenerate";
            }
            return null;
        }
        if (geometry instanceof EllipsoidGeometryData ellipsoid) {
            Vector3d radii = ellipsoid.getRadii();
            if (!PointUtils.isFinite(ellipsoid.getCenter()) || !PointUtils.isFinite(radii)
                    || radii.x <= EPS || radii.y <= EPS || radii.z <= EPS) {
                return "Ellipsoid surface is degenerate";
            }
            return null;
        }
        if (geometry instanceof HemisphereGeometryData hemisphere) {
            if (!PointUtils.isFinite(hemisphere.center()) || !VectorUtils.isNonZero(hemisphere.axis())
                    || !Double.isFinite(hemisphere.radius()) || hemisphere.radius() <= 0.0d) {
                return "Hemisphere surface is degenerate";
            }
            return null;
        }
        return "Unsupported surface geometry";
    }

    private static int indexOfPoint(List<Vector3d> candidates, Vector3d point) {
        for (int i = 0; i < candidates.size(); i++) {
            if (candidates.get(i).distanceSquared(point) < EPS) {
                return i;
            }
        }
        return -1;
    }

    private static @Nullable SurfaceSample sampleRandomSurfacePoint(GeometryData geometry, Random random) {
        if (geometry instanceof SphereData sphere) {
            Vector3d normal = SphereSurfaceSampling.normalizeStrict(SphereSurfaceSampling.sampleRandomUnitNormal(random));
            if (normal == null) {
                return null;
            }
            Vector3d point = new Vector3d(normal).mul(sphere.radius()).add(sphere.center());
            return new SurfaceSample(point, normal);
        }
        if (geometry instanceof BoxGeometryData box) {
            return sampleBoxSurface(box, random);
        }
        if (geometry instanceof CylinderGeometryData cylinder) {
            return sampleCylinderLateralSurface(cylinder, random);
        }
        if (geometry instanceof TorusGeometryData torus) {
            return sampleTorusSurface(torus, random);
        }
        if (geometry instanceof ConeGeometryData cone) {
            return sampleConeLateralSurface(cone, random);
        }
        if (geometry instanceof EllipsoidGeometryData ellipsoid) {
            return sampleEllipsoidSurface(ellipsoid, random);
        }
        if (geometry instanceof HemisphereGeometryData hemisphere) {
            return sampleHemisphereSurface(hemisphere, random);
        }
        return null;
    }

    private static @Nullable SurfaceSample sampleBoxSurface(BoxGeometryData box, Random random) {
        List<BoxFaceData> faces = box.getFaces();
        double totalArea = 0.0d;
        for (BoxFaceData face : faces) {
            totalArea += quadArea(face.getCorners());
        }
        if (totalArea <= EPS) {
            return null;
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
                Vector3d normal = SphereSurfaceSampling.normalizeStrict(face.getNormal());
                if (normal == null) {
                    return null;
                }
                return new SurfaceSample(point, normal);
            }
        }
        BoxFaceData last = faces.getLast();
        Vector3d normal = SphereSurfaceSampling.normalizeStrict(last.getNormal());
        if (normal == null) {
            return null;
        }
        return new SurfaceSample(new Vector3d(last.getCenter()), normal);
    }

    /** Lateral surface only (no caps). */
    private static @Nullable SurfaceSample sampleCylinderLateralSurface(CylinderGeometryData cylinder, Random random) {
        Vector3d start = cylinder.getStart();
        Vector3d end = cylinder.getEnd();
        Vector3d axis = new Vector3d(end).sub(start);
        double height = axis.length();
        if (height <= EPS) {
            return null;
        }
        axis.div(height);

        Vector3d tangent = orthonormalTangent(axis, random);
        if (tangent == null) {
            return null;
        }
        Vector3d bitangent = new Vector3d(axis).cross(tangent);
        if (bitangent.lengthSquared() <= EPS) {
            return null;
        }
        bitangent.normalize();
        double angle = random.nextDouble() * Math.PI * 2.0d;
        Vector3d radial = new Vector3d(tangent).mul(Math.cos(angle)).add(new Vector3d(bitangent).mul(Math.sin(angle)));
        Vector3d normal = SphereSurfaceSampling.normalizeStrict(radial);
        if (normal == null) {
            return null;
        }
        double t = random.nextDouble();
        Vector3d axisPoint = new Vector3d(start).add(new Vector3d(axis).mul(height * t));
        Vector3d point = new Vector3d(axisPoint).add(new Vector3d(normal).mul(cylinder.getRadius()));
        return new SurfaceSample(point, normal);
    }

    private static @Nullable SurfaceSample sampleTorusSurface(TorusGeometryData torus, Random random) {
        Vector3d axis = SphereSurfaceSampling.normalizeStrict(torus.axis());
        if (axis == null) {
            return null;
        }
        Vector3d tangent = orthonormalTangent(axis, random);
        if (tangent == null) {
            return null;
        }
        Vector3d bitangent = new Vector3d(axis).cross(tangent);
        if (bitangent.lengthSquared() <= EPS) {
            return null;
        }
        bitangent.normalize();

        double u = random.nextDouble() * Math.PI * 2.0d;
        double v = random.nextDouble() * Math.PI * 2.0d;
        double major = torus.majorRadius();
        double minor = torus.minorRadius();

        Vector3d ring = new Vector3d(tangent).mul(Math.cos(u)).add(new Vector3d(bitangent).mul(Math.sin(u)));
        Vector3d centerOnRing = new Vector3d(torus.center()).add(new Vector3d(ring).mul(major));
        Vector3d normal = new Vector3d(ring).mul(Math.cos(v)).add(new Vector3d(axis).mul(Math.sin(v)));
        Vector3d unitNormal = SphereSurfaceSampling.normalizeStrict(normal);
        if (unitNormal == null) {
            return null;
        }
        Vector3d point = new Vector3d(centerOnRing).add(new Vector3d(unitNormal).mul(minor));
        return new SurfaceSample(point, unitNormal);
    }

    /** Lateral surface only (no base disk). Analytic outward normal. */
    private static @Nullable SurfaceSample sampleConeLateralSurface(ConeGeometryData cone, Random random) {
        Vector3d apex = cone.getApex();
        Vector3d baseCenter = cone.getBaseCenter();
        Vector3d axis = new Vector3d(baseCenter).sub(apex);
        double height = axis.length();
        if (height <= EPS) {
            return null;
        }
        axis.div(height);

        double t = random.nextDouble();
        double r = cone.getBaseRadius() * t;
        Vector3d tangent = orthonormalTangent(axis, random);
        if (tangent == null) {
            return null;
        }
        Vector3d bitangent = new Vector3d(axis).cross(tangent);
        if (bitangent.lengthSquared() <= EPS) {
            return null;
        }
        bitangent.normalize();
        double angle = random.nextDouble() * Math.PI * 2.0d;
        Vector3d radial = new Vector3d(tangent).mul(Math.cos(angle)).add(new Vector3d(bitangent).mul(Math.sin(angle)));
        Vector3d point = new Vector3d(apex).add(new Vector3d(axis).mul(height * t)).add(new Vector3d(radial).mul(r));

        // Outward lateral normal: radial * H + axis * R
        Vector3d normal = new Vector3d(radial).mul(height).add(new Vector3d(axis).mul(cone.getBaseRadius()));
        Vector3d unitNormal = SphereSurfaceSampling.normalizeStrict(normal);
        if (unitNormal == null) {
            return null;
        }
        return new SurfaceSample(point, unitNormal);
    }

    private static @Nullable SurfaceSample sampleEllipsoidSurface(EllipsoidGeometryData ellipsoid, Random random) {
        Vector3d normal = SphereSurfaceSampling.normalizeStrict(SphereSurfaceSampling.sampleRandomUnitNormal(random));
        if (normal == null) {
            return null;
        }
        Vector3d radii = ellipsoid.getRadii();
        Vector3d local = new Vector3d(
            normal.x / Math.max(radii.x, 1.0e-9d),
            normal.y / Math.max(radii.y, 1.0e-9d),
            normal.z / Math.max(radii.z, 1.0e-9d)
        );
        Vector3d unitLocal = SphereSurfaceSampling.normalizeStrict(local);
        if (unitLocal == null) {
            return null;
        }
        Matrix3d orientation = ellipsoid.getOrientationMatrix();
        Vector3d orientedNormal = new Vector3d(unitLocal);
        orientation.transform(orientedNormal);
        Vector3d unitOriented = SphereSurfaceSampling.normalizeStrict(orientedNormal);
        if (unitOriented == null) {
            return null;
        }
        Vector3d scaled = new Vector3d(unitLocal.x * radii.x, unitLocal.y * radii.y, unitLocal.z * radii.z);
        orientation.transform(scaled);
        scaled.add(ellipsoid.getCenter());
        return new SurfaceSample(scaled, unitOriented);
    }

    private static @Nullable SurfaceSample sampleHemisphereSurface(HemisphereGeometryData hemisphere, Random random) {
        Vector3d normal = SphereSurfaceSampling.normalizeStrict(SphereSurfaceSampling.sampleRandomUnitNormal(random));
        Vector3d axis = SphereSurfaceSampling.normalizeStrict(hemisphere.axis());
        if (normal == null || axis == null) {
            return null;
        }
        if (normal.dot(axis) < 0.0d) {
            normal.negate();
        }
        Vector3d point = new Vector3d(normal).mul(hemisphere.radius()).add(hemisphere.center());
        return new SurfaceSample(point, new Vector3d(normal));
    }

    private static @Nullable Vector3d orthonormalTangent(Vector3d axis, Random random) {
        Vector3d reference = Math.abs(axis.y) < 0.9d ? new Vector3d(0.0d, 1.0d, 0.0d) : new Vector3d(1.0d, 0.0d, 0.0d);
        Vector3d tangent = new Vector3d(axis).cross(reference);
        if (tangent.lengthSquared() < EPS) {
            tangent.set(random.nextDouble(), random.nextDouble(), random.nextDouble());
            tangent.sub(new Vector3d(axis).mul(tangent.dot(axis)));
        }
        return SphereSurfaceSampling.normalizeStrict(tangent);
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

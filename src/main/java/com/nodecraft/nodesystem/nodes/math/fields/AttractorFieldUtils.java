package com.nodecraft.nodesystem.nodes.math.fields;

import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.SdfGeometryData;
import com.nodecraft.nodesystem.datatypes.SignedDistanceFieldData;
import com.nodecraft.nodesystem.datatypes.SphereData;
import com.nodecraft.nodesystem.util.Curve;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3d;
import org.joml.Vector3d;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

final class AttractorFieldUtils {
    static final double EPS = 1.0e-9d;

    enum FalloffMode {
        INVERSE,
        LINEAR,
        GAUSSIAN
    }

    private AttractorFieldUtils() {
    }

    static double falloff(double distance, double radius, double exponent, FalloffMode mode) {
        double d = Math.max(0.0d, distance);
        double r = Math.max(EPS, radius);
        double e = Math.max(0.001d, exponent);
        FalloffMode safeMode = mode == null ? FalloffMode.INVERSE : mode;
        return switch (safeMode) {
            case INVERSE -> {
                double x = d / r;
                yield 1.0d / (1.0d + Math.pow(x, e));
            }
            case LINEAR -> {
                double x = Math.max(0.0d, 1.0d - d / r);
                yield Math.pow(x, e);
            }
            case GAUSSIAN -> {
                double sigma = r / 3.0d;
                double x = d / Math.max(EPS, sigma);
                yield Math.exp(-0.5d * x * x);
            }
        };
    }

    static List<Vector3d> sampleCurvePolyline(Curve curve) {
        List<Vector3d> poly = new ArrayList<>();
        if (curve == null) {
            return poly;
        }
        for (Vec3d v : curve.getSamplePoints()) {
            poly.add(new Vector3d(v.x, v.y, v.z));
        }
        return poly;
    }

    static boolean tryExtractCenter(Object source, Vector3d dest) {
        if (source == null || dest == null) {
            return false;
        }
        if (source instanceof Vector3d center) {
            dest.set(center);
            return true;
        }
        if (source instanceof SphereData sphere) {
            dest.set(sphere.getCenter());
            return true;
        }
        if (source instanceof BoxGeometryData box) {
            dest.set(box.getCenter());
            return true;
        }
        if (source instanceof SdfGeometryData sdfGeometry) {
            Vector3d min = sdfGeometry.getMin();
            Vector3d max = sdfGeometry.getMax();
            dest.set(min).add(max).mul(0.5d);
            return true;
        }
        try {
            Method getCenter = source.getClass().getMethod("getCenter");
            Object result = getCenter.invoke(source);
            if (result instanceof Vector3d centerResult) {
                dest.set(centerResult);
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    static SignedDistanceFieldData tryExtractSdf(Object source) {
        if (source instanceof SignedDistanceFieldData sdf) {
            return sdf;
        }
        if (source instanceof SdfGeometryData sdfGeometry) {
            return sdfGeometry.getSdf();
        }
        return null;
    }

    static boolean vectorToSdfSurface(SignedDistanceFieldData sdf, Vector3d query, double step, Vector3d dest) {
        if (sdf == null || query == null || dest == null) {
            return false;
        }
        double h = Math.max(1.0e-4d, step);
        double d = sdf.sampleDistance(new Vector3d(query));
        double gx = sdf.sampleDistance(new Vector3d(query.x + h, query.y, query.z))
            - sdf.sampleDistance(new Vector3d(query.x - h, query.y, query.z));
        double gy = sdf.sampleDistance(new Vector3d(query.x, query.y + h, query.z))
            - sdf.sampleDistance(new Vector3d(query.x, query.y - h, query.z));
        double gz = sdf.sampleDistance(new Vector3d(query.x, query.y, query.z + h))
            - sdf.sampleDistance(new Vector3d(query.x, query.y, query.z - h));
        dest.set(gx, gy, gz);
        double lenSq = dest.lengthSquared();
        if (lenSq <= EPS) {
            dest.zero();
            return false;
        }
        dest.mul(1.0d / Math.sqrt(lenSq));
        dest.mul(-d);
        return true;
    }

    static boolean vectorToGeometrySurface(GeometryData geometry, Vector3d query, Vector3d dest) {
        if (geometry instanceof SphereData sphere) {
            return vectorToSphereSurface(sphere, query, dest);
        }
        if (geometry instanceof BoxGeometryData box) {
            return vectorToBoxSurface(box, query, dest);
        }
        return false;
    }

    private static boolean vectorToSphereSurface(SphereData sphere, Vector3d query, Vector3d dest) {
        Vector3d center = sphere.getCenter();
        Vector3d radial = new Vector3d(query).sub(center);
        double len = radial.length();
        if (len <= EPS) {
            radial.set(1.0d, 0.0d, 0.0d);
            len = 1.0d;
        }
        radial.mul(1.0d / len);
        Vector3d surface = new Vector3d(radial).mul(sphere.getRadius()).add(center);
        dest.set(surface).sub(query);
        return true;
    }

    private static boolean vectorToBoxSurface(BoxGeometryData box, Vector3d query, Vector3d dest) {
        Vector3d center = box.getCenter();
        Vector3d half = box.getHalfExtents();
        Matrix3d orientation = box.getOrientationMatrix();
        Matrix3d inverse = new Matrix3d(orientation);
        inverse.invert();

        Vector3d local = new Vector3d(query).sub(center);
        inverse.transform(local);

        boolean outside = Math.abs(local.x) > half.x || Math.abs(local.y) > half.y || Math.abs(local.z) > half.z;
        Vector3d closestLocal = new Vector3d(local);
        if (outside) {
            closestLocal.x = clamp(closestLocal.x, -half.x, half.x);
            closestLocal.y = clamp(closestLocal.y, -half.y, half.y);
            closestLocal.z = clamp(closestLocal.z, -half.z, half.z);
        } else {
            double dx = half.x - Math.abs(closestLocal.x);
            double dy = half.y - Math.abs(closestLocal.y);
            double dz = half.z - Math.abs(closestLocal.z);
            if (dx <= dy && dx <= dz) {
                closestLocal.x = Math.copySign(half.x, closestLocal.x == 0.0d ? 1.0d : closestLocal.x);
            } else if (dy <= dz) {
                closestLocal.y = Math.copySign(half.y, closestLocal.y == 0.0d ? 1.0d : closestLocal.y);
            } else {
                closestLocal.z = Math.copySign(half.z, closestLocal.z == 0.0d ? 1.0d : closestLocal.z);
            }
        }

        orientation.transform(closestLocal);
        closestLocal.add(center);
        dest.set(closestLocal).sub(query);
        return true;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}

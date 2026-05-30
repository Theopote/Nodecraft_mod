package com.nodecraft.nodesystem.nodes.geometry.curves.util;

import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public final class CurveMathUtils {

    private CurveMathUtils() {
    }

    public static double[] buildClampedUniformKnots(int knotCount, int degree, int n) {
        double[] knots = new double[knotCount];
        int last = knotCount - 1;
        int domainEnd = n - degree + 1;

        for (int i = 0; i < knotCount; i++) {
            if (i <= degree) {
                knots[i] = 0.0d;
            } else if (i >= n + 1) {
                knots[i] = domainEnd;
            } else {
                knots[i] = i - degree;
            }
        }

        knots[last] = domainEnd;
        return knots;
    }

    public static double basis(int i, int degree, double u, double[] knots) {
        if (degree == 0) {
            return (knots[i] <= u && u < knots[i + 1]) ? 1.0d : 0.0d;
        }

        double leftDenominator = knots[i + degree] - knots[i];
        double rightDenominator = knots[i + degree + 1] - knots[i + 1];

        double left = 0.0d;
        double right = 0.0d;

        if (leftDenominator > 0.0d) {
            left = ((u - knots[i]) / leftDenominator) * basis(i, degree - 1, u, knots);
        }
        if (rightDenominator > 0.0d) {
            right = ((knots[i + degree + 1] - u) / rightDenominator) * basis(i + 1, degree - 1, u, knots);
        }

        return left + right;
    }

    public static Vec3d evaluateBSpline(List<Vec3d> controlPoints, double[] knots, int degree, double u, int n) {
        if (u >= knots[n + 1]) {
            Vec3d end = controlPoints.get(n);
            return new Vec3d(end.x, end.y, end.z);
        }

        double x = 0.0d;
        double y = 0.0d;
        double z = 0.0d;

        for (int i = 0; i <= n; i++) {
            double basis = basis(i, degree, u, knots);
            if (basis == 0.0d) {
                continue;
            }
            Vec3d point = controlPoints.get(i);
            x += basis * point.x;
            y += basis * point.y;
            z += basis * point.z;
        }

        return new Vec3d(x, y, z);
    }

    public static Vec3d evaluateNurbs(List<Vec3d> controlPoints, List<Double> weights,
                                      double[] knots, int degree, double u, int n,
                                      double epsilon) {
        if (u >= knots[n + 1]) {
            Vec3d end = controlPoints.get(n);
            return new Vec3d(end.x, end.y, end.z);
        }

        double numeratorX = 0.0d;
        double numeratorY = 0.0d;
        double numeratorZ = 0.0d;
        double denominator = 0.0d;

        for (int i = 0; i <= n; i++) {
            double basis = basis(i, degree, u, knots);
            if (basis == 0.0d) {
                continue;
            }
            double weightedBasis = basis * weights.get(i);
            Vec3d point = controlPoints.get(i);
            numeratorX += weightedBasis * point.x;
            numeratorY += weightedBasis * point.y;
            numeratorZ += weightedBasis * point.z;
            denominator += weightedBasis;
        }

        if (denominator <= epsilon) {
            Vec3d fallback = controlPoints.getFirst();
            return new Vec3d(fallback.x, fallback.y, fallback.z);
        }

        return new Vec3d(numeratorX / denominator, numeratorY / denominator, numeratorZ / denominator);
    }

    public static List<Vec3d> sampleCatmullRom(List<Vec3d> points, int samplesPerSegment,
                                                double alpha, boolean closedPath,
                                                double epsilon) {
        List<Vec3d> sampled = new ArrayList<>();
        int count = points.size();
        int segmentCount = closedPath ? count : count - 1;

        for (int i = 0; i < segmentCount; i++) {
            Vec3d p0 = points.get(closedPath ? floorMod(i - 1, count) : Math.max(0, i - 1));
            Vec3d p1 = points.get(i);
            Vec3d p2 = points.get((i + 1) % count);
            Vec3d p3 = points.get(closedPath ? floorMod(i + 2, count) : Math.min(count - 1, i + 2));

            double t0 = 0.0d;
            double t1 = t0 + tj(t0, p0, p1, alpha);
            double t2 = t1 + tj(t1, p1, p2, alpha);
            double t3 = t2 + tj(t2, p2, p3, alpha);

            if (Math.abs(t1 - t0) <= epsilon || Math.abs(t2 - t1) <= epsilon || Math.abs(t3 - t2) <= epsilon) {
                appendLinearFallback(sampled, p1, p2, samplesPerSegment, i == 0);
                continue;
            }

            for (int j = 0; j <= samplesPerSegment; j++) {
                if (i > 0 && j == 0) {
                    continue;
                }
                double u = (double) j / (double) samplesPerSegment;
                double t = t1 + (t2 - t1) * u;
                sampled.add(interpolateCatmullRomPoint(p0, p1, p2, p3, t0, t1, t2, t3, t, epsilon));
            }
        }

        return sampled;
    }

    private static void appendLinearFallback(List<Vec3d> sampled, Vec3d p1, Vec3d p2,
                                             int samplesPerSegment, boolean includeStart) {
        for (int j = 0; j <= samplesPerSegment; j++) {
            if (!includeStart && j == 0) {
                continue;
            }
            double t = (double) j / (double) samplesPerSegment;
            sampled.add(lerp(p1, p2, t));
        }
    }

    private static Vec3d interpolateCatmullRomPoint(Vec3d p0, Vec3d p1, Vec3d p2, Vec3d p3,
                                                     double t0, double t1, double t2, double t3,
                                                     double t, double epsilon) {
        Vec3d a1 = blend(p0, p1, t0, t1, t, epsilon);
        Vec3d a2 = blend(p1, p2, t1, t2, t, epsilon);
        Vec3d a3 = blend(p2, p3, t2, t3, t, epsilon);

        Vec3d b1 = blend(a1, a2, t0, t2, t, epsilon);
        Vec3d b2 = blend(a2, a3, t1, t3, t, epsilon);

        return blend(b1, b2, t1, t2, t, epsilon);
    }

    private static Vec3d blend(Vec3d a, Vec3d b, double ta, double tb, double t, double epsilon) {
        if (Math.abs(tb - ta) <= epsilon) {
            return new Vec3d(a.x, a.y, a.z);
        }
        double w1 = (tb - t) / (tb - ta);
        double w2 = (t - ta) / (tb - ta);
        return new Vec3d(
            a.x * w1 + b.x * w2,
            a.y * w1 + b.y * w2,
            a.z * w1 + b.z * w2
        );
    }

    private static double tj(double ti, Vec3d pi, Vec3d pj, double alpha) {
        double dx = pj.x - pi.x;
        double dy = pj.y - pi.y;
        double dz = pj.z - pi.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return ti + Math.pow(distance, alpha);
    }

    private static Vec3d lerp(Vec3d start, Vec3d end, double t) {
        return new Vec3d(
            start.x + (end.x - start.x) * t,
            start.y + (end.y - start.y) * t,
            start.z + (end.z - start.z) * t
        );
    }

    private static int floorMod(int value, int modulus) {
        int result = value % modulus;
        return result < 0 ? result + modulus : result;
    }
}
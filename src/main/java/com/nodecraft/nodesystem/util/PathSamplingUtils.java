package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.nodes.geometry.curves.util.PathUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared arc-length path sampling for Resample Path, Path Frames, Voxelize Curve, etc.
 */
public final class PathSamplingUtils {

    public static final double EPS = 1.0e-9d;

    private PathSamplingUtils() {
    }

    public record PathSampleResult(
        List<Vector3d> points,
        List<Double> distances,
        List<Double> parameters,
        double totalLength,
        boolean closed,
        boolean valid
    ) {
        public static PathSampleResult invalid() {
            return new PathSampleResult(List.of(), List.of(), List.of(), 0.0d, false, false);
        }
    }

    public static PathSampleResult sample(@Nullable List<Vector3d> verts,
                                          SamplingMode mode,
                                          int count,
                                          double spacing) {
        if (verts == null || verts.size() < 2) {
            return PathSampleResult.invalid();
        }

        boolean closed = PathUtils.isClosed(verts);
        List<Vector3d> unique = closed ? verts.subList(0, verts.size() - 1) : verts;
        if (unique.size() < 2) {
            return PathSampleResult.invalid();
        }

        double[] cumulative = PathUtils.buildCumulative(unique, closed);
        if (cumulative == null) {
            return PathSampleResult.invalid();
        }
        double total = cumulative[cumulative.length - 1];
        if (total <= EPS && mode != SamplingMode.ORIGINAL) {
            return PathSampleResult.invalid();
        }

        return switch (mode) {
            case ORIGINAL -> sampleOriginal(verts, unique, closed, total);
            case COUNT -> sampleByCount(unique, closed, cumulative, total, count);
            case SPACING -> sampleBySpacing(unique, closed, cumulative, total, spacing);
        };
    }

    private static PathSampleResult sampleOriginal(List<Vector3d> verts,
                                                   List<Vector3d> unique,
                                                   boolean closed,
                                                   double total) {
        List<Vector3d> points = new ArrayList<>(verts.size());
        List<Double> distances = new ArrayList<>(verts.size());
        List<Double> parameters = new ArrayList<>(verts.size());
        double[] cumulative = PathUtils.buildCumulative(unique, closed);
        if (cumulative == null) {
            return PathSampleResult.invalid();
        }
        for (int i = 0; i < verts.size(); i++) {
            points.add(new Vector3d(verts.get(i)));
            double d = i < cumulative.length ? cumulative[Math.min(i, cumulative.length - 1)] : 0.0d;
            if (closed && i == verts.size() - 1) {
                d = total;
            }
            distances.add(d);
            parameters.add(total > EPS ? d / total : 0.0d);
        }
        return new PathSampleResult(points, distances, parameters, total, closed, true);
    }

    private static PathSampleResult sampleByCount(List<Vector3d> unique,
                                                  boolean closed,
                                                  double[] cumulative,
                                                  double total,
                                                  int count) {
        if (count < 2) {
            return PathSampleResult.invalid();
        }
        count = GenerationLimits.clampPositiveCount(count);
        List<Vector3d> points = new ArrayList<>(count);
        List<Double> distances = new ArrayList<>(count);
        List<Double> parameters = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double d = total * i / (double) (count - 1);
            points.add(PathUtils.sampleAtDistance(unique, closed, cumulative, d));
            distances.add(d);
            parameters.add(count == 1 ? 0.0d : i / (double) (count - 1));
        }
        if (closed && points.size() >= 2) {
            while (points.size() >= 2 && points.getFirst().distance(points.getLast()) < 1.0e-6d) {
                int last = points.size() - 1;
                points.remove(last);
                distances.remove(last);
                parameters.remove(last);
            }
        }
        return new PathSampleResult(points, distances, parameters, total, closed, !points.isEmpty());
    }

    private static PathSampleResult sampleBySpacing(List<Vector3d> unique,
                                                    boolean closed,
                                                    double[] cumulative,
                                                    double total,
                                                    double spacing) {
        if (spacing <= EPS) {
            return PathSampleResult.invalid();
        }
        List<Vector3d> points = new ArrayList<>();
        List<Double> distances = new ArrayList<>();
        List<Double> parameters = new ArrayList<>();
        int maxInstances = GenerationLimits.clampSpacingInstanceCount(total, spacing);
        int emitted = 0;
        for (double d = 0.0d; d <= total + EPS && emitted < maxInstances; d += spacing) {
            double clamped = Math.min(d, total);
            points.add(PathUtils.sampleAtDistance(unique, closed, cumulative, clamped));
            distances.add(clamped);
            parameters.add(total > EPS ? clamped / total : 0.0d);
            emitted++;
        }
        if (emitted < maxInstances
            && (distances.isEmpty() || distances.getLast() < total - EPS)) {
            points.add(PathUtils.sampleAtDistance(unique, closed, cumulative, total));
            distances.add(total);
            parameters.add(1.0d);
        }
        return new PathSampleResult(points, distances, parameters, total, closed, !points.isEmpty());
    }

    /**
     * Computes unit tangents at each sample distance along the path.
     */
    public static List<Vector3d> tangentsAtDistances(List<Vector3d> unique,
                                                     boolean closed,
                                                     double[] cumulative,
                                                     double total,
                                                     List<Double> sampleDistances) {
        List<Vector3d> tangents = new ArrayList<>(sampleDistances.size());
        double delta = Math.max(total * 1.0e-4d, 1.0e-4d);
        for (double d : sampleDistances) {
            double back = closed ? wrapDistance(d - delta, total) : Math.max(0.0d, d - delta);
            double forward = closed ? wrapDistance(d + delta, total) : Math.min(total, d + delta);
            Vector3d prev = PathUtils.sampleAtDistance(unique, closed, cumulative, back);
            Vector3d next = PathUtils.sampleAtDistance(unique, closed, cumulative, forward);
            Vector3d tangent = new Vector3d(next).sub(prev);
            if (tangent.lengthSquared() <= EPS) {
                continue;
            }
            tangent.normalize();
            tangents.add(tangent);
        }
        return tangents;
    }

    public static double wrapDistance(double value, double length) {
        if (length <= EPS) {
            return 0.0d;
        }
        double wrapped = value % length;
        return wrapped < 0.0d ? wrapped + length : wrapped;
    }
}

package com.nodecraft.nodesystem.nodes.geometry.architectural_primitives;

import com.nodecraft.nodesystem.nodes.geometry.curves.util.PathUtils;
import com.nodecraft.nodesystem.util.VectorUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared PATH sampling for architectural components.
 * <p>
 * Canonical path consumers (Railing, path-following Stair, Wall/Beam Along Path)
 * must use this layer — not a first→last chord approximation.
 */
final class ArchitecturalPathSupport {

    private static final double EPSILON = 1.0e-9d;
    private static final Vector3d WORLD_UP = new Vector3d(0.0d, 1.0d, 0.0d);

    private ArchitecturalPathSupport() {
    }

    /**
     * Resolved open/closed path with arc-length parameterization.
     */
    record PathGeometry(
        List<Vector3d> unique,
        boolean closed,
        double[] cumulative,
        double length
    ) {
    }

    /**
     * Local frame at a path sample: tangent = run, up ≈ world-up, side = sideways offset.
     */
    record SampleFrame(
        Vector3d origin,
        Vector3d tangent,
        Vector3d up,
        Vector3d side
    ) {
    }

    static @Nullable PathGeometry resolve(@Nullable Object pathValue) {
        List<Vector3d> points = PathUtils.resolvePath(pathValue);
        if (points == null || points.size() < 2) {
            return null;
        }
        boolean closed = PathUtils.isClosed(points);
        List<Vector3d> unique = closed ? List.copyOf(points.subList(0, points.size() - 1)) : List.copyOf(points);
        if (unique.size() < 2 && !closed) {
            return null;
        }
        if (unique.isEmpty()) {
            return null;
        }
        double[] cumulative = PathUtils.buildCumulative(unique, closed);
        if (cumulative == null) {
            return null;
        }
        double length = cumulative[cumulative.length - 1];
        if (length <= EPSILON) {
            return null;
        }
        return new PathGeometry(unique, closed, cumulative, length);
    }

    static SampleFrame sampleAt(PathGeometry path, double distance) {
        return sampleAt(path, distance, WORLD_UP);
    }

    static SampleFrame sampleAt(PathGeometry path, double distance, @Nullable Vector3d upHint) {
        double clamped = clampDistance(path, distance);
        Vector3d origin = PathUtils.sampleAtDistance(path.unique(), path.closed(), path.cumulative(), clamped);
        Vector3d tangent = estimateTangent(path, clamped);
        return buildFrame(origin, tangent, upHint);
    }

    /**
     * Arc-length sample distances for evenly spaced instances.
     * Closed paths omit the seam duplicate at {@code length} (same point as 0).
     * Open paths include start and end when {@code count ≥ 2}.
     */
    static List<Double> sampleDistancesEvenly(double length, int count, boolean closed) {
        int safeCount = Math.max(1, count);
        List<Double> distances = new ArrayList<>(safeCount);
        if (safeCount == 1 || length <= EPSILON) {
            distances.add(0.0d);
            return List.copyOf(distances);
        }
        if (closed) {
            for (int i = 0; i < safeCount; i++) {
                distances.add(length * i / (double) safeCount);
            }
        } else {
            for (int i = 0; i < safeCount; i++) {
                double t = i / (double) (safeCount - 1);
                if (i == safeCount - 1) {
                    t = 1.0d;
                }
                distances.add(length * t);
            }
        }
        return List.copyOf(distances);
    }

    /**
     * Arc-length sample distances at a fixed spacing.
     * Closed paths sample {@code [0, length)} and never append the seam at {@code length}.
     * Open paths include the end point.
     *
     * @return distances, or {@code null} when the derived instance count would exceed {@code maxInstances}
     */
    static @Nullable List<Double> sampleDistancesBySpacing(
        double length,
        double spacing,
        boolean closed,
        int maxInstances
    ) {
        if (!(spacing > EPSILON) || length <= EPSILON || maxInstances < 1) {
            return List.of();
        }
        long estimated = closed
            ? (long) Math.floor((length - EPSILON) / spacing) + 1L
            : (long) Math.ceil(length / spacing) + 1L;
        if (estimated > maxInstances) {
            return null;
        }

        List<Double> distances = new ArrayList<>((int) Math.min(estimated, maxInstances));
        if (closed) {
            for (double d = 0.0d; d < length - EPSILON; d += spacing) {
                if (distances.size() >= maxInstances) {
                    return null;
                }
                distances.add(d);
            }
            return List.copyOf(distances);
        }

        for (double d = 0.0d; d <= length + EPSILON; d += spacing) {
            distances.add(Math.min(d, length));
            if (distances.size() >= maxInstances) {
                break;
            }
        }
        if (distances.isEmpty() || distances.getLast() < length - EPSILON) {
            if (distances.size() >= maxInstances) {
                return null;
            }
            distances.add(length);
        }
        return List.copyOf(distances);
    }

    /**
     * Evenly spaced samples along the path (inclusive of start and end when count ≥ 2 on open paths).
     */
    static List<SampleFrame> sampleEvenly(PathGeometry path, int count) {
        List<Double> distances = sampleDistancesEvenly(path.length(), count, path.closed());
        List<SampleFrame> frames = new ArrayList<>(distances.size());
        for (double distance : distances) {
            frames.add(sampleAt(path, distance));
        }
        return List.copyOf(frames);
    }

    /**
     * Consecutive open polyline segments (vertex → vertex). Closed paths include the closing edge.
     */
    static List<Segment> segments(PathGeometry path) {
        List<Segment> result = new ArrayList<>();
        int count = path.closed() ? path.unique().size() : path.unique().size() - 1;
        for (int i = 0; i < count; i++) {
            Vector3d a = path.unique().get(i);
            Vector3d b = path.unique().get((i + 1) % path.unique().size());
            Vector3d dir = VectorUtils.safeSubtract(b, a);
            if (VectorUtils.safeLength(dir) > EPSILON) {
                result.add(new Segment(new Vector3d(a), new Vector3d(b)));
            }
        }
        return List.copyOf(result);
    }

    record Segment(Vector3d start, Vector3d end) {
    }

    static SampleFrame frameForDirection(Vector3d origin, Vector3d tangent) {
        return buildFrame(origin, tangent, WORLD_UP);
    }

    private static double clampDistance(PathGeometry path, double distance) {
        if (path.closed()) {
            double length = path.length();
            double wrapped = distance % length;
            if (wrapped < 0.0d) {
                wrapped += length;
            }
            return wrapped;
        }
        return Math.max(0.0d, Math.min(distance, path.length()));
    }

    private static Vector3d estimateTangent(PathGeometry path, double distance) {
        double delta = Math.max(path.length() * 1.0e-4d, 1.0e-4d);
        double back;
        double forward;
        if (path.closed()) {
            back = clampDistance(path, distance - delta);
            forward = clampDistance(path, distance + delta);
        } else {
            back = Math.max(0.0d, distance - delta);
            forward = Math.min(path.length(), distance + delta);
        }
        Vector3d prev = PathUtils.sampleAtDistance(path.unique(), path.closed(), path.cumulative(), back);
        Vector3d next = PathUtils.sampleAtDistance(path.unique(), path.closed(), path.cumulative(), forward);
        Vector3d tangent = VectorUtils.safeSubtract(next, prev);
        if (VectorUtils.safeLength(tangent) <= EPSILON) {
            for (Segment segment : segments(path)) {
                Vector3d dir = VectorUtils.safeSubtract(segment.end(), segment.start());
                Vector3d normalized = VectorUtils.safeNormalize(dir);
                if (normalized != null) {
                    return normalized;
                }
            }
            return new Vector3d(1.0d, 0.0d, 0.0d);
        }
        Vector3d normalized = VectorUtils.safeNormalize(tangent);
        return normalized != null ? normalized : new Vector3d(1.0d, 0.0d, 0.0d);
    }

    private static SampleFrame buildFrame(Vector3d origin, Vector3d tangent, @Nullable Vector3d upHint) {
        Vector3d run = VectorUtils.safeNormalize(tangent);
        if (run == null) {
            run = new Vector3d(1.0d, 0.0d, 0.0d);
        }

        Vector3d preferredUp = VectorUtils.safeNormalize(upHint);
        if (preferredUp == null) {
            preferredUp = new Vector3d(WORLD_UP);
        }

        Vector3d side = VectorUtils.safeCross(run, preferredUp);
        if (VectorUtils.safeLength(side) <= EPSILON) {
            Vector3d fallbackUp = Math.abs(run.y) < 0.99d
                ? new Vector3d(WORLD_UP)
                : new Vector3d(1.0d, 0.0d, 0.0d);
            side = VectorUtils.safeCross(run, fallbackUp);
        }
        side = VectorUtils.safeNormalize(side);
        if (side == null) {
            side = new Vector3d(1.0d, 0.0d, 0.0d);
        }

        Vector3d up = VectorUtils.safeNormalize(VectorUtils.safeCross(side, run));
        if (up == null) {
            up = preferredUp;
        }

        return new SampleFrame(new Vector3d(origin), run, up, side);
    }
}

package com.nodecraft.nodesystem.nodes.geometry.architectural_primitives;

import com.nodecraft.nodesystem.nodes.geometry.curves.util.PathUtils;
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
     * Evenly spaced samples along the path (inclusive of start and end when count ≥ 2).
     */
    static List<SampleFrame> sampleEvenly(PathGeometry path, int count) {
        int safeCount = Math.max(1, count);
        List<SampleFrame> frames = new ArrayList<>(safeCount);
        if (safeCount == 1) {
            frames.add(sampleAt(path, 0.0d));
            return List.copyOf(frames);
        }
        double span = path.length();
        for (int i = 0; i < safeCount; i++) {
            double t = i / (double) (safeCount - (path.closed() ? 0 : 1));
            if (!path.closed() && i == safeCount - 1) {
                t = 1.0d;
            }
            double distance = path.closed()
                ? (span * i / (double) safeCount)
                : (span * t);
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
            if (a.distanceSquared(b) > EPSILON * EPSILON) {
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
        Vector3d tangent = new Vector3d(next).sub(prev);
        if (tangent.lengthSquared() <= EPSILON * EPSILON) {
            // Fall back to first non-degenerate segment direction.
            for (Segment segment : segments(path)) {
                Vector3d dir = new Vector3d(segment.end()).sub(segment.start());
                if (dir.lengthSquared() > EPSILON * EPSILON) {
                    return dir.normalize();
                }
            }
            return new Vector3d(1.0d, 0.0d, 0.0d);
        }
        return tangent.normalize();
    }

    private static SampleFrame buildFrame(Vector3d origin, Vector3d tangent, @Nullable Vector3d upHint) {
        Vector3d run = new Vector3d(tangent);
        if (run.lengthSquared() <= EPSILON * EPSILON) {
            run.set(1.0d, 0.0d, 0.0d);
        } else {
            run.normalize();
        }

        Vector3d preferredUp = upHint != null && upHint.lengthSquared() > EPSILON * EPSILON
            ? new Vector3d(upHint).normalize()
            : new Vector3d(WORLD_UP);

        Vector3d side = new Vector3d(run).cross(preferredUp);
        if (side.lengthSquared() <= EPSILON * EPSILON) {
            Vector3d fallbackUp = Math.abs(run.y) < 0.99d
                ? new Vector3d(WORLD_UP)
                : new Vector3d(1.0d, 0.0d, 0.0d);
            side = new Vector3d(run).cross(fallbackUp);
        }
        if (side.lengthSquared() <= EPSILON * EPSILON) {
            side.set(1.0d, 0.0d, 0.0d);
        } else {
            side.normalize();
        }

        Vector3d up = new Vector3d(side).cross(run);
        if (up.lengthSquared() <= EPSILON * EPSILON) {
            up.set(preferredUp);
        } else {
            up.normalize();
        }

        return new SampleFrame(new Vector3d(origin), run, up, side);
    }
}

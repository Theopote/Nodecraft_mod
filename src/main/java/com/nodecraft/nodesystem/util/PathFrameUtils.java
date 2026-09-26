package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PlaneProjectionUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2d;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared path-frame helpers for Sweep, Curve Frame Along Path, and path arrays.
 * <p>
 * Frame convention (Sweep / Solid): {@code zAxis} follows the path tangent;
 * {@code xAxis}/{@code yAxis} span the section plane. Profile local offsets use
 * {@code (u, v, 0)} in that basis.
 * <p>
 * Frames along a path use <em>parallel transport</em> (minimum rotation) so section
 * axes do not flip when the least-aligned cardinal reference would jump.
 */
public final class PathFrameUtils {

    public static final double EPS = 1.0e-9d;

    private PathFrameUtils() {
    }

    /**
     * Right-handed path frame: {@code z} = tangent, {@code x}/{@code y} = section plane.
     */
    public record Frame(Vector3d origin, Vector3d xAxis, Vector3d yAxis, Vector3d zAxis) {
        public static Frame identity(Vector3d origin) {
            return new Frame(
                new Vector3d(origin),
                new Vector3d(1.0d, 0.0d, 0.0d),
                new Vector3d(0.0d, 1.0d, 0.0d),
                new Vector3d(0.0d, 0.0d, 1.0d)
            );
        }

        public Vector3d transform(Vector3d local) {
            Vector3d result = new Vector3d(origin);
            result.add(new Vector3d(xAxis).mul(local.x));
            result.add(new Vector3d(yAxis).mul(local.y));
            result.add(new Vector3d(zAxis).mul(local.z));
            return result;
        }
    }

    /**
     * Converts a Sweep-convention path frame to placement {@link FrameData}:
     * {@code X = tangent (z)}, {@code Y = normal (y)}, {@code Z = binormal (x)}.
     */
    public static FrameData toPlacementFrame(Frame frame) {
        Vector3d origin = new Vector3d(frame.origin());
        Vector3d x = new Vector3d(frame.zAxis());
        Vector3d y = new Vector3d(frame.yAxis());
        Vector3d z = new Vector3d(frame.xAxis());
        return new FrameData(origin, x, y, z);
    }

    /**
     * Parallel-transports placement frames for sample origins + tangents.
     */
    public static List<FrameData> placementFramesFromSamples(List<Vector3d> origins,
                                                             List<Vector3d> tangents,
                                                             @Nullable Vector3d upHint) {
        List<Frame> pathFrames = framesFromSamples(origins, tangents, upHint);
        List<FrameData> frames = new ArrayList<>(pathFrames.size());
        for (Frame frame : pathFrames) {
            frames.add(toPlacementFrame(frame));
        }
        return frames;
    }

    /**
     * Parallel-transports placement frames along a polyline (one frame per vertex).
     */
    public static List<FrameData> placementFramesAlongPolyline(List<Vector3d> points, @Nullable Vector3d upHint) {
        List<Frame> pathFrames = framesAlongPolyline(points, upHint);
        List<FrameData> frames = new ArrayList<>(pathFrames.size());
        for (Frame frame : pathFrames) {
            frames.add(toPlacementFrame(frame));
        }
        return frames;
    }

    /**
     * Builds an initial frame at {@code origin} with tangent {@code tangent}.
     * Optional {@code upHint} stabilizes the first section axes when provided.
     */
    public static Frame initialFrame(Vector3d origin, Vector3d tangent, @Nullable Vector3d upHint) {
        Vector3d zAxis = normalizeOr(new Vector3d(tangent), null);
        if (zAxis == null) {
            return Frame.identity(origin);
        }

        Vector3d reference = null;
        if (upHint != null) {
            Vector3d up = normalizeOr(new Vector3d(upHint), null);
            if (up != null) {
                reference = up;
            }
        }
        if (reference == null) {
            reference = leastAlignedCardinal(zAxis);
        }

        Vector3d xAxis = new Vector3d(reference).cross(zAxis);
        if (xAxis.lengthSquared() <= EPS) {
            reference = leastAlignedCardinal(zAxis);
            xAxis = new Vector3d(reference).cross(zAxis);
        }
        if (xAxis.lengthSquared() <= EPS) {
            return Frame.identity(origin);
        }
        xAxis.normalize();

        Vector3d yAxis = new Vector3d(zAxis).cross(xAxis);
        if (yAxis.lengthSquared() <= EPS) {
            return Frame.identity(origin);
        }
        yAxis.normalize();

        return new Frame(new Vector3d(origin), xAxis, yAxis, zAxis);
    }

    /**
     * Parallel-transports frames along a polyline (one frame per vertex).
     */
    public static List<Frame> framesAlongPolyline(List<Vector3d> points, @Nullable Vector3d upHint) {
        if (points == null || points.size() < 2) {
            return List.of();
        }
        List<Vector3d> tangents = new ArrayList<>(points.size());
        for (int i = 0; i < points.size(); i++) {
            tangents.add(computeTangent(points, i));
        }
        return framesFromSamples(points, tangents, upHint);
    }

    /**
     * Parallel-transports frames for an ordered list of sample origins + tangents.
     */
    public static List<Frame> framesFromSamples(List<Vector3d> origins,
                                                List<Vector3d> tangents,
                                                @Nullable Vector3d upHint) {
        if (origins == null || tangents == null || origins.isEmpty() || origins.size() != tangents.size()) {
            return List.of();
        }
        List<Frame> frames = new ArrayList<>(origins.size());
        Frame prev = initialFrame(origins.getFirst(), tangents.getFirst(), upHint);
        frames.add(prev);
        for (int i = 1; i < origins.size(); i++) {
            Frame next = transport(prev, origins.get(i), tangents.get(i));
            frames.add(next);
            prev = next;
        }
        return frames;
    }

    /**
     * Rotates the previous frame's section axes onto the new tangent with minimum rotation.
     */
    public static Frame transport(Frame previous, Vector3d origin, Vector3d tangent) {
        Vector3d zAxis = normalizeOr(new Vector3d(tangent), null);
        if (zAxis == null) {
            return Frame.identity(origin);
        }

        Vector3d prevZ = previous.zAxis();
        double cos = clamp(prevZ.dot(zAxis), -1.0d, 1.0d);
        Vector3d xAxis;
        Vector3d yAxis;

        if (cos > 1.0d - 1.0e-8d) {
            xAxis = new Vector3d(previous.xAxis());
            yAxis = new Vector3d(previous.yAxis());
        } else if (cos < -1.0d + 1.0e-8d) {
            // 180°: rotate section axes around a stable perpendicular.
            Vector3d pivot = leastAlignedCardinal(prevZ);
            Vector3d axis = new Vector3d(prevZ).cross(pivot);
            if (axis.lengthSquared() <= EPS) {
                axis = new Vector3d(1.0d, 0.0d, 0.0d);
            }
            axis.normalize();
            xAxis = rotateAroundUnitAxis(previous.xAxis(), axis, Math.PI);
            yAxis = rotateAroundUnitAxis(previous.yAxis(), axis, Math.PI);
        } else {
            Vector3d axis = new Vector3d(prevZ).cross(zAxis);
            if (axis.lengthSquared() <= EPS) {
                xAxis = new Vector3d(previous.xAxis());
                yAxis = new Vector3d(previous.yAxis());
            } else {
                axis.normalize();
                double angle = Math.acos(cos);
                xAxis = rotateAroundUnitAxis(previous.xAxis(), axis, angle);
                yAxis = rotateAroundUnitAxis(previous.yAxis(), axis, angle);
            }
        }

        // Re-orthonormalize against the new tangent.
        xAxis.sub(new Vector3d(zAxis).mul(xAxis.dot(zAxis)));
        if (xAxis.lengthSquared() <= EPS) {
            yAxis.sub(new Vector3d(zAxis).mul(yAxis.dot(zAxis)));
            if (yAxis.lengthSquared() <= EPS) {
                return initialFrame(origin, zAxis, null);
            }
            yAxis.normalize();
            xAxis = new Vector3d(yAxis).cross(zAxis).normalize();
        } else {
            xAxis.normalize();
            yAxis = new Vector3d(zAxis).cross(xAxis);
            if (yAxis.lengthSquared() <= EPS) {
                return initialFrame(origin, zAxis, null);
            }
            yAxis.normalize();
        }

        return new Frame(new Vector3d(origin), xAxis, yAxis, zAxis);
    }

    /**
     * Converts profile world points into section-local offsets {@code (u, v, 0)} using the
     * profile plane basis, relative to the profile center.
     */
    public static List<Vector3d> profileLocalOffsets(PolygonProfileData profile) {
        List<Vector3d> unique = profile.getUniquePoints();
        return pointsToLocalOffsets(unique, profile.getCenter(), profile.plane());
    }

    /**
     * Converts an ordered point ring into section-local offsets using an explicit plane
     * (or a Newell-fitted plane when {@code plane} is null).
     */
    public static List<Vector3d> pointsToLocalOffsets(List<Vector3d> points,
                                                      Vector3d center,
                                                      @Nullable PlaneData plane) {
        if (points == null || points.isEmpty()) {
            return List.of();
        }
        PlaneData resolvedPlane = plane != null ? plane : fitPlane(points, center);
        PlaneProjectionUtils.PlaneAxes axes = PlaneProjectionUtils.PlaneAxes.from(resolvedPlane);
        Vector2d centerUv = axes.to2d(center);
        List<Vector3d> locals = new ArrayList<>(points.size());
        for (Vector3d point : points) {
            Vector2d uv = axes.to2d(point);
            locals.add(new Vector3d(uv.x - centerUv.x, uv.y - centerUv.y, 0.0d));
        }
        return locals;
    }

    public static Vector3d computeTangent(List<Vector3d> points, int index) {
        return computeTangent(points, index, false);
    }

    /**
     * Tangent at a polyline vertex. When {@code closed}, endpoints use wrap-around neighbors
     * (same rule as polar full-circle: no duplicate seam vertex in the point list).
     */
    public static Vector3d computeTangent(List<Vector3d> points, int index, boolean closed) {
        int n = points.size();
        if (n < 2) {
            return new Vector3d(0.0d, 0.0d, 1.0d);
        }
        Vector3d tangent;
        if (closed && n >= 3) {
            int prev = (index - 1 + n) % n;
            int next = (index + 1) % n;
            tangent = new Vector3d(points.get(next)).sub(points.get(prev));
        } else if (index <= 0) {
            tangent = new Vector3d(points.get(1)).sub(points.get(0));
        } else if (index >= n - 1) {
            tangent = new Vector3d(points.get(index)).sub(points.get(index - 1));
        } else {
            tangent = new Vector3d(points.get(index + 1)).sub(points.get(index - 1));
        }
        Vector3d normalized = normalizeOr(tangent, null);
        return normalized == null ? new Vector3d(0.0d, 0.0d, 1.0d) : normalized;
    }

    public static PlaneData fitPlane(List<Vector3d> points, Vector3d center) {
        Vector3d normal = new Vector3d();
        int n = points.size();
        if (n >= 3) {
            for (int i = 0; i < n; i++) {
                Vector3d current = points.get(i);
                Vector3d next = points.get((i + 1) % n);
                normal.x += (current.y - next.y) * (current.z + next.z);
                normal.y += (current.z - next.z) * (current.x + next.x);
                normal.z += (current.x - next.x) * (current.y + next.y);
            }
        }
        if (normal.lengthSquared() <= EPS) {
            normal.set(0.0d, 1.0d, 0.0d);
        } else {
            normal.normalize();
        }
        return new PlaneData(new Vector3d(center), normal);
    }

    private static Vector3d rotateAroundUnitAxis(Vector3d point, Vector3d unitAxis, double angleRadians) {
        double cos = Math.cos(angleRadians);
        double sin = Math.sin(angleRadians);
        Vector3d term1 = new Vector3d(point).mul(cos);
        Vector3d term2 = new Vector3d(unitAxis).cross(point, new Vector3d()).mul(sin);
        Vector3d term3 = new Vector3d(unitAxis).mul(unitAxis.dot(point) * (1.0d - cos));
        return term1.add(term2).add(term3);
    }

    private static Vector3d leastAlignedCardinal(Vector3d axis) {
        double ax = Math.abs(axis.x);
        double ay = Math.abs(axis.y);
        double az = Math.abs(axis.z);
        if (ax <= ay && ax <= az) {
            return new Vector3d(1.0d, 0.0d, 0.0d);
        }
        if (ay <= az) {
            return new Vector3d(0.0d, 1.0d, 0.0d);
        }
        return new Vector3d(0.0d, 0.0d, 1.0d);
    }

    private static @Nullable Vector3d normalizeOr(Vector3d vector, @Nullable Vector3d fallback) {
        if (vector.lengthSquared() <= EPS) {
            return fallback == null ? null : new Vector3d(fallback);
        }
        return vector.normalize();
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}

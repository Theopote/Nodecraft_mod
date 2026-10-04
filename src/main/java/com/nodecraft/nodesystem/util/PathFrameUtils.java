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
        List<FrameData> frames = placementFramesFromSamples(origins, tangents, upHint, false, false);
        return frames == null ? List.of() : frames;
    }

    /**
     * Parallel-transports placement frames with optional RequireUp and closed-loop roll correction
     * (Graph V102).
     *
     * @return null when {@code requireUp} and the initial Up is parallel to the first tangent
     */
    public static @Nullable List<FrameData> placementFramesFromSamples(List<Vector3d> origins,
                                                                       List<Vector3d> tangents,
                                                                       @Nullable Vector3d upHint,
                                                                       boolean requireUp,
                                                                       boolean closed) {
        List<Frame> pathFrames = framesFromSamples(origins, tangents, upHint, requireUp, closed);
        if (pathFrames == null) {
            return null;
        }
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
     * When Up ∥ tangent, falls back to a least-aligned cardinal (tolerant).
     */
    public static Frame initialFrame(Vector3d origin, Vector3d tangent, @Nullable Vector3d upHint) {
        Vector3d zAxis = normalizeOr(tangent, null);
        if (zAxis == null) {
            return Frame.identity(origin);
        }

        Vector3d reference = null;
        if (upHint != null) {
            Vector3d up = normalizeOr(upHint, null);
            if (up != null) {
                reference = up;
            }
        }
        if (reference == null) {
            reference = leastAlignedCardinal(zAxis);
        }

        Vector3d xAxis = VectorUtils.safeNormalize(new Vector3d(reference).cross(zAxis));
        if (xAxis == null) {
            reference = leastAlignedCardinal(zAxis);
            xAxis = VectorUtils.safeNormalize(new Vector3d(reference).cross(zAxis));
        }
        if (xAxis == null) {
            return Frame.identity(origin);
        }

        Vector3d yAxis = VectorUtils.safeNormalize(new Vector3d(zAxis).cross(xAxis));
        if (yAxis == null) {
            return Frame.identity(origin);
        }

        return new Frame(new Vector3d(origin), xAxis, yAxis, zAxis);
    }

    /**
     * Like {@link #initialFrame} but requires a usable Up that is not parallel to the tangent —
     * no cardinal fallback (Graph V102 connected-Up fail-closed).
     *
     * @return null when Up is missing, zero, or parallel to tangent
     */
    public static @Nullable Frame initialFrameRequireUp(Vector3d origin, Vector3d tangent, Vector3d upHint) {
        Vector3d zAxis = normalizeOr(tangent, null);
        if (zAxis == null) {
            return null;
        }
        Vector3d up = normalizeOr(upHint, null);
        if (up == null) {
            return null;
        }
        Vector3d xAxis = VectorUtils.safeNormalize(new Vector3d(up).cross(zAxis));
        if (xAxis == null) {
            return null;
        }
        Vector3d yAxis = VectorUtils.safeNormalize(new Vector3d(zAxis).cross(xAxis));
        if (yAxis == null) {
            return null;
        }
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
        List<Frame> frames = framesFromSamples(points, tangents, upHint, false, false);
        return frames == null ? List.of() : frames;
    }

    /**
     * Sweep/spine frames: closed polylines use {@link #framesFromSamples} with
     * {@code closed=true} (existing holonomy correction). Fail-closed on degenerate tangents.
     */
    public static @Nullable List<Frame> framesAlongSpine(List<Vector3d> points, @Nullable Vector3d upHint) {
        if (points == null || points.size() < 2) {
            return List.of();
        }
        com.nodecraft.nodesystem.nodes.geometry.curves.util.PathUtils.ClosedVertices closedVerts =
            com.nodecraft.nodesystem.nodes.geometry.curves.util.PathUtils.closedUniqueVertices(points);
        List<Vector3d> verts = closedVerts.vertices();
        if (verts.size() < 2) {
            return null;
        }
        List<Vector3d> tangents = new ArrayList<>(verts.size());
        for (int i = 0; i < verts.size(); i++) {
            Vector3d tangent = tryComputeTangent(verts, i, closedVerts.closed());
            if (tangent == null) {
                return null;
            }
            tangents.add(tangent);
        }
        List<Frame> uniqueFrames = framesFromSamples(verts, tangents, upHint, false, closedVerts.closed());
        if (uniqueFrames == null || uniqueFrames.size() != verts.size()) {
            return null;
        }
        if (points.size() == verts.size()) {
            return uniqueFrames;
        }
        List<Frame> expanded = new ArrayList<>(uniqueFrames);
        Frame first = uniqueFrames.getFirst();
        expanded.add(new Frame(
            new Vector3d(points.getLast()),
            new Vector3d(first.xAxis()),
            new Vector3d(first.yAxis()),
            new Vector3d(first.zAxis())
        ));
        return expanded;
    }

    /**
     * Parallel-transports frames for an ordered list of sample origins + tangents.
     */
    public static List<Frame> framesFromSamples(List<Vector3d> origins,
                                                List<Vector3d> tangents,
                                                @Nullable Vector3d upHint) {
        List<Frame> frames = framesFromSamples(origins, tangents, upHint, false, false);
        return frames == null ? List.of() : frames;
    }

    /**
     * Parallel-transports frames with optional RequireUp and closed-loop roll correction.
     *
     * @return null when {@code requireUp} and Up ∥ first tangent; empty list for invalid input
     */
    public static @Nullable List<Frame> framesFromSamples(List<Vector3d> origins,
                                                          List<Vector3d> tangents,
                                                          @Nullable Vector3d upHint,
                                                          boolean requireUp,
                                                          boolean closed) {
        if (origins == null || tangents == null || origins.isEmpty() || origins.size() != tangents.size()) {
            return List.of();
        }
        List<Frame> frames = new ArrayList<>(origins.size());
        Frame prev;
        if (requireUp) {
            if (upHint == null) {
                return null;
            }
            prev = initialFrameRequireUp(origins.getFirst(), tangents.getFirst(), upHint);
            if (prev == null) {
                return null;
            }
        } else {
            prev = initialFrame(origins.getFirst(), tangents.getFirst(), upHint);
        }
        frames.add(prev);
        for (int i = 1; i < origins.size(); i++) {
            Frame next = transport(prev, origins.get(i), tangents.get(i));
            frames.add(next);
            prev = next;
        }
        if (closed && frames.size() >= 3) {
            return applyClosedRollCorrection(frames, origins);
        }
        return frames;
    }

    /**
     * Distributes closed-loop parallel-transport holonomy (roll error) evenly along arc length
     * so transporting the last frame onto the first matches the first section axes.
     */
    static List<Frame> applyClosedRollCorrection(List<Frame> frames, List<Vector3d> origins) {
        int n = frames.size();
        if (n < 3 || origins == null || origins.size() != n) {
            return frames;
        }
        Frame first = frames.getFirst();
        Frame last = frames.get(n - 1);
        Frame closeProbe = transport(last, first.origin(), first.zAxis());
        double error = signedAngleAboutAxis(first.xAxis(), closeProbe.xAxis(), first.zAxis());
        if (!Double.isFinite(error) || Math.abs(error) <= SpatialTolerance.EPS) {
            return frames;
        }

        double[] arc = new double[n];
        double total = 0.0d;
        arc[0] = 0.0d;
        for (int i = 1; i < n; i++) {
            total += origins.get(i).distance(origins.get(i - 1));
            arc[i] = total;
        }
        if (total <= SpatialTolerance.EPS) {
            return frames;
        }

        List<Frame> corrected = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            double angle = -error * (arc[i] / total);
            corrected.add(rotateSectionAboutTangent(frames.get(i), angle));
        }
        return corrected;
    }

    private static Frame rotateSectionAboutTangent(Frame frame, double angleRadians) {
        if (Math.abs(angleRadians) <= SpatialTolerance.EPS) {
            return frame;
        }
        Vector3d z = frame.zAxis();
        Vector3d x = rotateAroundUnitAxis(frame.xAxis(), z, angleRadians);
        x.sub(new Vector3d(z).mul(x.dot(z)));
        x = VectorUtils.safeNormalize(x);
        if (x == null) {
            return frame;
        }
        Vector3d y = VectorUtils.safeNormalize(new Vector3d(z).cross(x));
        if (y == null) {
            return frame;
        }
        return new Frame(new Vector3d(frame.origin()), x, y, new Vector3d(z));
    }

    /**
     * Signed angle from {@code from} to {@code to} about unit {@code axis}.
     */
    static double signedAngleAboutAxis(Vector3d from, Vector3d to, Vector3d axis) {
        Vector3d a = new Vector3d(from);
        a.sub(new Vector3d(axis).mul(a.dot(axis)));
        Vector3d b = new Vector3d(to);
        b.sub(new Vector3d(axis).mul(b.dot(axis)));
        a = VectorUtils.safeNormalize(a);
        b = VectorUtils.safeNormalize(b);
        if (a == null || b == null) {
            return 0.0d;
        }
        double sin = new Vector3d(a).cross(b).dot(axis);
        double cos = a.dot(b);
        return Math.atan2(sin, cos);
    }

    /**
     * Rotates the previous frame's section axes onto the new tangent with minimum rotation.
     */
    public static Frame transport(Frame previous, Vector3d origin, Vector3d tangent) {
        Vector3d zAxis = normalizeOr(tangent, null);
        if (zAxis == null) {
            return Frame.identity(origin);
        }

        Vector3d prevZ = previous.zAxis();
        double rawCos = VectorUtils.safeDot(prevZ, zAxis);
        if (!Double.isFinite(rawCos)) {
            return Frame.identity(origin);
        }
        double cos = clamp(rawCos, -1.0d, 1.0d);
        Vector3d xAxis;
        Vector3d yAxis;

        if (cos > 1.0d - 1.0e-8d) {
            xAxis = new Vector3d(previous.xAxis());
            yAxis = new Vector3d(previous.yAxis());
        } else if (cos < -1.0d + 1.0e-8d) {
            Vector3d pivot = leastAlignedCardinal(prevZ);
            Vector3d axis = VectorUtils.safeNormalize(new Vector3d(prevZ).cross(pivot));
            if (axis == null) {
                axis = new Vector3d(1.0d, 0.0d, 0.0d);
            }
            xAxis = rotateAroundUnitAxis(previous.xAxis(), axis, Math.PI);
            yAxis = rotateAroundUnitAxis(previous.yAxis(), axis, Math.PI);
        } else {
            Vector3d axis = VectorUtils.safeNormalize(new Vector3d(prevZ).cross(zAxis));
            if (axis == null) {
                xAxis = new Vector3d(previous.xAxis());
                yAxis = new Vector3d(previous.yAxis());
            } else {
                double angle = Math.acos(cos);
                xAxis = rotateAroundUnitAxis(previous.xAxis(), axis, angle);
                yAxis = rotateAroundUnitAxis(previous.yAxis(), axis, angle);
            }
        }

        xAxis.sub(new Vector3d(zAxis).mul(xAxis.dot(zAxis)));
        Vector3d unitX = VectorUtils.safeNormalize(xAxis);
        if (unitX == null) {
            yAxis.sub(new Vector3d(zAxis).mul(yAxis.dot(zAxis)));
            Vector3d unitY = VectorUtils.safeNormalize(yAxis);
            if (unitY == null) {
                return initialFrame(origin, zAxis, null);
            }
            yAxis = unitY;
            xAxis = VectorUtils.safeNormalize(new Vector3d(yAxis).cross(zAxis));
            if (xAxis == null) {
                return initialFrame(origin, zAxis, null);
            }
        } else {
            xAxis = unitX;
            yAxis = VectorUtils.safeNormalize(new Vector3d(zAxis).cross(xAxis));
            if (yAxis == null) {
                return initialFrame(origin, zAxis, null);
            }
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
     * <p>
     * Legacy tolerant API: degenerate tangents fall back to world +Z. Prefer
     * {@link #tryComputeTangent(List, int, boolean)} for fail-closed language nodes.
     */
    public static Vector3d computeTangent(List<Vector3d> points, int index, boolean closed) {
        Vector3d resolved = tryComputeTangent(points, index, closed);
        return resolved == null ? new Vector3d(0.0d, 0.0d, 1.0d) : resolved;
    }

    /**
     * Fail-closed tangent at a polyline vertex.
     *
     * @return normalized tangent, or {@code null} when the neighborhood is missing or degenerate
     */
    public static @Nullable Vector3d tryComputeTangent(List<Vector3d> points, int index, boolean closed) {
        if (points == null) {
            return null;
        }
        int n = points.size();
        if (n < 2 || index < 0 || index >= n) {
            return null;
        }
        Vector3d tangent;
        if (closed && n >= 3) {
            int prev = (index - 1 + n) % n;
            int next = (index + 1) % n;
            tangent = VectorUtils.safeSubtract(points.get(next), points.get(prev));
        } else if (index <= 0) {
            tangent = VectorUtils.safeSubtract(points.get(1), points.get(0));
        } else if (index >= n - 1) {
            tangent = VectorUtils.safeSubtract(points.get(index), points.get(index - 1));
        } else {
            tangent = VectorUtils.safeSubtract(points.get(index + 1), points.get(index - 1));
        }
        if (tangent == null) {
            return null;
        }
        return normalizeOr(tangent, null);
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
        Vector3d unitNormal = VectorUtils.safeNormalize(normal);
        if (unitNormal == null) {
            normal.set(0.0d, 1.0d, 0.0d);
        } else {
            normal = unitNormal;
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
        Vector3d unit = VectorUtils.safeNormalize(vector);
        if (unit != null) {
            return unit;
        }
        return fallback == null ? null : new Vector3d(fallback);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}

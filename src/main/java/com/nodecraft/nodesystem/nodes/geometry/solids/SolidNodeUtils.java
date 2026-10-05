package com.nodecraft.nodesystem.nodes.geometry.solids;

import com.nodecraft.core.exception.GeometryException;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.datatypes.SurfaceStripData;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PathUtils;
import com.nodecraft.nodesystem.util.PathFrameUtils;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import com.nodecraft.nodesystem.util.VectorUtils;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

final class SolidNodeUtils {
    static final double EPSILON = 1.0e-9d;

    private SolidNodeUtils() {
    }

    static @Nullable Vector3d resolvePoint(@Nullable Object value) {
        Vector3d resolved = SpatialValueResolver.resolvePoint(value);
        return resolved == null ? null : new Vector3d(resolved);
    }

    /**
     * Graph VECTOR ports: {@link com.nodecraft.nodesystem.datatypes.VectorData} only.
     */
    static @Nullable Vector3d resolveDirection(@Nullable Object value) {
        return VectorUtils.toStrictVectorPortValue(value);
    }

    static List<Vector3d> resolvePointList(@Nullable Object value) {
        if (value instanceof Collection<?> collection) {
            List<Vector3d> resolved = new ArrayList<>(collection.size());
            for (Object entry : collection) {
                Vector3d point = resolvePoint(entry);
                if (point != null) {
                    resolved.add(point);
                }
            }
            return resolved;
        }

        Vector3d point = resolvePoint(value);
        return point == null ? List.of() : List.of(point);
    }

    /**
     * Strict POINT_LIST for solids consumers: non-Collection → null;
     * empty collection → empty list (success empty); any non-{@link PointData}
     * or non-finite entry → null (fail closed, no filtering / index drift).
     */
    static @Nullable List<Vector3d> resolveStrictPointList(@Nullable Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return null;
        }
        if (collection.isEmpty()) {
            return List.of();
        }
        List<Vector3d> points = new ArrayList<>(collection.size());
        for (Object entry : collection) {
            if (!(entry instanceof PointData pointData)) {
                return null;
            }
            Vector3d position = pointData.position();
            if (!Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z)) {
                return null;
            }
            points.add(new Vector3d(position));
        }
        return List.copyOf(points);
    }

    static PolylineData createPolyline(List<Vector3d> points, boolean closed) {
        List<Vec3d> polylinePoints = new ArrayList<>(points.size() + 1);
        for (Vector3d point : points) {
            polylinePoints.add(toVec3d(point));
        }
        if (closed && points.size() >= 2) {
            Vector3d first = points.getFirst();
            Vector3d last = points.getLast();
            if (first.distanceSquared(last) > EPSILON * EPSILON) {
                polylinePoints.add(toVec3d(first));
            }
        }
        return polylinePoints.size() >= 2 ? new PolylineData(polylinePoints) : null;
    }

    /** Resolves a PATH-only spine; empty when the path is missing or too short. */
    static List<Vector3d> resolveSpinePoints(@Nullable Object pathObj) {
        List<Vector3d> path = PathUtils.resolvePath(pathObj);
        return path == null || path.size() < 2 ? List.of() : path;
    }

    /**
     * Arc-length resamples a closed or open section polyline to {@code targetCount} vertices.
     * Returns empty when resampling is impossible.
     */
    static List<Vector3d> resampleSection(List<Vector3d> section, int targetCount, boolean closed) {
        if (section == null || section.isEmpty()) {
            return List.of();
        }
        if (section.size() == targetCount) {
            List<Vector3d> copy = new ArrayList<>(section.size());
            for (Vector3d point : section) {
                copy.add(new Vector3d(point));
            }
            return List.copyOf(copy);
        }
        if (targetCount < 2 || section.size() < 2) {
            return List.of();
        }
        double[] cumulative = PathUtils.buildCumulative(section, closed);
        if (cumulative == null) {
            return List.of();
        }
        double total = cumulative[cumulative.length - 1];
        if (!(total > EPSILON) || !Double.isFinite(total)) {
            return List.of();
        }
        List<Vector3d> result = new ArrayList<>(targetCount);
        int divisor = closed ? targetCount : Math.max(1, targetCount - 1);
        for (int i = 0; i < targetCount; i++) {
            double distance = (total * i) / divisor;
            if (!Double.isFinite(distance)) {
                return List.of();
            }
            Vector3d sample = PathUtils.sampleAtDistance(section, closed, cumulative, distance);
            if (sample == null || !com.nodecraft.nodesystem.util.VectorUtils.isFinite(sample)) {
                return List.of();
            }
            result.add(sample);
        }
        return List.copyOf(result);
    }

    static Vector3d computeTangent(List<Vector3d> points, int index) {
        return PathFrameUtils.computeTangent(points, index);
    }

    static Frame buildFrame(Vector3d origin, Vector3d tangent) {
        PathFrameUtils.Frame frame = PathFrameUtils.initialFrame(origin, tangent, null);
        return new Frame(frame.origin(), frame.xAxis(), frame.yAxis(), frame.zAxis());
    }

    static List<Frame> framesAlongPolyline(List<Vector3d> points) {
        List<PathFrameUtils.Frame> frames = PathFrameUtils.framesAlongPolyline(points, null);
        List<Frame> converted = new ArrayList<>(frames.size());
        for (PathFrameUtils.Frame frame : frames) {
            converted.add(new Frame(frame.origin(), frame.xAxis(), frame.yAxis(), frame.zAxis()));
        }
        return converted;
    }

    static @Nullable Vector3d rotateAroundAxis(Vector3d point, Vector3d axisOrigin, Vector3d axisDirection, double angleRadians) {
        Vector3d k = com.nodecraft.nodesystem.util.VectorUtils.safeNormalize(axisDirection);
        Vector3d relative = com.nodecraft.nodesystem.util.VectorUtils.safeSubtract(point, axisOrigin);
        if (k == null || relative == null || !Double.isFinite(angleRadians)) {
            return null;
        }

        double cos = Math.cos(angleRadians);
        double sin = Math.sin(angleRadians);
        if (!Double.isFinite(cos) || !Double.isFinite(sin)) {
            return null;
        }

        Vector3d term1 = com.nodecraft.nodesystem.util.VectorUtils.safeScale(relative, cos);
        Vector3d cross = com.nodecraft.nodesystem.util.VectorUtils.safeCross(k, relative);
        Vector3d term2 = com.nodecraft.nodesystem.util.VectorUtils.safeScale(cross, sin);
        double kDot = com.nodecraft.nodesystem.util.VectorUtils.safeDot(k, relative);
        Vector3d term3 = com.nodecraft.nodesystem.util.VectorUtils.safeScale(k, kDot * (1.0d - cos));
        Vector3d rotated = com.nodecraft.nodesystem.util.VectorUtils.safeAdd(term1, term2);
        rotated = com.nodecraft.nodesystem.util.VectorUtils.safeAdd(rotated, term3);
        return com.nodecraft.nodesystem.util.VectorUtils.safeAdd(rotated, axisOrigin);
    }

    static @Nullable Vector3d computeCenter(List<Vector3d> points) {
        return com.nodecraft.nodesystem.util.PointUtils.safeListCenter(points);
    }

    static Vec3d toVec3d(Vector3d point) {
        return new Vec3d(point.x, point.y, point.z);
    }

    static @Nullable PathData toPath(@Nullable Object value) {
        return PathData.wrap(value);
    }

    static List<PathData> toPathList(@Nullable Collection<?> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<PathData> paths = new ArrayList<>(values.size());
        for (Object value : values) {
            PathData path = toPath(value);
            if (path != null) {
                paths.add(path);
            }
        }
        return List.copyOf(paths);
    }

    static List<SurfaceStripData> toSurfaceStripList(@Nullable Collection<?> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<SurfaceStripData> strips = new ArrayList<>(values.size());
        for (Object value : values) {
            if (value instanceof SurfaceStripData strip) {
                strips.add(strip);
            }
        }
        return List.copyOf(strips);
    }

    static int resolveBoxFaceIndex(@Nullable Object faceObj, @Nullable Object faceIndexObj) {
        if (faceObj instanceof BoxFaceData face) {
            return face.getIndex();
        }
        if (faceIndexObj instanceof Number number) {
            return number.intValue();
        }
        return -1;
    }

    static BoxFaceMapping resolveBoxFaceMapping(int faceIndex) {
        int axis = switch (faceIndex) {
            case 0, 1 -> 1;
            case 2, 3 -> 0;
            case 4, 5 -> 2;
            default -> throw new GeometryException("Unsupported box face index: " + faceIndex);
        };
        int direction = switch (faceIndex) {
            case 1, 3, 5 -> 1;
            default -> -1;
        };
        return new BoxFaceMapping(axis, direction);
    }

    static double getAxisValue(Vector3d vector, int axis) {
        return switch (axis) {
            case 0 -> vector.x;
            case 1 -> vector.y;
            case 2 -> vector.z;
            default -> throw new GeometryException("Unsupported axis: " + axis);
        };
    }

    static void setAxisValue(Vector3d vector, int axis, double value) {
        switch (axis) {
            case 0 -> vector.x = value;
            case 1 -> vector.y = value;
            case 2 -> vector.z = value;
            default -> throw new GeometryException("Unsupported axis: " + axis);
        }
    }

    record Frame(Vector3d origin, Vector3d xAxis, Vector3d yAxis, Vector3d zAxis) {
        static Frame identity(Vector3d origin) {
            return new Frame(
                new Vector3d(origin),
                new Vector3d(1.0d, 0.0d, 0.0d),
                new Vector3d(0.0d, 1.0d, 0.0d),
                new Vector3d(0.0d, 0.0d, 1.0d)
            );
        }

        Vector3d transform(Vector3d local) {
            Vector3d result = new Vector3d(origin);
            result.add(new Vector3d(xAxis).mul(local.x));
            result.add(new Vector3d(yAxis).mul(local.y));
            result.add(new Vector3d(zAxis).mul(local.z));
            return result;
        }
    }

    record BoxFaceMapping(int axis, int direction) {
    }
}

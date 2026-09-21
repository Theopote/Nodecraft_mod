package com.nodecraft.nodesystem.nodes.geometry.solids;

import com.nodecraft.core.exception.GeometryException;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PathUtils;
import com.nodecraft.nodesystem.util.PathFrameUtils;
import com.nodecraft.nodesystem.util.Vector3;
import net.minecraft.util.math.BlockPos;
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
        if (value instanceof PointData pointData) {
            return new Vector3d(pointData.getPosition());
        }
        if (value instanceof Vector3d vector) {
            return new Vector3d(vector);
        }
        if (value instanceof Vec3d vector) {
            return new Vector3d(vector.x, vector.y, vector.z);
        }
        if (value instanceof BlockPos blockPos) {
            return new Vector3d(blockPos.getX(), blockPos.getY(), blockPos.getZ());
        }
        return null;
    }

    /**
     * Strict direction resolver: accepts only vector-like values
     * ({@link Vector3d}, {@link Vec3d}, {@link Vector3}). Does not treat
     * {@link PointData} or {@link BlockPos} as directions.
     */
    static @Nullable Vector3d resolveDirection(@Nullable Object value) {
        if (value instanceof Vector3d vector) {
            return new Vector3d(vector);
        }
        if (value instanceof Vec3d vector) {
            return new Vector3d(vector.x, vector.y, vector.z);
        }
        if (value instanceof Vector3 vector) {
            return new Vector3d(vector.getX(), vector.getY(), vector.getZ());
        }
        return null;
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

        int segmentCount = closed ? section.size() : section.size() - 1;
        if (segmentCount < 1) {
            return List.of();
        }

        double[] cumulative = new double[segmentCount + 1];
        double total = 0.0d;
        for (int i = 0; i < segmentCount; i++) {
            Vector3d a = section.get(i);
            Vector3d b = section.get((i + 1) % section.size());
            total += a.distance(b);
            cumulative[i + 1] = total;
        }
        if (total <= EPSILON) {
            return List.of();
        }

        List<Vector3d> result = new ArrayList<>(targetCount);
        int divisor = closed ? targetCount : Math.max(1, targetCount - 1);
        for (int i = 0; i < targetCount; i++) {
            double distance = (total * i) / divisor;
            result.add(sampleSectionAtDistance(section, closed, cumulative, distance));
        }
        return List.copyOf(result);
    }

    private static Vector3d sampleSectionAtDistance(List<Vector3d> section,
                                                    boolean closed,
                                                    double[] cumulative,
                                                    double distance) {
        double clamped = Math.max(0.0d, Math.min(distance, cumulative[cumulative.length - 1]));
        for (int i = 0; i < cumulative.length - 1; i++) {
            double start = cumulative[i];
            double end = cumulative[i + 1];
            if (clamped <= end || i == cumulative.length - 2) {
                Vector3d a = section.get(i);
                Vector3d b = section.get((i + 1) % section.size());
                double segmentLength = end - start;
                if (segmentLength <= EPSILON) {
                    return new Vector3d(a);
                }
                double t = (clamped - start) / segmentLength;
                return new Vector3d(a).lerp(b, t);
            }
        }
        return new Vector3d(section.getFirst());
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

    static Vector3d rotateAroundAxis(Vector3d point, Vector3d axisOrigin, Vector3d axisDirection, double angleRadians) {
        Vector3d k = new Vector3d(axisDirection).normalize();
        Vector3d relative = new Vector3d(point).sub(axisOrigin);

        double cos = Math.cos(angleRadians);
        double sin = Math.sin(angleRadians);

        Vector3d term1 = new Vector3d(relative).mul(cos);
        Vector3d term2 = new Vector3d(k).cross(relative, new Vector3d()).mul(sin);
        Vector3d term3 = new Vector3d(k).mul(k.dot(relative) * (1.0d - cos));

        return term1.add(term2).add(term3).add(axisOrigin);
    }

    static Vector3d computeCenter(List<Vector3d> points) {
        Vector3d center = new Vector3d();
        for (Vector3d point : points) {
            center.add(point);
        }
        return points.isEmpty() ? center : center.div(points.size());
    }

    static Vec3d toVec3d(Vector3d point) {
        return new Vec3d(point.x, point.y, point.z);
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

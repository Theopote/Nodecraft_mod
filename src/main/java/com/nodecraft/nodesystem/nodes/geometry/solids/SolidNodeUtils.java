package com.nodecraft.nodesystem.nodes.geometry.solids;

import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.util.Curve;
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
        if (value instanceof PointData pointData) {
            return new Vector3d(pointData.getPosition());
        }
        if (value instanceof BlockPos blockPos) {
            return new Vector3d(blockPos.getX(), blockPos.getY(), blockPos.getZ());
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

    static List<Vector3d> resolveSpinePoints(@Nullable Object lineObj,
                                             @Nullable Object polylineObj,
                                             @Nullable Object curveObj,
                                             @Nullable Object pathPointsObj) {
        List<Vector3d> spinePoints = new ArrayList<>();
        if (lineObj instanceof LineData line) {
            spinePoints.add(fromVec3d(line.getStart()));
            spinePoints.add(fromVec3d(line.getEnd()));
        } else if (polylineObj instanceof PolylineData polyline) {
            for (Vec3d point : polyline.getPoints()) {
                spinePoints.add(fromVec3d(point));
            }
        } else if (curveObj instanceof Curve curve) {
            for (Vec3d point : curve.getSamplePoints()) {
                spinePoints.add(fromVec3d(point));
            }
        } else {
            spinePoints.addAll(resolvePointList(pathPointsObj));
        }
        return spinePoints;
    }

    static Vector3d computeTangent(List<Vector3d> points, int index) {
        Vector3d tangent;
        if (index == 0) {
            tangent = new Vector3d(points.get(1)).sub(points.get(0));
        } else if (index == points.size() - 1) {
            tangent = new Vector3d(points.get(index)).sub(points.get(index - 1));
        } else {
            tangent = new Vector3d(points.get(index + 1)).sub(points.get(index - 1));
        }
        return normalizeOr(tangent, new Vector3d(0.0d, 0.0d, 1.0d));
    }

    static Frame buildFrame(Vector3d origin, Vector3d tangent) {
        Vector3d zAxis = normalizeOr(new Vector3d(tangent), null);
        if (zAxis == null) {
            return Frame.identity(origin);
        }

        Vector3d reference = leastAlignedCardinal(zAxis);
        Vector3d xAxis = reference.cross(zAxis, new Vector3d());
        if (xAxis.lengthSquared() <= EPSILON) {
            return Frame.identity(origin);
        }
        xAxis.normalize();

        Vector3d yAxis = new Vector3d(zAxis).cross(xAxis);
        if (yAxis.lengthSquared() <= EPSILON) {
            return Frame.identity(origin);
        }
        yAxis.normalize();

        return new Frame(origin, xAxis, yAxis, zAxis);
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
            default -> throw new IllegalArgumentException("Unsupported box face index: " + faceIndex);
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
            default -> throw new IllegalArgumentException("Unsupported axis: " + axis);
        };
    }

    static void setAxisValue(Vector3d vector, int axis, double value) {
        switch (axis) {
            case 0 -> vector.x = value;
            case 1 -> vector.y = value;
            case 2 -> vector.z = value;
            default -> throw new IllegalArgumentException("Unsupported axis: " + axis);
        }
    }

    private static Vector3d fromVec3d(Vec3d point) {
        return new Vector3d(point.x, point.y, point.z);
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
        if (vector.lengthSquared() <= EPSILON) {
            return fallback == null ? null : fallback;
        }
        return vector.normalize();
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

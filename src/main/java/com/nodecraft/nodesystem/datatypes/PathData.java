package com.nodecraft.nodesystem.datatypes;

import com.nodecraft.nodesystem.util.Curve;
import com.nodecraft.nodesystem.util.VectorUtils;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;

/**
 * Unified graph-facing wrapper for line, polyline, and curve path representations.
 * Factories fail closed: kind always matches a structurally valid payload.
 */
public final class PathData {

    public enum Kind {
        LINE,
        POLYLINE,
        CURVE
    }

    private final Kind kind;
    private final LineData line;
    private final PolylineData polyline;
    private final Curve curve;

    private PathData(Kind kind, LineData line, PolylineData polyline, Curve curve) {
        this.kind = kind;
        this.line = line;
        this.polyline = polyline;
        this.curve = curve;
    }

    public static @Nullable PathData fromLine(@Nullable LineData line) {
        if (!isValidLine(line)) {
            return null;
        }
        return new PathData(Kind.LINE, line, null, null);
    }

    public static @Nullable PathData fromPolyline(@Nullable PolylineData polyline) {
        if (!isValidPolyline(polyline)) {
            return null;
        }
        return new PathData(Kind.POLYLINE, null, polyline, null);
    }

    public static @Nullable PathData fromCurve(@Nullable Curve curve) {
        if (!isValidCurve(curve)) {
            return null;
        }
        return new PathData(Kind.CURVE, null, null, curve);
    }

    public static @Nullable PathData wrap(@Nullable Object value) {
        if (value instanceof PathData path) {
            return path;
        }
        if (value instanceof LineData line) {
            return fromLine(line);
        }
        if (value instanceof PolylineData polyline) {
            return fromPolyline(polyline);
        }
        if (value instanceof Curve curve) {
            return fromCurve(curve);
        }
        return null;
    }

    public Kind getKind() {
        return kind;
    }

    public @Nullable LineData getLine() {
        return line;
    }

    public @Nullable PolylineData getPolyline() {
        return polyline;
    }

    public @Nullable Curve getCurve() {
        return curve;
    }

    private static boolean isValidLine(@Nullable LineData line) {
        if (line == null) {
            return false;
        }
        Vec3d start = line.start();
        Vec3d end = line.end();
        if (!isFinite(start) || !isFinite(end)) {
            return false;
        }
        double distance = VectorUtils.safeDistance(toVector(start), toVector(end));
        return Double.isFinite(distance) && distance > 0.0d;
    }

    private static boolean isValidPolyline(@Nullable PolylineData polyline) {
        if (polyline == null) {
            return false;
        }
        List<Vec3d> points = polyline.points();
        if (points == null || points.size() < 2) {
            return false;
        }
        for (Vec3d point : points) {
            if (!isFinite(point)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidCurve(@Nullable Curve curve) {
        if (curve == null || curve.getCurveType() == null) {
            return false;
        }
        List<Vec3d> controls = curve.getControlPoints();
        if (controls == null || controls.size() < 2) {
            return false;
        }
        for (Vec3d point : controls) {
            if (!isFinite(point)) {
                return false;
            }
        }
        return curve.getResolution() >= 2;
    }

    private static boolean isFinite(@Nullable Vec3d point) {
        return point != null
            && Double.isFinite(point.x)
            && Double.isFinite(point.y)
            && Double.isFinite(point.z);
    }

    private static Vector3d toVector(Vec3d point) {
        return new Vector3d(point.x, point.y, point.z);
    }
}

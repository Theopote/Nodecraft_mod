package com.nodecraft.nodesystem.datatypes;

import com.nodecraft.nodesystem.util.Curve;
import org.jetbrains.annotations.Nullable;

/**
 * Unified graph-facing wrapper for line, polyline, and curve path representations.
 * {@code LINE}, {@code POLYLINE}, and {@code CURVE} values may connect to {@code PATH} ports
 * implicitly; this type is used when a node explicitly emits a path bundle.
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

    public static PathData fromLine(LineData line) {
        return new PathData(Kind.LINE, line, null, null);
    }

    public static PathData fromPolyline(PolylineData polyline) {
        return new PathData(Kind.POLYLINE, null, polyline, null);
    }

    public static PathData fromCurve(Curve curve) {
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
}

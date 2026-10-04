package com.nodecraft.nodesystem.nodes.geometry.curves.util;

import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.VectorUtils;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared finite-sample fence for curve constructors before emitting PATH.
 */
public final class CurveSampleFence {

    public record Result(
        List<Vec3d> points,
        List<Vector3d> vectors,
        PathData path,
        double length
    ) {
    }

    private CurveSampleFence() {
    }

    public static @Nullable Result validate(@Nullable List<Vec3d> samples) {
        if (samples == null
            || samples.size() < 2
            || samples.size() > GenerationLimits.MAX_CURVE_SAMPLES) {
            return null;
        }
        List<Vec3d> copy = new ArrayList<>(samples.size());
        List<Vector3d> vectors = new ArrayList<>(samples.size());
        double length = 0.0d;
        Vector3d previous = null;
        for (Vec3d point : samples) {
            if (point == null
                || !Double.isFinite(point.x)
                || !Double.isFinite(point.y)
                || !Double.isFinite(point.z)) {
                return null;
            }
            Vector3d vector = new Vector3d(point.x, point.y, point.z);
            if (previous != null) {
                double segment = VectorUtils.safeDistance(previous, vector);
                if (!Double.isFinite(segment)) {
                    return null;
                }
                length += segment;
                if (!Double.isFinite(length)) {
                    return null;
                }
            }
            copy.add(point);
            vectors.add(vector);
            previous = vector;
        }
        PathData path;
        if (copy.size() == 2) {
            path = PathData.fromLine(new LineData(copy.get(0), copy.get(1)));
        } else {
            PolylineData polyline = PathUtils.createPolylineOrNull(copy);
            path = PathData.fromPolyline(polyline);
        }
        if (path == null) {
            return null;
        }
        return new Result(List.copyOf(copy), List.copyOf(vectors), path, length);
    }
}

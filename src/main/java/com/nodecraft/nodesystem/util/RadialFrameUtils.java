package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.FrameData;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

/**
 * Shared radial layout frame convention for Spiral and Phyllotaxis producers.
 * Delegates to {@link PathFrameUtils} so radial frames match path placement frames.
 */
public final class RadialFrameUtils {

    private static final Vector3d DEFAULT_UP = new Vector3d(0.0d, 1.0d, 0.0d);

    private RadialFrameUtils() {
    }

    public static FrameData placementFrame(Vector3d origin, Vector3d tangent) {
        return placementFrame(origin, tangent, DEFAULT_UP);
    }

    public static FrameData placementFrame(Vector3d origin, Vector3d tangent, @Nullable Vector3d upHint) {
        PathFrameUtils.Frame frame = PathFrameUtils.initialFrame(origin, tangent, upHint);
        return PathFrameUtils.toPlacementFrame(frame);
    }

    public static @Nullable Vector3d normalizeTangent(Vector3d tangent) {
        if (tangent == null
                || !Double.isFinite(tangent.x)
                || !Double.isFinite(tangent.y)
                || !Double.isFinite(tangent.z)
                || tangent.lengthSquared() <= PathFrameUtils.EPS) {
            return null;
        }
        return new Vector3d(tangent).normalize();
    }
}

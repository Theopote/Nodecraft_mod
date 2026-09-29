package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.FrameData;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;

/**
 * Shared radial layout frame convention for Spiral and Phyllotaxis producers.
 * Delegates to {@link PathFrameUtils} so radial frames match path placement frames.
 * <p>
 * Batch {@link #placementFrames} uses parallel transport (initial frame once, then
 * transport along tangents). Single {@link #placementFrame} builds an independent initial frame.
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

    /**
     * Parallel-transports placement frames for aligned sample origins and tangents.
     */
    public static List<FrameData> placementFrames(List<Vector3d> origins, List<Vector3d> tangents) {
        return placementFrames(origins, tangents, DEFAULT_UP);
    }

    public static List<FrameData> placementFrames(List<Vector3d> origins,
                                                  List<Vector3d> tangents,
                                                  @Nullable Vector3d upHint) {
        return PathFrameUtils.placementFramesFromSamples(origins, tangents, upHint);
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

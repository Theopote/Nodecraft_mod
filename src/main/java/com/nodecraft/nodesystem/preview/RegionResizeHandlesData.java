package com.nodecraft.nodesystem.preview;

import net.minecraft.util.math.Vec3d;

/**
 * Preview payload for face-center region resize handles.
 * <p>
 * {@code min} is inclusive world corner; {@code maxExclusive} is exclusive (same as
 * {@link PreviewManager#showRegionBox}).
 */
public final class RegionResizeHandlesData {

    @FunctionalInterface
    public interface FaceResizeCallback {
        /**
         * @param faceAxis 0=X, 1=Y, 2=Z
         * @param towardMax true for the max face, false for the min face
         * @param newInclusiveFaceCoord snapped inclusive block coordinate for that face
         */
        void onFaceResized(int faceAxis, boolean towardMax, int newInclusiveFaceCoord);
    }

    private final Vec3d minInclusive;
    private final Vec3d maxExclusive;
    private final FaceResizeCallback callback;

    public RegionResizeHandlesData(Vec3d minInclusive, Vec3d maxExclusive, FaceResizeCallback callback) {
        this.minInclusive = minInclusive;
        this.maxExclusive = maxExclusive;
        this.callback = callback;
    }

    public Vec3d minInclusive() {
        return minInclusive;
    }

    public Vec3d maxExclusive() {
        return maxExclusive;
    }

    public FaceResizeCallback callback() {
        return callback;
    }
}

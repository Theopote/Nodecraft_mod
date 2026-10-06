package com.nodecraft.nodesystem.datatypes;

import org.joml.Vector3d;

/**
 * Test-only factory for non-canonical {@link FrameData} used to probe fail-closed consumers.
 * Production code must use {@link FrameData#orthonormal} or {@link FrameData#canonical}.
 */
public final class FrameDataTestAccess {

    private FrameDataTestAccess() {
    }

    public static FrameData unchecked(Vector3d origin, Vector3d xAxis, Vector3d yAxis, Vector3d zAxis) {
        return new FrameData(origin, xAxis, yAxis, zAxis);
    }
}

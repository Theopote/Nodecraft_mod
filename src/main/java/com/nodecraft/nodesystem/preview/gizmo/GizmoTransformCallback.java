package com.nodecraft.nodesystem.preview.gizmo;

import org.joml.Vector3d;

/**
 * Receives live transform edits from an interactive gizmo.
 */
public interface GizmoTransformCallback {

    /**
     * @param translationDelta world-space translation delta for the current drag step
     * @param rotationDeltaDeg euler rotation delta in degrees for the current drag step
     * @param scaleDelta multiplicative scale delta for the current drag step
     */
    void onTransformDelta(Vector3d translationDelta, Vector3d rotationDeltaDeg, double scaleDelta);

    default void onDragBegin() {
    }

    default void onTransformCommit() {
    }
}

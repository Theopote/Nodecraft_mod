package com.nodecraft.nodesystem.preview.gizmo;

import com.nodecraft.nodesystem.preview.TransformGizmoPreviewData;
import org.joml.Vector3d;

import java.util.UUID;

/**
 * Node that exposes transform parameters editable through a world-space gizmo.
 */
public interface GizmoTransformTarget {

    UUID getNodeId();

    String getGizmoMode();

    void setGizmoMode(String mode);

    GizmoPortConstraints getGizmoPortConstraints();

    TransformGizmoPreviewData buildGizmoPreviewData();

    void applyGizmoDelta(Vector3d translationDelta, Vector3d rotationDeltaDeg, double scaleDelta);

    Object captureGizmoUndoState();

    void restoreGizmoUndoState(Object state);

    void markGizmoDirty();
}

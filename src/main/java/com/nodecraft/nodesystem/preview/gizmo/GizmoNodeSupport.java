package com.nodecraft.nodesystem.preview.gizmo;

import com.nodecraft.nodesystem.preview.PreviewManager;
import com.nodecraft.nodesystem.preview.PreviewOptions;
import com.nodecraft.nodesystem.preview.TransformGizmoPreviewData;
import org.joml.Vector3d;

import java.util.UUID;

public final class GizmoNodeSupport {

    private GizmoNodeSupport() {
    }

    public static void showForTarget(String previewOwnerNodeId, GizmoTransformTarget target) {
        showForTarget(previewOwnerNodeId, target, null, -1.0d, null);
    }

    public static void showForTarget(
        String previewOwnerNodeId,
        GizmoTransformTarget target,
        net.minecraft.util.math.Vec3d overrideOrigin,
        double overrideAxisLength
    ) {
        showForTarget(previewOwnerNodeId, target, overrideOrigin, overrideAxisLength, null);
    }

    public static void showForTarget(
        String previewOwnerNodeId,
        GizmoTransformTarget target,
        net.minecraft.util.math.Vec3d overrideOrigin,
        double overrideAxisLength,
        String modeOverride
    ) {
        if (previewOwnerNodeId == null || target == null) {
            return;
        }
        GizmoPortConstraints constraints = target.getGizmoPortConstraints();
        if (!constraints.isInteractive()) {
            hide(previewOwnerNodeId, target.getNodeId());
            return;
        }

        TransformGizmoPreviewData data = target.buildGizmoPreviewData();
        if (data == null) {
            hide(previewOwnerNodeId, target.getNodeId());
            return;
        }
        if (overrideOrigin != null || overrideAxisLength > 0.0d) {
            data = data.withOriginAndLength(
                overrideOrigin != null ? overrideOrigin : data.getOrigin(),
                overrideAxisLength > 0.0d ? overrideAxisLength : data.getBaseAxisLength()
            );
        }
        if (modeOverride != null && !modeOverride.isBlank()) {
            String effectiveMode = constraints.resolveEffectiveMode(modeOverride);
            data = data.withGizmoType(effectiveMode);
        }

        GizmoBindingRegistry.register(previewOwnerNodeId, createCallback(target));
        PreviewOptions options = PreviewOptions.createTransformGizmo();
        options.gizmoType = data.getGizmoType();
        PreviewManager.showTransformGizmo(previewOwnerNodeId, data, options);
    }

    public static void hide(String previewOwnerNodeId, UUID bindingNodeId) {
        if (previewOwnerNodeId != null) {
            GizmoBindingRegistry.unregister(previewOwnerNodeId);
        }
        if (bindingNodeId != null) {
            GizmoUndoCoordinator.cancelDrag(bindingNodeId);
        }
        if (previewOwnerNodeId != null) {
            PreviewManager.hideNodePreviewType(previewOwnerNodeId, "transformation_gizmo");
        }
    }

    public static GizmoTransformCallback createCallback(GizmoTransformTarget target) {
        return new GizmoTransformCallback() {
            @Override
            public void onDragBegin() {
                GizmoUndoCoordinator.beginDrag(target.getNodeId(), target.captureGizmoUndoState());
            }

            @Override
            public void onTransformDelta(Vector3d translationDelta, Vector3d rotationDeltaDeg, double scaleDelta) {
                target.applyGizmoDelta(translationDelta, rotationDeltaDeg, scaleDelta);
                target.markGizmoDirty();
            }

            @Override
            public void onTransformCommit() {
                GizmoUndoCoordinator.commitDrag(
                    target.getNodeId(),
                    target.captureGizmoUndoState(),
                    "Gizmo Transform"
                );
            }
        };
    }
}

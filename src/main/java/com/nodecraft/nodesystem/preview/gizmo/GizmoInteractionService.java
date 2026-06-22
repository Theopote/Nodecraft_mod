package com.nodecraft.nodesystem.preview.gizmo;

import com.nodecraft.nodesystem.interaction.WorldPickingService;
import com.nodecraft.nodesystem.preview.AbstractPreviewElement;
import com.nodecraft.nodesystem.preview.InteractivePreviewElement;
import com.nodecraft.nodesystem.preview.PreviewRenderer;
import com.nodecraft.nodesystem.preview.elements.TransformationGizmoElement;
import net.minecraft.util.math.Vec3d;

/**
 * Routes editor mouse input to active transformation gizmos.
 */
public final class GizmoInteractionService {

    private static final double MAX_PICK_DISTANCE = 256.0d;

    private TransformationGizmoElement activeGizmo;
    private boolean leftMouseWasDown;

    private GizmoInteractionService() {
    }

    private static final class Holder {
        private static final GizmoInteractionService INSTANCE = new GizmoInteractionService();
    }

    public static GizmoInteractionService getInstance() {
        return Holder.INSTANCE;
    }

    public void update(
        WorldPickingService.Ray ray,
        boolean isLeftMouseClicked,
        boolean isLeftMouseDown,
        boolean isLeftMouseReleased
    ) {
        if (ray == null) {
            clearHover();
            return;
        }

        Vec3d rayStart = ray.origin;
        Vec3d rayDirection = ray.direction;
        PreviewRenderer renderer = PreviewRenderer.getInstance();

        if (isLeftMouseReleased) {
            if (activeGizmo != null) {
                activeGizmo.onMouseRelease(rayStart, rayDirection, 0);
                activeGizmo = null;
            } else {
                dispatchRelease(renderer, rayStart, rayDirection);
            }
            leftMouseWasDown = false;
            return;
        }

        TransformationGizmoElement dragged = findDraggedGizmo(renderer);
        if (dragged != null) {
            activeGizmo = dragged;
            if (isLeftMouseDown) {
                dragged.onMouseDrag(rayStart, rayDirection, Vec3d.ZERO);
            }
            leftMouseWasDown = isLeftMouseDown;
            return;
        }

        if (isLeftMouseClicked) {
            TransformationGizmoElement clicked = pickClosestGizmo(renderer, rayStart, rayDirection);
            if (clicked != null && clicked.onMouseClick(rayStart, rayDirection, 0)) {
                activeGizmo = clicked;
                leftMouseWasDown = true;
                return;
            }
        }

        if (isLeftMouseDown && activeGizmo != null) {
            activeGizmo.onMouseDrag(rayStart, rayDirection, Vec3d.ZERO);
            leftMouseWasDown = true;
            return;
        }

        hoverGizmos(renderer, rayStart, rayDirection);
        if (!isLeftMouseDown) {
            activeGizmo = null;
        }
        leftMouseWasDown = isLeftMouseDown;
    }

    public boolean isDraggingGizmo() {
        return activeGizmo != null && activeGizmo.isBeingDragged();
    }

    private TransformationGizmoElement findDraggedGizmo(PreviewRenderer renderer) {
        for (AbstractPreviewElement element : renderer.getActiveElements()) {
            if (element instanceof TransformationGizmoElement gizmo && gizmo.isBeingDragged()) {
                return gizmo;
            }
        }
        return null;
    }

    private TransformationGizmoElement pickClosestGizmo(PreviewRenderer renderer, Vec3d rayStart, Vec3d rayDirection) {
        TransformationGizmoElement closest = null;
        double closestDistance = Double.MAX_VALUE;

        for (AbstractPreviewElement element : renderer.getActiveElements()) {
            if (!(element instanceof TransformationGizmoElement gizmo) || !gizmo.isInteractable()) {
                continue;
            }
            if (!gizmo.intersectsRay(rayStart, rayDirection, MAX_PICK_DISTANCE)) {
                continue;
            }
            double distance = gizmo.getInteractionCenter().distanceTo(rayStart);
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = gizmo;
            }
        }
        return closest;
    }

    private void hoverGizmos(PreviewRenderer renderer, Vec3d rayStart, Vec3d rayDirection) {
        boolean anyHovered = false;
        for (AbstractPreviewElement element : renderer.getActiveElements()) {
            if (element instanceof TransformationGizmoElement gizmo && gizmo.isInteractable() && !gizmo.isBeingDragged()) {
                if (gizmo.onMouseHover(rayStart, rayDirection)) {
                    anyHovered = true;
                }
            }
        }
        if (!anyHovered) {
            clearHover(renderer);
        }
    }

    private void clearHover() {
        clearHover(PreviewRenderer.getInstance());
    }

    private void clearHover(PreviewRenderer renderer) {
        for (AbstractPreviewElement element : renderer.getActiveElements()) {
            if (element instanceof TransformationGizmoElement gizmo && !gizmo.isBeingDragged()) {
                gizmo.clearHoverState();
            }
        }
    }

    private void dispatchRelease(PreviewRenderer renderer, Vec3d rayStart, Vec3d rayDirection) {
        for (AbstractPreviewElement element : renderer.getActiveElements()) {
            if (element instanceof InteractivePreviewElement interactive && interactive.isBeingDragged()) {
                interactive.onMouseRelease(rayStart, rayDirection, 0);
            }
        }
    }
}

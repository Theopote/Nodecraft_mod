package com.nodecraft.nodesystem.preview.gizmo;

import com.nodecraft.nodesystem.interaction.WorldPickingService;
import com.nodecraft.nodesystem.preview.AbstractPreviewElement;
import com.nodecraft.nodesystem.preview.InteractivePreviewElement;
import com.nodecraft.nodesystem.preview.PreviewRenderer;
import com.nodecraft.nodesystem.preview.elements.RegionResizeHandlesElement;
import com.nodecraft.nodesystem.preview.elements.TransformationGizmoElement;
import net.minecraft.util.math.Vec3d;

/**
 * Routes editor mouse input to interactive preview elements (transform gizmos, region resize handles).
 */
public final class GizmoInteractionService {

    private static final double MAX_PICK_DISTANCE = 256.0d;

    private InteractivePreviewElement activeInteractive;
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

        Vec3d rayStart = ray.origin();
        Vec3d rayDirection = ray.direction();
        PreviewRenderer renderer = PreviewRenderer.getInstance();

        if (isLeftMouseReleased) {
            if (activeInteractive != null) {
                activeInteractive.onMouseRelease(rayStart, rayDirection, 0);
                activeInteractive = null;
            } else {
                dispatchRelease(renderer, rayStart, rayDirection);
            }
            leftMouseWasDown = false;
            return;
        }

        InteractivePreviewElement dragged = findDraggedInteractive(renderer);
        if (dragged != null) {
            activeInteractive = dragged;
            if (isLeftMouseDown) {
                dragged.onMouseDrag(rayStart, rayDirection, Vec3d.ZERO);
            }
            leftMouseWasDown = isLeftMouseDown;
            return;
        }

        if (isLeftMouseClicked) {
            InteractivePreviewElement clicked = pickClosestInteractive(renderer, rayStart, rayDirection);
            if (clicked != null && clicked.onMouseClick(rayStart, rayDirection, 0)) {
                activeInteractive = clicked;
                leftMouseWasDown = true;
                return;
            }
        }

        if (isLeftMouseDown && activeInteractive != null) {
            activeInteractive.onMouseDrag(rayStart, rayDirection, Vec3d.ZERO);
            leftMouseWasDown = true;
            return;
        }

        hoverInteractives(renderer, rayStart, rayDirection);
        if (!isLeftMouseDown) {
            activeInteractive = null;
        }
        leftMouseWasDown = isLeftMouseDown;
    }

    public boolean isDraggingGizmo() {
        return activeInteractive != null && activeInteractive.isBeingDragged();
    }

    private InteractivePreviewElement findDraggedInteractive(PreviewRenderer renderer) {
        for (AbstractPreviewElement element : renderer.getActiveElements()) {
            if (element instanceof InteractivePreviewElement interactive && interactive.isBeingDragged()) {
                return interactive;
            }
        }
        return null;
    }

    private InteractivePreviewElement pickClosestInteractive(
        PreviewRenderer renderer,
        Vec3d rayStart,
        Vec3d rayDirection
    ) {
        InteractivePreviewElement closest = null;
        double closestDistance = Double.MAX_VALUE;

        for (AbstractPreviewElement element : renderer.getActiveElements()) {
            if (!(element instanceof InteractivePreviewElement interactive) || !interactive.isInteractable()) {
                continue;
            }
            if (!interactive.intersectsRay(rayStart, rayDirection, MAX_PICK_DISTANCE)) {
                continue;
            }
            double distance = interactive.getInteractionCenter().distanceTo(rayStart);
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = interactive;
            }
        }
        return closest;
    }

    private void hoverInteractives(PreviewRenderer renderer, Vec3d rayStart, Vec3d rayDirection) {
        boolean anyHovered = false;
        for (AbstractPreviewElement element : renderer.getActiveElements()) {
            if (element instanceof InteractivePreviewElement interactive
                && interactive.isInteractable()
                && !interactive.isBeingDragged()) {
                if (interactive.onMouseHover(rayStart, rayDirection)) {
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
            } else if (element instanceof RegionResizeHandlesElement handles && !handles.isBeingDragged()) {
                handles.clearHoverState();
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

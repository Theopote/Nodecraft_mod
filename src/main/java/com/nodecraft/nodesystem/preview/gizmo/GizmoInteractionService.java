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
    /** Require this many consecutive "button up" frames before ending a drag. */
    private static final int RELEASE_CONFIRM_FRAMES = 3;

    private InteractivePreviewElement activeInteractive;
    private InteractivePreviewElement lastHoveredInteractive;
    private boolean hoveringInteractive;
    private boolean leftMouseWasDown;
    /** True from successful press until confirmed release — independent of element flags. */
    private boolean dragSessionActive;
    private int leftUpStreak;

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
        // `isLeftMouseReleased` is intentionally ignored for ending sessions: a single-frame
        // GLFW blip was ending the drag (and wiping activeFace) after 1–2 blocks of movement.
        if (isLeftMouseDown) {
            leftUpStreak = 0;
        } else {
            leftUpStreak = Math.min(leftUpStreak + 1, RELEASE_CONFIRM_FRAMES + 1);
        }

        if (ray == null) {
            if (dragSessionActive && (isLeftMouseDown || leftUpStreak < RELEASE_CONFIRM_FRAMES)) {
                // Keep session; skip this frame's ray work.
                leftMouseWasDown = isLeftMouseDown || dragSessionActive;
                return;
            }
            if (!isLeftMouseDown) {
                endDragSession(null, null);
                clearHover();
                hoveringInteractive = false;
                lastHoveredInteractive = null;
            }
            leftMouseWasDown = isLeftMouseDown;
            return;
        }

        Vec3d rayStart = ray.origin();
        Vec3d rayDirection = ray.direction();
        PreviewRenderer renderer = PreviewRenderer.getInstance();

        // Active drag session: continue until button has been up for several frames.
        if (dragSessionActive) {
            if (isLeftMouseDown || leftUpStreak < RELEASE_CONFIRM_FRAMES) {
                InteractivePreviewElement dragged = resolveDragTarget(renderer);
                if (dragged != null) {
                    activeInteractive = dragged;
                    hoveringInteractive = true;
                    dragged.onMouseDrag(rayStart, rayDirection, Vec3d.ZERO);
                }
                leftMouseWasDown = true;
                return;
            }
            endDragSession(rayStart, rayDirection);
            leftMouseWasDown = false;
            hoverInteractives(renderer, rayStart, rayDirection);
            return;
        }

        InteractivePreviewElement alreadyDragging = findDraggedInteractive(renderer);
        if (isLeftMouseDown && alreadyDragging != null) {
            activeInteractive = alreadyDragging;
            lastHoveredInteractive = alreadyDragging;
            dragSessionActive = true;
            hoveringInteractive = true;
            alreadyDragging.onMouseDrag(rayStart, rayDirection, Vec3d.ZERO);
            leftMouseWasDown = true;
            return;
        }

        // Start only on rising edge — never re-click every held frame (that resets drag origin).
        if (isLeftMouseClicked || (isLeftMouseDown && !leftMouseWasDown)) {
            InteractivePreviewElement target = null;
            if (lastHoveredInteractive != null && lastHoveredInteractive.isInteractable()) {
                boolean stickyOk = lastHoveredInteractive instanceof RegionResizeHandlesElement handles
                    ? (handles.isHandleHovered()
                        || handles.intersectsRay(rayStart, rayDirection, MAX_PICK_DISTANCE))
                    : lastHoveredInteractive.intersectsRay(rayStart, rayDirection, MAX_PICK_DISTANCE);
                if (stickyOk) {
                    target = lastHoveredInteractive;
                }
            }
            if (target == null) {
                target = pickClosestInteractive(renderer, rayStart, rayDirection);
            }
            if (target != null && target.onMouseClick(rayStart, rayDirection, 0)) {
                activeInteractive = target;
                lastHoveredInteractive = target;
                dragSessionActive = true;
                hoveringInteractive = true;
                target.onMouseDrag(rayStart, rayDirection, Vec3d.ZERO);
                leftMouseWasDown = true;
                return;
            }
        }

        hoverInteractives(renderer, rayStart, rayDirection);
        if (!isLeftMouseDown) {
            activeInteractive = null;
            dragSessionActive = false;
        }
        leftMouseWasDown = isLeftMouseDown;
    }

    public boolean isDraggingGizmo() {
        return dragSessionActive
            || (activeInteractive != null && activeInteractive.isBeingDragged());
    }

    /** True while a handle/gizmo is hovered or dragged — world clicks/picks should be locked out. */
    public boolean isCapturingInput() {
        return hoveringInteractive || isDraggingGizmo();
    }

    /** Call near end of ImGui frame so resize cursors win over default Arrow. */
    public void refreshHoverCursor() {
        for (AbstractPreviewElement element : PreviewRenderer.getInstance().getActiveElements()) {
            if (element instanceof RegionResizeHandlesElement handles && handles.isHandleHovered()) {
                handles.refreshHoverCursor();
            }
        }
    }

    private void endDragSession(Vec3d rayStart, Vec3d rayDirection) {
        if (activeInteractive != null) {
            if (rayStart != null && rayDirection != null) {
                activeInteractive.onMouseRelease(rayStart, rayDirection, 0);
            } else if (activeInteractive instanceof RegionResizeHandlesElement handles) {
                handles.endDrag();
            } else {
                activeInteractive.setBeingDragged(false);
            }
            activeInteractive = null;
        } else if (rayStart != null && rayDirection != null) {
            dispatchRelease(PreviewRenderer.getInstance(), rayStart, rayDirection);
        }
        dragSessionActive = false;
        leftUpStreak = 0;
    }

    private InteractivePreviewElement resolveDragTarget(PreviewRenderer renderer) {
        InteractivePreviewElement dragged = findDraggedInteractive(renderer);
        if (dragged != null) {
            return dragged;
        }
        if (activeInteractive == null) {
            return null;
        }
        for (AbstractPreviewElement element : renderer.getActiveElements()) {
            if (element == activeInteractive) {
                return activeInteractive;
            }
        }
        String owner = null;
        if (activeInteractive instanceof AbstractPreviewElement ape) {
            owner = ape.getOwnerNodeId();
        }
        if (owner != null) {
            for (AbstractPreviewElement element : renderer.getActiveElements()) {
                if (element instanceof InteractivePreviewElement interactive
                    && owner.equals(element.getOwnerNodeId())
                    && interactive.isInteractable()
                    && element.getClass() == activeInteractive.getClass()) {
                    return interactive;
                }
            }
        }
        // Keep calling the old instance so in-memory drag fields (activeFace, etc.) still apply
        // even if it was briefly removed from the active map.
        return activeInteractive;
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
            double distance = distanceRayToPoint(rayStart, rayDirection, interactive.getInteractionCenter());
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = interactive;
            }
        }
        return closest;
    }

    private static double distanceRayToPoint(Vec3d rayStart, Vec3d rayDirection, Vec3d point) {
        Vec3d dir = rayDirection.lengthSquared() > 1.0e-12d ? rayDirection.normalize() : new Vec3d(0, 0, 1);
        Vec3d toPoint = point.subtract(rayStart);
        double t = Math.max(0.0d, toPoint.dotProduct(dir));
        Vec3d closest = rayStart.add(dir.multiply(t));
        return closest.distanceTo(point);
    }

    private void hoverInteractives(PreviewRenderer renderer, Vec3d rayStart, Vec3d rayDirection) {
        boolean anyHovered = false;
        InteractivePreviewElement hovered = null;
        for (AbstractPreviewElement element : renderer.getActiveElements()) {
            if (element instanceof InteractivePreviewElement interactive
                && interactive.isInteractable()
                && !interactive.isBeingDragged()) {
                if (interactive.onMouseHover(rayStart, rayDirection)) {
                    anyHovered = true;
                    hovered = interactive;
                }
            }
        }
        hoveringInteractive = anyHovered || isDraggingGizmo();
        if (anyHovered) {
            lastHoveredInteractive = hovered;
        } else {
            clearHover(renderer);
            lastHoveredInteractive = null;
        }
    }

    private void clearHover() {
        clearHover(PreviewRenderer.getInstance());
        hoveringInteractive = false;
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

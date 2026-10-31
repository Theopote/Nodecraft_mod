package com.nodecraft.nodesystem.preview.elements;

import com.nodecraft.nodesystem.preview.AbstractPreviewElement;
import com.nodecraft.nodesystem.preview.InteractivePreviewElement;
import com.nodecraft.nodesystem.preview.PreviewOptions;
import com.nodecraft.nodesystem.preview.PreviewRenderer;
import com.nodecraft.nodesystem.preview.RegionResizeHandlesData;
import com.nodecraft.nodesystem.preview.gizmo.GizmoMath;
import imgui.ImGui;
import imgui.flag.ImGuiMouseCursor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Six face-center resize handles for an AABB region (WorldEdit-style gizmo points).
 */
public class RegionResizeHandlesElement extends AbstractPreviewElement implements InteractivePreviewElement {

    private static final double HANDLE_RADIUS = 0.10d;
    private static final double ARROW_LENGTH = 0.32d;
    private static final int FACE_COUNT = 6;
    /** World-space pick radius near the camera; grows gently with distance. */
    private static final double BASE_PICK_RADIUS = 0.22d;
    private static final double STICKY_PICK_SCALE = 1.55d;

    private volatile RegionResizeHandlesData handlesData;
    private float interactionRadius = 0.28f;

    private boolean interactable = true;
    private boolean beingDragged = false;
    private int hoveredFace = -1;
    private int activeFace = -1;
    private Vec3d dragPlaneNormal = Vec3d.ZERO;
    private Vec3d dragAxis = new Vec3d(1.0d, 0.0d, 0.0d);
    private Vec3d dragAxisOrigin = Vec3d.ZERO;
    private Vec3d dragStartPoint = Vec3d.ZERO;
    private double dragStartAlong = 0.0d;
    private float dragStartMouseX = 0.0f;
    private float dragStartMouseY = 0.0f;
    private double dragScreenAxisX = 1.0d;
    private double dragScreenAxisY = 0.0d;
    private double dragPixelsPerBlock = 40.0d;
    private boolean dragUseScreenSpace = false;
    private int dragStartFaceCoord = 0;
    private int lastEmittedFaceCoord = 0;
    private int cursorFace = -1;

    public RegionResizeHandlesElement(String id, String ownerNodeId, Object data, PreviewOptions options) {
        super(id, ownerNodeId, data, options);
        this.renderPriority = options != null && options.renderPriority != null ? options.renderPriority : 1000;
        if (options != null && options.interactionRadius != null) {
            this.interactionRadius = Math.max(0.2f, options.interactionRadius);
        }
    }

    @Override
    protected void processData(Object data) {
        if (data instanceof RegionResizeHandlesData handles) {
            this.handlesData = handles;
            return;
        }
        this.handlesData = null;
    }

    @Override
    public void updateOptions(PreviewOptions newOptions) {
        super.updateOptions(newOptions);
        if (newOptions != null && newOptions.interactionRadius != null) {
            this.interactionRadius = Math.max(0.1f, newOptions.interactionRadius);
        }
        if (newOptions != null && newOptions.renderPriority != null) {
            this.renderPriority = newOptions.renderPriority;
        }
    }

    @Override
    public void render(MatrixStack matrices, Camera camera, float partialTicks, float globalOpacity) {
        RegionResizeHandlesData data = handlesData;
        if (data == null) {
            return;
        }

        float finalOpacity = opacity * globalOpacity;
        if (finalOpacity <= 0.01f) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        VertexConsumerProvider provider = PreviewRenderer.getInstance().getActiveVertexConsumers();
        VertexConsumerProvider.Immediate immediate = null;
        boolean flushImmediately = false;
        if (provider == null) {
            immediate = client.getBufferBuilders().getEntityVertexConsumers();
            provider = immediate;
            flushImmediately = true;
        }

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Vec3d cameraPos = camera.getCameraPos();
        Vec3d cameraLook = camera.getFocusedEntity() != null
            ? camera.getFocusedEntity().getRotationVec(partialTicks)
            : new Vec3d(0.0d, 0.0d, 1.0d);

        VertexConsumer lines = provider.getBuffer(RenderLayers.lines());
        for (int face = 0; face < FACE_COUNT; face++) {
            Vec3d center = faceCenter(data, face);
            Vec3d normal = faceNormal(face);
            boolean frontFacing = cameraLook.dotProduct(normal) < 0.0d;
            boolean highlighted = face == hoveredFace || face == activeFace;

            float[] axisColor = axisRgb(faceAxis(face));
            float boost = highlighted ? 0.25f : 0.0f;
            float r = Math.min(1.0f, axisColor[0] + boost);
            float g = Math.min(1.0f, axisColor[1] + boost);
            float b = Math.min(1.0f, axisColor[2] + boost);
            // Back faces stay same hue but dimmer so the user knows which side is behind.
            float alpha = frontFacing ? finalOpacity : finalOpacity * 0.4f;

            Vec3d local = center.subtract(cameraPos);
            drawHandleCube(lines, matrix, local, HANDLE_RADIUS * (highlighted ? 1.35d : 1.0d), r, g, b, alpha);

            Vec3d arrowA = local.add(normal.multiply(ARROW_LENGTH));
            Vec3d arrowB = local.subtract(normal.multiply(ARROW_LENGTH));
            if (frontFacing) {
                drawLine(lines, matrix, arrowA, arrowB, r, g, b, alpha, highlighted ? 3.2f : 2.2f);
                drawArrowTips(lines, matrix, local, normal, r, g, b, alpha);
            } else {
                drawDashedLine(lines, matrix, arrowA, arrowB, r, g, b, alpha * 0.85f, 1.8f);
            }
        }

        if (flushImmediately && immediate != null) {
            immediate.draw();
        }
    }

    private void drawHandleCube(
        VertexConsumer consumer,
        Matrix4f matrix,
        Vec3d center,
        double size,
        float r,
        float g,
        float b,
        float alpha
    ) {
        double h = size;
        Vec3d[] corners = new Vec3d[] {
            center.add(-h, -h, -h), center.add(h, -h, -h), center.add(h, h, -h), center.add(-h, h, -h),
            center.add(-h, -h, h), center.add(h, -h, h), center.add(h, h, h), center.add(-h, h, h)
        };
        int[][] edges = {
            {0, 1}, {1, 2}, {2, 3}, {3, 0},
            {4, 5}, {5, 6}, {6, 7}, {7, 4},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };
        for (int[] edge : edges) {
            drawLine(consumer, matrix, corners[edge[0]], corners[edge[1]], r, g, b, alpha, 2.0f);
        }
    }

    private void drawArrowTips(
        VertexConsumer consumer,
        Matrix4f matrix,
        Vec3d center,
        Vec3d normal,
        float r,
        float g,
        float b,
        float alpha
    ) {
        Vec3d tip = center.add(normal.multiply(ARROW_LENGTH));
        Vec3d tipNeg = center.subtract(normal.multiply(ARROW_LENGTH));
        Vec3d reference = Math.abs(normal.y) < 0.9d ? new Vec3d(0.0d, 1.0d, 0.0d) : new Vec3d(1.0d, 0.0d, 0.0d);
        Vec3d side = normal.crossProduct(reference).normalize().multiply(0.08d);
        Vec3d back = normal.multiply(-0.12d);
        drawLine(consumer, matrix, tip, tip.add(back).add(side), r, g, b, alpha, 2.0f);
        drawLine(consumer, matrix, tip, tip.add(back).subtract(side), r, g, b, alpha, 2.0f);
        Vec3d backNeg = normal.multiply(0.12d);
        drawLine(consumer, matrix, tipNeg, tipNeg.add(backNeg).add(side), r, g, b, alpha, 2.0f);
        drawLine(consumer, matrix, tipNeg, tipNeg.add(backNeg).subtract(side), r, g, b, alpha, 2.0f);
    }

    private void drawDashedLine(
        VertexConsumer consumer,
        Matrix4f matrix,
        Vec3d start,
        Vec3d end,
        float r,
        float g,
        float b,
        float alpha,
        float lineWidth
    ) {
        Vec3d delta = end.subtract(start);
        int segments = 6;
        for (int i = 0; i < segments; i += 2) {
            double t0 = (double) i / segments;
            double t1 = (double) (i + 1) / segments;
            drawLine(consumer, matrix, start.add(delta.multiply(t0)), start.add(delta.multiply(t1)), r, g, b, alpha, lineWidth);
        }
    }

    private void drawLine(
        VertexConsumer consumer,
        Matrix4f matrix,
        Vec3d start,
        Vec3d end,
        float r,
        float g,
        float b,
        float alpha,
        float lineWidth
    ) {
        Vector3f normal = new Vector3f((float) (end.x - start.x), (float) (end.y - start.y), (float) (end.z - start.z));
        if (normal.lengthSquared() < 1.0e-12f) {
            normal.set(0.0f, 1.0f, 0.0f);
        } else {
            normal.normalize();
        }
        consumer.vertex(matrix, (float) start.x, (float) start.y, (float) start.z)
            .color(r, g, b, alpha)
            .normal(normal.x, normal.y, normal.z)
            .lineWidth(lineWidth);
        consumer.vertex(matrix, (float) end.x, (float) end.y, (float) end.z)
            .color(r, g, b, alpha)
            .normal(normal.x, normal.y, normal.z)
            .lineWidth(lineWidth);
    }

    private static Vec3d faceCenter(RegionResizeHandlesData data, int face) {
        Vec3d min = data.minInclusive();
        Vec3d max = data.maxExclusive();
        double cx = (min.x + max.x) * 0.5d;
        double cy = (min.y + max.y) * 0.5d;
        double cz = (min.z + max.z) * 0.5d;
        return switch (face) {
            case 0 -> new Vec3d(min.x, cy, cz);          // -X
            case 1 -> new Vec3d(max.x, cy, cz);          // +X
            case 2 -> new Vec3d(cx, min.y, cz);          // -Y
            case 3 -> new Vec3d(cx, max.y, cz);          // +Y
            case 4 -> new Vec3d(cx, cy, min.z);          // -Z
            case 5 -> new Vec3d(cx, cy, max.z);          // +Z
            default -> new Vec3d(cx, cy, cz);
        };
    }

    private static Vec3d faceNormal(int face) {
        return switch (face) {
            case 0 -> new Vec3d(-1.0d, 0.0d, 0.0d);
            case 1 -> new Vec3d(1.0d, 0.0d, 0.0d);
            case 2 -> new Vec3d(0.0d, -1.0d, 0.0d);
            case 3 -> new Vec3d(0.0d, 1.0d, 0.0d);
            case 4 -> new Vec3d(0.0d, 0.0d, -1.0d);
            case 5 -> new Vec3d(0.0d, 0.0d, 1.0d);
            default -> new Vec3d(0.0d, 1.0d, 0.0d);
        };
    }

    private static int faceAxis(int face) {
        return face / 2;
    }

    private static boolean faceTowardMax(int face) {
        return (face % 2) == 1;
    }

    /** Always +X/+Y/+Z so min-face outward normals do not invert drag direction. */
    private static Vec3d faceAxisDir(int face) {
        return switch (faceAxis(face)) {
            case 0 -> new Vec3d(1.0d, 0.0d, 0.0d);
            case 1 -> new Vec3d(0.0d, 1.0d, 0.0d);
            default -> new Vec3d(0.0d, 0.0d, 1.0d);
        };
    }

    /** X=red, Y=green, Z=blue (standard transform gizmo colors). */
    private static float[] axisRgb(int axis) {
        return switch (axis) {
            case 0 -> new float[] { 1.0f, 0.22f, 0.22f };
            case 1 -> new float[] { 0.25f, 0.95f, 0.3f };
            default -> new float[] { 0.25f, 0.5f, 1.0f };
        };
    }

    public boolean isHandleHovered() {
        return hoveredFace >= 0 || beingDragged;
    }

    /** Re-apply ImGui cursor late in the frame so UI widgets do not overwrite it. */
    public void refreshHoverCursor() {
        int face = beingDragged ? activeFace : hoveredFace;
        if (face >= 0) {
            applyResizeCursor(face);
        }
    }

    private int pickFace(Vec3d rayStart, Vec3d rayDirection, double maxDistance) {
        RegionResizeHandlesData data = handlesData;
        if (data == null || rayDirection == null) {
            return -1;
        }
        int best = -1;
        double bestDist = Double.MAX_VALUE;
        for (int face = 0; face < FACE_COUNT; face++) {
            Vec3d center = faceCenter(data, face);
            Vec3d normal = faceNormal(face);
            // Match the drawn arrow segment so hover lines up with what the user sees.
            Vec3d segA = center.add(normal.multiply(ARROW_LENGTH));
            Vec3d segB = center.subtract(normal.multiply(ARROW_LENGTH));
            double distance = Math.max(1.0d, center.distanceTo(rayStart));
            if (distance > maxDistance) {
                continue;
            }
            double pickR = pickRadiusForDistance(distance, face == hoveredFace);
            double perp = GizmoMath.rayToSegmentDistance(rayStart, rayDirection, segA, segB);
            if (perp <= pickR && perp < bestDist) {
                bestDist = perp;
                best = face;
            }
        }
        return best;
    }

    private double pickRadiusForDistance(double distance, boolean sticky) {
        double base = Math.max(BASE_PICK_RADIUS, interactionRadius);
        // ~1.6° screen cone, capped so far handles do not become huge hit targets.
        double scaled = Math.min(0.55d, Math.max(base, distance * 0.022d));
        return sticky ? scaled * STICKY_PICK_SCALE : scaled;
    }

    @Override
    public boolean intersectsRay(Vec3d rayStart, Vec3d rayDirection, double maxDistance) {
        return pickFace(rayStart, rayDirection, maxDistance) >= 0;
    }

    @Override
    public boolean onMouseClick(Vec3d rayStart, Vec3d rayDirection, int button) {
        if (!interactable || button != 0) {
            return false;
        }
        if (beingDragged && activeFace >= 0) {
            return true;
        }
        // Prefer already-hovered face so press-while-hover always starts drag.
        int face = hoveredFace >= 0 ? hoveredFace : pickFace(rayStart, rayDirection, 256.0d);
        if (face < 0) {
            return false;
        }
        RegionResizeHandlesData data = handlesData;
        if (data == null) {
            return false;
        }

        activeFace = face;
        hoveredFace = face;
        beingDragged = true;
        dragAxis = faceAxisDir(face);
        Vec3d center = faceCenter(data, face);
        dragAxisOrigin = center;
        dragPlaneNormal = GizmoMath.cameraFacingPlaneNormal(dragAxis, rayStart.subtract(center));
        Vec3d axisHit = GizmoMath.closestPointOnAxis(rayStart, rayDirection, center, dragAxis);
        if (axisHit == null) {
            axisHit = GizmoMath.intersectRayWithPlane(rayStart, rayDirection, center, dragPlaneNormal);
        }
        dragStartPoint = axisHit != null ? axisHit : center;
        dragStartAlong = dragStartPoint.subtract(dragAxisOrigin).dotProduct(dragAxis);
        dragStartFaceCoord = inclusiveFaceCoord(data, face);
        lastEmittedFaceCoord = dragStartFaceCoord;

        // Screen-space drag: mouse pixel motion along the projected +axis. Logs showed world
        // axis-closest-point deltas plateau after ~1 block while the cursor kept moving.
        dragStartMouseX = ImGui.getIO().getMousePosX();
        dragStartMouseY = ImGui.getIO().getMousePosY();
        dragUseScreenSpace = initScreenSpaceDragAxis(center, dragAxis);

        applyResizeCursor(face);
        com.nodecraft.core.NodeCraft.LOGGER.info(
            "RegionResize drag START face={} startCoord={} screenSpace={} pxPerBlock={}",
            face, dragStartFaceCoord, dragUseScreenSpace, dragPixelsPerBlock
        );
        return true;
    }

    @Override
    public boolean onMouseDrag(Vec3d rayStart, Vec3d rayDirection, Vec3d deltaMovement) {
        // activeFace is the source of truth; beingDragged may briefly desync after input glitches.
        if (activeFace < 0) {
            return false;
        }
        beingDragged = true;
        RegionResizeHandlesData data = handlesData;
        if (data == null || data.callback() == null) {
            return false;
        }

        double screenDelta = Double.NaN;
        if (dragUseScreenSpace && dragPixelsPerBlock > 1.0d) {
            float mx = ImGui.getIO().getMousePosX();
            float my = ImGui.getIO().getMousePosY();
            double alongPixels = (mx - dragStartMouseX) * dragScreenAxisX
                + (my - dragStartMouseY) * dragScreenAxisY;
            screenDelta = alongPixels / dragPixelsPerBlock;
        }

        double planeDelta = Double.NaN;
        Vec3d hit = GizmoMath.intersectRayWithPlane(rayStart, rayDirection, dragStartPoint, dragPlaneNormal);
        if (hit != null) {
            planeDelta = hit.subtract(dragAxisOrigin).dotProduct(dragAxis) - dragStartAlong;
        }

        final double deltaAlong;
        if (!Double.isNaN(screenDelta) && !Double.isNaN(planeDelta)) {
            // Whichever moved farther from the re-anchor — screen can stall on bad scale,
            // plane can plateau; taking the larger keeps slow continuous drags alive.
            deltaAlong = Math.abs(screenDelta) >= Math.abs(planeDelta) ? screenDelta : planeDelta;
        } else if (!Double.isNaN(screenDelta)) {
            deltaAlong = screenDelta;
        } else if (!Double.isNaN(planeDelta)) {
            deltaAlong = planeDelta;
        } else {
            return true;
        }

        int newCoord = (int) Math.round(dragStartFaceCoord + deltaAlong);
        if (newCoord == lastEmittedFaceCoord) {
            return true;
        }
        com.nodecraft.core.NodeCraft.LOGGER.info(
            "RegionResize drag MOVE face={} delta={} screen={} plane={} {} -> {}",
            activeFace, deltaAlong, screenDelta, planeDelta, lastEmittedFaceCoord, newCoord
        );
        lastEmittedFaceCoord = newCoord;
        data.callback().onFaceResized(faceAxis(activeFace), faceTowardMax(activeFace), newCoord);
        // Re-anchor after each step so follow-the-handle motion only needs ~1 block of travel
        // per cell. Without this, an overstated pixels/block scale caps slow drags at 1–2 cells.
        reanchorDrag(rayStart, rayDirection, newCoord);
        return true;
    }

    /** Resets drag baselines to the current cursor after a face coord is committed. */
    private void reanchorDrag(Vec3d rayStart, Vec3d rayDirection, int faceCoord) {
        dragStartFaceCoord = faceCoord;
        dragStartMouseX = ImGui.getIO().getMousePosX();
        dragStartMouseY = ImGui.getIO().getMousePosY();
        Vec3d hit = GizmoMath.intersectRayWithPlane(rayStart, rayDirection, dragAxisOrigin, dragPlaneNormal);
        if (hit == null) {
            hit = GizmoMath.closestPointOnAxis(rayStart, rayDirection, dragAxisOrigin, dragAxis);
        }
        if (hit != null) {
            dragStartPoint = hit;
            dragStartAlong = hit.subtract(dragAxisOrigin).dotProduct(dragAxis);
        }
    }

    /**
     * Maps the world +axis into ImGui mouse pixel space using the camera right/up basis.
     * @return false when looking nearly along the axis
     */
    private boolean initScreenSpaceDragAxis(Vec3d origin, Vec3d axis) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.gameRenderer == null || client.getWindow() == null) {
            return false;
        }
        Camera camera = client.gameRenderer.getCamera();
        if (camera == null) {
            return false;
        }

        var q = camera.getRotation();
        Vector3f right = new Vector3f(1.0f, 0.0f, 0.0f).rotate(q);
        Vector3f up = new Vector3f(0.0f, 1.0f, 0.0f).rotate(q);
        double sx = axis.x * right.x + axis.y * right.y + axis.z * right.z;
        // ImGui Y grows downward; camera up is opposite on screen.
        double sy = -(axis.x * up.x + axis.y * up.y + axis.z * up.z);
        double planeLen = Math.hypot(sx, sy);
        if (planeLen < 0.08d) {
            return false;
        }
        dragScreenAxisX = sx / planeLen;
        dragScreenAxisY = sy / planeLen;

        double dist = Math.max(1.0d, camera.getCameraPos().distanceTo(origin));
        float fovDeg = (float) client.options.getFov().getValue();
        try {
            if (client.gameRenderer instanceof com.nodecraft.mixin.accessor.GameRendererAccessor accessor) {
                float actual = accessor.nodecraft$invokeGetFov(camera, 0.0f, true);
                if (actual > 0 && actual < 180) {
                    fovDeg = actual;
                }
            }
        } catch (Throwable ignored) {
        }
        // Mouse positions are in ImGui display space — must match that, not raw window pixels.
        float displayH = ImGui.getIO().getDisplaySizeY();
        if (displayH <= 1.0f) {
            displayH = client.getWindow().getHeight();
        }
        if (displayH <= 1.0f) {
            return false;
        }
        double fullPixelsPerBlock = (displayH * 0.5d) / (Math.tan(Math.toRadians(fovDeg) * 0.5d) * dist);
        // Slightly softer than pure projection so slow follow-the-handle feels one-to-one.
        dragPixelsPerBlock = fullPixelsPerBlock * planeLen * 0.85d;
        return dragPixelsPerBlock > 2.0d;
    }

    @Override
    public boolean onMouseRelease(Vec3d rayStart, Vec3d rayDirection, int button) {
        endDrag();
        return true;
    }

    /** Clears drag geometry; only call on confirmed mouse-up (not single-frame blips). */
    public void endDrag() {
        beingDragged = false;
        activeFace = -1;
        dragAxis = new Vec3d(1.0d, 0.0d, 0.0d);
        restoreCursor();
    }

    @Override
    public boolean onMouseHover(Vec3d rayStart, Vec3d rayDirection) {
        if (!interactable || beingDragged) {
            return false;
        }
        int face = pickFace(rayStart, rayDirection, 256.0d);
        hoveredFace = face;
        if (face >= 0) {
            applyResizeCursor(face);
            return true;
        }
        restoreCursor();
        return false;
    }

    public void clearHoverState() {
        if (!beingDragged) {
            hoveredFace = -1;
            restoreCursor();
        }
    }

    private static int inclusiveFaceCoord(RegionResizeHandlesData data, int face) {
        Vec3d min = data.minInclusive();
        Vec3d maxEx = data.maxExclusive();
        return switch (face) {
            case 0 -> (int) Math.floor(min.x);
            case 1 -> (int) Math.ceil(maxEx.x) - 1;
            case 2 -> (int) Math.floor(min.y);
            case 3 -> (int) Math.ceil(maxEx.y) - 1;
            case 4 -> (int) Math.floor(min.z);
            case 5 -> (int) Math.ceil(maxEx.z) - 1;
            default -> 0;
        };
    }

    private void applyResizeCursor(int face) {
        cursorFace = face;
        // ImGui owns the OS cursor while NodecraftScreen is open; GLFW.glfwSetCursor is overwritten.
        int cursor = switch (faceAxis(face)) {
            case 1 -> ImGuiMouseCursor.ResizeNS;   // Y: vertical double-arrow
            case 0, 2 -> ImGuiMouseCursor.ResizeEW; // X/Z: horizontal double-arrow
            default -> ImGuiMouseCursor.ResizeAll;
        };
        try {
            ImGui.setMouseCursor(cursor);
        } catch (Throwable ignored) {
            // ImGui may not have a frame yet; ignore.
        }
    }

    private void restoreCursor() {
        if (cursorFace < 0) {
            return;
        }
        cursorFace = -1;
        try {
            ImGui.setMouseCursor(ImGuiMouseCursor.Arrow);
        } catch (Throwable ignored) {
            // ignore
        }
    }

    @Override
    public boolean shouldRender(Camera camera) {
        RegionResizeHandlesData data = handlesData;
        if (data == null || isExpired() || !visible) {
            return false;
        }
        Vec3d center = new Vec3d(
            (data.minInclusive().x + data.maxExclusive().x) * 0.5d,
            (data.minInclusive().y + data.maxExclusive().y) * 0.5d,
            (data.minInclusive().z + data.maxExclusive().z) * 0.5d
        );
        double size = Math.max(
            Math.max(data.maxExclusive().x - data.minInclusive().x, data.maxExclusive().y - data.minInclusive().y),
            data.maxExclusive().z - data.minInclusive().z
        );
        return camera.getCameraPos().distanceTo(center)
            <= PreviewRenderer.getInstance().getSettings().maxRenderDistance + size;
    }

    @Override
    public void cleanup() {
        endDrag();
        handlesData = null;
        hoveredFace = -1;
    }

    @Override
    public boolean isBeingDragged() {
        return beingDragged;
    }

    @Override
    public void setBeingDragged(boolean dragged) {
        this.beingDragged = dragged;
        // Do NOT clear activeFace here — mid-drag flag glitches must not wipe drag geometry.
        // Only {@link #endDrag()} / {@link #onMouseRelease} may reset the face.
        if (!dragged) {
            restoreCursor();
        }
    }

    @Override
    public float getInteractionRadius() {
        return interactionRadius;
    }

    @Override
    public Vec3d getInteractionCenter() {
        RegionResizeHandlesData data = handlesData;
        if (data == null) {
            return Vec3d.ZERO;
        }
        if (activeFace >= 0) {
            return faceCenter(data, activeFace);
        }
        if (hoveredFace >= 0) {
            return faceCenter(data, hoveredFace);
        }
        return new Vec3d(
            (data.minInclusive().x + data.maxExclusive().x) * 0.5d,
            (data.minInclusive().y + data.maxExclusive().y) * 0.5d,
            (data.minInclusive().z + data.maxExclusive().z) * 0.5d
        );
    }

    @Override
    public boolean isInteractable() {
        return interactable && handlesData != null;
    }

    @Override
    public void setInteractable(boolean interactable) {
        this.interactable = interactable;
    }
}

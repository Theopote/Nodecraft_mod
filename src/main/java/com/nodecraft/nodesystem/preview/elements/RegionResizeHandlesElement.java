package com.nodecraft.nodesystem.preview.elements;

import com.nodecraft.nodesystem.preview.AbstractPreviewElement;
import com.nodecraft.nodesystem.preview.InteractivePreviewElement;
import com.nodecraft.nodesystem.preview.PreviewOptions;
import com.nodecraft.nodesystem.preview.PreviewRenderer;
import com.nodecraft.nodesystem.preview.RegionResizeHandlesData;
import com.nodecraft.nodesystem.preview.gizmo.GizmoMath;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

/**
 * Six face-center resize handles for an AABB region (WorldEdit-style gizmo points).
 */
public class RegionResizeHandlesElement extends AbstractPreviewElement implements InteractivePreviewElement {

    private static final double HANDLE_RADIUS = 0.22d;
    private static final double ARROW_LENGTH = 0.45d;
    private static final int FACE_COUNT = 6;

    private volatile RegionResizeHandlesData handlesData;
    private float interactionRadius = 0.45f;

    private boolean interactable = true;
    private boolean beingDragged = false;
    private int hoveredFace = -1;
    private int activeFace = -1;
    private Vec3d dragPlaneNormal = Vec3d.ZERO;
    private Vec3d dragStartPoint = Vec3d.ZERO;
    private int dragStartFaceCoord = 0;
    private boolean cursorOverridden = false;
    private static long hResizeCursor;
    private static long vResizeCursor;
    private static long allResizeCursor;

    public RegionResizeHandlesElement(String id, String ownerNodeId, Object data, PreviewOptions options) {
        super(id, ownerNodeId, data, options);
        this.renderPriority = options != null && options.renderPriority != null ? options.renderPriority : 1000;
        if (options != null && options.interactionRadius != null) {
            this.interactionRadius = Math.max(0.1f, options.interactionRadius);
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

            float alpha = frontFacing ? finalOpacity : finalOpacity * 0.35f;
            float r = highlighted ? 1.0f : (frontFacing ? 1.0f : 0.55f);
            float g = highlighted ? 0.95f : (frontFacing ? 0.82f : 0.45f);
            float b = highlighted ? 0.35f : (frontFacing ? 0.27f : 0.2f);

            Vec3d local = center.subtract(cameraPos);
            drawHandleCube(lines, matrix, local, HANDLE_RADIUS * (highlighted ? 1.25d : 1.0d), r, g, b, alpha);

            Vec3d arrowA = local.add(normal.multiply(ARROW_LENGTH));
            Vec3d arrowB = local.subtract(normal.multiply(ARROW_LENGTH));
            if (frontFacing) {
                drawLine(lines, matrix, arrowA, arrowB, r, g, b, alpha, highlighted ? 3.5f : 2.5f);
                drawArrowTips(lines, matrix, local, normal, r, g, b, alpha);
            } else {
                // Dashed bidirectional indicator for back faces.
                drawDashedLine(lines, matrix, arrowA, arrowB, r, g, b, alpha * 0.85f, 2.0f);
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
        Vec3d side = normal.crossProduct(reference).normalize().multiply(0.12d);
        Vec3d back = normal.multiply(-0.18d);
        drawLine(consumer, matrix, tip, tip.add(back).add(side), r, g, b, alpha, 2.0f);
        drawLine(consumer, matrix, tip, tip.add(back).subtract(side), r, g, b, alpha, 2.0f);
        Vec3d backNeg = normal.multiply(0.18d);
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

    private int pickFace(Vec3d rayStart, Vec3d rayDirection, double maxDistance) {
        RegionResizeHandlesData data = handlesData;
        if (data == null) {
            return -1;
        }
        int best = -1;
        double bestDist = Double.MAX_VALUE;
        double pickR = Math.max(interactionRadius, HANDLE_RADIUS * 1.5d);
        for (int face = 0; face < FACE_COUNT; face++) {
            Vec3d center = faceCenter(data, face);
            Vec3d toCenter = center.subtract(rayStart);
            double t = toCenter.dotProduct(rayDirection.normalize());
            if (t < 0.0d || t > maxDistance) {
                continue;
            }
            Vec3d closest = rayStart.add(rayDirection.normalize().multiply(t));
            if (closest.distanceTo(center) <= pickR && t < bestDist) {
                bestDist = t;
                best = face;
            }
        }
        return best;
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
        int face = pickFace(rayStart, rayDirection, 256.0d);
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
        Vec3d normal = faceNormal(face);
        Vec3d center = faceCenter(data, face);
        dragPlaneNormal = GizmoMath.cameraFacingPlaneNormal(normal, rayDirection);
        dragStartPoint = GizmoMath.intersectRayWithPlane(rayStart, rayDirection, center, dragPlaneNormal);
        if (dragStartPoint == null) {
            dragStartPoint = center;
        }
        dragStartFaceCoord = inclusiveFaceCoord(data, face);
        applyResizeCursor(face);
        return true;
    }

    @Override
    public boolean onMouseDrag(Vec3d rayStart, Vec3d rayDirection, Vec3d deltaMovement) {
        if (!beingDragged || activeFace < 0) {
            return false;
        }
        RegionResizeHandlesData data = handlesData;
        if (data == null || data.callback() == null) {
            return false;
        }

        Vec3d hit = GizmoMath.intersectRayWithPlane(rayStart, rayDirection, dragStartPoint, dragPlaneNormal);
        if (hit == null) {
            return true;
        }

        Vec3d normal = faceNormal(activeFace);
        double deltaAlong = hit.subtract(dragStartPoint).dotProduct(normal);
        int newCoord = dragStartFaceCoord + (int) Math.round(deltaAlong);
        data.callback().onFaceResized(faceAxis(activeFace), faceTowardMax(activeFace), newCoord);
        return true;
    }

    @Override
    public boolean onMouseRelease(Vec3d rayStart, Vec3d rayDirection, int button) {
        beingDragged = false;
        activeFace = -1;
        restoreCursor();
        return true;
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
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            return;
        }
        long cursor = switch (faceAxis(face)) {
            case 0 -> {
                if (hResizeCursor == 0L) {
                    hResizeCursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_HRESIZE_CURSOR);
                }
                yield hResizeCursor;
            }
            case 1 -> {
                if (vResizeCursor == 0L) {
                    vResizeCursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_VRESIZE_CURSOR);
                }
                yield vResizeCursor;
            }
            default -> {
                // Prefer all-axes resize when available (GLFW 3.4+); fall back to crosshair.
                if (allResizeCursor == 0L) {
                    try {
                        allResizeCursor = GLFW.glfwCreateStandardCursor(0x0003600B); // GLFW_RESIZE_ALL_CURSOR
                    } catch (Throwable ignored) {
                        allResizeCursor = 0L;
                    }
                    if (allResizeCursor == 0L) {
                        allResizeCursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_CROSSHAIR_CURSOR);
                    }
                }
                yield allResizeCursor;
            }
        };
        if (cursor != 0L) {
            GLFW.glfwSetCursor(client.getWindow().getHandle(), cursor);
            cursorOverridden = true;
        }
    }

    private void restoreCursor() {
        if (!cursorOverridden) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.getWindow() != null) {
            GLFW.glfwSetCursor(client.getWindow().getHandle(), 0L);
        }
        cursorOverridden = false;
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
        restoreCursor();
        handlesData = null;
        hoveredFace = -1;
        activeFace = -1;
        beingDragged = false;
    }

    @Override
    public boolean isBeingDragged() {
        return beingDragged;
    }

    @Override
    public void setBeingDragged(boolean dragged) {
        this.beingDragged = dragged;
        if (!dragged) {
            activeFace = -1;
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

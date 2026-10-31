package com.nodecraft.nodesystem.preview.elements;

import com.nodecraft.nodesystem.preview.AbstractPreviewElement;
import com.nodecraft.nodesystem.preview.InteractivePreviewElement;
import com.nodecraft.nodesystem.preview.PreviewOptions;
import com.nodecraft.nodesystem.preview.PreviewRenderer;
import com.nodecraft.nodesystem.preview.TransformGizmoPreviewData;
import com.nodecraft.nodesystem.preview.gizmo.GizmoBindingRegistry;
import com.nodecraft.nodesystem.preview.gizmo.GizmoHandle;
import com.nodecraft.nodesystem.preview.gizmo.GizmoMath;
import com.nodecraft.nodesystem.preview.gizmo.GizmoTransformCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Interactive transformation gizmo (move / rotate / scale).
 */
public class TransformationGizmoElement extends AbstractPreviewElement implements InteractivePreviewElement {

    private static final int RING_SEGMENTS = 32;

    private TransformGizmoPreviewData gizmoData;
    private float gizmoSize = 1.0f;
    private float interactionRadius = 0.35f;
    private String gizmoType = "all";

    /** Volatile: the render thread sets it, the node-execution thread reads it (drag survival). */
    private volatile boolean beingDragged = false;
    private boolean interactable = true;
    private GizmoHandle hoveredHandle = null;
    private GizmoHandle activeHandle = null;

    private Vec3d dragPlaneNormal = Vec3d.ZERO;
    private Vec3d dragStartPoint = Vec3d.ZERO;
    private double dragStartAngleDeg = 0.0d;
    private double dragStartScaleDistance = 1.0d;
    private Vec3d dragRingNormal = Vec3d.ZERO;
    private Vec3d dragRingReference = Vec3d.ZERO;

    /**
     * Drag baselines frozen at press time. The gizmo origin moves while dragging (the node
     * re-executes and republishes the preview), so every measurement is taken against these
     * fixed values rather than the live origin. This keeps the reported displacement equal to
     * the total distance travelled since the press, independent of frame pacing.
     */
    private Vec3d dragOrigin = Vec3d.ZERO;
    /** Point on the drag plane at press time (translation / scale anchor). */
    private Vec3d dragPlanePoint = Vec3d.ZERO;
    /** Last absolute values applied, used to emit deltas against a frozen baseline. */
    private Vec3d lastAppliedTranslation = Vec3d.ZERO;
    private double lastAppliedRotationDeg = 0.0d;
    private double lastAppliedScale = 1.0d;
    /** Raw (unwrapped) angle of the previous frame, used for seam-aware step accumulation. */
    private double lastRawAngleDeg = 0.0d;

    public TransformationGizmoElement(String id, String ownerNodeId, Object data, PreviewOptions options) {
        super(id, ownerNodeId, data, options);
        this.renderPriority = -100;

        if (options.gizmoSize != null) {
            this.gizmoSize = Math.max(0.1f, options.gizmoSize);
        }
        if (options.interactionRadius != null) {
            this.interactionRadius = Math.max(0.05f, options.interactionRadius);
        }
        if (options.gizmoType != null) {
            this.gizmoType = options.gizmoType;
        }
    }

    @Override
    protected void processData(Object data) {
        if (data instanceof TransformGizmoPreviewData previewData) {
            this.gizmoData = previewData;
            this.gizmoType = previewData.getGizmoType();
            return;
        }
        if (data instanceof Vec3d center) {
            this.gizmoData = new TransformGizmoPreviewData(center);
            return;
        }
        this.gizmoData = null;
    }

    @Override
    public void render(MatrixStack matrices, Camera camera, float partialTicks, float globalOpacity) {
        if (gizmoData == null) {
            return;
        }

        float finalOpacity = opacity * globalOpacity;
        if (finalOpacity <= 0.01f) {
            return;
        }

        double axisLength = resolveAxisLength(camera);
        double pickRadius = Math.max(interactionRadius, axisLength * 0.08d);

        MinecraftClient client = MinecraftClient.getInstance();
        VertexConsumerProvider provider = PreviewRenderer.getInstance().getActiveVertexConsumers();
        VertexConsumerProvider.Immediate immediate = null;
        boolean flushImmediately = false;
        if (provider == null) {
            immediate = client.getBufferBuilders().getEntityVertexConsumers();
            provider = immediate;
            flushImmediately = true;
        }

        VertexConsumer vertexConsumer = provider.getBuffer(RenderLayers.lines());
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Vec3d cameraPos = camera.getCameraPos();
        Vec3d origin = gizmoData.getOrigin().subtract(cameraPos);

        if (showsMove()) {
            drawAxis(vertexConsumer, matrix, origin, gizmoData.getXAxis(), axisLength, 1.0f, 0.25f, 0.25f, finalOpacity, GizmoHandle.AXIS_X);
            drawAxis(vertexConsumer, matrix, origin, gizmoData.getYAxis(), axisLength, 0.25f, 1.0f, 0.25f, finalOpacity, GizmoHandle.AXIS_Y);
            drawAxis(vertexConsumer, matrix, origin, gizmoData.getZAxis(), axisLength, 0.25f, 0.55f, 1.0f, finalOpacity, GizmoHandle.AXIS_Z);
        }

        if (showsRotate()) {
            double ringRadius = axisLength * 0.85d;
            drawRing(vertexConsumer, matrix, origin, gizmoData.getXAxis(), ringRadius, 1.0f, 0.25f, 0.25f, finalOpacity, GizmoHandle.RING_X);
            drawRing(vertexConsumer, matrix, origin, gizmoData.getYAxis(), ringRadius, 0.25f, 1.0f, 0.25f, finalOpacity, GizmoHandle.RING_Y);
            drawRing(vertexConsumer, matrix, origin, gizmoData.getZAxis(), ringRadius, 0.25f, 0.55f, 1.0f, finalOpacity, GizmoHandle.RING_Z);
        }

        if (showsScale()) {
            drawScaleHandle(vertexConsumer, matrix, origin, gizmoData.getXAxis(), axisLength, 1.0f, 0.25f, 0.25f, finalOpacity, GizmoHandle.SCALE_X, pickRadius);
            drawScaleHandle(vertexConsumer, matrix, origin, gizmoData.getYAxis(), axisLength, 0.25f, 1.0f, 0.25f, finalOpacity, GizmoHandle.SCALE_Y, pickRadius);
            drawScaleHandle(vertexConsumer, matrix, origin, gizmoData.getZAxis(), axisLength, 0.25f, 0.55f, 1.0f, finalOpacity, GizmoHandle.SCALE_Z, pickRadius);
            drawCenterScaleHandle(vertexConsumer, matrix, origin, finalOpacity, GizmoHandle.SCALE_UNIFORM, pickRadius * 0.75d);
        }

        if (flushImmediately && immediate != null) {
            immediate.draw();
        }
    }

    private boolean showsMove() {
        return gizmoData != null && gizmoData.showsMove();
    }

    private boolean showsRotate() {
        return gizmoData != null && gizmoData.showsRotate();
    }

    private boolean showsScale() {
        return gizmoData != null && gizmoData.showsScale();
    }

    private double resolveAxisLength(Camera camera) {
        double base = gizmoData.getBaseAxisLength() * gizmoSize;
        double distance = camera.getCameraPos().distanceTo(gizmoData.getOrigin());
        return Math.max(0.75d, base * Math.max(0.5d, distance * 0.08d));
    }

    private void drawAxis(
        VertexConsumer vertexConsumer,
        Matrix4f matrix,
        Vec3d origin,
        Vec3d axis,
        double length,
        float r,
        float g,
        float b,
        float alpha,
        GizmoHandle handle
    ) {
        if (axis.lengthSquared() < 1.0e-9d) {
            return;
        }
        float[] color = resolveColor(handle, r, g, b);
        Vec3d normalized = axis.normalize();
        Vec3d end = origin.add(normalized.multiply(length));
        drawLine(vertexConsumer, matrix, origin, end, color[0], color[1], color[2], alpha);

        Vec3d reference = Math.abs(normalized.y) < 0.95d ? new Vec3d(0.0d, 1.0d, 0.0d) : new Vec3d(1.0d, 0.0d, 0.0d);
        Vec3d side = normalized.crossProduct(reference).normalize().multiply(length * 0.12d);
        Vec3d back = normalized.multiply(-length * 0.18d);
        drawLine(vertexConsumer, matrix, end, end.add(back).add(side), color[0], color[1], color[2], alpha);
        drawLine(vertexConsumer, matrix, end, end.add(back).subtract(side), color[0], color[1], color[2], alpha);
    }

    private void drawRing(
        VertexConsumer vertexConsumer,
        Matrix4f matrix,
        Vec3d origin,
        Vec3d axis,
        double radius,
        float r,
        float g,
        float b,
        float alpha,
        GizmoHandle handle
    ) {
        if (axis.lengthSquared() < 1.0e-9d) {
            return;
        }
        float[] color = resolveColor(handle, r, g, b);
        Vec3d normal = axis.normalize();
        Vec3d tangent = normal.crossProduct(Math.abs(normal.y) < 0.95d ? new Vec3d(0.0d, 1.0d, 0.0d) : new Vec3d(1.0d, 0.0d, 0.0d));
        if (tangent.lengthSquared() < 1.0e-9d) {
            tangent = new Vec3d(1.0d, 0.0d, 0.0d);
        }
        tangent = tangent.normalize();
        Vec3d bitangent = normal.crossProduct(tangent).normalize();

        Vec3d previous = null;
        for (int i = 0; i <= RING_SEGMENTS; i++) {
            double angle = (Math.PI * 2.0d * i) / RING_SEGMENTS;
            Vec3d point = origin
                .add(tangent.multiply(Math.cos(angle) * radius))
                .add(bitangent.multiply(Math.sin(angle) * radius));
            if (previous != null) {
                drawLine(vertexConsumer, matrix, previous, point, color[0], color[1], color[2], alpha);
            }
            previous = point;
        }
    }

    private void drawScaleHandle(
        VertexConsumer vertexConsumer,
        Matrix4f matrix,
        Vec3d origin,
        Vec3d axis,
        double length,
        float r,
        float g,
        float b,
        float alpha,
        GizmoHandle handle,
        double cubeSize
    ) {
        if (axis.lengthSquared() < 1.0e-9d) {
            return;
        }
        float[] color = resolveColor(handle, r, g, b);
        Vec3d end = origin.add(axis.normalize().multiply(length));
        drawLine(vertexConsumer, matrix, origin, end, color[0], color[1], color[2], alpha * 0.85f);
        drawCube(vertexConsumer, matrix, end, cubeSize, color[0], color[1], color[2], alpha);
    }

    private void drawCenterScaleHandle(
        VertexConsumer vertexConsumer,
        Matrix4f matrix,
        Vec3d origin,
        float alpha,
        GizmoHandle handle,
        double cubeSize
    ) {
        float r = 0.95f;
        float g = 0.95f;
        float b = 0.95f;
        float[] color = resolveColor(handle, r, g, b);
        drawCube(vertexConsumer, matrix, origin, cubeSize, color[0], color[1], color[2], alpha);
    }

    private void drawCube(
        VertexConsumer vertexConsumer,
        Matrix4f matrix,
        Vec3d center,
        double size,
        float r,
        float g,
        float b,
        float alpha
    ) {
        double h = size * 0.5d;
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
            drawLine(vertexConsumer, matrix, corners[edge[0]], corners[edge[1]], r, g, b, alpha);
        }
    }

    private float[] resolveColor(GizmoHandle handle, float r, float g, float b) {
        if (handle == hoveredHandle || handle == activeHandle) {
            return new float[] {
                Math.min(1.0f, r + 0.35f),
                Math.min(1.0f, g + 0.35f),
                Math.min(1.0f, b + 0.35f)
            };
        }
        return new float[] { r, g, b };
    }

    private void drawLine(
        VertexConsumer vertexConsumer,
        Matrix4f matrix,
        Vec3d start,
        Vec3d end,
        float r,
        float g,
        float b,
        float alpha
    ) {
        Vector3f normal = new Vector3f((float) (end.x - start.x), (float) (end.y - start.y), (float) (end.z - start.z));
        if (normal.lengthSquared() < 1.0e-12f) {
            normal.set(0.0f, 1.0f, 0.0f);
        } else {
            normal.normalize();
        }

        float lineWidth = options.lineWidth != null ? Math.max(1.0f, options.lineWidth) : 2.0f;
        if (hoveredHandle != null || activeHandle != null) {
            lineWidth += 0.5f;
        }

        vertexConsumer.vertex(matrix, (float) start.x, (float) start.y, (float) start.z)
            .color(r, g, b, alpha)
            .normal(normal.x, normal.y, normal.z)
            .lineWidth(lineWidth);
        vertexConsumer.vertex(matrix, (float) end.x, (float) end.y, (float) end.z)
            .color(r, g, b, alpha)
            .normal(normal.x, normal.y, normal.z)
            .lineWidth(lineWidth);
    }

    @Override
    public boolean shouldRender(Camera camera) {
        if (gizmoData == null || isExpired() || !visible) {
            return false;
        }
        double distance = camera.getCameraPos().distanceTo(gizmoData.getOrigin());
        return distance <= PreviewRenderer.getInstance().getSettings().maxRenderDistance + gizmoData.getBaseAxisLength() * 4.0d;
    }

    @Override
    public void cleanup() {
        gizmoData = null;
        hoveredHandle = null;
        activeHandle = null;
        beingDragged = false;
        resetDragBaselines();
    }

    public GizmoHandle pickHandle(Vec3d rayStart, Vec3d rayDirection, double maxDistance) {
        if (!interactable || gizmoData == null) {
            return null;
        }

        double axisLength = resolveAxisLengthFromRay(rayStart);
        double pickRadius = Math.max(interactionRadius, axisLength * 0.08d);
        Vec3d origin = gizmoData.getOrigin();

        GizmoHandle bestHandle = null;
        double bestDistance = Double.MAX_VALUE;

        if (showsMove()) {
            bestHandle = considerAxisHit(bestHandle, bestDistance, rayStart, rayDirection, maxDistance, origin, gizmoData.getXAxis(), axisLength, pickRadius, GizmoHandle.AXIS_X);
            bestDistance = bestHandle == GizmoHandle.AXIS_X
                ? axisHitDistance(rayStart, rayDirection, origin, gizmoData.getXAxis(), axisLength, pickRadius)
                : bestDistance;
            bestHandle = considerAxisHit(bestHandle, bestDistance, rayStart, rayDirection, maxDistance, origin, gizmoData.getYAxis(), axisLength, pickRadius, GizmoHandle.AXIS_Y);
            bestDistance = bestHandle == GizmoHandle.AXIS_Y
                ? axisHitDistance(rayStart, rayDirection, origin, gizmoData.getYAxis(), axisLength, pickRadius)
                : bestDistance;
            bestHandle = considerAxisHit(bestHandle, bestDistance, rayStart, rayDirection, maxDistance, origin, gizmoData.getZAxis(), axisLength, pickRadius, GizmoHandle.AXIS_Z);
        }

        if (showsRotate()) {
            double ringRadius = axisLength * 0.85d;
            double ringThickness = Math.max(pickRadius * 0.75d, axisLength * 0.06d);
            bestHandle = considerRingHit(bestHandle, bestDistance, rayStart, rayDirection, maxDistance, origin, gizmoData.getXAxis(), ringRadius, ringThickness, GizmoHandle.RING_X);
            bestDistance = updateBestDistance(bestHandle, bestDistance, rayStart, rayDirection, origin, GizmoHandle.RING_X, axisLength, pickRadius, ringRadius, ringThickness);
            bestHandle = considerRingHit(bestHandle, bestDistance, rayStart, rayDirection, maxDistance, origin, gizmoData.getYAxis(), ringRadius, ringThickness, GizmoHandle.RING_Y);
            bestDistance = updateBestDistance(bestHandle, bestDistance, rayStart, rayDirection, origin, GizmoHandle.RING_Y, axisLength, pickRadius, ringRadius, ringThickness);
            bestHandle = considerRingHit(bestHandle, bestDistance, rayStart, rayDirection, maxDistance, origin, gizmoData.getZAxis(), ringRadius, ringThickness, GizmoHandle.RING_Z);
        }

        if (showsScale()) {
            bestHandle = considerScaleHit(bestHandle, bestDistance, rayStart, rayDirection, maxDistance, origin, gizmoData.getXAxis(), axisLength, pickRadius, GizmoHandle.SCALE_X);
            bestHandle = considerScaleHit(bestHandle, bestDistance, rayStart, rayDirection, maxDistance, origin, gizmoData.getYAxis(), axisLength, pickRadius, GizmoHandle.SCALE_Y);
            bestHandle = considerScaleHit(bestHandle, bestDistance, rayStart, rayDirection, maxDistance, origin, gizmoData.getZAxis(), axisLength, pickRadius, GizmoHandle.SCALE_Z);
            bestHandle = considerCenterScaleHit(bestHandle, bestDistance, rayStart, rayDirection, maxDistance, origin, pickRadius * 0.75d, GizmoHandle.SCALE_UNIFORM);
        }

        return bestHandle;
    }

    private GizmoHandle considerAxisHit(
        GizmoHandle currentBest,
        double currentDistance,
        Vec3d rayStart,
        Vec3d rayDirection,
        double maxDistance,
        Vec3d origin,
        Vec3d axis,
        double length,
        double radius,
        GizmoHandle handle
    ) {
        GizmoMath.RayHit hit = GizmoMath.rayCylinderHit(rayStart, rayDirection, origin, axis, length, radius);
        if (hit == null || hit.distance() > maxDistance) {
            return currentBest;
        }
        if (currentBest == null || hit.distance() < currentDistance) {
            return handle;
        }
        return currentBest;
    }

    private double axisHitDistance(Vec3d rayStart, Vec3d rayDirection, Vec3d origin, Vec3d axis, double length, double radius) {
        GizmoMath.RayHit hit = GizmoMath.rayCylinderHit(rayStart, rayDirection, origin, axis, length, radius);
        return hit == null ? Double.MAX_VALUE : hit.distance();
    }

    private GizmoHandle considerRingHit(
        GizmoHandle currentBest,
        double currentDistance,
        Vec3d rayStart,
        Vec3d rayDirection,
        double maxDistance,
        Vec3d origin,
        Vec3d axis,
        double radius,
        double thickness,
        GizmoHandle handle
    ) {
        GizmoMath.RayHit hit = GizmoMath.rayRingHit(rayStart, rayDirection, origin, axis, radius, thickness);
        if (hit == null || hit.distance() > maxDistance) {
            return currentBest;
        }
        if (currentBest == null || hit.distance() < currentDistance) {
            return handle;
        }
        return currentBest;
    }

    private double updateBestDistance(
        GizmoHandle bestHandle,
        double bestDistance,
        Vec3d rayStart,
        Vec3d rayDirection,
        Vec3d origin,
        GizmoHandle handle,
        double axisLength,
        double pickRadius,
        double ringRadius,
        double ringThickness
    ) {
        if (bestHandle != handle) {
            return bestDistance;
        }
        GizmoMath.RayHit hit = GizmoMath.rayRingHit(rayStart, rayDirection, origin, axisForHandle(handle), ringRadius, ringThickness);
        return hit == null ? bestDistance : hit.distance();
    }

    private GizmoHandle considerScaleHit(
        GizmoHandle currentBest,
        double currentDistance,
        Vec3d rayStart,
        Vec3d rayDirection,
        double maxDistance,
        Vec3d origin,
        Vec3d axis,
        double length,
        double radius,
        GizmoHandle handle
    ) {
        Vec3d end = origin.add(axis.normalize().multiply(length));
        double distance = GizmoMath.rayToSegmentDistance(rayStart, rayDirection, origin, end);
        if (distance <= radius && rayStart.distanceTo(origin) <= maxDistance) {
            if (currentBest == null || distance < currentDistance) {
                return handle;
            }
        }
        return pickCube(rayStart, rayDirection, end, radius, handle) != null ? handle : currentBest;
    }

    private GizmoHandle considerCenterScaleHit(
        GizmoHandle currentBest,
        double currentDistance,
        Vec3d rayStart,
        Vec3d rayDirection,
        double maxDistance,
        Vec3d origin,
        double radius,
        GizmoHandle handle
    ) {
        if (rayStart.distanceTo(origin) > maxDistance) {
            return currentBest;
        }
        GizmoHandle picked = pickCube(rayStart, rayDirection, origin, radius, handle);
        return picked != null ? picked : currentBest;
    }

    public void clearHoverState() {
        hoveredHandle = null;
    }

    private double resolveAxisLengthFromRay(Vec3d rayStart) {
        double base = gizmoData.getBaseAxisLength() * gizmoSize;
        double distance = rayStart.distanceTo(gizmoData.getOrigin());
        return Math.max(0.75d, base * Math.max(0.5d, distance * 0.08d));
    }

    private GizmoHandle pickCube(Vec3d rayStart, Vec3d rayDirection, Vec3d center, double radius, GizmoHandle handle) {
        double distance = rayStart.distanceTo(center);
        Vec3d direction = GizmoMath.normalizeOr(rayDirection, new Vec3d(0.0d, 0.0d, -1.0d));
        Vec3d closest = GizmoMath.intersectRayWithPlane(rayStart, direction, center, direction);
        if (closest != null && closest.distanceTo(center) <= radius * 1.5d) {
            return handle;
        }
        return distance <= radius * 2.0d ? handle : null;
    }

    @Override
    public boolean intersectsRay(Vec3d rayStart, Vec3d rayDirection, double maxDistance) {
        return pickHandle(rayStart, rayDirection, maxDistance) != null;
    }

    @Override
    public boolean onMouseClick(Vec3d rayStart, Vec3d rayDirection, int button) {
        if (!interactable || button != 0 || gizmoData == null) {
            return false;
        }

        GizmoHandle handle = pickHandle(rayStart, rayDirection, 256.0d);
        if (handle == null) {
            return false;
        }

        activeHandle = handle;
        beingDragged = true;
        beginDrag(rayStart, rayDirection, handle);

        GizmoTransformCallback callback = GizmoBindingRegistry.get(getOwnerNodeId());
        if (callback != null) {
            callback.onDragBegin();
        }
        return true;
    }

    private void beginDrag(Vec3d rayStart, Vec3d rayDirection, GizmoHandle handle) {
        Vec3d origin = gizmoData.getOrigin();
        Vec3d axis = axisForHandle(handle);
        Vec3d cameraDirection = rayStart.subtract(origin);
        // Freeze the origin: it moves as the node re-executes during the drag.
        dragOrigin = origin;
        if (handle.isTranslation() || handle.isScale()) {
            dragPlaneNormal = GizmoMath.cameraFacingPlaneNormal(axis, cameraDirection);
            dragStartPoint = GizmoMath.intersectRayWithPlane(rayStart, rayDirection, origin, dragPlaneNormal);
            if (dragStartPoint == null) {
                dragStartPoint = origin;
            }
            // The drag plane is anchored at the press-time origin, not the live one.
            dragPlanePoint = origin;
            lastAppliedTranslation = dragStartPoint;
            lastAppliedRotationDeg = 0.0d;
            lastAppliedScale = 1.0d;
            lastRawAngleDeg = 0.0d;
            if (handle.isScale()) {
                dragStartScaleDistance = Math.max(1.0e-6d, dragStartPoint.subtract(dragOrigin).dotProduct(axis.normalize()));
            }
        } else if (handle.isRotation()) {
            dragRingNormal = axis.normalize();
            dragRingReference = referenceOnRing(dragRingNormal);
            Vec3d hit = GizmoMath.intersectRayWithPlane(rayStart, rayDirection, origin, dragRingNormal);
            dragStartAngleDeg = hit == null ? 0.0d : GizmoMath.signedAngleOnPlane(dragOrigin, dragRingNormal, dragRingReference, hit);
            lastAppliedTranslation = Vec3d.ZERO;
            // Deltas are relative to the press-time angle, not to zero.
            lastAppliedRotationDeg = 0.0d;
            lastRawAngleDeg = dragStartAngleDeg;
            lastAppliedScale = 1.0d;
        }
    }

    @Override
    public boolean onMouseDrag(Vec3d rayStart, Vec3d rayDirection, Vec3d deltaMovement) {
        if (!beingDragged || activeHandle == null || gizmoData == null) {
            return false;
        }

        GizmoTransformCallback callback = GizmoBindingRegistry.get(getOwnerNodeId());
        if (callback == null) {
            return false;
        }

        Vec3d axis = axisForHandle(activeHandle);
        Vector3d translationDelta = new Vector3d();
        Vector3d rotationDelta = new Vector3d();
        double scaleDelta = 1.0d;

        if (activeHandle.isTranslation()) {
            // Measure against the frozen press-time plane and baseline. The camera-facing drag
            // plane contains the drag axis, so sliding the origin along that axis leaves the
            // intersection unchanged — but keeping the baseline frozen guarantees the reported
            // displacement is the total since the press, independent of frame pacing.
            Vec3d currentPoint = GizmoMath.intersectRayWithPlane(rayStart, rayDirection, dragPlanePoint, dragPlaneNormal);
            if (currentPoint != null) {
                Vec3d delta = currentPoint.subtract(dragStartPoint);
                double projected = delta.dotProduct(axis.normalize());
                Vec3d absolute = dragStartPoint.add(axis.normalize().multiply(projected));
                Vec3d step = absolute.subtract(lastAppliedTranslation);
                translationDelta.set(step.x, step.y, step.z);
                lastAppliedTranslation = absolute;
            }
        } else if (activeHandle.isRotation()) {
            Vec3d hit = GizmoMath.intersectRayWithPlane(rayStart, rayDirection, dragOrigin, dragRingNormal);
            if (hit != null) {
                double angle = GizmoMath.signedAngleOnPlane(dragOrigin, dragRingNormal, dragRingReference, hit);
                // Accumulate seam-aware steps: crossing the +/-180 seam must not spin the gizmo.
                double step = GizmoMath.normalizeDegrees(angle - lastRawAngleDeg);
                lastRawAngleDeg = angle;
                lastAppliedRotationDeg += step;
                switch (activeHandle) {
                    case RING_X -> rotationDelta.x = step;
                    case RING_Y -> rotationDelta.y = step;
                    case RING_Z -> rotationDelta.z = step;
                    default -> {
                    }
                }
            }
        } else if (activeHandle.isScale()) {
            Vec3d currentPoint = GizmoMath.intersectRayWithPlane(rayStart, rayDirection, dragPlanePoint, dragPlaneNormal);
            if (currentPoint != null) {
                double absolute;
                if (activeHandle == GizmoHandle.SCALE_UNIFORM) {
                    double currentDistance = currentPoint.distanceTo(dragOrigin);
                    double startDistance = dragStartPoint.distanceTo(dragOrigin);
                    absolute = startDistance > 1.0e-6d ? currentDistance / startDistance : 1.0d;
                } else {
                    double currentDistance = currentPoint.subtract(dragOrigin).dotProduct(axis.normalize());
                    absolute = dragStartScaleDistance > 1.0e-6d
                        ? currentDistance / dragStartScaleDistance
                        : 1.0d;
                }
                if (!Double.isFinite(absolute) || absolute <= 1.0e-9d) {
                    absolute = lastAppliedScale;
                }
                // Emit the ratio between the last applied scale and the new absolute scale so
                // the node's multiplicative accumulation stays consistent.
                scaleDelta = lastAppliedScale > 1.0e-9d ? absolute / lastAppliedScale : 1.0d;
                lastAppliedScale = absolute;
            }
        }

        if (translationDelta.lengthSquared() > 1.0e-12d
            || rotationDelta.lengthSquared() > 1.0e-12d
            || Math.abs(scaleDelta - 1.0d) > 1.0e-9d) {
            callback.onTransformDelta(translationDelta, rotationDelta, scaleDelta);
        }
        return true;
    }

    @Override
    public boolean onMouseRelease(Vec3d rayStart, Vec3d rayDirection, int button) {
        if (!beingDragged || button != 0) {
            return false;
        }

        GizmoTransformCallback callback = GizmoBindingRegistry.get(getOwnerNodeId());
        if (callback != null) {
            callback.onTransformCommit();
        }

        activeHandle = null;
        setBeingDragged(false);
        resetDragBaselines();
        return true;
    }

    /** Clears the frozen drag baselines so a later drag starts from a clean state. */
    private void resetDragBaselines() {
        dragOrigin = Vec3d.ZERO;
        dragPlanePoint = Vec3d.ZERO;
        dragPlaneNormal = Vec3d.ZERO;
        dragStartPoint = Vec3d.ZERO;
        dragRingNormal = Vec3d.ZERO;
        dragRingReference = Vec3d.ZERO;
        dragStartAngleDeg = 0.0d;
        dragStartScaleDistance = 1.0d;
        lastAppliedTranslation = Vec3d.ZERO;
        lastAppliedRotationDeg = 0.0d;
        lastAppliedScale = 1.0d;
        lastRawAngleDeg = 0.0d;
    }

    @Override
    public boolean onMouseHover(Vec3d rayStart, Vec3d rayDirection) {
        if (!interactable || beingDragged || gizmoData == null) {
            hoveredHandle = null;
            return false;
        }
        hoveredHandle = pickHandle(rayStart, rayDirection, 256.0d);
        return hoveredHandle != null;
    }

    @Override
    public boolean isBeingDragged() {
        return beingDragged;
    }

    @Override
    public void setBeingDragged(boolean dragged) {
        this.beingDragged = dragged;
        if (!dragged) {
            activeHandle = null;
        }
    }

    @Override
    public float getInteractionRadius() {
        return interactionRadius;
    }

    @Override
    public Vec3d getInteractionCenter() {
        return gizmoData != null ? gizmoData.getOrigin() : Vec3d.ZERO;
    }

    @Override
    public boolean isInteractable() {
        return interactable;
    }

    @Override
    public void setInteractable(boolean interactable) {
        this.interactable = interactable;
    }

    private Vec3d axisForHandle(GizmoHandle handle) {
        return switch (handle) {
            case AXIS_X, RING_X, SCALE_X -> gizmoData.getXAxis();
            case AXIS_Y, RING_Y, SCALE_Y -> gizmoData.getYAxis();
            case AXIS_Z, RING_Z, SCALE_Z -> gizmoData.getZAxis();
            case SCALE_UNIFORM -> new Vec3d(1.0d, 1.0d, 1.0d);
        };
    }

    private Vec3d referenceOnRing(Vec3d ringNormal) {
        Vec3d tangent = ringNormal.crossProduct(Math.abs(ringNormal.y) < 0.95d ? new Vec3d(0.0d, 1.0d, 0.0d) : new Vec3d(1.0d, 0.0d, 0.0d));
        if (tangent.lengthSquared() < 1.0e-9d) {
            return new Vec3d(1.0d, 0.0d, 0.0d);
        }
        return gizmoData.getOrigin().add(tangent.normalize());
    }
}

package com.nodecraft.nodesystem.preview.elements;

import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.preview.AbstractPreviewElement;
import com.nodecraft.nodesystem.preview.PreviewOptions;
import com.nodecraft.nodesystem.preview.PreviewRenderer;
import com.nodecraft.nodesystem.util.Curve;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.nodecraft.core.NodeCraft;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class LinesElement extends AbstractPreviewElement {

    private volatile List<Vec3d> points = new ArrayList<>();
    private static final AtomicLong lastDebugLog = new AtomicLong(0);
    private Vector3f color = new Vector3f(1.0f, 0.85f, 0.2f);
    private boolean smoothCurves = false;
    private float lineWidth = 1.5f;
    private boolean showDirection = false;
    private float arrowSize = 0.25f;

    public LinesElement(String id, String ownerNodeId, Object data, PreviewOptions options) {
        super(id, ownerNodeId, data, options);
        this.renderPriority = 17;

        if (options.color != null) {
            this.color = new Vector3f(options.color);
        }
        if (options.smoothCurves != null) {
            this.smoothCurves = options.smoothCurves;
        }
        if (options.lineWidth != null) {
            this.lineWidth = Math.max(0.25f, options.lineWidth);
        }
        if (options.showArrows != null) {
            this.showDirection = options.showArrows;
        }
        if (options.arrowSize != null) {
            this.arrowSize = Math.max(0.05f, options.arrowSize);
        }
    }

    @Override
    protected void processData(Object data) {
        List<Vec3d> nextPoints = new ArrayList<>();

        if (data instanceof LineData line) {
            nextPoints.add(line.getStart());
            nextPoints.add(line.getEnd());
        } else if (data instanceof PolylineData polyline) {
            nextPoints.addAll(polyline.getPoints());
        } else if (data instanceof Curve curve) {
            nextPoints.addAll(curve.getSamplePoints());
        } else if (data instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Vec3d vec) {
                    nextPoints.add(vec);
                }
            }
        }

        points = nextPoints;
    }

    @Override
    public void render(MatrixStack matrices, Camera camera, float partialTicks, float globalOpacity) {
        List<Vec3d> pointsSnapshot = points;
        if (pointsSnapshot.size() < 2) {
            long now = System.currentTimeMillis();
            if (now - lastDebugLog.get() > 2000L && lastDebugLog.compareAndSet(lastDebugLog.get(), now)) {
                NodeCraft.LOGGER.info("[LinesElement] render() skipped: points.size()={}", pointsSnapshot.size());
            }
            return;
        }

        float finalOpacity = opacity * globalOpacity;
        if (finalOpacity <= 0.01f) {
            long now = System.currentTimeMillis();
            if (now - lastDebugLog.get() > 2000L && lastDebugLog.compareAndSet(lastDebugLog.get(), now)) {
                NodeCraft.LOGGER.info("[LinesElement] render() skipped: finalOpacity={}", finalOpacity);
            }
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        VertexConsumerProvider provider = PreviewRenderer.getInstance().getActiveVertexConsumers();
        {
            long now = System.currentTimeMillis();
            if (now - lastDebugLog.get() > 2000L && lastDebugLog.compareAndSet(lastDebugLog.get(), now)) {
                NodeCraft.LOGGER.info("[LinesElement] render() called: points={}, provider={}, opacity={}", pointsSnapshot.size(), provider != null ? provider.getClass().getSimpleName() : "null", finalOpacity);
            }
        }
        VertexConsumerProvider.Immediate immediate = null;
        boolean flushImmediately = false;
        if (provider == null) {
            immediate = client.getBufferBuilders().getEntityVertexConsumers();
            provider = immediate;
            flushImmediately = true;
        }

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Vec3d cameraPos = camera.getCameraPos();

        for (int i = 0; i < pointsSnapshot.size() - 1; i++) {
            Vec3d start = pointsSnapshot.get(i).subtract(cameraPos);
            Vec3d end = pointsSnapshot.get(i + 1).subtract(cameraPos);
            VertexConsumer segmentConsumer = provider.getBuffer(RenderLayers.LINES);
            drawLine(segmentConsumer, matrix, start, end, finalOpacity);
        }

        if (showDirection && pointsSnapshot.size() >= 2) {
            Vec3d start = pointsSnapshot.get(pointsSnapshot.size() - 2).subtract(cameraPos);
            Vec3d end = pointsSnapshot.get(pointsSnapshot.size() - 1).subtract(cameraPos);
            VertexConsumer arrowConsumer = provider.getBuffer(RenderLayers.LINES);
            drawArrowHead(arrowConsumer, matrix, start, end, finalOpacity);
        }

        if (flushImmediately && immediate != null) {
            immediate.draw();
        }
    }

    private void drawArrowHead(VertexConsumer vertexConsumer, Matrix4f matrix, Vec3d start, Vec3d end, float alpha) {
        Vec3d direction = end.subtract(start);
        double length = direction.length();
        if (length < 1.0e-6d) {
            return;
        }

        double scaledArrowSize = Math.max(0.05d, arrowSize * Math.max(0.5d, lineWidth * 0.6d));
        Vec3d dir = direction.normalize();
        Vec3d reference = Math.abs(dir.y) < 0.95d ? new Vec3d(0.0d, 1.0d, 0.0d) : new Vec3d(1.0d, 0.0d, 0.0d);
        Vec3d side = dir.crossProduct(reference).normalize().multiply(scaledArrowSize);
        Vec3d back = dir.multiply(-scaledArrowSize * 1.6d);

        drawLine(vertexConsumer, matrix, end, end.add(back).add(side), alpha);
        drawLine(vertexConsumer, matrix, end, end.add(back).subtract(side), alpha);
    }

    private void drawLine(VertexConsumer vertexConsumer, Matrix4f matrix, Vec3d start, Vec3d end, float alpha) {
        Vector3f normal = new Vector3f(0.0f, 1.0f, 0.0f);

        vertexConsumer.vertex(matrix, (float) start.x, (float) start.y, (float) start.z)
            .color(color.x(), color.y(), color.z(), alpha)
            .normal(normal.x, normal.y, normal.z)
            .lineWidth(lineWidth);
        vertexConsumer.vertex(matrix, (float) end.x, (float) end.y, (float) end.z)
            .color(color.x(), color.y(), color.z(), alpha)
            .normal(normal.x, normal.y, normal.z)
            .lineWidth(lineWidth);
    }

    @Override
    public boolean shouldRender(Camera camera) {
        List<Vec3d> pointsSnapshot = points;
        if (pointsSnapshot.size() < 2 || isExpired()) {
            long now = System.currentTimeMillis();
            if (now - lastDebugLog.get() > 2000L && lastDebugLog.compareAndSet(lastDebugLog.get(), now)) {
                NodeCraft.LOGGER.info("[LinesElement] shouldRender=false: points={}, expired={}", pointsSnapshot.size(), isExpired());
            }
            return false;
        }
        float maxDistance = PreviewRenderer.getInstance().getSettings().maxRenderDistance;
        Vec3d cameraPos = camera.getCameraPos();
        for (Vec3d point : pointsSnapshot) {
            if (cameraPos.distanceTo(point) <= maxDistance) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void cleanup() {
        points = new ArrayList<>();
    }
}

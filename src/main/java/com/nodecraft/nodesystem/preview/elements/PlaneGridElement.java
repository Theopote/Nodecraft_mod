package com.nodecraft.nodesystem.preview.elements;

import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.preview.AbstractPreviewElement;
import com.nodecraft.nodesystem.preview.PlaneGridPreviewData;
import com.nodecraft.nodesystem.preview.PreviewOptions;
import com.nodecraft.nodesystem.preview.PreviewRenderer;
import com.nodecraft.nodesystem.preview.protocol.PreviewPlanePayload;
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

public class PlaneGridElement extends AbstractPreviewElement {

    private PlaneGridPreviewData planeGridData;
    private Vector3f color = new Vector3f(0.35f, 0.75f, 1.0f);
    private float lineWidth = 1.5f;
    private boolean enablePulse = false;

    public PlaneGridElement(String id, String ownerNodeId, Object data, PreviewOptions options) {
        super(id, ownerNodeId, data, options);
        this.renderPriority = 20;

        if (options.color != null) {
            this.color = new Vector3f(options.color);
        }
        if (options.lineWidth != null) {
            this.lineWidth = Math.max(0.25f, options.lineWidth);
        }
        if (options.pulseAnimation != null) {
            this.enablePulse = options.pulseAnimation;
        }
    }

    @Override
    protected void processData(Object data) {
        if (data instanceof PreviewPlanePayload payload) {
            this.planeGridData = payload.getPlaneGridData();
            return;
        }
        this.planeGridData = data instanceof PlaneGridPreviewData previewData ? previewData : null;
    }

    @Override
    public void render(MatrixStack matrices, Camera camera, float partialTicks, float globalOpacity) {
        if (planeGridData == null || planeGridData.getPlane() == null) {
            return;
        }

        float finalOpacity = opacity * globalOpacity;
        if (enablePulse) {
            finalOpacity *= (float) (0.65f + 0.35f * Math.sin(System.currentTimeMillis() * 0.003));
        }
        if (finalOpacity <= 0.01f) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        VertexConsumerProvider.Immediate immediate = client.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer vertexConsumer = immediate.getBuffer(RenderLayers.lines());
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Vec3d cameraPos = camera.getCameraPos();

        PlaneData plane = planeGridData.getPlane();
        Vector3d origin = plane.getPoint();
        Vector3d normal = plane.getNormal();
        if (normal.lengthSquared() < 1.0e-9d) {
            return;
        }
        normal.normalize();

        Vector3d tangent = buildTangent(normal);
        Vector3d bitangent = new Vector3d(normal).cross(tangent).normalize();

        double halfSize = planeGridData.getGridSize() * 0.5d;
        double spacing = Math.min(planeGridData.getGridSpacing(), planeGridData.getGridSize());
        int steps = Math.max(1, (int) Math.floor(halfSize / spacing));

        for (int step = -steps; step <= steps; step++) {
            double offset = step * spacing;
            if (offset < -halfSize - 1.0e-6d || offset > halfSize + 1.0e-6d) {
                continue;
            }

            Vec3d lineAStart = toRenderPoint(origin, tangent, -halfSize, bitangent, offset, cameraPos);
            Vec3d lineAEnd = toRenderPoint(origin, tangent, halfSize, bitangent, offset, cameraPos);
            Vec3d lineBStart = toRenderPoint(origin, tangent, offset, bitangent, -halfSize, cameraPos);
            Vec3d lineBEnd = toRenderPoint(origin, tangent, offset, bitangent, halfSize, cameraPos);

            boolean major = step == 0;
            float lineAlpha = major ? Math.min(1.0f, finalOpacity * 1.15f) : finalOpacity * 0.7f;
            float scale = major ? 1.1f : 1.0f;
            drawLine(vertexConsumer, matrix, lineAStart, lineAEnd, color.x() * scale, color.y() * scale, color.z() * scale, lineAlpha, lineWidth);
            drawLine(vertexConsumer, matrix, lineBStart, lineBEnd, color.x() * scale, color.y() * scale, color.z() * scale, lineAlpha, lineWidth);
        }

        if (planeGridData.isShowAxes()) {
            drawAxis(vertexConsumer, matrix, origin, tangent, halfSize, cameraPos, 1.0f, 0.25f, 0.25f, finalOpacity, lineWidth);
            drawAxis(vertexConsumer, matrix, origin, bitangent, halfSize, cameraPos, 0.25f, 1.0f, 0.25f, finalOpacity, lineWidth);
        }
        if (planeGridData.isShowNormal()) {
            drawAxis(vertexConsumer, matrix, origin, normal, Math.max(1.0d, halfSize * 0.75d), cameraPos, 0.25f, 0.55f, 1.0f, finalOpacity, lineWidth);
        }

        immediate.draw();
    }

    @Override
    public boolean shouldRender(Camera camera) {
        if (planeGridData == null || planeGridData.getPlane() == null || isExpired()) {
            return false;
        }

        Vector3d origin = planeGridData.getPlane().getPoint();
        Vec3d center = new Vec3d(origin.x, origin.y, origin.z);
        double distance = camera.getCameraPos().distanceTo(center);
        return distance <= PreviewRenderer.getInstance().getSettings().maxRenderDistance + planeGridData.getGridSize();
    }

    @Override
    public void cleanup() {
        planeGridData = null;
    }

    private static Vector3d buildTangent(Vector3d normal) {
        Vector3d reference = Math.abs(normal.y) < 0.99d
            ? new Vector3d(0.0d, 1.0d, 0.0d)
            : new Vector3d(1.0d, 0.0d, 0.0d);
        return reference.cross(normal, new Vector3d()).normalize();
    }

    private static Vec3d toRenderPoint(Vector3d origin, Vector3d tangent, double tangentScale,
                                       Vector3d bitangent, double bitangentScale, Vec3d cameraPos) {
        double x = origin.x + tangent.x * tangentScale + bitangent.x * bitangentScale - cameraPos.x;
        double y = origin.y + tangent.y * tangentScale + bitangent.y * bitangentScale - cameraPos.y;
        double z = origin.z + tangent.z * tangentScale + bitangent.z * bitangentScale - cameraPos.z;
        return new Vec3d(x, y, z);
    }

    private static void drawAxis(VertexConsumer vertexConsumer, Matrix4f matrix, Vector3d origin, Vector3d direction,
                                 double length, Vec3d cameraPos, float r, float g, float b, float a, float lineWidth) {
        Vec3d start = new Vec3d(origin.x - cameraPos.x, origin.y - cameraPos.y, origin.z - cameraPos.z);
        Vec3d end = new Vec3d(
            origin.x + direction.x * length - cameraPos.x,
            origin.y + direction.y * length - cameraPos.y,
            origin.z + direction.z * length - cameraPos.z
        );
        drawLine(vertexConsumer, matrix, start, end, r, g, b, a, lineWidth);
    }

    private static void drawLine(VertexConsumer vertexConsumer, Matrix4f matrix, Vec3d start, Vec3d end,
                                 float r, float g, float b, float a, float lineWidth) {
        Vector3f normal = new Vector3f((float) (end.x - start.x), (float) (end.y - start.y), (float) (end.z - start.z));
        if (normal.lengthSquared() < 1.0e-12f) {
            normal.set(0.0f, 1.0f, 0.0f);
        } else {
            normal.normalize();
        }

        vertexConsumer.vertex(matrix, (float) start.x, (float) start.y, (float) start.z)
            .color(r, g, b, a)
            .normal(normal.x, normal.y, normal.z)
            .lineWidth(lineWidth);
        vertexConsumer.vertex(matrix, (float) end.x, (float) end.y, (float) end.z)
            .color(r, g, b, a)
            .normal(normal.x, normal.y, normal.z)
            .lineWidth(lineWidth);
    }
}

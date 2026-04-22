package com.nodecraft.nodesystem.preview.elements;

import com.nodecraft.nodesystem.preview.AbstractPreviewElement;
import com.nodecraft.nodesystem.preview.PreviewRenderer;
import com.nodecraft.nodesystem.preview.PreviewOptions;
import com.nodecraft.nodesystem.preview.protocol.PreviewVector;
import com.nodecraft.nodesystem.preview.protocol.PreviewVectorsPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class VectorsElement extends AbstractPreviewElement {

    private volatile List<Vec3d> vectors = new ArrayList<>();
    private volatile List<Vec3d> startPoints = new ArrayList<>();
    private Vector3f color = new Vector3f(0.2f, 1.0f, 0.2f);
    private float arrowSize = 0.2f;
    private float lengthScale = 1.0f;
    private boolean showArrows = true;

    public VectorsElement(String id, String ownerNodeId, Object data, PreviewOptions options) {
        super(id, ownerNodeId, data, options);
        this.renderPriority = 18;

        if (options.color != null) {
            this.color = new Vector3f(options.color);
        }
        if (options.arrowSize != null) {
            this.arrowSize = Math.max(0.05f, options.arrowSize);
        }
        if (options.lengthScale != null) {
            this.lengthScale = Math.max(0.01f, options.lengthScale);
        }
        if (options.showArrows != null) {
            this.showArrows = options.showArrows;
        }
    }

    @Override
    protected void processData(Object data) {
        List<Vec3d> nextVectors = new ArrayList<>();
        List<Vec3d> nextStartPoints = new ArrayList<>();

        if (data instanceof PreviewVectorsPayload payload) {
            for (PreviewVector v : payload.getVectors()) {
                nextVectors.add(new Vec3d(v.dirX(), v.dirY(), v.dirZ()));
                nextStartPoints.add(new Vec3d(v.originX(), v.originY(), v.originZ()));
            }
            vectors = nextVectors;
            startPoints = nextStartPoints;
            return;
        }

        if (data instanceof Object[] array && array.length >= 2
            && array[0] instanceof List<?> vectorList
            && array[1] instanceof List<?> startList) {
            int count = Math.min(vectorList.size(), startList.size());
            for (int i = 0; i < count; i++) {
                Vec3d vector = toVec3d(vectorList.get(i));
                Vec3d start = toVec3d(startList.get(i));
                if (vector != null && start != null) {
                    nextVectors.add(vector);
                    nextStartPoints.add(start);
                }
            }
        }

        vectors = nextVectors;
        startPoints = nextStartPoints;
    }

    private Vec3d toVec3d(Object value) {
        if (value instanceof Vec3d vec) {
            return vec;
        }
        if (value instanceof org.joml.Vector3d vec) {
            return new Vec3d(vec.x, vec.y, vec.z);
        }
        if (value instanceof net.minecraft.util.math.BlockPos pos) {
            return pos.toCenterPos();
        }
        return null;
    }

    @Override
    public void render(MatrixStack matrices, Camera camera, float partialTicks, float globalOpacity) {
        List<Vec3d> vectorsSnapshot = vectors;
        List<Vec3d> startPointsSnapshot = startPoints;
        if (vectorsSnapshot.isEmpty()) {
            return;
        }

        float finalOpacity = opacity * globalOpacity;
        if (finalOpacity <= 0.01f) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        VertexConsumerProvider.Immediate immediate = client.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer vertexConsumer = immediate.getBuffer(RenderLayers.lines());
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Vec3d cameraPos = camera.getCameraPos();

        for (int i = 0; i < vectorsSnapshot.size(); i++) {
            Vec3d start = startPointsSnapshot.get(i).subtract(cameraPos);
            Vec3d direction = vectorsSnapshot.get(i).multiply(lengthScale);
            Vec3d end = start.add(direction);
            drawLine(vertexConsumer, matrix, start, end, finalOpacity);

            if (showArrows) {
                drawArrowHead(vertexConsumer, matrix, start, end, finalOpacity);
            }
        }

        immediate.draw();
    }

    private void drawArrowHead(VertexConsumer vertexConsumer, Matrix4f matrix, Vec3d start, Vec3d end, float alpha) {
        Vec3d direction = end.subtract(start);
        double length = direction.length();
        if (length < 1.0e-6d) {
            return;
        }

        Vec3d dir = direction.normalize();
        Vec3d reference = Math.abs(dir.y) < 0.95d ? new Vec3d(0.0d, 1.0d, 0.0d) : new Vec3d(1.0d, 0.0d, 0.0d);
        Vec3d side = dir.crossProduct(reference).normalize().multiply(arrowSize);
        Vec3d back = dir.multiply(-arrowSize * 1.5d);

        drawLine(vertexConsumer, matrix, end, end.add(back).add(side), alpha);
        drawLine(vertexConsumer, matrix, end, end.add(back).subtract(side), alpha);
    }

    private void drawLine(VertexConsumer vertexConsumer, Matrix4f matrix, Vec3d start, Vec3d end, float alpha) {
        Vector3f normal = new Vector3f((float) (end.x - start.x), (float) (end.y - start.y), (float) (end.z - start.z));
        if (normal.lengthSquared() < 1.0e-12f) {
            normal.set(0.0f, 1.0f, 0.0f);
        } else {
            normal.normalize();
        }

        vertexConsumer.vertex(matrix, (float) start.x, (float) start.y, (float) start.z)
            .color(color.x(), color.y(), color.z(), alpha)
            .normal(normal.x, normal.y, normal.z);
        vertexConsumer.vertex(matrix, (float) end.x, (float) end.y, (float) end.z)
            .color(color.x(), color.y(), color.z(), alpha)
            .normal(normal.x, normal.y, normal.z);
    }

    @Override
    public boolean shouldRender(Camera camera) {
        List<Vec3d> vectorsSnapshot = vectors;
        List<Vec3d> startPointsSnapshot = startPoints;
        if (vectorsSnapshot.isEmpty() || isExpired()) {
            return false;
        }
        float maxDistance = PreviewRenderer.getInstance().getSettings().maxRenderDistance;
        Vec3d cameraPos = camera.getCameraPos();
        for (Vec3d start : startPointsSnapshot) {
            if (cameraPos.distanceTo(start) <= maxDistance) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void cleanup() {
        vectors = new ArrayList<>();
        startPoints = new ArrayList<>();
    }
}

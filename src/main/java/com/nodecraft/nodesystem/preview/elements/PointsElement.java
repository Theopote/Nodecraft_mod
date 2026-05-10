package com.nodecraft.nodesystem.preview.elements;

import com.nodecraft.nodesystem.preview.AbstractPreviewElement;
import com.nodecraft.nodesystem.preview.PreviewRenderer;
import com.nodecraft.nodesystem.preview.PreviewOptions;
import com.nodecraft.nodesystem.preview.protocol.PreviewPoint;
import com.nodecraft.nodesystem.preview.protocol.PreviewPointsPayload;
import com.nodecraft.nodesystem.util.Coordinate;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class PointsElement extends AbstractPreviewElement {

    private volatile List<Vec3d> points = new ArrayList<>();
    private Vector3f color = new Vector3f(1.0f, 0.2f, 0.2f);
    private float lineWidth = 1.5f;
    private float pointSize = 0.2f;

    public PointsElement(String id, String ownerNodeId, Object data, PreviewOptions options) {
        super(id, ownerNodeId, data, options);
        this.renderPriority = 18;

        if (options.color != null) {
            this.color = new Vector3f(options.color);
        }
        if (options.lineWidth != null) {
            this.lineWidth = Math.max(0.25f, options.lineWidth);
        }
        if (options.pointSize != null) {
            this.pointSize = Math.max(0.05f, options.pointSize);
        }
    }

    @Override
    protected void processData(Object data) {
        if (data instanceof PreviewPointsPayload payload) {
            List<Vec3d> nextPoints = new ArrayList<>(payload.getPoints().size());
            for (PreviewPoint p : payload.getPoints()) {
                nextPoints.add(new Vec3d(p.x(), p.y(), p.z()));
            }
            points = nextPoints;
            return;
        }

        List<Vec3d> nextPoints = new ArrayList<>();

        if (data instanceof List<?> list) {
            for (Object item : list) {
                addPoint(nextPoints, item);
            }
            points = nextPoints;
            return;
        }

        addPoint(nextPoints, data);
        points = nextPoints;
    }

    private void addPoint(List<Vec3d> target, Object data) {
        if (data instanceof Coordinate coordinate) {
            target.add(new Vec3d(coordinate.getX() + 0.5d, coordinate.getY() + 0.5d, coordinate.getZ() + 0.5d));
        } else if (data instanceof BlockPos pos) {
            target.add(pos.toCenterPos());
        } else if (data instanceof Vec3d vec) {
            target.add(vec);
        }
    }

    @Override
    public void render(MatrixStack matrices, Camera camera, float partialTicks, float globalOpacity) {
        List<Vec3d> pointsSnapshot = points;
        if (pointsSnapshot.isEmpty()) {
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

        VertexConsumer vertexConsumer = provider.getBuffer(RenderLayers.lines());
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Vec3d cameraPos = camera.getCameraPos();

        for (Vec3d point : pointsSnapshot) {
            drawCross(vertexConsumer, matrix, point.subtract(cameraPos), pointSize, finalOpacity);
        }

        if (flushImmediately) {
            immediate.draw();
        }
    }

    private void drawCross(VertexConsumer vertexConsumer, Matrix4f matrix, Vec3d center, float size, float alpha) {
        double half = size * 0.5d;
        drawLine(vertexConsumer, matrix, new Vec3d(center.x - half, center.y, center.z), new Vec3d(center.x + half, center.y, center.z), alpha);
        drawLine(vertexConsumer, matrix, new Vec3d(center.x, center.y - half, center.z), new Vec3d(center.x, center.y + half, center.z), alpha);
        drawLine(vertexConsumer, matrix, new Vec3d(center.x, center.y, center.z - half), new Vec3d(center.x, center.y, center.z + half), alpha);
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
        if (pointsSnapshot.isEmpty() || isExpired()) {
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

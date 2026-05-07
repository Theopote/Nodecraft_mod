package com.nodecraft.nodesystem.preview.elements;

import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.preview.AbstractPreviewElement;
import com.nodecraft.nodesystem.preview.PreviewOptions;
import com.nodecraft.nodesystem.preview.PreviewRenderer;
import com.nodecraft.nodesystem.preview.protocol.PreviewRegionPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
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

public class RegionBoxElement extends AbstractPreviewElement {

    private volatile List<BoundingBox> regions = new ArrayList<>();
    private Vector3f color = new Vector3f(0.2f, 0.7f, 1.0f);
    private Vector3f fillColor = new Vector3f(0.2f, 0.7f, 1.0f);
    private boolean enablePulse = false;
    private boolean showFill = false;
    private boolean showOutline = true;
    private float lineWidth = 2.0f;
    private static final float OUTLINE_EXPAND = 0.004f;
    private static final int[][] BOX_EDGES = new int[][] {
        {0, 1}, {1, 2}, {2, 3}, {3, 0},
        {4, 5}, {5, 6}, {6, 7}, {7, 4},
        {0, 4}, {1, 5}, {2, 6}, {3, 7}
    };

    public RegionBoxElement(String id, String ownerNodeId, Object data, PreviewOptions options) {
        super(id, ownerNodeId, data, options);
        this.renderPriority = 15;

        if (options.color != null) {
            this.color = new Vector3f(options.color);
        }
        if (options.tintColor != null) {
            this.fillColor = new Vector3f(options.tintColor);
        }
        if (options.pulseAnimation != null) {
            this.enablePulse = options.pulseAnimation;
        }
        if (options.showFill != null) {
            this.showFill = options.showFill;
        }
        if (options.showOutline != null) {
            this.showOutline = options.showOutline;
        }
        if (options.lineWidth != null) {
            this.lineWidth = Math.max(0.25f, options.lineWidth);
        }
    }

    @Override
    public void updateOptions(PreviewOptions newOptions) {
        super.updateOptions(newOptions);
        if (newOptions == null) {
            return;
        }

        if (newOptions.color != null) {
            this.color = new Vector3f(newOptions.color);
        }
        if (newOptions.tintColor != null) {
            this.fillColor = new Vector3f(newOptions.tintColor);
        }
        if (newOptions.pulseAnimation != null) {
            this.enablePulse = newOptions.pulseAnimation;
        }
        if (newOptions.showFill != null) {
            this.showFill = newOptions.showFill;
        }
        if (newOptions.showOutline != null) {
            this.showOutline = newOptions.showOutline;
        }
        if (newOptions.lineWidth != null) {
            this.lineWidth = Math.max(0.25f, newOptions.lineWidth);
        }
    }

    @Override
    protected void processData(Object data) {
        if (data instanceof PreviewRegionPayload pr) {
            regions = List.of(new BoundingBox(
                new Vec3d(pr.minX(), pr.minY(), pr.minZ()),
                new Vec3d(pr.maxX() + 1.0d, pr.maxY() + 1.0d, pr.maxZ() + 1.0d)
            ));
            return;
        }

        List<BoundingBox> nextRegions = new ArrayList<>();

        if (data instanceof List<?> list) {
            for (Object item : list) {
                processItem(nextRegions, item);
            }
            regions = nextRegions;
            return;
        }

        processItem(nextRegions, data);
        regions = nextRegions;
    }

    private void processItem(List<BoundingBox> target, Object item) {
        if (item instanceof PreviewRegionPayload pr) {
            target.add(new BoundingBox(
                new Vec3d(pr.minX(), pr.minY(), pr.minZ()),
                new Vec3d(pr.maxX() + 1.0d, pr.maxY() + 1.0d, pr.maxZ() + 1.0d)
            ));
        } else if (item instanceof BoundingBox box) {
            target.add(box);
        } else if (item instanceof RegionData region && region.isComplete()) {
            BlockPos min = region.getMinCorner();
            BlockPos max = region.getMaxCorner();
            if (min != null && max != null) {
                target.add(new BoundingBox(
                    new Vec3d(min.getX(), min.getY(), min.getZ()),
                    new Vec3d(max.getX() + 1.0d, max.getY() + 1.0d, max.getZ() + 1.0d)
                ));
            }
        } else if (item instanceof Object[] array && array.length >= 2) {
            Vec3d min = extractPosition(array[0]);
            Vec3d max = extractPosition(array[1]);
            if (min != null && max != null) {
                target.add(new BoundingBox(min, max));
            }
        }
    }

    private Vec3d extractPosition(Object obj) {
        if (obj instanceof Vec3d vec) {
            return vec;
        }
        if (obj instanceof BlockPos pos) {
            return new Vec3d(pos.getX(), pos.getY(), pos.getZ());
        }
        return null;
    }

    @Override
    public void render(MatrixStack matrices, Camera camera, float partialTicks, float globalOpacity) {
        List<BoundingBox> regionsSnapshot = regions;
        if (regionsSnapshot.isEmpty()) {
            return;
        }

        float finalOpacity = opacity * globalOpacity;
        if (enablePulse) {
            finalOpacity *= (float) (0.5f + 0.5f * Math.sin(System.currentTimeMillis() * 0.003));
        }
        if (finalOpacity <= 0.01f) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        VertexConsumerProvider.Immediate immediate = client.getBufferBuilders().getEntityVertexConsumers();
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Vec3d cameraPos = camera.getCameraPos();

        if (showFill) {
            VertexConsumer fillVertexConsumer = immediate.getBuffer(RenderLayers.debugFilledBox());
            for (BoundingBox region : regionsSnapshot) {
                drawFilledBox(fillVertexConsumer, matrix, region, cameraPos, Math.max(0.12f, finalOpacity * 0.5f));
            }
            immediate.draw();
        }

        if (showOutline) {
            VertexConsumer lineVertexConsumer = immediate.getBuffer(RenderLayers.lines());
            for (BoundingBox region : regionsSnapshot) {
                drawBox(lineVertexConsumer, matrices, region, cameraPos, Math.max(0.95f, finalOpacity));
            }
            immediate.draw();
        }
    }

    private void drawBox(VertexConsumer vertexConsumer, MatrixStack matrices, BoundingBox box, Vec3d cameraPos, float alpha) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float clampedAlpha = Math.max(0.0f, Math.min(1.0f, alpha));
        drawBoxEdges(vertexConsumer, matrix, box, cameraPos, OUTLINE_EXPAND, color.x(), color.y(), color.z(), clampedAlpha);
    }

    private void drawBoxEdges(
        VertexConsumer vertexConsumer,
        Matrix4f matrix,
        BoundingBox box,
        Vec3d cameraPos,
        float expand,
        float r,
        float g,
        float b,
        float alpha
    ) {
        Vec3d min = box.min.subtract(cameraPos).subtract(expand, expand, expand);
        Vec3d max = box.max.subtract(cameraPos).add(expand, expand, expand);

        Vec3d[] vertices = new Vec3d[] {
            new Vec3d(min.x, min.y, min.z),
            new Vec3d(max.x, min.y, min.z),
            new Vec3d(max.x, min.y, max.z),
            new Vec3d(min.x, min.y, max.z),
            new Vec3d(min.x, max.y, min.z),
            new Vec3d(max.x, max.y, min.z),
            new Vec3d(max.x, max.y, max.z),
            new Vec3d(min.x, max.y, max.z)
        };

        for (int[] edge : BOX_EDGES) {
            drawLine(vertexConsumer, matrix, vertices[edge[0]], vertices[edge[1]], r, g, b, alpha);
        }
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

        vertexConsumer.vertex(matrix, (float) start.x, (float) start.y, (float) start.z)
            .color(r, g, b, alpha)
            .normal(normal.x, normal.y, normal.z)
            .lineWidth(lineWidth);
        vertexConsumer.vertex(matrix, (float) end.x, (float) end.y, (float) end.z)
            .color(r, g, b, alpha)
            .normal(normal.x, normal.y, normal.z)
            .lineWidth(lineWidth);
    }

    private void drawFilledBox(VertexConsumer vertexConsumer, Matrix4f matrix, BoundingBox box, Vec3d cameraPos, float alpha) {
        float minX = (float) (box.min.x - cameraPos.x);
        float minY = (float) (box.min.y - cameraPos.y);
        float minZ = (float) (box.min.z - cameraPos.z);
        float maxX = (float) (box.max.x - cameraPos.x);
        float maxY = (float) (box.max.y - cameraPos.y);
        float maxZ = (float) (box.max.z - cameraPos.z);

        // Bottom
        quad(vertexConsumer, matrix, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, alpha);
        // Top
        quad(vertexConsumer, matrix, minX, maxY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, minX, maxY, maxZ, alpha);
        // North
        quad(vertexConsumer, matrix, minX, minY, minZ, maxX, minY, minZ, maxX, maxY, minZ, minX, maxY, minZ, alpha);
        // South
        quad(vertexConsumer, matrix, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, alpha);
        // West
        quad(vertexConsumer, matrix, minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, alpha);
        // East
        quad(vertexConsumer, matrix, maxX, minY, minZ, maxX, minY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, alpha);
    }

    private void quad(
        VertexConsumer vertexConsumer,
        Matrix4f matrix,
        float x1, float y1, float z1,
        float x2, float y2, float z2,
        float x3, float y3, float z3,
        float x4, float y4, float z4,
        float alpha
    ) {
        Vector3f normal = new Vector3f(
            ((y2 - y1) * (z3 - z1)) - ((z2 - z1) * (y3 - y1)),
            ((z2 - z1) * (x3 - x1)) - ((x2 - x1) * (z3 - z1)),
            ((x2 - x1) * (y3 - y1)) - ((y2 - y1) * (x3 - x1))
        );
        if (normal.lengthSquared() < 1.0e-8f) {
            normal.set(0.0f, 1.0f, 0.0f);
        } else {
            normal.normalize();
        }

        fullBrightVertex(vertexConsumer, matrix, x1, y1, z1, alpha, normal);
        fullBrightVertex(vertexConsumer, matrix, x2, y2, z2, alpha, normal);
        fullBrightVertex(vertexConsumer, matrix, x3, y3, z3, alpha, normal);
        fullBrightVertex(vertexConsumer, matrix, x4, y4, z4, alpha, normal);

        // Draw reversed winding to keep fill visible even if current layer/state culls back faces.
        Vector3f opposite = new Vector3f(normal).mul(-1.0f);
        fullBrightVertex(vertexConsumer, matrix, x4, y4, z4, alpha, opposite);
        fullBrightVertex(vertexConsumer, matrix, x3, y3, z3, alpha, opposite);
        fullBrightVertex(vertexConsumer, matrix, x2, y2, z2, alpha, opposite);
        fullBrightVertex(vertexConsumer, matrix, x1, y1, z1, alpha, opposite);
    }

    private void fullBrightVertex(
        VertexConsumer vertexConsumer,
        Matrix4f matrix,
        float x,
        float y,
        float z,
        float alpha,
        Vector3f normal
    ) {
        vertexConsumer.vertex(matrix, x, y, z)
            .color(fillColor.x(), fillColor.y(), fillColor.z(), alpha)
            .texture(0.5f, 0.5f)
            .overlay(OverlayTexture.DEFAULT_UV)
            .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
            .normal(normal.x, normal.y, normal.z);
    }

    @Override
    public boolean shouldRender(Camera camera) {
        List<BoundingBox> regionsSnapshot = regions;
        if (regionsSnapshot.isEmpty() || isExpired()) {
            return false;
        }
        Vec3d cameraPos = camera.getCameraPos();
        float maxDistance = PreviewRenderer.getInstance().getSettings().maxRenderDistance;
        for (BoundingBox region : regionsSnapshot) {
            if (cameraPos.distanceTo(region.getCenter()) <= maxDistance + region.getSize()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void cleanup() {
        regions = new ArrayList<>();
    }

    public static class BoundingBox {
        public final Vec3d min;
        public final Vec3d max;

        public BoundingBox(Vec3d min, Vec3d max) {
            this.min = new Vec3d(Math.min(min.x, max.x), Math.min(min.y, max.y), Math.min(min.z, max.z));
            this.max = new Vec3d(Math.max(min.x, max.x), Math.max(min.y, max.y), Math.max(min.z, max.z));
        }

        public Vec3d getCenter() {
            return new Vec3d((min.x + max.x) / 2.0d, (min.y + max.y) / 2.0d, (min.z + max.z) / 2.0d);
        }

        public double getSize() {
            return Math.max(Math.max(max.x - min.x, max.y - min.y), max.z - min.z);
        }
    }
}

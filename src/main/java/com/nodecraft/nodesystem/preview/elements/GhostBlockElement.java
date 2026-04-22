package com.nodecraft.nodesystem.preview.elements;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.nodesystem.preview.AbstractPreviewElement;
import com.nodecraft.nodesystem.preview.PreviewOptions;
import com.nodecraft.nodesystem.preview.protocol.PreviewBlock;
import com.nodecraft.nodesystem.preview.protocol.PreviewBlocksPayload;
import com.nodecraft.nodesystem.preview.PreviewRenderer;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * 幽灵方块预览元素 (完善版)
 * 用于显示半透明的方块预览，支持原始纹理渲染
 * <p><b>Preview 协议（方块）</b>：仅接受 {@link com.nodecraft.nodesystem.preview.protocol.PreviewBlocksPayload}，
 * 由 {@link com.nodecraft.nodesystem.preview.PreviewManager} / {@link com.nodecraft.nodesystem.preview.PreviewRenderer} 保证类型边界。
 * <p>
 * 性能优化：
 * - 避免在渲染循环中重复获取设置，将 maxRenderDistance 在循环外获取一次
 * - 使用方块颜色信息而非完整纹理渲染，提高渲染效率
 * - 优化了内存分配和方法调用开销
 * <p>
 * 代码简化：
 * - 使用 Java 16+ 的模式匹配（Pattern Matching for instanceof）简化类型检查
 * - 将复杂的 Map 数据处理逻辑提取到单独的方法中
 * - 改进了代码的可读性和维护性
 * <p>
 * 健壮性优化：
 * - 无效方块ID返回空气而不是石头，避免错误的视觉效果
 * - 空气方块会被渲染逻辑自动跳过，不会显示任何内容
 * - 提供更好的调试体验，错误不会产生意外的视觉干扰
 * <p>
 * 渲染状态管理优化：
 * - 假设通用渲染状态（blend, depthTest, depthMask）已由 PreviewRenderer 设置
 * - 每个渲染方法只管理自己特有的状态，并在 finally 块中恢复
 * - 避免重复设置和恢复通用状态，提高渲染效率
 * - 使用 try-finally 确保状态恢复的可靠性
 */
public class GhostBlockElement extends AbstractPreviewElement {
    
    private volatile List<BlockData> blocks = new ArrayList<>();
    private Vector3f tintColor = new Vector3f(0.2f, 0.7f, 1.0f); // 默认天蓝色
    private String textureMode = "original"; // "original", "solid_color", "wireframe"
    private boolean useOriginalTexture = true;
    private float ghostOpacity = 0.5f; // 幽灵方块的透明度
    private long lastRenderInfoLogMs = 0L;
    private long lastShouldRenderSkipLogMs = 0L;
    

    public GhostBlockElement(String id, String ownerNodeId, Object data, PreviewOptions options) {
        super(id, ownerNodeId, data, options);
        this.renderPriority = 20; // 较低优先级，在线框之后渲染
        
        // 从选项中读取设置（与 RegionBoxElement 一致：主色用 color，着色用 tintColor 覆盖）
        if (options.color != null) {
            this.tintColor = new Vector3f(options.color);
        }
        if (options.tintColor != null) {
            this.tintColor = new Vector3f(options.tintColor);
        }
        if (options.textureMode != null) {
            this.textureMode = options.textureMode;
        }
        if (options.useOriginalTexture != null) {
            this.useOriginalTexture = options.useOriginalTexture;
        }
        if (options.opacity != null) {
            this.ghostOpacity = options.opacity;
        }
    }
    
    /**
     * 仅解析 {@link PreviewBlocksPayload}。
     */
    @Override
    protected void processData(Object data) {
        if (!(data instanceof PreviewBlocksPayload payload)) {
            NodeCraft.LOGGER.warn(
                "GhostBlockElement expected PreviewBlocksPayload but got {}",
                data == null ? "null" : data.getClass().getName()
            );
            blocks = List.of();
            return;
        }
        List<BlockData> nextBlocks = new ArrayList<>(payload.getBlocks().size());
        for (PreviewBlock b : payload.getBlocks()) {
            nextBlocks.add(new BlockData(new Vec3d(b.x(), b.y(), b.z()), b.blockId()));
        }
        blocks = nextBlocks;
    }
    
    @Override
    public void render(MatrixStack matrices, Camera camera, float partialTicks, float globalOpacity) {
        List<BlockData> blocksSnapshot = blocks;
        if (blocksSnapshot.isEmpty()) {
            return;
        }
        
        // 计算最终透明度
        float finalOpacity = ghostOpacity * globalOpacity;

        long now = System.currentTimeMillis();
        if (now - lastRenderInfoLogMs > 1000L) {
            lastRenderInfoLogMs = now;
            NodeCraft.LOGGER.info("GhostBlockElement render tick: ownerNode={}, mode={}, blocks={}, ghostOpacity={}, globalOpacity={}, finalOpacity={}, maxDistance={}",
                ownerNodeId, textureMode, blocksSnapshot.size(), ghostOpacity, globalOpacity, finalOpacity,
                PreviewRenderer.getInstance().getSettings().maxRenderDistance);
        }

        if (finalOpacity <= 0.01f) {
            return;
        }
        
        MinecraftClient client = MinecraftClient.getInstance();
        World world = client.world;
        float maxRenderDistance = PreviewRenderer.getInstance().getSettings().maxRenderDistance;
        
        if (world == null) {
            NodeCraft.LOGGER.warn("世界为空，无法渲染幽灵方块");
            return;
        }

        // 根据纹理模式选择渲染方法
        switch (textureMode) {
            case "solid_color":
                renderSolidColor(matrices, camera, finalOpacity, blocksSnapshot, maxRenderDistance);
                break;
            case "wireframe":
                renderWireframe(matrices, camera, finalOpacity, blocksSnapshot, maxRenderDistance);
                break;
            case "original":
            default:
                renderOriginalTexture(matrices, camera, world, finalOpacity, blocksSnapshot, maxRenderDistance);
                break;
        }
    }
    
    /**
     * 渲染原始纹理的幽灵方块
     * <p>
     * 优化说明：
     * - 使用方块的原始颜色信息而不是完整的纹理渲染
     * - 通过 BlockColors.getColor() 获取方块的基础颜色
     * - 应用着色和透明度效果
     * - 相比完整的 BlockRenderManager.renderBlock()，这种方法更高效且稳定
     * <p>
     * 渲染状态管理：
     * - 假设通用状态（blend, depthTest, depthMask）已由 PreviewRenderer 设置
     * - 此方法不需要设置任何特有状态，直接使用通用状态即可
     */
    private void renderOriginalTexture(MatrixStack matrices,
                                       Camera camera,
                                       World world,
                                       float opacity,
                                       List<BlockData> blocksSnapshot,
                                       float maxRenderDistance) {
        MinecraftClient client = MinecraftClient.getInstance();
        Vec3d cameraPos = camera.getCameraPos();
        DrawContext draw = beginDraw(client);
        VertexConsumer vertexConsumer = draw.provider().getBuffer(RenderLayers.debugFilledBox());
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        for (BlockData blockData : blocksSnapshot) {
            double distance = cameraPos.distanceTo(blockData.position);
            if (distance > maxRenderDistance) {
                continue;
            }

            BlockPos blockPos = new BlockPos(
                (int) Math.floor(blockData.position.x),
                (int) Math.floor(blockData.position.y),
                (int) Math.floor(blockData.position.z)
            );

            BlockState blockState = getBlockState(blockData.blockId);
            if (blockState.isAir()) {
                continue;
            }

            float minX = (float) (blockData.position.x - cameraPos.x);
            float minY = (float) (blockData.position.y - cameraPos.y);
            float minZ = (float) (blockData.position.z - cameraPos.z);
            float maxX = minX + 1.0f;
            float maxY = minY + 1.0f;
            float maxZ = minZ + 1.0f;

            try {
                int color = MinecraftClient.getInstance().getBlockColors().getColor(blockState, world, blockPos, 0);
                float r = ((color >> 16) & 0xFF) / 255.0f;
                float g = ((color >> 8) & 0xFF) / 255.0f;
                float b = (color & 0xFF) / 255.0f;
                r = Math.min(1.0f, r * tintColor.x());
                g = Math.min(1.0f, g * tintColor.y());
                b = Math.min(1.0f, b * tintColor.z());

                drawFilledAxisAlignedBox(vertexConsumer, matrix, minX, minY, minZ, maxX, maxY, maxZ, r, g, b, opacity);
            } catch (Exception e) {
                NodeCraft.LOGGER.warn("渲染幽灵方块失败: {}, 位置: {}", blockData.blockId, blockPos, e);
            }
        }
        endDraw(draw);
    }
    
    /**
     * 渲染纯色幽灵方块
     * <p>
     * 渲染状态管理：
     * - 假设通用状态（blend, depthTest, depthMask）已由 PreviewRenderer 设置
     * - 此方法特有状态：disableCull（禁用面剔除以确保所有面都可见）
     */
    private void renderSolidColor(MatrixStack matrices,
                                  Camera camera,
                                  float opacity,
                                  List<BlockData> blocksSnapshot,
                                  float maxRenderDistance) {
        Vec3d cameraPos = camera.getCameraPos();
        MinecraftClient client = MinecraftClient.getInstance();
        DrawContext draw = beginDraw(client);
        VertexConsumer vertexConsumer = draw.provider().getBuffer(RenderLayers.debugFilledBox());
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        float r = tintColor.x();
        float g = tintColor.y();
        float b = tintColor.z();

        for (BlockData blockData : blocksSnapshot) {
            double distance = cameraPos.distanceTo(blockData.position);
            if (distance > maxRenderDistance) {
                continue;
            }

            BlockState blockState = getBlockState(blockData.blockId);
            if (blockState.isAir()) {
                continue;
            }

            float minX = (float) (blockData.position.x - cameraPos.x);
            float minY = (float) (blockData.position.y - cameraPos.y);
            float minZ = (float) (blockData.position.z - cameraPos.z);
            float maxX = minX + 1.0f;
            float maxY = minY + 1.0f;
            float maxZ = minZ + 1.0f;

            drawFilledAxisAlignedBox(vertexConsumer, matrix, minX, minY, minZ, maxX, maxY, maxZ, r, g, b, opacity);
        }

        endDraw(draw);
    }
    
    /**
     * 渲染线框幽灵方块
     * <p>
     * 渲染状态管理：
     * - 假设通用状态（blend, depthTest, depthMask）已由 PreviewRenderer 设置
     * - 此方法特有状态：lineWidth（线宽）和 disableCull（禁用面剔除）
     */
    private void renderWireframe(MatrixStack matrices,
                                 Camera camera,
                                 float opacity,
                                 List<BlockData> blocksSnapshot,
                                 float maxRenderDistance) {
        Vec3d cameraPos = camera.getCameraPos();
        MinecraftClient client = MinecraftClient.getInstance();
        DrawContext draw = beginDraw(client);
        VertexConsumer vertexConsumer = draw.provider().getBuffer(RenderLayers.lines());
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        
        float r = tintColor.x();
        float g = tintColor.y();
        float b = tintColor.z();

        // 立方体的顶点
        Vec3d[] vertices = {
            new Vec3d(0, 0, 0), new Vec3d(1, 0, 0), new Vec3d(1, 1, 0), new Vec3d(0, 1, 0),
            new Vec3d(0, 0, 1), new Vec3d(1, 0, 1), new Vec3d(1, 1, 1), new Vec3d(0, 1, 1)
        };
        
        // 立方体的边
        int[][] edges = {
            {0, 1}, {1, 2}, {2, 3}, {3, 0}, // 底面
            {4, 5}, {5, 6}, {6, 7}, {7, 4}, // 顶面
            {0, 4}, {1, 5}, {2, 6}, {3, 7}  // 垂直边
        };
        
        for (BlockData blockData : blocksSnapshot) {
            // 检查渲染距离
            double distance = cameraPos.distanceTo(blockData.position);
            if (distance > maxRenderDistance) {
                continue;
            }
            
            // 检查方块是否为空气（健壮性优化）
            BlockState blockState = getBlockState(blockData.blockId);
            if (blockState.isAir()) {
                continue;
            }
            
            // 计算相对位置
            float renderX = (float) (blockData.position.x - cameraPos.x);
            float renderY = (float) (blockData.position.y - cameraPos.y);
            float renderZ = (float) (blockData.position.z - cameraPos.z);
            
            // 绘制立方体的边
            for (int[] edge : edges) {
                Vec3d v1 = vertices[edge[0]];
                Vec3d v2 = vertices[edge[1]];
                
                vertexConsumer.vertex(matrix, renderX + (float)v1.x, renderY + (float)v1.y, renderZ + (float)v1.z)
                    .color(r, g, b, opacity)
                    .normal(0.0f, 1.0f, 0.0f)
                    .lineWidth(1.5f);
                vertexConsumer.vertex(matrix, renderX + (float)v2.x, renderY + (float)v2.y, renderZ + (float)v2.z)
                    .color(r, g, b, opacity)
                    .normal(0.0f, 1.0f, 0.0f)
                    .lineWidth(1.5f);
            }
        }
        
        endDraw(draw);
    }

    private record DrawContext(VertexConsumerProvider provider, VertexConsumerProvider.Immediate ownImmediate, boolean flushAfter) {
    }

    private DrawContext beginDraw(MinecraftClient client) {
        VertexConsumerProvider active = PreviewRenderer.getInstance().getActiveVertexConsumers();
        if (active != null) {
            return new DrawContext(active, null, false);
        }
        VertexConsumerProvider.Immediate immediate = client.getBufferBuilders().getEntityVertexConsumers();
        return new DrawContext(immediate, immediate, true);
    }

    private void endDraw(DrawContext ctx) {
        if (ctx.flushAfter() && ctx.ownImmediate() != null) {
            ctx.ownImmediate().draw();
        }
    }

    private void drawFilledAxisAlignedBox(
        VertexConsumer vertexConsumer,
        Matrix4f matrix,
        float minX,
        float minY,
        float minZ,
        float maxX,
        float maxY,
        float maxZ,
        float r,
        float g,
        float b,
        float a
    ) {
        quad(vertexConsumer, matrix, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        quad(vertexConsumer, matrix, minX, maxY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        quad(vertexConsumer, matrix, minX, minY, minZ, maxX, minY, minZ, maxX, maxY, minZ, minX, maxY, minZ, r, g, b, a);
        quad(vertexConsumer, matrix, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        quad(vertexConsumer, matrix, minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);
        quad(vertexConsumer, matrix, maxX, minY, minZ, maxX, minY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, r, g, b, a);
    }

    private void quad(
        VertexConsumer vertexConsumer,
        Matrix4f matrix,
        float x1,
        float y1,
        float z1,
        float x2,
        float y2,
        float z2,
        float x3,
        float y3,
        float z3,
        float x4,
        float y4,
        float z4,
        float r,
        float g,
        float b,
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

        fullBrightVertex(vertexConsumer, matrix, x1, y1, z1, r, g, b, alpha, normal);
        fullBrightVertex(vertexConsumer, matrix, x2, y2, z2, r, g, b, alpha, normal);
        fullBrightVertex(vertexConsumer, matrix, x3, y3, z3, r, g, b, alpha, normal);
        fullBrightVertex(vertexConsumer, matrix, x4, y4, z4, r, g, b, alpha, normal);

        Vector3f opposite = new Vector3f(normal).mul(-1.0f);
        fullBrightVertex(vertexConsumer, matrix, x4, y4, z4, r, g, b, alpha, opposite);
        fullBrightVertex(vertexConsumer, matrix, x3, y3, z3, r, g, b, alpha, opposite);
        fullBrightVertex(vertexConsumer, matrix, x2, y2, z2, r, g, b, alpha, opposite);
        fullBrightVertex(vertexConsumer, matrix, x1, y1, z1, r, g, b, alpha, opposite);
    }

    private void fullBrightVertex(
        VertexConsumer vertexConsumer,
        Matrix4f matrix,
        float x,
        float y,
        float z,
        float r,
        float g,
        float b,
        float alpha,
        Vector3f normal
    ) {
        vertexConsumer.vertex(matrix, x, y, z)
            .color(r, g, b, alpha)
            .texture(0.5f, 0.5f)
            .overlay(OverlayTexture.DEFAULT_UV)
            .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
            .normal(normal.x, normal.y, normal.z);
    }
    
    /**
     * 根据方块ID获取方块状态
     * <p>
     * 健壮性优化：
     * - 当方块ID无效或解析失败时，返回空气而不是石头
     * - 避免因无效ID而在预览中产生错误的视觉效果
     * - 空气方块会被渲染逻辑自动跳过，不会显示任何内容
     */
    private BlockState getBlockState(String blockId) {
        try {
            Identifier identifier = Identifier.tryParse(blockId);
            if (identifier == null) {
                NodeCraft.LOGGER.warn("无效的方块ID格式: {}, 将其视为空气", blockId);
                return Blocks.AIR.getDefaultState();
            }
            Block block = Registries.BLOCK.get(identifier);
            return block.getDefaultState();
        } catch (Exception e) {
            NodeCraft.LOGGER.warn("无法解析方块ID: {}, 将其视为空气", blockId);
            return Blocks.AIR.getDefaultState();
        }
    }
    
    @Override
    public boolean shouldRender(Camera camera) {
        if (blocks.isEmpty()) {
            logShouldRenderSkip(camera, "empty_blocks", Double.NaN);
            return false;
        }
        
        // 检查是否过期
        if (isExpired()) {
            logShouldRenderSkip(camera, "expired", Double.NaN);
            return false;
        }
        
        // 检查距离 - 在循环外获取一次最大渲染距离
        Vec3d cameraPos = camera.getCameraPos();
        float maxDistance = PreviewRenderer.getInstance().getSettings().maxRenderDistance;
        double nearest = Double.MAX_VALUE;
        for (BlockData block : blocks) {
            double d = cameraPos.distanceTo(block.position);
            if (d < nearest) {
                nearest = d;
            }
            if (d <= maxDistance) {
                return true;
            }
        }

        logShouldRenderSkip(camera, "beyond_max_distance", nearest);
        return false;
    }

    private void logShouldRenderSkip(Camera camera, String reason, double nearestDistance) {
        long now = System.currentTimeMillis();
        if (now - lastShouldRenderSkipLogMs < 2000L) {
            return;
        }
        lastShouldRenderSkipLogMs = now;
        Vec3d cam = camera.getCameraPos();
        BlockData sample = blocks.isEmpty() ? null : blocks.getFirst();
        if ("beyond_max_distance".equals(reason) && sample != null) {
            NodeCraft.LOGGER.info(
                "GhostBlockElement shouldRender=false: ownerNode={}, reason={}, blockCount={}, maxDistance={}, nearest={}, cam=({},{},{}), samplePos=({},{},{})",
                ownerNodeId, reason, blocks.size(),
                PreviewRenderer.getInstance().getSettings().maxRenderDistance,
                Double.isFinite(nearestDistance) ? nearestDistance : -1.0,
                cam.x, cam.y, cam.z,
                sample.position.x, sample.position.y, sample.position.z
            );
        } else {
            NodeCraft.LOGGER.info("GhostBlockElement shouldRender=false: ownerNode={}, reason={}, blockCount={}, cam=({},{},{})",
                ownerNodeId, reason, blocks.size(), cam.x, cam.y, cam.z);
        }
    }
    
    @Override
    public void cleanup() {
        blocks.clear();
    }
    
    // ================= Getters/Setters =================
    
    public List<BlockData> getBlocks() {
        return new ArrayList<>(blocks);
    }
    
    public Vector3f getTintColor() {
        return new Vector3f(tintColor);
    }
    
    public void setTintColor(Vector3f tintColor) {
        this.tintColor = new Vector3f(tintColor);
    }
    
    public String getTextureMode() {
        return textureMode;
    }
    
    public void setTextureMode(String textureMode) {
        this.textureMode = textureMode;
    }
    
    public boolean isUseOriginalTexture() {
        return useOriginalTexture;
    }
    
    public void setUseOriginalTexture(boolean useOriginalTexture) {
        this.useOriginalTexture = useOriginalTexture;
    }
    
    public float getGhostOpacity() {
        return ghostOpacity;
    }
    
    public void setGhostOpacity(float ghostOpacity) {
        this.ghostOpacity = Math.max(0.0f, Math.min(1.0f, ghostOpacity));
    }
    
    // ================= 内部类 =================
    
    /**
     * 方块数据
     */
    public static class BlockData {
        public final Vec3d position;
        public final String blockId;
        
        public BlockData(Vec3d position, String blockId) {
            this.position = position;
            this.blockId = blockId;
        }
        
        @Override
        public String toString() {
            return String.format("BlockData{pos=%.1f,%.1f,%.1f, block=%s}", 
                position.x, position.y, position.z, blockId);
        }
    }
} 
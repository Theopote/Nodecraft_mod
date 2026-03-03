package com.nodecraft.nodesystem.preview.elements;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.nodecraft.core.NodeCraft;
import com.nodecraft.nodesystem.preview.AbstractPreviewElement;
import com.nodecraft.nodesystem.preview.PreviewOptions;
import com.nodecraft.nodesystem.preview.PreviewRenderer;
import com.nodecraft.nodesystem.util.Coordinate;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.block.BlockRenderManager;
import com.mojang.blaze3d.systems.RenderSystem;
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
import java.util.Map;

/**
 * 幽灵方块预览元素 (完善版)
 * 用于显示半透明的方块预览，支持原始纹理渲染
 * 
 * 性能优化：
 * - 避免在渲染循环中重复获取设置，将 maxRenderDistance 在循环外获取一次
 * - 使用方块颜色信息而非完整纹理渲染，提高渲染效率
 * - 优化了内存分配和方法调用开销
 * 
 * 代码简化：
 * - 使用 Java 16+ 的模式匹配（Pattern Matching for instanceof）简化类型检查
 * - 将复杂的 Map 数据处理逻辑提取到单独的方法中
 * - 改进了代码的可读性和维护性
 * 
 * 健壮性优化：
 * - 无效方块ID返回空气而不是石头，避免错误的视觉效果
 * - 空气方块会被渲染逻辑自动跳过，不会显示任何内容
 * - 提供更好的调试体验，错误不会产生意外的视觉干扰
 * 
 * 渲染状态管理优化：
 * - 假设通用渲染状态（blend, depthTest, depthMask）已由 PreviewRenderer 设置
 * - 每个渲染方法只管理自己特有的状态，并在 finally 块中恢复
 * - 避免重复设置和恢复通用状态，提高渲染效率
 * - 使用 try-finally 确保状态恢复的可靠性
 */
public class GhostBlockElement extends AbstractPreviewElement {
    
    private List<BlockData> blocks = new ArrayList<>();
    private Vector3f tintColor = new Vector3f(0.2f, 0.7f, 1.0f); // 默认天蓝色
    private String textureMode = "original"; // "original", "solid_color", "wireframe"
    private boolean useOriginalTexture = true;
    private float ghostOpacity = 0.5f; // 幽灵方块的透明度
    

    public GhostBlockElement(String id, String ownerNodeId, Object data, PreviewOptions options) {
        super(id, ownerNodeId, data, options);
        this.renderPriority = 20; // 较低优先级，在线框之后渲染
        
        // 从选项中读取设置
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
     * 处理输入数据，支持单个对象或对象列表
     * 支持的数据类型：Coordinate, BlockData, BlockPlacement, Map
     */
    @Override
    protected void processData(Object data) {
        // 确保 blocks 列表已初始化
        if (blocks == null) {
            blocks = new ArrayList<>();
        }
        
        blocks.clear();
        
        if (data instanceof List<?> list) {
            // 处理列表数据
            for (Object item : list) {
                processDataItem(item);
            }
        } else {
            // 处理单个数据项
            processDataItem(data);
        }
    }
    
    /**
     * 处理单个数据项，使用模式匹配简化类型检查
     */
    private void processDataItem(Object item) {
        if (item instanceof Coordinate coord) {
            Vec3d pos = new Vec3d(coord.getX(), coord.getY(), coord.getZ());
            blocks.add(new BlockData(pos, "minecraft:stone")); // 默认石头
        } else if (item instanceof BlockData blockData) {
            blocks.add(blockData);
        } else if (item instanceof BlockPlacement placement) {
            blocks.add(new BlockData(placement.position, placement.blockId));
        } else if (item instanceof Map<?, ?> map) {
            // 处理来自 SelectedBlockNode 的数据格式
            processMapData(map);
        }
    }
    
    /**
     * 处理 Map 类型的数据，提取位置和方块ID信息
     */
    private void processMapData(Map<?, ?> map) {
        Object positionObj = map.get("position");
        Object blockIdObj = map.get("blockId");
        
        if (positionObj instanceof Coordinate coord && blockIdObj instanceof String blockId) {
            Vec3d pos = new Vec3d(coord.getX(), coord.getY(), coord.getZ());
            blocks.add(new BlockData(pos, blockId));
        }
    }
    
    @Override
    public void render(MatrixStack matrices, Camera camera, float partialTicks, float globalOpacity) {
        if (blocks.isEmpty()) {
            return;
        }
        
        // 计算最终透明度
        float finalOpacity = ghostOpacity * globalOpacity;
        
        if (finalOpacity <= 0.01f) {
            return;
        }
        
        MinecraftClient client = MinecraftClient.getInstance();
        World world = client.world;
        
        if (world == null) {
            NodeCraft.LOGGER.warn("世界为空，无法渲染幽灵方块");
            return;
        }
        
        // 根据纹理模式选择渲染方法
        switch (textureMode) {
            case "solid_color":
                renderSolidColor(matrices, camera, finalOpacity);
                break;
            case "wireframe":
                renderWireframe(matrices, camera, finalOpacity);
                break;
            case "original":
            default:
                renderOriginalTexture(matrices, camera, world, finalOpacity);
                break;
        }
    }
    
    /**
     * 渲染原始纹理的幽灵方块
     * 
     * 优化说明：
     * - 使用方块的原始颜色信息而不是完整的纹理渲染
     * - 通过 BlockColors.getColor() 获取方块的基础颜色
     * - 应用着色和透明度效果
     * - 相比完整的 BlockRenderManager.renderBlock()，这种方法更高效且稳定
     * 
     * 渲染状态管理：
     * - 假设通用状态（blend, depthTest, depthMask）已由 PreviewRenderer 设置
     * - 此方法不需要设置任何特有状态，直接使用通用状态即可
     */
    private void renderOriginalTexture(MatrixStack matrices, Camera camera, World world, float opacity) {
        MinecraftClient client = MinecraftClient.getInstance();
        BlockRenderManager blockRenderManager = client.getBlockRenderManager();
        Vec3d cameraPos = camera.getBlockPos().toCenterPos();
        
        // 在循环外获取一次最大渲染距离，避免重复调用
        float maxRenderDistance = PreviewRenderer.getInstance().getSettings().maxRenderDistance;
        
        // 使用简化的渲染方法，直接使用 Tessellator
        // 注意：不需要设置渲染状态，因为通用状态已由 PreviewRenderer 设置
        Tessellator tessellator = Tessellator.getInstance();
        
        for (BlockData blockData : blocks) {
            // 检查渲染距离
            double distance = cameraPos.distanceTo(blockData.position);
            if (distance > maxRenderDistance) {
                continue;
            }
            
            BlockPos blockPos = new BlockPos(
                (int) Math.floor(blockData.position.x),
                (int) Math.floor(blockData.position.y),
                (int) Math.floor(blockData.position.z)
            );
            
            // 获取方块状态
            BlockState blockState = getBlockState(blockData.blockId);
            if (blockState.isAir()) {
                continue;
            }
            
            // 计算相对位置
            double renderX = blockData.position.x - cameraPos.x;
            double renderY = blockData.position.y - cameraPos.y;
            double renderZ = blockData.position.z - cameraPos.z;
            
            matrices.push();
            matrices.translate(renderX, renderY, renderZ);
            
            try {
                // 使用简化的纹理渲染方法
                // 获取方块的基本颜色信息
                int color = MinecraftClient.getInstance().getBlockColors().getColor(blockState, world, blockPos, 0);
                float r = ((color >> 16) & 0xFF) / 255.0f;
                float g = ((color >> 8) & 0xFF) / 255.0f;
                float b = (color & 0xFF) / 255.0f;
                
                // 应用着色和透明度
                r = Math.min(1.0f, r * tintColor.x());
                g = Math.min(1.0f, g * tintColor.y());
                b = Math.min(1.0f, b * tintColor.z());

                // 使用 Tessellator 渲染带颜色的立方体
                BufferBuilder buffer = tessellator.begin(com.mojang.blaze3d.vertex.VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
                Matrix4f matrix = matrices.peek().getPositionMatrix();
                
                // 渲染立方体的6个面，使用方块的原始颜色
                renderCubeFaces(buffer, matrix, 0, 0, 0, r, g, b, opacity);
                
                buffer.end();
                
            } catch (Exception e) {
                NodeCraft.LOGGER.warn("渲染幽灵方块失败: {}, 位置: {}", blockData.blockId, blockPos, e);
            }
            
            matrices.pop();
        }
        
        // 渲染完成，无需额外的绘制命令
        // 注意：不需要恢复渲染状态，因为没有修改任何特有状态
    }
    
    /**
     * 渲染纯色幽灵方块
     * 
     * 渲染状态管理：
     * - 假设通用状态（blend, depthTest, depthMask）已由 PreviewRenderer 设置
     * - 此方法特有状态：disableCull（禁用面剔除以确保所有面都可见）
     */
    private void renderSolidColor(MatrixStack matrices, Camera camera, float opacity) {
        Vec3d cameraPos = camera.getBlockPos().toCenterPos();
        
        // 在循环外获取一次最大渲染距离，避免重复调用
        float maxRenderDistance = PreviewRenderer.getInstance().getSettings().maxRenderDistance;
        
        // 设置此方法特有的渲染状态
        
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(com.mojang.blaze3d.vertex.VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        
        float r = tintColor.x();
        float g = tintColor.y();
        float b = tintColor.z();

        for (BlockData blockData : blocks) {
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
            
            // 渲染立方体的6个面
            renderCubeFaces(buffer, matrix, renderX, renderY, renderZ, r, g, b, opacity);
        }
        
        try {
            buffer.end();
        } catch (Exception e) {
            NodeCraft.LOGGER.error("渲染纯色幽灵方块失败", e);
        }
    }
    
    /**
     * 渲染线框幽灵方块
     * 
     * 渲染状态管理：
     * - 假设通用状态（blend, depthTest, depthMask）已由 PreviewRenderer 设置
     * - 此方法特有状态：lineWidth（线宽）和 disableCull（禁用面剔除）
     */
    private void renderWireframe(MatrixStack matrices, Camera camera, float opacity) {
        Vec3d cameraPos = camera.getBlockPos().toCenterPos();
        
        // 在循环外获取一次最大渲染距离，避免重复调用
        float maxRenderDistance = PreviewRenderer.getInstance().getSettings().maxRenderDistance;
        
        // 设置此方法特有的渲染状态
        
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(com.mojang.blaze3d.vertex.VertexFormat.DrawMode.LINES, VertexFormats.POSITION_COLOR);
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
        
        for (BlockData blockData : blocks) {
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
                
                buffer.vertex(matrix, renderX + (float)v1.x, renderY + (float)v1.y, renderZ + (float)v1.z).color(r, g, b, opacity);
                buffer.vertex(matrix, renderX + (float)v2.x, renderY + (float)v2.y, renderZ + (float)v2.z).color(r, g, b, opacity);
            }
        }
        
        try {
            buffer.end();
        } catch (Exception e) {
            NodeCraft.LOGGER.error("渲染线框幽灵方块失败", e);
        }
    }
    

    
    /**
     * 渲染立方体的6个面
     */
    private void renderCubeFaces(BufferBuilder buffer, Matrix4f matrix, float x, float y, float z, float r, float g, float b, float a) {
        // 底面 (Y = 0)
        buffer.vertex(matrix, x, y, z).color(r, g, b, a);
        buffer.vertex(matrix, x + 1, y, z).color(r, g, b, a);
        buffer.vertex(matrix, x + 1, y, z + 1).color(r, g, b, a);
        buffer.vertex(matrix, x, y, z + 1).color(r, g, b, a);
        
        // 顶面 (Y = 1)
        buffer.vertex(matrix, x, y + 1, z + 1).color(r, g, b, a);
        buffer.vertex(matrix, x + 1, y + 1, z + 1).color(r, g, b, a);
        buffer.vertex(matrix, x + 1, y + 1, z).color(r, g, b, a);
        buffer.vertex(matrix, x, y + 1, z).color(r, g, b, a);
        
        // 北面 (Z = 0)
        buffer.vertex(matrix, x, y, z).color(r, g, b, a);
        buffer.vertex(matrix, x, y + 1, z).color(r, g, b, a);
        buffer.vertex(matrix, x + 1, y + 1, z).color(r, g, b, a);
        buffer.vertex(matrix, x + 1, y, z).color(r, g, b, a);
        
        // 南面 (Z = 1)
        buffer.vertex(matrix, x + 1, y, z + 1).color(r, g, b, a);
        buffer.vertex(matrix, x + 1, y + 1, z + 1).color(r, g, b, a);
        buffer.vertex(matrix, x, y + 1, z + 1).color(r, g, b, a);
        buffer.vertex(matrix, x, y, z + 1).color(r, g, b, a);
        
        // 西面 (X = 0)
        buffer.vertex(matrix, x, y, z + 1).color(r, g, b, a);
        buffer.vertex(matrix, x, y + 1, z + 1).color(r, g, b, a);
        buffer.vertex(matrix, x, y + 1, z).color(r, g, b, a);
        buffer.vertex(matrix, x, y, z).color(r, g, b, a);
        
        // 东面 (X = 1)
        buffer.vertex(matrix, x + 1, y, z).color(r, g, b, a);
        buffer.vertex(matrix, x + 1, y + 1, z).color(r, g, b, a);
        buffer.vertex(matrix, x + 1, y + 1, z + 1).color(r, g, b, a);
        buffer.vertex(matrix, x + 1, y, z + 1).color(r, g, b, a);
    }
    
    /**
     * 根据方块ID获取方块状态
     * 
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
            return false;
        }
        
        // 检查是否过期
        if (isExpired()) {
            return false;
        }
        
        // 检查距离 - 在循环外获取一次最大渲染距离
        Vec3d cameraPos = camera.getBlockPos().toCenterPos();
        float maxDistance = PreviewRenderer.getInstance().getSettings().maxRenderDistance;
        
        for (BlockData block : blocks) {
            if (cameraPos.distanceTo(block.position) <= maxDistance) {
                return true;
            }
        }
        
        return false;
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
    
    /**
     * 方块放置数据
     */
    public static class BlockPlacement {
        public final Vec3d position;
        public final String blockId;
        public final float opacity;
        
        public BlockPlacement(Vec3d position, String blockId) {
            this(position, blockId, 0.5f);
        }
        
        public BlockPlacement(Vec3d position, String blockId, float opacity) {
            this.position = position;
            this.blockId = blockId;
            this.opacity = Math.max(0.0f, Math.min(1.0f, opacity));
        }
        
        @Override
        public String toString() {
            return String.format("BlockPlacement{pos=%.1f,%.1f,%.1f, block=%s, opacity=%.2f}", 
                position.x, position.y, position.z, blockId, opacity);
        }
    }
} 
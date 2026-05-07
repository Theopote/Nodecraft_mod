package com.nodecraft.nodesystem.preview.elements;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.nodesystem.preview.AbstractPreviewElement;
import com.nodecraft.nodesystem.preview.PreviewOptions;
import com.nodecraft.nodesystem.preview.PreviewRenderer;
import com.nodecraft.nodesystem.util.Coordinate; // 确保这是你的整数坐标类
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix4f; // 确保使用 JOML 矩阵
import org.joml.Vector3f; // 确保使用 JOML 向量

import java.util.ArrayList;
import java.util.List;

/**
 * 方块线框高亮预览元素
 * 用于在世界中显示单个或多个方块的线框高亮效果。
 */
public class BlockHighlightElement extends AbstractPreviewElement {
    private volatile List<Coordinate> blockPositions = new ArrayList<>(); // 存储方块的整数坐标
    private Vector3f color = new Vector3f(1.0f, 0.8f, 0.0f); // 默认橙黄色 (RGBA)
    private Vector3f fillColor = new Vector3f(1.0f, 0.8f, 0.0f); // 填充颜色（可独立配置）
    private float lineWidth = 4.0f; // 线宽 (增加线宽确保可见)
    private float minOpacity = 0.0f; // 最小透明度值，默认为0允许完全按照设置的透明度显示
    private boolean enablePulse = false; // 是否启用脉冲动画（仅在选项明确开启时启用）
    private boolean showOutline = true; // 是否显示方块边框
    private boolean showFill = false; // 是否显示方块表面填充
    private float pulsePhase = 0.0f; // 脉冲动画的当前阶段 (0.0 - 1.0)
    private static final float OUTLINE_EXPAND = 0.004f; // 轻微外扩，避免与填充面共面导致边框不可见

    // 预计算的方块顶点偏移量，用于构建线框
    private static final Vec3d[] CUBE_VERTICES = {
            new Vec3d(0, 0, 0), new Vec3d(1, 0, 0), new Vec3d(1, 1, 0), new Vec3d(0, 1, 0),
            new Vec3d(0, 0, 1), new Vec3d(1, 0, 1), new Vec3d(1, 1, 1), new Vec3d(0, 1, 1)
    };
    // 12 条边的索引对
    private static final int[][] CUBE_EDGES = {
            {0, 1}, {1, 2}, {2, 3}, {3, 0}, // Bottom face
            {4, 5}, {5, 6}, {6, 7}, {7, 4}, // Top face
            {0, 4}, {1, 5}, {2, 6}, {3, 7}  // Vertical edges
    };

    /**
     * 构造一个新的方块高亮预览元素
     * @param id 预览元素的唯一ID
     * @param ownerNodeId 拥有此预览的节点ID
     * @param data 要高亮的方块位置数据 (Coordinate 或 List<Coordinate>)
     * @param options 预览选项
     */
    public BlockHighlightElement(String id, String ownerNodeId, Object data, PreviewOptions options) {
        super(id, ownerNodeId, data, options);
        this.renderPriority = 10; // 中等优先级

        // 从选项中读取设置
        if (options.color != null) {
            this.color = new Vector3f(options.color.x(), options.color.y(), options.color.z()); // 使用 JOML Vector3f 的访问器方法
        }
        if (options.lineWidth != null) {
            this.lineWidth = options.lineWidth;
        }
        if (options.tintColor != null) {
            this.fillColor = new Vector3f(options.tintColor.x(), options.tintColor.y(), options.tintColor.z());
        }
        if (options.pulseAnimation != null) {
            this.enablePulse = options.pulseAnimation;
        }
        if (options.opacity != null) {
            this.opacity = options.opacity;
        }
        if (options.minOpacity != null) {
            this.minOpacity = options.minOpacity;
        }
        if (options.showFill != null) {
            this.showFill = options.showFill;
        }
        if (options.showOutline != null) {
            this.showOutline = options.showOutline;
        }

        // 处理输入数据
        processData(data);
    }

    /**
     * 处理输入数据，解析为 Coordinate 列表。
     * @param data 要高亮的方块位置数据 (Coordinate 或 List<Coordinate>)
     */
    @Override
    protected void processData(Object data) {
        List<Coordinate> nextBlockPositions = new ArrayList<>();

        if (data instanceof Coordinate) {
            nextBlockPositions.add((Coordinate) data);
        } else if (data instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Coordinate) {
                    nextBlockPositions.add((Coordinate) item);
                }
            }
        }
        // 不处理 Vec3d 或其他类型，此元素仅用于方块高亮

        blockPositions = nextBlockPositions;
    }

    /**
     * 渲染方块线框。
     * @param matrices 矩阵栈
     * @param camera 玩家相机
     * @param partialTicks 渲染时间差
     * @param globalOpacity 全局不透明度设置
     */
    @Override
    public void render(MatrixStack matrices, Camera camera, float partialTicks, float globalOpacity) {
        List<Coordinate> blockPositionsSnapshot = blockPositions;
        if (blockPositionsSnapshot.isEmpty()) {
            return;
        }

        // 移除频繁的调试信息以提高性能

        // 更新脉冲动画阶段
        if (enablePulse) {
            pulsePhase = (pulsePhase + partialTicks * 0.1f) % (float)(Math.PI * 2); // 增加动画速度
        }

        // 计算最终透明度和亮度
        float finalOpacity = opacity * globalOpacity;
        float pulseFactor = 1.0f;

        if (enablePulse) {
            // 脉冲效果：从 0.6 到 1.0 的强度波动
            pulseFactor = (float) (0.6 + 0.4 * Math.sin(pulsePhase));
        }

        // 应用最小透明度限制
        finalOpacity = Math.max(finalOpacity, minOpacity);

        Vec3d cameraPos = camera.getCameraPos();

        // 准备渲染系统状态

        // 直接使用Minecraft原生的方块边框渲染方法
        MinecraftClient client = MinecraftClient.getInstance();
        WorldRenderer worldRenderer = client.worldRenderer;
        World world = client.world;
        
        if (world == null || worldRenderer == null) {
            NodeCraft.LOGGER.warn("world 或 worldRenderer 为 null");
            return;
        }

        VertexConsumerProvider vertexConsumerProvider = PreviewRenderer.getInstance().getActiveVertexConsumers();
        VertexConsumerProvider.Immediate immediateConsumers = null;
        boolean shouldFlushImmediately = false;
        if (vertexConsumerProvider == null) {
            immediateConsumers = client.getBufferBuilders().getEntityVertexConsumers();
            vertexConsumerProvider = immediateConsumers;
            shouldFlushImmediately = true;
        }

        float maxRenderDistance = PreviewRenderer.getInstance().getSettings().maxRenderDistance;

        // 遍历所有要高亮的方块位置
        for (Coordinate pos : blockPositionsSnapshot) {
            // 检查是否在渲染距离内
            double distance = cameraPos.distanceTo(new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
            if (distance > maxRenderDistance) {
                continue;
            }

            BlockPos blockPos = new BlockPos(pos.getX(), pos.getY(), pos.getZ());
            BlockState blockState = world.getBlockState(blockPos);
            
            // 如果方块是空气，跳过
            if (blockState.isAir()) {
                continue;
            }

            // 使用简化的方块边框渲染
            try {
                if (showFill) {
                    VertexConsumer fillVertexConsumer = vertexConsumerProvider.getBuffer(RenderLayers.debugFilledBox());
                    try {
                        renderSimpleBlockFill(matrices, cameraPos, blockPos, finalOpacity, pulseFactor, fillVertexConsumer);
                    } catch (IllegalStateException fillException) {
                        if (fillException.getMessage() != null && fillException.getMessage().contains("Not building")) {
                            NodeCraft.LOGGER.debug("Block highlight fill skipped this frame for preview {} due to pipeline state: {}", getId(), fillException.getMessage());
                        } else {
                            throw fillException;
                        }
                    }
                }
                if (showOutline) {
                    VertexConsumer lineVertexConsumer = vertexConsumerProvider.getBuffer(RenderLayers.LINES);
                    renderSimpleBlockOutline(matrices, cameraPos, blockPos, finalOpacity, pulseFactor, lineVertexConsumer);
                }
            } catch (IllegalStateException e) {
                if (e.getMessage() != null && e.getMessage().contains("Not building")) {
                    NodeCraft.LOGGER.debug("Block highlight outline skipped this frame for preview {} due to pipeline state: {}", getId(), e.getMessage());
                    continue;
                }
                throw e;
            }
        }

        if (shouldFlushImmediately && immediateConsumers != null) {
            immediateConsumers.draw();
        }

        // 恢复渲染系统状态
    }

    /**
     * 渲染方块轮廓 - 使用简化的渲染方法
     */
    private void renderSimpleBlockOutline(MatrixStack matrices, Vec3d cameraPos, BlockPos blockPos, float alpha, float pulseFactor, VertexConsumer vertexConsumer) {
        // 计算相对位置
        double renderX = blockPos.getX() - cameraPos.x;
        double renderY = blockPos.getY() - cameraPos.y;
        double renderZ = blockPos.getZ() - cameraPos.z;
        
        matrices.push();
        matrices.translate(renderX, renderY, renderZ);
        
        // 使用更简单、更可靠的渲染状态设置
        // 1.21.11 下线宽/剔除状态由渲染管线管理
        
        // 边框颜色使用主色（由 SelectionState 决定）
        float r = color.x();
        float g = color.y();
        float b = color.z();
        float a = alpha * pulseFactor; // 直接使用计算的透明度，不再强制最小值
        
        // 使用统一的线框缓冲提交
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        
        // 双层轮廓：外层深色 + 内层高亮，参考 chronoblocks 的高对比可视化效果
        renderOutlineEdges(vertexConsumer, matrix, 2.0f * OUTLINE_EXPAND, 0.02f, 0.02f, 0.02f, Math.min(1.0f, a * 0.95f));
        renderOutlineEdges(vertexConsumer, matrix, OUTLINE_EXPAND, r, g, b, a);
        
        matrices.pop();
        
        // 恢复渲染状态
        // 1.21.11 下线宽/剔除状态由渲染管线管理
    }

    private Vec3d expandOutlineVertex(Vec3d vertex, float expand) {
        double ex = vertex.x == 0.0 ? -expand : expand;
        double ey = vertex.y == 0.0 ? -expand : expand;
        double ez = vertex.z == 0.0 ? -expand : expand;
        return new Vec3d(vertex.x + ex, vertex.y + ey, vertex.z + ez);
    }

    private void renderOutlineEdges(VertexConsumer vertexConsumer, Matrix4f matrix, float expand, float r, float g, float b, float a) {
        for (int[] edge : CUBE_EDGES) {
            Vec3d v1 = expandOutlineVertex(CUBE_VERTICES[edge[0]], expand);
            Vec3d v2 = expandOutlineVertex(CUBE_VERTICES[edge[1]], expand);

            vertexConsumer.vertex(matrix, (float) v1.x, (float) v1.y, (float) v1.z)
                .color(r, g, b, a)
                .normal(0.0f, 1.0f, 0.0f)
                .lineWidth(lineWidth);
            vertexConsumer.vertex(matrix, (float) v2.x, (float) v2.y, (float) v2.z)
                .color(r, g, b, a)
                .normal(0.0f, 1.0f, 0.0f)
                .lineWidth(lineWidth);
        }
    }

    /**
     * 渲染方块半透明填充面
     */
    private void renderSimpleBlockFill(MatrixStack matrices, Vec3d cameraPos, BlockPos blockPos, float alpha, float pulseFactor, VertexConsumer vertexConsumer) {
        double renderX = blockPos.getX() - cameraPos.x;
        double renderY = blockPos.getY() - cameraPos.y;
        double renderZ = blockPos.getZ() - cameraPos.z;

        matrices.push();
        matrices.translate(renderX, renderY, renderZ);

        // 填充颜色使用独立 tintColor（由样式菜单配置）
        float r = fillColor.x();
        float g = fillColor.y();
        float b = fillColor.z();
        float a = Math.max(0.08f, alpha * 0.28f * pulseFactor);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        renderCubeFaces(vertexConsumer, matrix, 0.0f, 0.0f, 0.0f, r, g, b, a);

        matrices.pop();
    }

    /**
     * 渲染立方体的6个面
     */
    private void renderCubeFaces(VertexConsumer vertexConsumer, Matrix4f matrix, float x, float y, float z, float r, float g, float b, float a) {
        vertexConsumer.vertex(matrix, x, y, z).color(r, g, b, a);
        vertexConsumer.vertex(matrix, x + 1, y, z).color(r, g, b, a);
        vertexConsumer.vertex(matrix, x + 1, y, z + 1).color(r, g, b, a);
        vertexConsumer.vertex(matrix, x, y, z + 1).color(r, g, b, a);

        vertexConsumer.vertex(matrix, x, y + 1, z + 1).color(r, g, b, a);
        vertexConsumer.vertex(matrix, x + 1, y + 1, z + 1).color(r, g, b, a);
        vertexConsumer.vertex(matrix, x + 1, y + 1, z).color(r, g, b, a);
        vertexConsumer.vertex(matrix, x, y + 1, z).color(r, g, b, a);

        vertexConsumer.vertex(matrix, x, y, z).color(r, g, b, a);
        vertexConsumer.vertex(matrix, x, y + 1, z).color(r, g, b, a);
        vertexConsumer.vertex(matrix, x + 1, y + 1, z).color(r, g, b, a);
        vertexConsumer.vertex(matrix, x + 1, y, z).color(r, g, b, a);

        vertexConsumer.vertex(matrix, x + 1, y, z + 1).color(r, g, b, a);
        vertexConsumer.vertex(matrix, x + 1, y + 1, z + 1).color(r, g, b, a);
        vertexConsumer.vertex(matrix, x, y + 1, z + 1).color(r, g, b, a);
        vertexConsumer.vertex(matrix, x, y, z + 1).color(r, g, b, a);

        vertexConsumer.vertex(matrix, x, y, z + 1).color(r, g, b, a);
        vertexConsumer.vertex(matrix, x, y + 1, z + 1).color(r, g, b, a);
        vertexConsumer.vertex(matrix, x, y + 1, z).color(r, g, b, a);
        vertexConsumer.vertex(matrix, x, y, z).color(r, g, b, a);

        vertexConsumer.vertex(matrix, x + 1, y, z).color(r, g, b, a);
        vertexConsumer.vertex(matrix, x + 1, y + 1, z).color(r, g, b, a);
        vertexConsumer.vertex(matrix, x + 1, y + 1, z + 1).color(r, g, b, a);
        vertexConsumer.vertex(matrix, x + 1, y, z + 1).color(r, g, b, a);
    }

    /**
     * 判断此预览元素是否应该被渲染。
     * @param camera 玩家相机
     * @return 如果至少有一个方块在渲染距离内且未过期，则返回 true。
     */
    @Override
    public boolean shouldRender(Camera camera) {
        if (blockPositions.isEmpty()) {
            return false;
        }
        
        if (isExpired()) {
            return false;
        }

        float maxRenderDistance = PreviewRenderer.getInstance().getSettings().maxRenderDistance;
        Vec3d cameraPos = camera.getCameraPos();

        // 检查是否有任何方块在渲染距离内
        for (Coordinate pos : blockPositions) {
            double distance = cameraPos.distanceTo(new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
            if (distance <= maxRenderDistance) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void cleanup() {
        // 清理资源，如方块位置列表
        blockPositions.clear();
    }

    public Vector3f getColor() {
        return new Vector3f(color);
    }

    public void setColor(Vector3f color) {
        // 确保颜色值在 0-1 范围内
        this.color = new Vector3f(
                Math.max(0.0f, Math.min(1.0f, color.x())),
                Math.max(0.0f, Math.min(1.0f, color.y())),
                Math.max(0.0f, Math.min(1.0f, color.z()))
        );
    }
}

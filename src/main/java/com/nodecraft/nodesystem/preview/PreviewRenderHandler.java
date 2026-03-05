package com.nodecraft.nodesystem.preview;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;

/**
 * 预览渲染事件处理器
 * 负责将预览渲染器集成到 Minecraft 的渲染管线中
 */
public class PreviewRenderHandler {
    
    private static boolean initialized = false;
    
    /**
     * 初始化预览渲染处理器
     * 注册到 Fabric 的世界渲染事件
     */
    public static void initialize() {
        if (initialized) {
            return;
        }
        
        // 注册世界渲染事件 - 使用 BEFORE_DEBUG_RENDER 以确保方块轮廓正确显示
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register((context) -> {
            try {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.world == null || client.player == null) {
                    return;
                }
                
                Camera camera = client.gameRenderer.getCamera();
                MatrixStack matrices = context.matrices();
                VertexConsumerProvider consumers = context.consumers();
                float tickDelta = client.getRenderTickCounter().getTickProgress(true);
                
                // 调用预览渲染器
                PreviewRenderer.getInstance().setActiveVertexConsumers(consumers);
                PreviewRenderer.getInstance().renderAll(matrices, camera, tickDelta);
                PreviewRenderer.getInstance().setActiveVertexConsumers(null);
                
            } catch (Exception e) {
                System.err.println("Error rendering previews: " + e.getMessage());
                e.printStackTrace();
            }
        });
        
        initialized = true;
        // Preview Render Handler initialized
    }
} 
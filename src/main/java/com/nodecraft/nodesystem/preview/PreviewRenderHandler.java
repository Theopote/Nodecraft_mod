package com.nodecraft.nodesystem.preview;

import com.nodecraft.core.NodeCraft;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 预览渲染事件处理器
 * 负责将预览渲染器集成到 Minecraft 的渲染管线中
 */
public class PreviewRenderHandler {
    
    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    
    /**
     * 初始化预览渲染处理器
     * 注册到 Fabric 的世界渲染事件
     */
    public static void initialize() {
        if (!initialized.compareAndSet(false, true)) {
            return;
        }
        
        // 注册世界渲染事件 - END_MAIN 在 1.21.x 下对线段路径预览更稳定
        WorldRenderEvents.END_MAIN.register((context) -> {
            try {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.world == null || client.player == null) {
                    return;
                }
                
                Camera camera = client.gameRenderer.getCamera();
                MatrixStack matrices = context.matrices();
                float tickDelta = client.getRenderTickCounter().getTickProgress(true);
                
                // 调用预览渲染器
                PreviewRenderer.getInstance().setActiveVertexConsumers(context.consumers());
                PreviewRenderer.getInstance().renderAll(matrices, camera, tickDelta);
                PreviewRenderer.getInstance().setActiveVertexConsumers(null);
                
            } catch (Exception e) {
                NodeCraft.LOGGER.error("Error rendering previews", e);
            }
        });
        
        // Preview Render Handler initialized
    }
} 

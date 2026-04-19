package com.nodecraft.mixin;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.minecraft.client.MinecraftClientController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 游戏状态Mixin
 * 监听游戏状态变化，管理NodeCraft模式的激活/停用
 * 确保：
 * 1. 打开NodeCraft界面时自动隐藏十字星并释放鼠标
 * 2. 关闭NodeCraft界面时自动恢复十字星
 * 3. 离开游戏世界时强制清理NodeCraft状态
 */
@Mixin(MinecraftClient.class)
public class GameStateMixin {
    
    /**
     * 监听屏幕切换事件
     * 当切换到/从NodeCraft界面时管理十字星显示状态
     * 当切换到主菜单等界面时清理NodeCraft状态
     */
    @Inject(method = "setScreen", at = @At("HEAD"))
    private void onSetScreen(Screen screen, CallbackInfo ci) {
        try {
            MinecraftClient client = (MinecraftClient) (Object) this;
            Screen currentScreen = client.currentScreen;
            MinecraftClientController controller = MinecraftClientController.getInstance();
            
            // 检查是否从NodeCraft界面切换到其他界面
            if (currentScreen instanceof com.nodecraft.gui.screens.NodecraftScreen) {
                if (!(screen instanceof com.nodecraft.gui.screens.NodecraftScreen)) {
                    // 从NodeCraft界面切换到其他界面，停用NodeCraft模式
                    controller.deactivateNodeCraftMode();
                    NodeCraft.LOGGER.info("从NodeCraft界面切换到其他界面，已停用NodeCraft模式");
                }
            }
            
            // 检查是否切换到NodeCraft界面
            if (screen instanceof com.nodecraft.gui.screens.NodecraftScreen) {
                if (!(currentScreen instanceof com.nodecraft.gui.screens.NodecraftScreen)) {
                    // 切换到NodeCraft界面，激活NodeCraft模式
                    controller.activateNodeCraftMode();
                    NodeCraft.LOGGER.info("切换到NodeCraft界面，已激活NodeCraft模式");
                }
            }
            
            // 检查是否切换到了表示离开游戏世界的屏幕
            if (screen instanceof TitleScreen || 
                screen instanceof DisconnectedScreen ||
                screen instanceof ConnectScreen) {
                
                NodeCraft.LOGGER.info("检测到游戏状态切换到: {}, 清理NodeCraft状态", 
                    screen.getClass().getSimpleName());
                
                // 强制停用NodeCraft模式
                controller.forceCleanup();
                
                // 关闭NodeCraft屏幕
                if (currentScreen instanceof com.nodecraft.gui.screens.NodecraftScreen nodecraftScreen) {
                    MinecraftClient.getInstance().execute(() -> {
                        try {
                            nodecraftScreen.requestClose();
                            NodeCraft.LOGGER.info("NodeCraft屏幕已在状态切换时关闭");
                        } catch (Exception e) {
                            NodeCraft.LOGGER.error("在状态切换时关闭NodeCraft屏幕出错", e);
                        }
                    });
                }
            }
        } catch (Exception e) {
            NodeCraft.LOGGER.error("处理屏幕切换时出错", e);
        }
    }
    
    /**
     * 监听断开连接事件
     * 这是处理退出存档的主要方法，包括单人游戏和多人游戏
     */
    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screen/Screen;ZZ)V", at = @At("HEAD"))
    private void onDisconnect(Screen disconnectionScreen, boolean transferring, boolean joining, CallbackInfo ci) {
        try {
            NodeCraft.LOGGER.info("检测到断开连接事件，强制清理NodeCraft状态");
            
            // 强制清理NodeCraft模式
            MinecraftClientController.getInstance().forceCleanup();
            
            // 强制关闭NodeCraft屏幕
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.currentScreen instanceof com.nodecraft.gui.screens.NodecraftScreen nodecraftScreen) {
                MinecraftClient.getInstance().execute(() -> {
                    try {
                        nodecraftScreen.requestClose();
                        NodeCraft.LOGGER.info("NodeCraft屏幕已在断开连接时强制关闭");
                    } catch (Exception e) {
                        NodeCraft.LOGGER.error("在断开连接时关闭NodeCraft屏幕出错", e);
                    }
                });
            }
        } catch (Exception e) {
            NodeCraft.LOGGER.error("处理断开连接时出错", e);
        }
    }
} 

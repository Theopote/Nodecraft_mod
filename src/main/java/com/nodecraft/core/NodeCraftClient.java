package com.nodecraft.core;

import com.nodecraft.core.debug.SystemInfo;
import com.nodecraft.gui.editor.impl.ImGuiNodeEditor;
import com.nodecraft.gui.editor.NodeEditorFactory;
import com.nodecraft.gui.editor.integration.ImGuiRenderer;
import com.nodecraft.gui.window.WindowManager;
import com.nodecraft.gui.screens.NodecraftScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class NodeCraftClient implements ClientModInitializer {
    
    private static KeyBinding openNodeEditorKey;
    
    @Override
    public void onInitializeClient() {
        NodeCraft.LOGGER.info("初始化NodeCraft客户端...");
        
        // 记录系统信息，帮助诊断问题
        SystemInfo.logSystemInfo();
        
        // 尽早加载编辑器实现
        NodeEditorFactory.loadEditors();
        
        // 初始化ImGui (如果可用)
        initializeImGui();
        
        // 初始化GUI系统 (现在主要是确保单例初始化)
        initializeGUI();
        
        // 初始化渲染系统
        initializeRendering();
        
        // 注册按键绑定
        registerKeyBindings();
        
        // 注册客户端命令
        registerClientCommands();
        
        NodeCraft.LOGGER.info("NodeCraft客户端初始化完成!");
    }
    
    private void initializeImGui() {
        NodeCraft.LOGGER.info("开始初始化ImGui...");
        
        try {
            // 首先检查ImGui库是否可用
            if (!NodeEditorFactory.isImGuiSupported()) {
                NodeCraft.LOGGER.info("ImGui支持不可用，跳过ImGui初始化");
                return;
            }
            
            // 初始化ImGui渲染器
            ImGuiRenderer renderer = ImGuiRenderer.getInstance();
            
            // 注册客户端生命周期事件
            ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
                NodeCraft.LOGGER.info("关闭ImGui渲染器...");
                renderer.shutdown();
            });
            
            // 注册客户端初始化完成事件
            ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
                NodeCraft.LOGGER.info("Minecraft客户端启动完成，确保ImGui初始化...");
                try {
                    if (!renderer.isInitialized()) {
                        renderer.init();
                        NodeCraft.LOGGER.info("ImGuiRenderer 初始化完成 (via CLIENT_STARTED)");
                    }
                    // 在 ImGuiRenderer 初始化后，初始化 ImGuiNodeEditor
                    ImGuiNodeEditor.getInstance().init();
                    NodeCraft.LOGGER.info("ImGuiNodeEditor 初始化完成 (via CLIENT_STARTED)");
                } catch (Exception e) {
                    NodeCraft.LOGGER.error("客户端启动后ImGui或ImGuiNodeEditor初始化失败", e);
                }
            });
            
            // 注册ImGui渲染回调 - 应该在每一帧渲染前处理输入
            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                try {
                    // 无条件更新平台后端以保持状态同步
                    if (renderer.isInitialized()) {
                        renderer.updatePlatformBackend();
                    }
                    
                    // 更新窗口管理器状态
                    WindowManager windowManager = WindowManager.getInstance();
                    if (windowManager.isInitialized()) {
                        windowManager.updateWindowState();
                    }
                    
                    // 更新Minecraft客户端控制器状态
                    com.nodecraft.minecraft.client.MinecraftClientController.getInstance().updateHudMessage();

                } catch (Exception e) {
                    NodeCraft.LOGGER.error("ImGui后端更新或输入处理失败", e);
                }
            });
            
            NodeCraft.LOGGER.info("ImGui注册初始化完成");
        } catch (Exception e) {
            NodeCraft.LOGGER.error("ImGui初始化过程中发生错误", e);
        }
    }
    
    private void initializeGUI() {
        // 初始化GUI系统
        NodeCraft.LOGGER.info("初始化节点编辑器GUI (移除了ImGuiNodeEditor.init)");
        
        try {
            if (NodeEditorFactory.isImGuiSupported()) {
                // ImGuiNodeEditor 的初始化移至 CLIENT_STARTED 回调
                // ImGuiNodeEditor.getInstance().init(); 
                NodeCraft.LOGGER.info("ImGui节点编辑器将在客户端启动后初始化");
            } else {
                NodeCraft.LOGGER.info("将使用原生节点编辑器");
            }
        } catch (Exception e) {
            // 捕获其他可能的 GUI 初始化错误
            NodeCraft.LOGGER.error("非ImGui节点编辑器GUI初始化失败", e);
        }
    }
    
    private void initializeRendering() {
        // 初始化渲染系统
        NodeCraft.LOGGER.info("初始化渲染系统...");
        
        // 初始化预览渲染处理器
        com.nodecraft.nodesystem.preview.PreviewRenderHandler.initialize();
    }
    
    private void registerKeyBindings() {
        // 注册打开节点编辑器的快捷键
        openNodeEditorKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.nodecraft.open_editor",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_N,
            KeyBinding.Category.create(Identifier.of("nodecraft", "general"))
        ));
        
        // 添加按键监听
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // 检查快捷键是否按下
            while (openNodeEditorKey.wasPressed()) {
                // 获取当前屏幕
                net.minecraft.client.gui.screen.Screen currentScreen = client.currentScreen;

                // 如果当前是 NodecraftScreen，则关闭它
                if (currentScreen instanceof NodecraftScreen) {
                    NodeCraft.LOGGER.info("关闭节点编辑器屏幕");
                    client.setScreen(null);
                } else {
                    // 否则，打开 NodecraftScreen
                    NodeCraft.LOGGER.info("打开节点编辑器屏幕 (不暂停游戏模式)");
                    client.setScreen(new NodecraftScreen()); // NodecraftScreen 内部会使用工厂创建编辑器
                    NodeCraft.LOGGER.info("提示：按住鼠标中键可以控制游戏视角");
                }
            }
        });
        
        NodeCraft.LOGGER.info("注册NodeCraft快捷键...");
    }
    
    private void registerClientCommands() {
        NodeCraft.LOGGER.info("注册客户端命令...");

        // 注册客户端命令
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            // 注册高亮测试命令
            com.nodecraft.debug.HighlightTestCommand.register(dispatcher);
            NodeCraft.LOGGER.info("已注册高亮测试命令");
        });

        NodeCraft.LOGGER.info("客户端命令注册完成");
    }
} 
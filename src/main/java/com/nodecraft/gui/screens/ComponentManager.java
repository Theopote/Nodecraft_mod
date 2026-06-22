package com.nodecraft.gui.screens;

import java.util.ArrayList;
import java.util.List;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.components.panel.CanvasComponent;
import com.nodecraft.gui.components.EditorComponent;
import com.nodecraft.gui.components.panel.LeftPanelComponent;
import com.nodecraft.gui.components.panel.NodeLibraryComponent;
import com.nodecraft.gui.components.PropertyPanelComponent;
import com.nodecraft.gui.editor.base.INodeEditor;
import com.nodecraft.gui.layout.LayoutConstraints;
import com.nodecraft.gui.layout.LayoutManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

/**
 * 管理 Nodecraft 编辑器 UI 组件的创建和注册。
 */
public class ComponentManager {

    private final LayoutManager layoutManager;
    private final INodeEditor currentEditor;
    private Screen parentScreen;

    private LeftPanelComponent leftPanelComponent;
    private CanvasComponent canvasComponent;
    private PropertyPanelComponent propertyPanelComponent;

    private final List<EditorComponent> editorComponents = new ArrayList<>();

    public ComponentManager(
            LayoutManager layoutManager,
            INodeEditor currentEditor) {
        this.layoutManager = layoutManager;
        this.currentEditor = currentEditor;
        
        if (MinecraftClient.getInstance() != null) {
            this.parentScreen = MinecraftClient.getInstance().currentScreen;
        }
    }

    /**
     * 设置父屏幕
     * @param screen 父屏幕实例
     */
    public void setParentScreen(Screen screen) {
        this.parentScreen = screen;
    }
    
    /**
     * 获取父屏幕
     * @return 父屏幕实例
     */
    public Screen getParentScreen() {
        return parentScreen;
    }

    /**
     * 初始化所有 UI 组件。
     */
    public void initComponents() {
        if (!editorComponents.isEmpty()) {
            NodeCraft.LOGGER.warn("尝试重复初始化组件");
            return;
        }
        
        try {
            this.propertyPanelComponent = new PropertyPanelComponent();
            registerComponent(this.propertyPanelComponent, LayoutConstraints.createPropertyPanel(
                EditorConstants.PROPERTY_PANEL_WIDTH_RATIO, EditorConstants.MIN_PANEL_WIDTH));

            CallbackHandler localHandler = new CallbackHandler(/* this.statusBarComponent */);

            this.leftPanelComponent = new LeftPanelComponent(localHandler);
            registerComponent(this.leftPanelComponent, LayoutConstraints.createNodePanel(
                EditorConstants.NODE_PANEL_WIDTH_RATIO, EditorConstants.MIN_PANEL_WIDTH));
            
            if (this.currentEditor == null) {
                throw new IllegalStateException("currentEditor 在 ComponentManager 中未初始化");
            }
            this.canvasComponent = new CanvasComponent(currentEditor, localHandler);
            registerComponent(this.canvasComponent, LayoutConstraints.createCanvas(
                EditorConstants.CANVAS_WIDTH_RATIO, EditorConstants.MIN_CANVAS_WIDTH));
            
            NodeCraft.LOGGER.info("UI 组件初始化完成 (由 ComponentManager 管理)");
        } catch (Exception e) {
            NodeCraft.LOGGER.error("UI 组件初始化失败: {}", e.getMessage(), e);
            // 可以考虑向上抛出异常或返回失败状态
        }
    }
    
    /**
     * 向布局管理器注册组件。
     * @param component 组件实例
     * @param constraints 布局约束
     */
    private void registerComponent(Object component, LayoutConstraints constraints) {
        if (component instanceof EditorComponent editorComponent) {
            if (this.layoutManager == null) {
                throw new IllegalStateException("layoutManager 在 ComponentManager 中未初始化");
            }
            layoutManager.registerComponent(editorComponent, constraints);
            editorComponents.add(editorComponent);
        } else {
            NodeCraft.LOGGER.warn("组件未实现 EditorComponent 接口: {}", component.getClass().getName());
        }
    }

    /**
     * 清理所有组件引用。
     */
    public void cleanup() {
        leftPanelComponent = null;
        canvasComponent = null;
        propertyPanelComponent = null;
        editorComponents.clear();
        NodeCraft.LOGGER.info("UI 组件已清理 (由 ComponentManager 管理)");
    }

    // --- Getters for components ---

    public LeftPanelComponent getLeftPanelComponent() {
        return leftPanelComponent;
    }

    public NodeLibraryComponent getNodeLibraryComponent() {
        return leftPanelComponent != null ? leftPanelComponent.getNodeLibraryComponent() : null;
    }

    public CanvasComponent getCanvasComponent() {
        return canvasComponent;
    }

    public PropertyPanelComponent getPropertyPanelComponent() {
        return propertyPanelComponent;
    }

    public List<EditorComponent> getEditorComponents() {
        return editorComponents;
    }

    /**
     * 广播事件到所有注册的组件
     * @param eventType 事件类型
     * @param eventData 事件数据
     */
    public void broadcastEvent(String eventType, Object eventData) {
        NodeCraft.LOGGER.debug("广播事件到组件: {}, 数据: {}", eventType, eventData);
        int handledCount = 0;
        
        for (EditorComponent component : editorComponents) {
            try {
                boolean handled = component.handleEvent(eventType, eventData);
                if (handled) {
                    handledCount++;
                    NodeCraft.LOGGER.debug("事件 {} 被组件 {} 处理", eventType, component.getComponentId());
                }
            } catch (Exception e) {
                NodeCraft.LOGGER.error("组件处理事件时出错: {}", component.getComponentId(), e);
            }
        }
        
        NodeCraft.LOGGER.debug("事件 {} 广播完成，被 {} 个组件处理", eventType, handledCount);
    }
} 
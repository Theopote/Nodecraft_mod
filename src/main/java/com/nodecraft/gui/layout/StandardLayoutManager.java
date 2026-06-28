package com.nodecraft.gui.layout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.components.EditorComponent;
import com.nodecraft.gui.layout.LayoutConstraints.RegionType;
import com.nodecraft.gui.screens.EditorConstants;
import com.nodecraft.gui.layout.LayoutConfig;

import imgui.ImGui; // 需要 ImGui 获取样式

/**
 * 标准布局管理器
 * 实现了标准的编辑器布局算法：顶部工具栏、底部状态栏、左侧节点面板、右侧属性面板、中间画布
 */
public class StandardLayoutManager implements LayoutManager {

    // 组件及其布局约束
    private final Map<String, EditorComponent> componentsById = new HashMap<>();
    private final Map<String, LayoutConstraints> constraintsById = new HashMap<>();
    
    // 根据区域类型分组的组件列表
    private final Map<RegionType, List<String>> regionComponents = new HashMap<>();

    // 存储计算后的布局结果
    private final Map<String, LayoutDimensions> computedLayouts = new HashMap<>();
    
    // 默认组件间距 - 使用常量
    private float defaultSpacing = EditorConstants.DEFAULT_ITEM_SPACING; // 使用常量
    
    /**
     * 构造函数
     */
    public StandardLayoutManager() {
        // 初始化区域组件列表
        for (RegionType type : RegionType.values()) {
            regionComponents.put(type, new ArrayList<>());
        }
    }
    
    @Override
    public void registerComponent(EditorComponent component, LayoutConstraints layoutConstraints) {
        String componentId = component.getComponentId(); // 假设 EditorComponent 有 getId()
        componentsById.put(componentId, component);
        constraintsById.put(componentId, layoutConstraints);
        
        // 添加到适当的区域组中
        RegionType regionType = layoutConstraints.getRegionType();
        regionComponents.get(regionType).add(componentId);
        
        NodeCraft.LOGGER.debug("注册组件: id={}, 区域={}", componentId, regionType);
    }

    @Override
    public boolean removeComponent(String componentId) {
        if (!componentsById.containsKey(componentId)) {
            return false;
        }
        
        // 从组件映射中移除
        componentsById.remove(componentId);
        computedLayouts.remove(componentId); // 清理计算结果
        
        // 从区域组件列表中移除
        LayoutConstraints removedConstraints = constraintsById.remove(componentId);
        if (removedConstraints != null) {
            regionComponents.get(removedConstraints.getRegionType()).remove(componentId);
        }
        
        return true;
    }

    /**
     * 计算所有注册组件的布局。
     * @param availableWidth 可用内容区宽度
     * @param availableHeight 可用内容区高度
     * @param startX 内容区起始 X 坐标
     * @param startY 内容区起始 Y 坐标
     * @param config 布局配置 (比例、最小尺寸)
     * @param showMenuBar 菜单栏是否显示 (影响起始 Y)
     */
    @Override
    public void calculateLayout(float availableWidth, float availableHeight, float startX, float startY, LayoutConfig config, boolean showMenuBar) {
        computedLayouts.clear(); // 清除旧结果

        // 获取样式间距，计算需要用到
        // 注意：理想情况下 LayoutManager 不应直接依赖 ImGui，但这简化了间距处理
        // 更好的方式是将间距作为参数传入或配置
        // 现在默认间距已使用常量，但计算中仍然直接获取 ImGui 样式值，这可能导致不一致
        // 考虑是否应该在 calculateLayout 中也使用 defaultSpacing 或 EditorConstants.DEFAULT_ITEM_SPACING
        // final float itemSpacingX = ImGui.getStyle().getItemSpacingX(); // 保持现状，或者改为常量？
        // final float itemSpacingY = ImGui.getStyle().getItemSpacingY(); // 保持现状，或者改为常量？
        final float itemSpacingX = this.defaultSpacing; // 改为使用内部的 defaultSpacing (它现在来自常量)
        final float itemSpacingY = this.defaultSpacing;

        // --- 第1步：计算工具栏和状态栏的高度 ---
        // float toolbarHeight = computeFixedRegionHeight(RegionType.TOOLBAR, EditorConstants.TOOLBAR_HEIGHT);
        float toolbarHeight = 0; // 工具栏高度强制为 0
        float statusBarHeight = 0; // 强制为 0，以防万一

        // --- 第2步：计算中间内容区域的位置和尺寸 ---
        float middleStartY = startY + toolbarHeight + (toolbarHeight > 0 ? itemSpacingY : 0);
        float middleAvailableHeight = availableHeight - toolbarHeight - (toolbarHeight > 0 ? itemSpacingY : 0);
        middleAvailableHeight = Math.max(0, middleAvailableHeight); // 确保不为负
        
        // --- 第3步：计算中间三列的宽度 --- (核心逻辑迁移)
        float middleAvailableWidth = availableWidth;
        float nodePanelW = middleAvailableWidth * config.nodePanelRatio();
        float canvasW = middleAvailableWidth * config.canvasRatio();
        float propertyPanelW = middleAvailableWidth * config.propertyPanelRatio();

        // 判断面板是否需要渲染（比例大于0）
        boolean renderNodePanel = config.nodePanelRatio() > 0.001f;
        boolean renderPropertyPanel = config.propertyPanelRatio() > 0.001f;

        // 如果比例为0，则将宽度也设为0
        if (!renderNodePanel) {
            nodePanelW = 0;
        }
        if (!renderPropertyPanel) {
            propertyPanelW = 0;
        }

        // 仅对需要渲染的面板应用最小宽度限制
        if (renderNodePanel) {
            nodePanelW = Math.max(nodePanelW, config.minNodePanelWidth());
        }
        canvasW = Math.max(canvasW, config.minCanvasWidth());
        if (renderPropertyPanel) {
            propertyPanelW = Math.max(propertyPanelW, config.minPropertyPanelWidth());
        }

        // 宽度调整逻辑
        float totalMiddleWidth = nodePanelW + canvasW + propertyPanelW;
        // 计算分隔符数量（根据显示的面板数量）
        int separatorCount = (renderNodePanel ? 1 : 0) + (renderPropertyPanel ? 1 : 0);
        totalMiddleWidth += itemSpacingX * separatorCount; // 只考虑显示的面板之间的间距

        if (totalMiddleWidth > middleAvailableWidth) {
            float overflow = totalMiddleWidth - middleAvailableWidth;
            float canvasReduction = Math.min(overflow, canvasW - config.minCanvasWidth());
            canvasW -= canvasReduction;
            overflow -= canvasReduction;

            if (overflow > 0) {
                float totalMinimizableWidth = 0;
                if (renderNodePanel) {
                    totalMinimizableWidth += (nodePanelW - config.minNodePanelWidth());
                }
                if (renderPropertyPanel) {
                    totalMinimizableWidth += (propertyPanelW - config.minPropertyPanelWidth());
                }
                
                if (totalMinimizableWidth > 0) {
                    float leftRatio = renderNodePanel ? 
                        (nodePanelW - config.minNodePanelWidth()) / totalMinimizableWidth : 0;
                    float rightRatio = 1.0f - leftRatio;
                    
                    float nodePanelReduction = renderNodePanel ? 
                        Math.min(overflow * leftRatio, nodePanelW - config.minNodePanelWidth()) : 0;
                    float propertyPanelReduction = renderPropertyPanel ? 
                        Math.min(overflow * rightRatio, propertyPanelW - config.minPropertyPanelWidth()) : 0;
                    
                    nodePanelW -= nodePanelReduction;
                    propertyPanelW -= propertyPanelReduction;
                }
            }
            
            // 重新计算画布宽度，填充剩余空间
            float separatorsWidth = itemSpacingX * separatorCount;
            canvasW = middleAvailableWidth - nodePanelW - propertyPanelW - separatorsWidth;
            canvasW = Math.max(canvasW, config.minCanvasWidth()); // 确保画布宽度尊重最小值
            
            // 确保面板宽度尊重最小值
            if (renderNodePanel) {
                nodePanelW = Math.max(nodePanelW, config.minNodePanelWidth());
            }
            if (renderPropertyPanel) {
                propertyPanelW = Math.max(propertyPanelW, config.minPropertyPanelWidth());
            }
            
            // 最终检查并调整总宽度（如有需要，可能会再次略微减少画布）
            totalMiddleWidth = nodePanelW + canvasW + propertyPanelW + separatorsWidth;
            if (totalMiddleWidth > middleAvailableWidth) {
                canvasW -= (totalMiddleWidth - middleAvailableWidth);
            }
        } else if (totalMiddleWidth < middleAvailableWidth) {
            // 增加画布宽度以填充可用空间
            canvasW += (middleAvailableWidth - totalMiddleWidth);
        }
        
        // 确保最终宽度不为负
        if (renderNodePanel) {
            nodePanelW = Math.max(0, nodePanelW);
        } else {
            nodePanelW = 0;
        }
        canvasW = Math.max(0, canvasW);
        if (renderPropertyPanel) {
            propertyPanelW = Math.max(0, propertyPanelW);
        } else {
            propertyPanelW = 0;
        }

        // --- 第4步：计算并存储各区域组件的布局 ---
        float currentX = startX;
        // 工具栏
        // calculateAndStoreLayout(RegionType.TOOLBAR, currentX, startY, availableWidth, toolbarHeight);
        
        // 节点面板（仅当比例大于0时渲染）
        if (renderNodePanel) {
            calculateAndStoreLayout(RegionType.NODE_PANEL, currentX, middleStartY, nodePanelW, middleAvailableHeight);
            currentX += nodePanelW + itemSpacingX;
        }
        
        // 画布
        calculateAndStoreLayout(RegionType.CANVAS, currentX, middleStartY, canvasW, middleAvailableHeight);
        
        // 属性面板（仅当比例大于0时渲染）
        if (renderPropertyPanel) {
            currentX += canvasW + itemSpacingX;
            calculateAndStoreLayout(RegionType.PROPERTY_PANEL, currentX, middleStartY, propertyPanelW, middleAvailableHeight);
        }
        
        // 状态栏 (移除状态栏的布局计算)
        // float statusBarY = middleStartY + middleAvailableHeight + (statusBarHeight > 0 ? itemSpacingY : 0);
        // calculateAndStoreLayout(RegionType.STATUS_BAR, startX, statusBarY, availableWidth, statusBarHeight);
        
        NodeCraft.LOGGER.debug("计算布局完成: W={}, H={}, StartX={}, StartY={}", availableWidth, availableHeight, startX, startY);
    }
    
    /**
     * 计算指定区域内所有组件的布局并存储。
     * （简化版：目前只支持区域内单个组件，占据整个区域）
     * 
     * @param regionType 区域类型
     * @param regionX 区域起始 X
     * @param regionY 区域起始 Y
     * @param regionWidth 区域宽度
     * @param regionHeight 区域高度
     */
    private void calculateAndStoreLayout(RegionType regionType, float regionX, float regionY, float regionWidth, float regionHeight) {
        List<String> componentsInRegion = regionComponents.get(regionType);
        if (componentsInRegion == null || componentsInRegion.isEmpty()) {
            return;
        }
        
        if (regionHeight <= 0 || regionWidth <= 0) {
            NodeCraft.LOGGER.trace("跳过零尺寸区域: {} (w={}, h={})", regionType, regionWidth, regionHeight);
            return; // Skip zero-size regions
        }

        int componentCount = componentsInRegion.size();
        float componentHeight = regionHeight / componentCount;
        for (int i = 0; i < componentCount; i++) {
            String componentId = componentsInRegion.get(i);
            float componentY = regionY + componentHeight * i;
            float height = i == componentCount - 1
                ? regionY + regionHeight - componentY
                : componentHeight;
            LayoutDimensions dims = new LayoutDimensions(regionX, componentY, regionWidth, height);
            computedLayouts.put(componentId, dims);
            NodeCraft.LOGGER.trace("计算组件布局: id={}, x={}, y={}, w={}, h={}",
                componentId, dims.x(), dims.y(), dims.width(), dims.height());
        }
    }
    
    /**
     * 计算固定区域（工具栏、状态栏）的高度。
     * @param regionType 区域类型 (TOOLBAR 或 STATUS_BAR)
     * @param defaultHeight 默认/配置的高度
     * @return 计算出的高度
     */
    private float computeFixedRegionHeight(RegionType regionType, float defaultHeight) {
        // 移除工具栏区域的处理逻辑
        if (regionType == RegionType.TOOLBAR) {
            return 0;
        }
        // 移除状态栏区域的处理逻辑
        if (regionType == RegionType.STATUS_BAR) {
            return 0;
        }

        List<String> region = regionComponents.get(regionType);
        if (region == null || region.isEmpty()) {
            return 0;
        }
        // 简化：直接使用配置的高度，忽略组件内部约束
        return defaultHeight;
    }

    // 这个方法是旧的，现在由 calculateLayout 统一处理
    /*
    @Override
    public LayoutDimensions computeLayout(float availableWidth, float availableHeight) {
        // ... old logic ...
    }
    */

    // --- Getters --- 
    
    /**
     * 获取计算后的组件布局信息。
     * @param component 需要获取布局的组件
     * @return 计算出的布局维度，如果未计算或组件未注册则返回 null。
     */
    @Override
    public LayoutDimensions getComputedLayout(EditorComponent component) {
         if (component == null) return null;
         return computedLayouts.get(component.getComponentId());
    }

    /**
     * 获取计算后的组件布局信息。
     * @param componentId 需要获取布局的组件 ID
     * @return 计算出的布局维度，如果未计算或组件未注册则返回 null。
     */
    @Override
    public LayoutDimensions getComputedLayout(String componentId) {
         return computedLayouts.get(componentId);
    }

    public void setDefaultSpacing(float spacing) {
        this.defaultSpacing = spacing;
    }

    public float getDefaultSpacing() {
        return defaultSpacing;
    }
    
    // 移除不再需要的内部方法
    /*
    private void setComponentBoundsForRegion(...) { ... }
    private float computeToolbarHeight() { ... }
    private float computeStatusBarHeight() { ... }
    private float[] computePanelWidths(float availableWidth) { ... }
    */
    
    // 移除 LayoutManager 接口中不再适合的方法 (renderComponents, save/loadLayout)
    // 这些更适合由 Screen 或专门的持久化类处理
    /*
    @Override
    public void renderComponents(EditorComponent[] components, LayoutDimensions layout, float paddingX, float paddingY) {
        // ...
    }
    @Override
    public boolean saveLayout() {
        // ...
    }
    @Override
    public boolean loadLayout() {
       // ...
    }
    */

    // 实现 LayoutManager 接口的其他方法 (如果需要)
    @Override
    public Map<String, EditorComponent> getRegisteredComponents() {
        return new HashMap<>(componentsById); // 返回副本以防外部修改
    }

    // 添加缺失的接口方法实现
    @Override
    public LayoutConstraints getComponentConstraints(String componentId) {
        return constraintsById.get(componentId);
    }

    @Override
    public boolean setComponentConstraints(String componentId, LayoutConstraints constraints) {
        if (!componentsById.containsKey(componentId)) {
            return false;
        }
        
        // 更新区域组件列表
        LayoutConstraints oldConstraints = this.constraintsById.get(componentId);
        RegionType newRegionType = constraints.getRegionType();
        
        if (oldConstraints != null && oldConstraints.getRegionType() != newRegionType) {
            // 从旧区域移除
            regionComponents.get(oldConstraints.getRegionType()).remove(componentId);
            // 添加到新区域 (如果新区域列表不存在则创建)
            regionComponents.computeIfAbsent(newRegionType, k -> new ArrayList<>()).add(componentId);
        }
        
        // 更新约束
        this.constraintsById.put(componentId, constraints);
        return true;
    }
} 

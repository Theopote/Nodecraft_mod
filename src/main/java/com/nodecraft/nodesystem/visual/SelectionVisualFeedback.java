package com.nodecraft.nodesystem.visual;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.nodesystem.preview.PreviewManager;
import com.nodecraft.nodesystem.preview.PreviewOptions;
import com.nodecraft.nodesystem.util.Coordinate;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 选择视觉反馈管理器 (重构版)
 * 基于现有的 PreviewManager 系统，提供语义化的选择反馈API
 * 
 * 这个类作为现有预览系统的语义层封装，提供：
 * 1. 统一的选择状态管理
 * 2. 标准化的视觉样式
 * 3. 简化的API接口
 * 4. 向后兼容性
 */
public class SelectionVisualFeedback {
    
    private static volatile SelectionVisualFeedback instance;
    
    // 预览ID管理 - 跟踪每个节点的预览
    private final ConcurrentMap<String, String> nodePreviewMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Coordinate> blockSelectionMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, SelectionState> blockSelectionStateMap = new ConcurrentHashMap<>();

    // 拾取高亮样式设置（可由菜单实时调整）
    private volatile boolean blockHighlightShowFill = true;
    private volatile boolean blockHighlightShowOutline = true;
    private volatile boolean blockHighlightEnablePulse = true;
    private volatile float blockHighlightLineWidth = 3.0f;
    private volatile float blockHighlightOpacityScale = 1.0f;
    private volatile float blockHighlightFillR = 1.0f;
    private volatile float blockHighlightFillG = 0.8f;
    private volatile float blockHighlightFillB = 0.0f;
    
    // 选择状态枚举
    public enum SelectionState {
        HOVERING(1.0f, 1.0f, 0.6f, 0.6f, false),    // 悬停 - 淡黄色半透明
        SELECTING(1.0f, 1.0f, 0.0f, 0.8f, true),    // 选择中 - 黄色脉冲
        SELECTED(0.0f, 1.0f, 0.0f, 0.8f, true),     // 已选中 - 绿色脉冲
        CONFIRMED(0.0f, 0.8f, 1.0f, 1.0f, false),   // 已确认 - 青色实线
        ERROR(1.0f, 0.0f, 0.0f, 0.9f, true),        // 错误 - 红色脉冲
        LOCKED(0.0f, 0.0f, 1.0f, 1.0f, false);     // 锁定 - 蓝色实线

        public final float r, g, b, opacity;
        public final boolean pulse;
        
        SelectionState(float r, float g, float b, float opacity, boolean pulse) {
            this.r = r;
            this.g = g;
            this.b = b;
            this.opacity = opacity;
            this.pulse = pulse;
        }
    }

    // 实体选择状态枚举
    public enum EntitySelectionState {
        HOVERING(1.0f, 0.8f, 0.4f, 0.6f, false),    // 悬停 - 淡橙色半透明
        SELECTING(1.0f, 0.6f, 0.0f, 0.8f, true),    // 选择中 - 橙色脉冲
        SELECTED(1.0f, 0.5f, 0.0f, 0.8f, true),     // 已选中 - 橙色脉冲
        CONFIRMED(0.8f, 0.0f, 1.0f, 1.0f, false),   // 已确认 - 紫色实线
        ERROR(1.0f, 0.0f, 0.0f, 0.9f, true),        // 错误 - 红色脉冲
        LOCKED(0.0f, 0.0f, 1.0f, 1.0f, false);      // 锁定 - 蓝色实线
        
        public final float r, g, b, opacity;
        public final boolean pulse;
        
        EntitySelectionState(float r, float g, float b, float opacity, boolean pulse) {
            this.r = r;
            this.g = g;
            this.b = b;
            this.opacity = opacity;
            this.pulse = pulse;
        }
    }
    
    // 向后兼容的枚举类型
    @Deprecated
    public enum BlockSelectionType {
        SELECTED, CONFIRMED, ERROR, PREVIEW;
        
        public SelectionState toSelectionState() {
            return switch (this) {
                case SELECTED -> SelectionState.SELECTED;
                case CONFIRMED -> SelectionState.CONFIRMED;
                case ERROR -> SelectionState.ERROR;
                case PREVIEW -> SelectionState.HOVERING;
            };
        }
    }
    
    @Deprecated
    public enum EntitySelectionType {
        SELECTED, CONFIRMED, ERROR, PREVIEW;
        
        public EntitySelectionState toEntitySelectionState() {
            return switch (this) {
                case SELECTED -> EntitySelectionState.SELECTED;
                case CONFIRMED -> EntitySelectionState.CONFIRMED;
                case ERROR -> EntitySelectionState.ERROR;
                case PREVIEW -> EntitySelectionState.HOVERING;
            };
        }
    }
    
    private SelectionVisualFeedback() {
        // 私有构造函数，单例模式
    }
    
    public static SelectionVisualFeedback getInstance() {
        if (instance == null) {
            synchronized (SelectionVisualFeedback.class) {
                if (instance == null) {
                    instance = new SelectionVisualFeedback();
                }
            }
        }
        return instance;
    }

    public boolean isBlockHighlightShowFill() {
        return blockHighlightShowFill;
    }

    public void setBlockHighlightShowFill(boolean showFill) {
        this.blockHighlightShowFill = showFill;
        refreshActiveBlockSelections();
    }

    public boolean isBlockHighlightShowOutline() {
        return blockHighlightShowOutline;
    }

    public void setBlockHighlightShowOutline(boolean showOutline) {
        this.blockHighlightShowOutline = showOutline;
        refreshActiveBlockSelections();
    }

    public boolean isBlockHighlightEnablePulse() {
        return blockHighlightEnablePulse;
    }

    public void setBlockHighlightEnablePulse(boolean enablePulse) {
        this.blockHighlightEnablePulse = enablePulse;
        refreshActiveBlockSelections();
    }

    public float getBlockHighlightLineWidth() {
        return blockHighlightLineWidth;
    }

    public void setBlockHighlightLineWidth(float lineWidth) {
        this.blockHighlightLineWidth = Math.max(0.5f, Math.min(8.0f, lineWidth));
        refreshActiveBlockSelections();
    }

    public float getBlockHighlightOpacityScale() {
        return blockHighlightOpacityScale;
    }

    public void setBlockHighlightOpacityScale(float opacityScale) {
        this.blockHighlightOpacityScale = Math.max(0.1f, Math.min(1.0f, opacityScale));
        refreshActiveBlockSelections();
    }

    public float[] getBlockHighlightFillColor() {
        return new float[] { blockHighlightFillR, blockHighlightFillG, blockHighlightFillB };
    }

    public void setBlockHighlightFillColor(float r, float g, float b) {
        this.blockHighlightFillR = Math.max(0.0f, Math.min(1.0f, r));
        this.blockHighlightFillG = Math.max(0.0f, Math.min(1.0f, g));
        this.blockHighlightFillB = Math.max(0.0f, Math.min(1.0f, b));
        refreshActiveBlockSelections();
    }

    public void resetBlockHighlightStyle() {
        this.blockHighlightShowFill = true;
        this.blockHighlightShowOutline = true;
        this.blockHighlightEnablePulse = true;
        this.blockHighlightLineWidth = 3.0f;
        this.blockHighlightOpacityScale = 1.0f;
        this.blockHighlightFillR = 1.0f;
        this.blockHighlightFillG = 0.8f;
        this.blockHighlightFillB = 0.0f;
        refreshActiveBlockSelections();
    }
    
    // ================= 新的语义化API =================
    
    /**
     * 显示方块选择反馈
     * @param nodeId 节点ID
     * @param position 方块位置
     * @param state 选择状态
     */
    public void showBlockSelection(String nodeId, Coordinate position, SelectionState state) {
        try {
            // 清除之前的预览
            clearFeedback(nodeId);

            // 创建预览选项
            PreviewOptions options = createBlockSelectionOptions(state);

            // 使用 PreviewManager 显示高亮
            String previewId = PreviewManager.highlightBlock(nodeId, position, options);

            if (previewId != null) {
                // 记录预览ID
                nodePreviewMap.put(nodeId, previewId);
                blockSelectionMap.put(nodeId, position);
                blockSelectionStateMap.put(nodeId, state);
                NodeCraft.LOGGER.debug("方块选择反馈显示成功: 节点={}, 位置={}, 状态={}, 预览ID={}",
                    nodeId, position, state, previewId);
            } else {
                NodeCraft.LOGGER.error("方块选择反馈显示失败: PreviewManager.highlightBlock 返回 null, 节点={}, 位置={}",
                    nodeId, position);
            }

        } catch (Exception e) {
            NodeCraft.LOGGER.error("显示方块选择反馈失败: 节点={}, 位置={}, 状态={}",
                nodeId, position, state, e);
        }
    }
    
    /**
     * 显示实体选择反馈
     * @param nodeId 节点ID
     * @param position 实体位置
     * @param state 选择状态
     */
    public void showEntitySelection(String nodeId, Vec3d position, EntitySelectionState state) {
        try {
            // 清除之前的预览
            clearFeedback(nodeId);
            
            // 创建预览选项
            PreviewOptions options = createEntitySelectionOptions(state);
            
            // 将Vec3d转换为Coordinate用于显示
            Coordinate coord = new Coordinate((int)Math.floor(position.x), 
                                            (int)Math.floor(position.y), 
                                            (int)Math.floor(position.z));
            
            // 使用 PreviewManager 显示高亮
            String previewId = PreviewManager.highlightBlock(nodeId, coord, options);
            
            // 记录预览ID
            nodePreviewMap.put(nodeId, previewId);
            
            NodeCraft.LOGGER.debug("显示实体选择反馈: 节点={}, 位置={}, 状态={}, 预览ID={}", 
                nodeId, position, state, previewId);
                
        } catch (Exception e) {
            NodeCraft.LOGGER.error("显示实体选择反馈失败: 节点={}, 位置={}, 状态={}", 
                nodeId, position, state, e);
        }
    }
    
    /**
     * 显示多方块选择反馈
     * @param nodeId 节点ID
     * @param positions 方块位置列表
     * @param state 选择状态
     */
    public void showMultiBlockSelection(String nodeId, List<Coordinate> positions, SelectionState state) {
        try {
            // 清除之前的预览
            clearFeedback(nodeId);
            
            // 创建预览选项
            PreviewOptions options = createBlockSelectionOptions(state);
            
            // 使用 PreviewManager 显示多方块高亮
            String previewId = PreviewManager.highlightBlocks(nodeId, positions, options);
            
            // 记录预览ID
            nodePreviewMap.put(nodeId, previewId);
            
            NodeCraft.LOGGER.debug("显示多方块选择反馈: 节点={}, 方块数量={}, 状态={}, 预览ID={}", 
                nodeId, positions.size(), state, previewId);
                
        } catch (Exception e) {
            NodeCraft.LOGGER.error("显示多方块选择反馈失败: 节点={}, 方块数量={}, 状态={}", 
                nodeId, positions.size(), state, e);
        }
    }
    

    /**
     * 清除节点的所有视觉反馈
     * @param nodeId 节点ID
     */
    public void clearFeedback(String nodeId) {
        try {
            String previewId = nodePreviewMap.remove(nodeId);
            if (previewId != null) {
                PreviewManager.hidePreview(previewId);
                NodeCraft.LOGGER.debug("清除视觉反馈: 节点={}, 预览ID={}", nodeId, previewId);
            }

            blockSelectionMap.remove(nodeId);
            blockSelectionStateMap.remove(nodeId);
            
            // 同时清除可能的临时高亮
            PreviewManager.hideNodePreviews(nodeId + "_temp");
            
        } catch (Exception e) {
            NodeCraft.LOGGER.error("清除视觉反馈失败: 节点={}", nodeId, e);
        }
    }
    
    // ================= 向后兼容的API =================
    
    /**
     * @deprecated 使用 showBlockSelection(String, Coordinate, SelectionState) 替代
     */
    @Deprecated
    public void showBlockSelection(String nodeId, Coordinate position, BlockSelectionType type) {
        showBlockSelection(nodeId, position, type.toSelectionState());
    }
    
    /**
     * @deprecated 使用 showEntitySelection(String, Vec3d, EntitySelectionState) 替代
     */
    @Deprecated
    public void showEntitySelection(String nodeId, Vec3d position, EntitySelectionType type) {
        showEntitySelection(nodeId, position, type.toEntitySelectionState());
    }
    
    /**
     * @deprecated 使用 showMultiBlockSelection(String, List<Coordinate>, SelectionState) 替代
     */
    @Deprecated
    public void showMultiBlockSelection(String nodeId, List<Coordinate> blockList, BlockSelectionType type) {
        showMultiBlockSelection(nodeId, blockList, type.toSelectionState());
    }
    
    /**
     * @deprecated 使用 clearFeedback(String) 替代
     */
    @Deprecated
    public void clearBlockSelection(String nodeId) {
        clearFeedback(nodeId);
    }
    
    /**
     * @deprecated 使用 clearFeedback(String) 替代
     */
    @Deprecated
    public void clearEntitySelection(String nodeId) {
        clearFeedback(nodeId);
    }
    
    // ================= 私有辅助方法 =================
    
    /**
     * 创建方块选择的预览选项
     */
    private PreviewOptions createBlockSelectionOptions(SelectionState state) {
        PreviewOptions options = new PreviewOptions()
            .setColor(state.r, state.g, state.b)
            .setTintColor(blockHighlightFillR, blockHighlightFillG, blockHighlightFillB)
            .setOpacity(state.opacity * blockHighlightOpacityScale)
            .setLineWidth(blockHighlightLineWidth)
            .wireframeMode();

        options.setShowFill(blockHighlightShowFill);
        options.setShowOutline(blockHighlightShowOutline);
        
        if (state.pulse && blockHighlightEnablePulse) {
            options.enablePulse();
        }
        
        return options;
    }

    private void refreshActiveBlockSelections() {
        if (blockSelectionMap.isEmpty()) {
            return;
        }

        for (String nodeId : blockSelectionMap.keySet()) {
            Coordinate position = blockSelectionMap.get(nodeId);
            SelectionState state = blockSelectionStateMap.get(nodeId);
            if (position == null || state == null) {
                continue;
            }

            try {
                clearFeedback(nodeId);
                PreviewOptions options = createBlockSelectionOptions(state);
                String previewId = PreviewManager.highlightBlock(nodeId, position, options);
                if (previewId != null) {
                    nodePreviewMap.put(nodeId, previewId);
                    blockSelectionMap.put(nodeId, position);
                    blockSelectionStateMap.put(nodeId, state);
                }
            } catch (Exception e) {
                NodeCraft.LOGGER.warn("刷新方块高亮样式失败: 节点={}, 错误={}", nodeId, e.getMessage());
            }
        }
    }
    
    /**
     * 创建实体选择的预览选项
     */
    private PreviewOptions createEntitySelectionOptions(EntitySelectionState state) {
        PreviewOptions options = new PreviewOptions()
            .setColor(state.r, state.g, state.b)
            .setOpacity(state.opacity)
            .setLineWidth(2.5f)
            .wireframeMode();
        
        if (state.pulse) {
            options.enablePulse();
        }
        
        return options;
    }
} 
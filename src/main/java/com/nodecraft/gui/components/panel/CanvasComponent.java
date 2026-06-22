package com.nodecraft.gui.components.panel;

import com.nodecraft.gui.components.EditorComponent;

import java.util.Map;
import java.util.Objects; // 导入 Objects
import java.util.UUID;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.preset.GraphPresetApplier;
import com.nodecraft.gui.preset.GraphPresetCatalog;
import com.nodecraft.gui.preset.GraphPresetRules;
import com.nodecraft.gui.editor.base.INodeEditor;
import com.nodecraft.gui.editor.impl.ImGuiNodeEditor;
import com.nodecraft.gui.editor.impl.NodePosition;

import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.ImVec2;
import imgui.flag.ImGuiPopupFlags;

/**
 * NodeCraft编辑器画布组件
 * 负责渲染中央画布区域，处理画布拖动和缩放
 */
public class CanvasComponent implements EditorComponent {
    
    // 内部常量类，用于样式和配置
    private static class CanvasConstants {
        static final float DEFAULT_GRID_SIZE = 20.0f;
        static final float MIN_ZOOM = 0.2f;
        static final float MAX_ZOOM = 3.0f;
        static final float ZOOM_STEP = 0.1f;
        static final float[] DEFAULT_BACKGROUND_COLOR = new float[]{0.1f, 0.1f, 0.15f, 1.0f};
        static final int GRID_COLOR = ImGui.getColorU32(0.4f, 0.4f, 0.4f, 1.0f); // 稍暗的灰色
        static final int GRID_MINOR_ALPHA = (int)(0.2f * 255);
        static final int GRID_MAJOR_ALPHA = (int)(0.4f * 255);
        static final float GRID_THICKNESS = 1.0f;
        static final int MAJOR_LINE_INTERVAL = 5;
        static final String DRAG_DROP_PAYLOAD_TYPE = "DND_NODE_FROM_LIBRARY";
        static final String PRESET_DRAG_DROP_PAYLOAD_TYPE = GraphPresetCatalog.PRESET_DRAG_PAYLOAD;
    }
    
    // 画布状态
    private float canvasZoom = 1.0f;
    private float canvasOffsetX = 0;
    private float canvasOffsetY = 0;
    private boolean isDraggingCanvas = false;
    private final ImVec2 lastMousePos = new ImVec2();
    private float gridSize = CanvasConstants.DEFAULT_GRID_SIZE; // 使用常量作为默认值
    private boolean showGrid = true;
    private boolean visible = true;
    private final String componentId = "canvas";
    
    // 画布实际渲染尺寸，用于准确的视图适应计算
    private float lastRenderedCanvasWidth = 0;
    private float lastRenderedCanvasHeight = 0;
    
    // 拖放状态跟踪 - 用于防止拖放时触发框选
    private static boolean isNodeDragDropActive = false;
    private static boolean isPresetDragDropActive = false;
    
    // 样式设置 (现在使用常量初始化，但保留字段以便未来可能的动态修改)
    private float[] canvasBackgroundColor = CanvasConstants.DEFAULT_BACKGROUND_COLOR.clone(); 
    // private float[] gridLineColor - 不再需要，直接在 drawGrid 使用常量
    // private float[] gridMajorLineColor - 不再需要
    
    // 编辑器引用
    private final INodeEditor nodeEditor;
    
    // 右键上下文菜单相关
    private float contextMenuPosX = 0;
    private float contextMenuPosY = 0;
    
    // 节点显示模式
    public enum NodeDisplayMode {
        FULL,        // 完整显示（图标+标题+详情）
        COMPACT,     // 紧凑显示（图标+标题）
        ICON_ONLY,   // 仅图标
        TEXT_ONLY    // 仅文本
    }
    
    // 当前节点显示模式
    private NodeDisplayMode nodeDisplayMode = NodeDisplayMode.FULL;
    
    // 是否显示节点预览
    private boolean showNodePreviews = true;
    
    /**
     * 节点添加回调接口
     */
    public interface NodeDropCallback {
        void onNodeDropped(String nodeId, float x, float y);
    }
    
    private final NodeDropCallback dropCallback;
    
    /**
     * 构造函数
     * @param nodeEditor 节点编辑器
     * @param dropCallback 节点拖放回调
     */
    public CanvasComponent(INodeEditor nodeEditor, NodeDropCallback dropCallback) {
        // 强制要求 nodeEditor 非空
        this.nodeEditor = Objects.requireNonNull(nodeEditor, "节点编辑器 (nodeEditor) 不能为空"); 
        this.dropCallback = dropCallback;
    }
    
    /**
     * 实现EditorComponent接口的render方法
     */
    @Override
    public void render(float x, float y, float width, float height, float paddingX, float paddingY) {
        render(y, x - paddingX, width, height, paddingX);
    }
    
    /**
     * 实现EditorComponent接口的init方法
     */
    @Override
    public void init() {
        // 组件初始化时，重置画布视图到默认状态
        resetCanvasView();
    }
    
    /**
     * 实现EditorComponent接口的cleanup方法
     */
    @Override
    public void cleanup() {
        // 画布不需要特殊清理操作
    }
    
    /**
     * 实现EditorComponent接口的setVisible方法
     */
    @Override
    public void setVisible(boolean visible) {
        this.visible = visible;
    }
    
    /**
     * 实现EditorComponent接口的isVisible方法
     */
    @Override
    public boolean isVisible() {
        return visible;
    }
    
    /**
     * 实现EditorComponent接口的getComponentId方法
     */
    @Override
    public String getComponentId() {
        return componentId;
    }
    
    /**
     * 获取节点编辑器实例
     * @return 节点编辑器实例
     */
    public INodeEditor getNodeEditor() {
        return nodeEditor;
    }
    
    /**
     * 实现EditorComponent接口的handleEvent方法
     */
    @Override
    public boolean handleEvent(String eventType, Object data) {
        // 默认实现不处理任何事件
        return false;
    }
    
    /**
     * 设置画布背景颜色
     * @param r 红色 (0-1)
     * @param g 绿色 (0-1)
     * @param b 蓝色 (0-1)
     * @param a 透明度 (0-1)
     */
    public void setBackgroundColor(float r, float g, float b, float a) {
        this.canvasBackgroundColor[0] = r;
        this.canvasBackgroundColor[1] = g;
        this.canvasBackgroundColor[2] = b;
        this.canvasBackgroundColor[3] = a;
    }

    public float[] getBackgroundColor() {
        return this.canvasBackgroundColor;
    }

    public float getBackgroundAlpha() {
        return this.canvasBackgroundColor[3];
    }

    public void setBackgroundAlpha(float alpha) {
        this.canvasBackgroundColor[3] = Math.max(0.0f, Math.min(1.0f, alpha));
    }
    
    /**
     * 画布组件渲染完成后的安全清理 (简化版)
     * 仅处理已知问题：关闭弹出窗口和重置光标
     */
    private void ensureCleanState() {
        try {
            // 关闭所有可能打开的弹出窗口
            while (ImGui.isPopupOpen("", ImGuiPopupFlags.AnyPopupId)) {
                ImGui.closeCurrentPopup();
                NodeCraft.LOGGER.debug("CanvasComponent清理: 关闭了一个未关闭的弹出窗口");
            }
            
            // 重置光标位置（如果 DrawList 存在）
            if (ImGui.getWindowDrawList() != null) {
                 // 简化：不再检查无效位置，直接尝试重置
                ImGui.setCursorPos(0, 0); 
                NodeCraft.LOGGER.debug("CanvasComponent清理: 重置光标位置到 (0, 0)");
            }
            
            // 记录清理完成
            // NodeCraft.LOGGER.debug("CanvasComponent状态清理完成"); // 可以移除或保留
        } catch (Exception e) {
            // 使用更通用的错误消息
            NodeCraft.LOGGER.error("CanvasComponent状态清理时出错: {}", e.getMessage());
        }
    }
    
    /**
     * 渲染画布
     * @param contentStartY 内容起始Y坐标
     * @param canvasStartX 画布起始X坐标(已经计算好的确切位置)
     * @param canvasWidth 画布宽度
     * @param contentHeight 内容高度
     * @param windowPaddingX 窗口水平内边距
     */
    public void render(float contentStartY, float canvasStartX, float canvasWidth, 
                     float contentHeight, float windowPaddingX) {
        if (!visible) {
            return;
        }
        
        try {
            if (NodeCraft.LOGGER.isDebugEnabled()) {
                NodeCraft.LOGGER.debug("渲染画布组件");
            }
            
            // 安全检查 - 确保所有输入参数有效
            if (Float.isNaN(canvasStartX) || Float.isInfinite(canvasStartX)) {
                NodeCraft.LOGGER.warn("无效的画布起始X坐标: {}, 重置为 0", canvasStartX);
                canvasStartX = 0f;
            }
            if (Float.isNaN(contentStartY) || Float.isInfinite(contentStartY)) {
                 NodeCraft.LOGGER.warn("无效的内容起始Y坐标: {}, 重置为 0", contentStartY);
                contentStartY = 0f;
            }
            float safeCanvasWidth = Math.max(50, canvasWidth); // 保证最小宽度
            float safeContentHeight = Math.max(50, contentHeight); // 保证最小高度
            
            if (NodeCraft.LOGGER.isDebugEnabled()) {
                // 记录原始与修正后的参数
                NodeCraft.LOGGER.debug("画布参数 - 原始: startY={}, startX={}, width={}, height={}", 
                                  contentStartY, canvasStartX, canvasWidth, contentHeight);
                NodeCraft.LOGGER.debug("画布参数 - 修正: width={}, height={}", 
                                  safeCanvasWidth, safeContentHeight);
            }
            
            // 直接执行渲染和交互逻辑
            // 渲染画布内容 (调用 nodeEditor.renderImGui())
            
            // 处理画布交互（拖拽，缩放等）
            ImGuiIO io = ImGui.getIO();
            // 获取当前(LayoutRenderer创建的)子窗口的位置和大小
            ImVec2 canvasScreenPos = ImGui.getWindowPos(); 
            ImVec2 canvasSize = ImGui.getWindowSize();
            
            // 记录画布的实际渲染尺寸，供 fitToContent 和其他需要尺寸的方法使用
            this.lastRenderedCanvasWidth = canvasSize.x;
            this.lastRenderedCanvasHeight = canvasSize.y;
            
            if (NodeCraft.LOGGER.isDebugEnabled()) {
                NodeCraft.LOGGER.debug("画布位置：x={}, y={}, 宽度={}, 高度={}", 
                                  canvasScreenPos.x, canvasScreenPos.y, canvasSize.x, canvasSize.y);
                NodeCraft.LOGGER.debug("记录画布渲染尺寸: width={}, height={}", lastRenderedCanvasWidth, lastRenderedCanvasHeight);
            }
            
            // 如果启用网格，先绘制网格（放在节点渲染前，这样网格就会在底层）
            if (showGrid) {
                drawGrid(canvasScreenPos, canvasSize, gridSize, canvasOffsetX, canvasOffsetY);
            }
            
            // 在网格之上渲染节点和连接线
            renderEditorContent();
            
            // 处理画布交互操作
            handleCanvasInteraction(canvasScreenPos, canvasSize, io);
            
            // 处理拖放目标，允许从节点库拖放节点到画布
            handleDragDropTarget(canvasScreenPos, canvasSize);
            
            // 处理右键上下文菜单
            handleContextMenu(canvasScreenPos);
            
        } catch (Exception e) {
            // 使用通用异常处理方法
            handleException(e, "画布渲染");
        }
    }
    
    /**
     * 处理画布交互（拖动和缩放）
     */
    private void handleCanvasInteraction(ImVec2 canvasScreenPos, ImVec2 canvasSize, ImGuiIO io) {
        // 检查鼠标是否在画布窗口内
        boolean isHovered = ImGui.isWindowHovered();
        boolean isWantCaptureMouse = io.getWantCaptureMouse();
        
        // 每100帧记录一次状态信息，避免日志过多
        if (Math.random() < 0.01) {
            NodeCraft.LOGGER.debug("画布交互状态: 悬停={}, 鼠标捕获={}, 拖动={}, 位置=({}, {})", 
                isHovered, isWantCaptureMouse, isDraggingCanvas, io.getMousePosX(), io.getMousePosY());
        }
        
        // 1. 检测画布拖动开始 (中键点击)
        if (isHovered && ImGui.isMouseClicked(2)) { 
            isDraggingCanvas = true;
            lastMousePos.set(io.getMousePosX(), io.getMousePosY()); 
            NodeCraft.LOGGER.debug("画布拖动开始: 位置=({}, {})", io.getMousePosX(), io.getMousePosY());
        }
        
        // 2. 处理画布缩放 - 只要鼠标悬停在画布上就处理缩放
        // 为了修复"画布不能使用鼠标滚轮缩放"的问题，不再检查isWantCaptureMouse
        if (isHovered) {
            // 在画布上使用滚轮缩放
            handleCanvasZooming(canvasScreenPos, canvasSize, io);
            
            // 处理左键点击和其他交互 - 这里仍然保持原有的限制条件
            if (!isWantCaptureMouse) {
                handleCanvasClicks(canvasScreenPos, canvasSize, io);
            }
        }
        
        // 3 & 4: 处理画布持续拖动和结束拖动
        // 注意：一旦开始拖动，即使鼠标移出画布或被ImGui捕获，也应继续处理拖动
        handleCanvasDraggingLogic(canvasScreenPos, canvasSize, io);
    }
    
    /**
     * 处理画布缩放
     */
    private void handleCanvasZooming(ImVec2 canvasScreenPos, ImVec2 canvasSize, ImGuiIO io) {
        float mouseWheel = io.getMouseWheel();
        if (mouseWheel != 0) {
            float prevZoom = canvasZoom;
            // 使用常量进行缩放限制和步长
            canvasZoom = Math.max(CanvasConstants.MIN_ZOOM, 
                             Math.min(CanvasConstants.MAX_ZOOM, canvasZoom + mouseWheel * CanvasConstants.ZOOM_STEP));
            
            // 以鼠标为中心缩放
            float mouseXInCanvas = (io.getMousePosX() - canvasScreenPos.x - canvasOffsetX) / prevZoom;
            float mouseYInCanvas = (io.getMousePosY() - canvasScreenPos.y - canvasOffsetY) / prevZoom;
            
            canvasOffsetX += mouseXInCanvas * (prevZoom - canvasZoom);
            canvasOffsetY += mouseYInCanvas * (prevZoom - canvasZoom);

            NodeCraft.LOGGER.debug("画布缩放: {}->{}，滚轮={}，位置=({}, {})", 
                prevZoom, canvasZoom, mouseWheel, io.getMousePosX(), io.getMousePosY());
        }
    }

    /**
     * 处理画布拖动（持续和结束）
     */
    private void handleCanvasDraggingLogic(ImVec2 canvasScreenPos, ImVec2 canvasSize, ImGuiIO io) {
        // 处理画布拖动 - 不需要检查悬停状态，只需要确保鼠标按下 (改为中键)
        if (isDraggingCanvas && ImGui.isMouseDown(2)) { // 1表示ImGui中的右键按钮索引 -> 改为 2 (中键)
            float deltaX = io.getMousePosX() - lastMousePos.x;
            float deltaY = io.getMousePosY() - lastMousePos.y;
            
            // 只在有实际移动时更新偏移（避免浮点数累积误差）
            if (Math.abs(deltaX) > 0.5f || Math.abs(deltaY) > 0.5f) {
                canvasOffsetX += deltaX;
                canvasOffsetY += deltaY;
                
                // 每10帧记录一次拖动信息
                if (Math.random() < 0.1) {
                    NodeCraft.LOGGER.debug("画布拖动中: 偏移=({}, {}), 增量=({}, {})", 
                        canvasOffsetX, canvasOffsetY, deltaX, deltaY);
                }
            }
            
            lastMousePos.set(io.getMousePosX(), io.getMousePosY());
        }
        
        // 结束画布拖动 (改为中键)
        if (isDraggingCanvas && !ImGui.isMouseDown(2)) { // 1表示ImGui中的右键按钮索引 -> 改为 2 (中键)
            isDraggingCanvas = false;
            NodeCraft.LOGGER.debug("结束画布拖动: 最终偏移=({}, {})", canvasOffsetX, canvasOffsetY);
        }
    }

    /**
     * 处理画布上的鼠标点击
     */
    private void handleCanvasClicks(ImVec2 canvasScreenPos, ImVec2 canvasSize, ImGuiIO io) {
        // 添加左键点击处理 - 用于节点选择或其他操作
        if (ImGui.isMouseClicked(0)) {
            NodeCraft.LOGGER.debug("画布左键点击: 位置=({}, {})", io.getMousePosX(), io.getMousePosY());
            // 这里可以添加节点选择或其他交互逻辑
        }
        
        // 添加双击检测
        if (ImGui.isMouseDoubleClicked(0)) {
            NodeCraft.LOGGER.debug("画布双击: 位置=({}, {})", io.getMousePosX(), io.getMousePosY());
            // 检查是否点击在空白处 (通过委托给nodeEditor检查)
            boolean isOverNode = false;
            if (nodeEditor instanceof ImGuiNodeEditor editor) {
                isOverNode = editor.isMouseOverAnyNode(io.getMousePosX(), io.getMousePosY(), canvasScreenPos);
            }
            
            if (!isOverNode) {
                // 计算世界坐标
                float worldX = (io.getMousePosX() - canvasScreenPos.x - canvasOffsetX) / canvasZoom;
                float worldY = (io.getMousePosY() - canvasScreenPos.y - canvasOffsetY) / canvasZoom;
                openNodeSearchPopup(worldX, worldY);
            }
        }
        
        // 可以扩展处理其他按钮的点击
    }

    /**
     * 打开节点搜索弹窗
     * @param x 世界坐标X
     * @param y 世界坐标Y
     */
    private void openNodeSearchPopup(float x, float y) {
        NodeCraft.LOGGER.info("请求打开节点搜索栏，位置: ({}, {})", x, y);
        // 发送事件，由NodeLibraryComponent处理
        if (nodeEditor instanceof ImGuiNodeEditor) {
            ((ImGuiNodeEditor)nodeEditor).requestNodeSearch(x, y);
        }
    }

    /**
     * 渲染编辑器内容
     */
    private void renderEditorContent() {
        try {
            // 构造函数已确保 nodeEditor 不为 null
            if (nodeEditor instanceof ImGuiNodeEditor imguiEditor) {
                // 同步画布状态到ImGuiNodeEditor
                imguiEditor.setCanvasView(canvasZoom, canvasOffsetX, canvasOffsetY);
            }
            
            nodeEditor.renderImGui();
        } catch (Exception e) {
            // 使用通用异常处理方法
            handleException(e, "编辑器内容渲染");
            // NodeCraft.LOGGER.error("编辑器ImGui渲染失败", e);
        }
    }
    
    /**
     * 处理拖放目标
     */
    private void handleDragDropTarget(ImVec2 canvasScreenPos, ImVec2 canvasSize) {
        Object nodePayload = ImGui.getDragDropPayload(CanvasConstants.DRAG_DROP_PAYLOAD_TYPE);
        Object presetPayload = ImGui.getDragDropPayload(CanvasConstants.PRESET_DRAG_DROP_PAYLOAD_TYPE);
        if (nodePayload == null && presetPayload == null) {
            isNodeDragDropActive = false;
            isPresetDragDropActive = false;
            return;
        }

        ImGui.setCursorScreenPos(canvasScreenPos.x, canvasScreenPos.y);
        ImGui.invisibleButton("##canvasDropTarget", canvasSize.x, canvasSize.y);

        boolean dragDropTargetBegin = false;
        try {
            dragDropTargetBegin = ImGui.beginDragDropTarget();
            if (!dragDropTargetBegin) {
                isNodeDragDropActive = false;
                isPresetDragDropActive = false;
                return;
            }

            isNodeDragDropActive = nodePayload != null;
            isPresetDragDropActive = presetPayload != null;

            if (nodePayload != null) {
                Object payload = ImGui.acceptDragDropPayload(CanvasConstants.DRAG_DROP_PAYLOAD_TYPE);
                if (payload != null) {
                    handleNodeDropPayload(payload, canvasScreenPos);
                    isNodeDragDropActive = false;
                }
            }

            if (presetPayload != null) {
                Object payload = ImGui.acceptDragDropPayload(CanvasConstants.PRESET_DRAG_DROP_PAYLOAD_TYPE);
                if (payload != null) {
                    handlePresetDropPayload(payload, canvasScreenPos);
                    isPresetDragDropActive = false;
                }
            }
        } catch (Exception e) {
            handleException(e, "处理拖放目标");
        } finally {
            if (dragDropTargetBegin) {
                try {
                    ImGui.endDragDropTarget();
                } catch (Exception ignored) {
                    // ignore
                }
            }
            if (nodePayload == null) {
                isNodeDragDropActive = false;
            }
            if (presetPayload == null) {
                isPresetDragDropActive = false;
            }
        }
    }

    private void handleNodeDropPayload(Object payload, ImVec2 canvasScreenPos) {
        String nodeId = parseDragDropText(payload);
        if (nodeId == null) {
            NodeCraft.LOGGER.error("拖放数据类型未知: {}", payload.getClass().getName());
            return;
        }

        ImGuiIO io = ImGui.getIO();
        float dropX = (io.getMousePosX() - canvasScreenPos.x - canvasOffsetX) / canvasZoom;
        float dropY = (io.getMousePosY() - canvasScreenPos.y - canvasOffsetY) / canvasZoom;

        NodeCraft.LOGGER.info("节点已拖放到画布: {} 在位置 ({}, {})", nodeId, dropX, dropY);
        if (dropCallback != null) {
            dropCallback.onNodeDropped(nodeId, dropX, dropY);
        } else {
            NodeCraft.LOGGER.warn("拖放回调未设置，无法处理节点拖放");
        }
    }

    private void handlePresetDropPayload(Object payload, ImVec2 canvasScreenPos) {
        String presetId = parseDragDropText(payload);
        if (presetId == null) {
            NodeCraft.LOGGER.error("预设拖放数据无效");
            return;
        }

        GraphPresetRules.GraphPresetDefinition preset =
                GraphPresetCatalog.getInstance().getPresetDefinition(presetId);
        if (preset == null) {
            NodeCraft.LOGGER.warn("未找到预设: {}", presetId);
            return;
        }

        ImGuiIO io = ImGui.getIO();
        float dropX = (io.getMousePosX() - canvasScreenPos.x - canvasOffsetX) / canvasZoom;
        float dropY = (io.getMousePosY() - canvasScreenPos.y - canvasOffsetY) / canvasZoom;

        GraphPresetApplier.ApplyResult result = GraphPresetApplier.apply(preset, dropX, dropY);
        if (!result.success()) {
            NodeCraft.LOGGER.warn("Failed to apply preset {} at drop position: {}", presetId, result.message());
        } else {
            NodeCraft.LOGGER.info("Applied preset {} at ({}, {})", presetId, dropX, dropY);
        }
    }

    private static String parseDragDropText(Object payload) {
        if (payload instanceof byte[] payloadBytes) {
            return new String(payloadBytes, java.nio.charset.StandardCharsets.UTF_8);
        }
        if (payload instanceof String text) {
            return text;
        }
        return null;
    }
    
    /**
     * 绘制网格 (改进版，考虑缩放和偏移)
     */
    private void drawGrid(ImVec2 canvasScreenPos, ImVec2 canvasSize, float worldGridSize, float offsetX, float offsetY) {
        if (worldGridSize <= 0 || canvasZoom <= 0) return;

        // 网格与画布背景共享透明度，避免背景设为0时仍残留网格层。
        float gridAlphaScale = Math.max(0.0f, Math.min(1.0f, this.canvasBackgroundColor[3]));
        if (gridAlphaScale <= 0.001f) return;
        
        ImDrawList drawList = ImGui.getWindowDrawList();
        // 使用 CanvasConstants 中的常量
        // final int gridColor = CanvasConstants.GRID_COLOR; // GRID_COLOR 已包含颜色值
        // final int gridMinorAlpha = CanvasConstants.GRID_MINOR_ALPHA;
        // final int gridMajorAlpha = CanvasConstants.GRID_MAJOR_ALPHA;
        // final float gridThickness = CanvasConstants.GRID_THICKNESS;
        // final int majorLineInterval = CanvasConstants.MAJOR_LINE_INTERVAL;
        
        // 计算屏幕上可见的世界坐标范围
        final float viewLeft = -offsetX / canvasZoom;
        final float viewTop = -offsetY / canvasZoom;
        final float viewRight = (canvasSize.x - offsetX) / canvasZoom;
        final float viewBottom = (canvasSize.y - offsetY) / canvasZoom;
        
        // --- 绘制垂直线 ---
        float startWorldX = (float)Math.floor(viewLeft / worldGridSize) * worldGridSize;
        for (float worldX = startWorldX; worldX < viewRight; worldX += worldGridSize) {
            // 将世界坐标转换为屏幕坐标
            float screenX = canvasScreenPos.x + worldX * canvasZoom + offsetX;
            
            // 优化：如果线条完全在屏幕外，则跳过
            if (screenX < canvasScreenPos.x || screenX > canvasScreenPos.x + canvasSize.x) {
                continue;
            }
            
            // 判断是主要线还是次要线
            boolean isMajorLine = Math.abs(Math.round(worldX / worldGridSize) % CanvasConstants.MAJOR_LINE_INTERVAL) == 0;
            int baseAlpha = isMajorLine ? CanvasConstants.GRID_MAJOR_ALPHA : CanvasConstants.GRID_MINOR_ALPHA;
            int alpha = Math.max(0, Math.min(255, (int)(baseAlpha * gridAlphaScale)));
            int finalColor = (alpha << 24) | (CanvasConstants.GRID_COLOR & 0x00FFFFFF); // 应用透明度
            
            drawList.addLine(screenX, canvasScreenPos.y, screenX, canvasScreenPos.y + canvasSize.y, finalColor, CanvasConstants.GRID_THICKNESS);
        }
        
        // --- 绘制水平线 ---
        float startWorldY = (float)Math.floor(viewTop / worldGridSize) * worldGridSize;
        for (float worldY = startWorldY; worldY < viewBottom; worldY += worldGridSize) {
            // 将世界坐标转换为屏幕坐标
            float screenY = canvasScreenPos.y + worldY * canvasZoom + offsetY;
            
            // 优化：如果线条完全在屏幕外，则跳过
            if (screenY < canvasScreenPos.y || screenY > canvasScreenPos.y + canvasSize.y) {
                continue;
            }
            
            // 判断是主要线还是次要线
            boolean isMajorLine = Math.abs(Math.round(worldY / worldGridSize) % CanvasConstants.MAJOR_LINE_INTERVAL) == 0;
            int baseAlpha = isMajorLine ? CanvasConstants.GRID_MAJOR_ALPHA : CanvasConstants.GRID_MINOR_ALPHA;
            int alpha = Math.max(0, Math.min(255, (int)(baseAlpha * gridAlphaScale)));
            int finalColor = (alpha << 24) | (CanvasConstants.GRID_COLOR & 0x00FFFFFF); // 应用透明度
            
            drawList.addLine(canvasScreenPos.x, screenY, canvasScreenPos.x + canvasSize.x, screenY, finalColor, CanvasConstants.GRID_THICKNESS);
        }
    }
    
    /**
     * 设置是否显示网格
     */
    public void setShowGrid(boolean showGrid) {
        this.showGrid = showGrid;
        
        // 将网格显示状态传递给编辑器实例
        if (nodeEditor instanceof ImGuiNodeEditor editor) {
            editor.setShowGrid(showGrid);
        }
        
        NodeCraft.LOGGER.info("网格显示状态已设置为: {}", showGrid ? "显示" : "隐藏");
    }
    
    /**
     * 获取是否显示网格
     * @return 是否显示网格
     */
    public boolean isShowGrid() {
        return showGrid;
    }

    /**
     * 重置画布视图到默认状态 (1.0f 缩放, (0,0) 偏移)。
     * 这是 "重置视图" 菜单项应该调用的功能。
     */
    public void resetToDefault() {
        this.canvasZoom = 1.0f;
        this.canvasOffsetX = 0;
        this.canvasOffsetY = 0;
        NodeCraft.LOGGER.info("画布视图已重置到默认状态 (1.0x, (0,0)偏移)");
        
        // 传递给编辑器，确保编辑器也同步这个状态
        if (nodeEditor instanceof ImGuiNodeEditor) {
            ((ImGuiNodeEditor)nodeEditor).setCanvasView(canvasZoom, canvasOffsetX, canvasOffsetY);
        }
    }
    
    /**
     * 重置画布视图 - 恢复到原始比例（1:1缩放，居中显示）
     */
    public void resetCanvasView() {
        resetToDefault();
    }
    
    /**
     * 适应画布内容 - 自动调整视图以显示所有节点。
     * 这是 "适应内容" 菜单项应该调用的功能。
     */
    public void fitToContent() {
        if (!(nodeEditor instanceof ImGuiNodeEditor)) {
            resetCanvasView(); // 如果无法适应，则回退到默认视图
            return;
        }
        
        ImGuiNodeEditor editor = (ImGuiNodeEditor) nodeEditor;
        Map<UUID, NodePosition> positions = editor.getNodePositions();
        
        // 如果没有节点，重置视图并返回
        if (positions.isEmpty()) {
            resetCanvasView(); // 如果没有节点，回退到默认视图
            NodeCraft.LOGGER.info("画布已重置（无节点）");
            return;
        }
        
        // 【重要前提】确保 ImGuiNodeEditor 在渲染时，将节点的实际渲染尺寸（ImGui.GetItemRectSize()）
        // 存储到 NodePosition 的 width 和 height 字段中。
        // 如果 ImGuiNodeEditor 没有这个机制，fitToContent 的准确性会受限。
        // 例如，在 ImGuiNodeEditor 的节点渲染循环中，渲染完每个节点后：
        // ImVec2 itemSize = ImGui.GetItemRectSize();
        // NodePosition pos = nodePositions.get(node.getId());
        // if (pos != null) { pos.width = itemSize.x; pos.height = itemSize.y; }
        
        // 计算所有节点的边界
        float minX = Float.MAX_VALUE;
        float maxX = Float.MIN_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = Float.MIN_VALUE;
        
        boolean hasValidNode = false; // 标记是否找到任何有效节点
        for (Map.Entry<UUID, NodePosition> entry : positions.entrySet()) {
            NodePosition pos = entry.getValue();
            // 依赖 NodePosition 中存储的实际渲染尺寸。
            // 如果 width 或 height 仍为 0，则使用默认估算值。
            float nodeWidth = pos.width > 0 ? pos.width : 150; 
            float nodeHeight = pos.height > 0 ? pos.height : 100;
            
            minX = Math.min(minX, pos.x);
            maxX = Math.max(maxX, pos.x + nodeWidth);
            minY = Math.min(minY, pos.y);
            maxY = Math.max(maxY, pos.y + nodeHeight);
            hasValidNode = true;
        }

        // 先计算初始内容边界
        float initialContentWidth = maxX - minX;
        float initialContentHeight = maxY - minY;
        
        // 添加适当的边距，使内容不贴边显示
        float padding = Math.max(50.0f, Math.min(initialContentWidth, initialContentHeight) * 0.1f);
        minX -= padding;
        maxX += padding;
        minY -= padding;
        maxY += padding;
        
        // 获取画布当前的实际渲染尺寸，而不是 ImGui.getContentRegionAvail()
        float viewportWidth = lastRenderedCanvasWidth;
        float viewportHeight = lastRenderedCanvasHeight;
        
        // 确保视口尺寸有效，提供备用方案
        if (viewportWidth <= 0 || viewportHeight <= 0) {
            NodeCraft.LOGGER.warn("画布渲染尺寸无效 ({}x{})，尝试获取当前 ImGui 窗口大小作为备用", viewportWidth, viewportHeight);
            viewportWidth = ImGui.getWindowWidth(); // 这是一个通用备用，可能会获取主窗口大小
            viewportHeight = ImGui.getWindowHeight();
            if (viewportWidth <= 0 || viewportHeight <= 0) {
                NodeCraft.LOGGER.warn("ImGui 窗口大小也无效，使用硬编码默认值 800x600");
                viewportWidth = 800; 
                viewportHeight = 600;
            }
        }
        
        // 计算最终的内容宽高（包含边距）
        float contentWidth = maxX - minX;
        float contentHeight = maxY - minY;
        
        // 避免除以零错误
        if (contentWidth <= 0) contentWidth = 1;
        if (contentHeight <= 0) contentHeight = 1;
        
        // 计算合适的缩放比例，使内容适合视口（保留一些空间）
        float scaleX = viewportWidth / contentWidth;
        float scaleY = viewportHeight / contentHeight;
        
        // 使用较小的缩放比例确保所有内容都可见
        float scale = Math.min(scaleX, scaleY);
        
        // 为了更好的视觉效果，稍微缩小一点缩放比例（保留5%的边距）
        scale *= 0.95f;
        
        // 限制缩放范围
        scale = Math.max(CanvasConstants.MIN_ZOOM, Math.min(scale, CanvasConstants.MAX_ZOOM));
        
        // 计算偏移量以居中显示
        float offsetX = -minX * scale + (viewportWidth - contentWidth * scale) / 2;
        float offsetY = -minY * scale + (viewportHeight - contentHeight * scale) / 2;
        
        // 应用缩放和偏移
        this.canvasZoom = scale;
        this.canvasOffsetX = offsetX;
        this.canvasOffsetY = offsetY;
        
        NodeCraft.LOGGER.info("画布已适应所有节点：缩放 = {}, 偏移 = ({}, {})", scale, offsetX, offsetY);
        NodeCraft.LOGGER.debug("节点边界：({}, {}) 到 ({}, {}), 内容尺寸：{} x {}", 
                              minX, minY, maxX, maxY, contentWidth, contentHeight);
        NodeCraft.LOGGER.debug("视口尺寸：{} x {}, 缩放比例：scaleX={}, scaleY={}, 最终scale={}", 
                              viewportWidth, viewportHeight, scaleX, scaleY, scale);
        NodeCraft.LOGGER.debug("居中偏移：extraOffsetX={}, extraOffsetY={}", 
                              (viewportWidth - contentWidth * scale) / 2, (viewportHeight - contentHeight * scale) / 2);
        
        // 同步画布状态到ImGuiNodeEditor
        if (nodeEditor instanceof ImGuiNodeEditor) {
            ((ImGuiNodeEditor)nodeEditor).setCanvasView(canvasZoom, canvasOffsetX, canvasOffsetY);
        }
    }
    
    /**
     * 放大画布视图
     */
    public void zoomIn() {
        this.canvasZoom = Math.min(this.canvasZoom + CanvasConstants.ZOOM_STEP, CanvasConstants.MAX_ZOOM);
        NodeCraft.LOGGER.info("画布放大: {}", this.canvasZoom);
    }
    
    /**
     * 缩小画布视图
     */
    public void zoomOut() {
        this.canvasZoom = Math.max(this.canvasZoom - CanvasConstants.ZOOM_STEP, CanvasConstants.MIN_ZOOM);
        NodeCraft.LOGGER.info("画布缩小: {}", this.canvasZoom);
    }
    

    
    /**
     * 切换网格显示
     */
    public void toggleGrid() {
        setShowGrid(!showGrid);
    }
    
    /**
     * 切换节点显示模式
     */
    public void toggleNodeDisplayMode() {
        // 按顺序循环切换显示模式
        switch (nodeDisplayMode) {
            case FULL:
                nodeDisplayMode = NodeDisplayMode.COMPACT;
                NodeCraft.LOGGER.info("节点显示模式: 紧凑模式");
                break;
            case COMPACT:
                nodeDisplayMode = NodeDisplayMode.ICON_ONLY;
                NodeCraft.LOGGER.info("节点显示模式: 仅图标");
                break;
            case ICON_ONLY:
                nodeDisplayMode = NodeDisplayMode.TEXT_ONLY;
                NodeCraft.LOGGER.info("节点显示模式: 仅文本");
                break;
            case TEXT_ONLY:
                nodeDisplayMode = NodeDisplayMode.FULL;
                NodeCraft.LOGGER.info("节点显示模式: 完整模式");
                break;
        }
        
        // 将显示模式传递给编辑器
        if (nodeEditor instanceof ImGuiNodeEditor editor) {
            editor.setNodeDisplayMode(nodeDisplayMode.ordinal());
        }
    }
    
    /**
     * 获取当前节点显示模式
     * @return 当前节点显示模式
     */
    public NodeDisplayMode getNodeDisplayMode() {
        return nodeDisplayMode;
    }
    
    /**
     * 设置节点显示模式
     * @param mode 要设置的显示模式
     */
    public void setNodeDisplayMode(NodeDisplayMode mode) {
        this.nodeDisplayMode = mode;
        
        // 将显示模式传递给编辑器
        if (nodeEditor instanceof ImGuiNodeEditor editor) {
            editor.setNodeDisplayMode(nodeDisplayMode.ordinal());
        }
    }
    
    /**
     * 切换节点预览
     */
    public void toggleNodePreviews() {
        showNodePreviews = !showNodePreviews;
        NodeCraft.LOGGER.info("节点预览: {}", showNodePreviews ? "开启" : "关闭");
        
        // 将预览状态传递给编辑器
        if (nodeEditor instanceof ImGuiNodeEditor editor) {
            editor.setShowNodePreviews(showNodePreviews);
        }
    }
    
    /**
     * 获取是否显示节点预览
     * @return 是否显示节点预览
     */
    public boolean isShowNodePreviews() {
        return showNodePreviews;
    }
    
    /**
     * 设置是否显示节点预览
     * @param show 是否显示
     */
    public void setShowNodePreviews(boolean show) {
        this.showNodePreviews = show;
        
        // 将预览状态传递给编辑器
        if (nodeEditor instanceof ImGuiNodeEditor editor) {
            editor.setShowNodePreviews(showNodePreviews);
        }
    }
    
    /**
     * 通用的异常处理方法
     * @param e 捕获到的异常
     * @param context 发生异常的操作上下文
     */
    private void handleException(Exception e, String context) {
        NodeCraft.LOGGER.error("{}失败: {}", context, e.getMessage());
        if (e.getCause() != null) {
            NodeCraft.LOGGER.error("根本原因: {}", e.getCause().getMessage());
        }
        // 尝试清理 ImGui 状态
        ensureCleanState(); 
    }

    /**
     * 处理画布右键上下文菜单
     */
    private void handleContextMenu(ImVec2 canvasScreenPos) {
        // 检测鼠标右键点击，并且确保鼠标悬停在当前画布窗口上
        if (ImGui.isMouseClicked(1) && ImGui.isWindowHovered()) {
            boolean shouldShowCanvasMenu = true; // 默认显示画布菜单
            
            if (nodeEditor instanceof ImGuiNodeEditor editor) {

                // 检查是否点击在节点上
                boolean isOverNode = editor.isMouseOverAnyNode(ImGui.getIO().getMousePosX(), ImGui.getIO().getMousePosY(), canvasScreenPos);
                
                // 检查是否点击在连接线上
                boolean isOverConnection = false;
                if (editor.getInteraction() != null) {
                    isOverConnection = editor.getInteraction().isHoveringConnection();
                }
                
                // 如果点击在节点上，由编辑器处理节点上下文菜单
                if (isOverNode) {
                    editor.handleNodeRightClick(ImGui.getIO().getMousePosX(), ImGui.getIO().getMousePosY());
                    shouldShowCanvasMenu = false;
                }
                // 如果点击在连接线上，不显示任何菜单（连接线的右键操作由ImGuiNodeInteraction处理）
                else if (isOverConnection) {
                    shouldShowCanvasMenu = false;
                    NodeCraft.LOGGER.debug("右键点击在连接线上，不显示画布菜单");
                }
            }
            
            // 只有在空白处点击时才打开画布上下文菜单
            if (shouldShowCanvasMenu) {
                ImGui.openPopup("CanvasContextMenu");
                
                // 记录右键点击的世界坐标，用于后续可能的节点添加
                contextMenuPosX = (ImGui.getIO().getMousePosX() - canvasScreenPos.x - canvasOffsetX) / canvasZoom;
                contextMenuPosY = (ImGui.getIO().getMousePosY() - canvasScreenPos.y - canvasOffsetY) / canvasZoom;
                
                NodeCraft.LOGGER.debug("在画布空白处右键，显示画布菜单");
            }
        }
        
        // 开始绘制弹出菜单（如果它被打开了）
        if (ImGui.beginPopup("CanvasContextMenu")) {
            try { // 使用 try-finally 确保 endPopup 被调用
                // 视图选项子菜单
                if (ImGui.beginMenu("视图选项")) {
                    // 添加菜单项：重置视图
                    if (ImGui.menuItem("重置视图")) {
                        resetCanvasView();
                    }
                    
                    // 缩放选项
                    if (ImGui.menuItem("放大")) {
                        zoomIn();
                    }
                    
                    if (ImGui.menuItem("缩小")) {
                        zoomOut();
                    }
                    
                    if (ImGui.menuItem("适应内容")) {
                        fitToContent();
                    }
                    
                    ImGui.separator();
                    
                    // 添加菜单项：切换网格显示 (文本根据当前状态变化)
                    if (ImGui.menuItem(showGrid ? "隐藏网格" : "显示网格")) {
                        setShowGrid(!showGrid);
                    }
                    
                    // 节点显示模式子菜单
                    if (ImGui.beginMenu("节点显示模式")) {
                        if (ImGui.menuItem("完整模式", null, nodeDisplayMode == NodeDisplayMode.FULL)) {
                            setNodeDisplayMode(NodeDisplayMode.FULL);
                        }
                        if (ImGui.menuItem("紧凑模式", null, nodeDisplayMode == NodeDisplayMode.COMPACT)) {
                            setNodeDisplayMode(NodeDisplayMode.COMPACT);
                        }
                        if (ImGui.menuItem("仅图标", null, nodeDisplayMode == NodeDisplayMode.ICON_ONLY)) {
                            setNodeDisplayMode(NodeDisplayMode.ICON_ONLY);
                        }
                        if (ImGui.menuItem("仅文本", null, nodeDisplayMode == NodeDisplayMode.TEXT_ONLY)) {
                            setNodeDisplayMode(NodeDisplayMode.TEXT_ONLY);
                        }
                        ImGui.endMenu();
                    }
                    
                    // 节点预览选项
                    if (ImGui.menuItem("节点预览", null, showNodePreviews)) {
                        toggleNodePreviews();
                    }
                    
                    ImGui.endMenu();
                }
                
                ImGui.separator();
                
                // 粘贴操作
                if (ImGui.menuItem("粘贴", "Ctrl+V")) {
                    // 委托给编辑器处理粘贴操作
                    if (nodeEditor instanceof ImGuiNodeEditor) {
                        ((ImGuiNodeEditor)nodeEditor).pasteNodesAtPosition(contextMenuPosX, contextMenuPosY);
                    }
                }
                
                // 添加新节点选项
                if (ImGui.menuItem("添加节点...")) {
                    openNodeSearchPopup(contextMenuPosX, contextMenuPosY);
                }
                
            } finally {
                ImGui.endPopup();
            }
        }
    }

    
    /**
     * 获取画布缩放比例
     * @return 缩放比例
     */
    public float getCanvasZoom() {
        return canvasZoom;
    }

    /**
     * 检查是否正在进行节点拖放操作
     * @return true 如果正在拖放节点
     */
    public static boolean isNodeDragDropActive() {
        return isNodeDragDropActive || isPresetDragDropActive;
    }

    public static boolean isPresetDragDropActive() {
        return isPresetDragDropActive;
    }
    
    /**
     * 计算并返回画布当前可见区域中心的世界坐标。
     * 假设在 ImGui 渲染循环中调用，可以获取到当前窗口的屏幕位置和尺寸。
     * @return 包含世界X和世界Y坐标的ImVec2
     */
    public ImVec2 getCanvasCenterWorldPosition() {
        ImVec2 canvasScreenPos = ImGui.getWindowPos(); // 当前画布窗口的屏幕位置
        ImVec2 canvasSize = ImGui.getWindowSize();   // 当前画布窗口的尺寸
        
        // 优先使用最后一次渲染记录的尺寸，避免在非渲染帧中获取 ImGui.getWindowSize() 的不确定性
        float currentDisplayWidth = lastRenderedCanvasWidth > 0 ? lastRenderedCanvasWidth : canvasSize.x;
        float currentDisplayHeight = lastRenderedCanvasHeight > 0 ? lastRenderedCanvasHeight : canvasSize.y;

        // 计算画布在屏幕上的中心点
        float screenCenterX = canvasScreenPos.x + currentDisplayWidth / 2.0f;
        float screenCenterY = canvasScreenPos.y + currentDisplayHeight / 2.0f;

        // 将屏幕中心点转换为世界坐标
        float worldX = (screenCenterX - canvasOffsetX) / canvasZoom;
        float worldY = (screenCenterY - canvasOffsetY) / canvasZoom;

        return new ImVec2(worldX, worldY);
    }
} 

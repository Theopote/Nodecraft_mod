package com.nodecraft.gui.editor.impl;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.gui.components.CanvasComponent;
import com.nodecraft.gui.editor.integration.ImGuiInputAdapter;

import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiMouseButton;
import com.nodecraft.core.NodeCraft;

/**
 * ImGui节点编辑器的交互逻辑组件。
 * 负责处理鼠标点击、拖拽、框选、端口连接等用户交互事件，
 * 并维护编辑器内部的交互状态机。
 */
public class ImGuiNodeInteraction {

    /**
     * 交互状态枚举 - 使用状态机模式管理复杂的交互逻辑。
     * 定义了编辑器当前可能处于的交互模式。
     */
    public enum InteractionState {
        IDLE,                // 空闲状态：没有进行任何交互
        DRAGGING_NODE,       // 拖拽节点状态：正在拖动一个或多个节点
        BOX_SELECTING,       // 框选状态：正在拖动鼠标进行区域选择
        CREATING_CONNECTION, // 创建连接状态：正在从一个端口拖出连接线
        PANNING_CANVAS       // 平移画布状态：正在拖动画布背景
    }

    private final ICanvasEditor editor; // 对画布编辑器的引用，用于访问编辑器状态和调用其功能

    // 状态机相关：当前交互状态
    private InteractionState currentState = InteractionState.IDLE;

    // 框选状态变量：存储框选区域的世界坐标起始和结束点
    private NodePosition boxSelectStart = new NodePosition(0, 0);
    private NodePosition boxSelectEnd = new NodePosition(0, 0);

    // 节点拖拽状态：存储正在拖拽的节点的ID（通常是发起拖拽的那个节点）
    private UUID draggingNodeId = null;

    // 端口连接状态：存储连接的源端口信息和预览线的起始位置
    private UUID sourceNodeId = null;
    private String sourcePortId = null;
    private ImVec2 dragPreviewLineStartPos = new ImVec2();
    private boolean isFromOutputPort = true; // true表示连接从输出端口开始，false表示从输入端口开始

    // 端口悬停状态：存储当前鼠标悬停的端口信息
    private UUID hoveredNodeId = null; // 悬停端口所属的节点ID
    private String hoveredPortId = null; // 悬停端口的ID
    private boolean isHoveredPortOutput = false; // 悬停端口是否为输出端口

    // 连接线悬停状态：存储当前鼠标悬停的连接线信息
    private boolean isHoveringConnection = false;
    private UUID hoveredConnectionSourceNodeId = null;
    private String hoveredConnectionSourcePortId = null;
    private UUID hoveredConnectionTargetNodeId = null;
    private String hoveredConnectionTargetPortId = null;

    // --- 交互状态追踪：精确区分节点主体、自定义UI和画布的活跃状态 ---
    // 这个变量用于追踪哪个节点的主体（通过invisibleButton）当前是活跃的
    private UUID nodeBodyActive = null;
    // 这个变量用于追踪哪个节点的自定义UI（通过ImGui.isItemActive()在child window内部）当前是活跃的
    private UUID nodeCustomUIActive = null;

    // 画布平移：记录鼠标按下时的画布偏移量，用于计算拖拽
    private float initialCanvasOffsetX;
    private float initialCanvasOffsetY;
    private ImVec2 panStartMousePos = new ImVec2(); // 记录平移开始时的鼠标屏幕位置

    // 常量：用于碰撞检测和布局计算的因子
    private static final float PORT_DETECTION_RADIUS_FACTOR = 4.0f; // 端口检测半径的放大因子（增大以便更容易拖出连接线）
    private static final float REROUTE_PORT_DETECTION_RADIUS_FACTOR = 1.8f; // 中继节点使用更小命中半径，避免抢占节点选择
    private static final float NODE_CENTER_PRIORITY_FACTOR = 0.7f; // 节点中心区域优先级因子
    private static final float CONNECTION_DETECTION_DISTANCE = 5.0f; // 连接线检测的最大距离

    // 视觉反馈相关变量：用于端口高亮动画
    private boolean shouldShowPortHighlight = false;
    private float portHighlightAnimationTimer = 0f;

    /**
     * 构造函数。
     * @param editor 画布编辑器的引用。
     */
    public ImGuiNodeInteraction(ICanvasEditor editor) {
        this.editor = editor;
    }

    // --- Getter 方法 ---
    public NodePosition getBoxSelectStart() { return boxSelectStart; }
    public NodePosition getBoxSelectEnd() { return boxSelectEnd; }
    public boolean isBoxSelecting() { return currentState == InteractionState.BOX_SELECTING; }
    public boolean isDraggingNode() { return currentState == InteractionState.DRAGGING_NODE; }
    public boolean isCreatingConnection() { return currentState == InteractionState.CREATING_CONNECTION; }
    public ImVec2 getDragPreviewLineStartPos() { return dragPreviewLineStartPos; }
    public boolean isFromOutputPort() { return isFromOutputPort; }
    public UUID getHoveredNodeId() { return hoveredNodeId; }
    public String getHoveredPortId() { return hoveredPortId; }
    public boolean isHoveredPortOutput() { return isHoveredPortOutput; }
    public boolean isHoveringConnection() { return isHoveringConnection; }
    public UUID getHoveredConnectionSourceNodeId() { return hoveredConnectionSourceNodeId; }
    public String getHoveredConnectionSourcePortId() { return hoveredConnectionSourcePortId; }
    public UUID getHoveredConnectionTargetNodeId() { return hoveredConnectionTargetNodeId; }
    public String getHoveredConnectionTargetPortId() { return hoveredConnectionTargetPortId; }
    public UUID getSourceNodeId() { return sourceNodeId; }
    public String getSourcePortId() { return sourcePortId; }
    public float getPortHighlightAnimationTimer() { return portHighlightAnimationTimer; }
    public boolean shouldShowPortHighlight() { return shouldShowPortHighlight; }
    public UUID getDraggingNodeId() { return draggingNodeId; } // Added getter for draggingNodeId


    /**
     * 设置框选状态。
     * @param isBoxSelecting true 表示开始框选，false 表示结束框选。
     */
    public void setBoxSelecting(boolean isBoxSelecting) {
        this.currentState = isBoxSelecting ? InteractionState.BOX_SELECTING : InteractionState.IDLE;
    }

    /**
     * 设置当前有活动节点主体交互（invisibleButton被激活）的节点ID。
     * 由 ImGuiNodeRenderer 在渲染节点主体时调用。
     * @param nodeId 正在交互的节点ID。
     */
    public void setNodeBodyActive(UUID nodeId) {
        this.nodeBodyActive = nodeId;
    }

    /**
     * 清除当前有活动节点主体交互的节点ID。
     * 由 ImGuiNodeRenderer 在渲染节点主体时调用。
     * @param nodeId 刚刚失去交互的节点ID。
     */
    public void clearNodeBodyActive(UUID nodeId) {
        if (Objects.equals(this.nodeBodyActive, nodeId)) {
            this.nodeBodyActive = null;
        }
    }

    /**
     * 检查是否有节点主体正在被交互。
     * @return true 如果有节点主体正在交互。
     */
    public boolean isNodeBodyActive() {
        return nodeBodyActive != null;
    }

    /**
     * 设置当前有活动自定义UI（child window内部ImGui Item被激活）的节点ID。
     * 由 ImGuiNodeRenderer 在渲染自定义UI时调用。
     * @param nodeId 正在交互的节点ID。
     */
    public void setNodeCustomUIActive(UUID nodeId) {
        this.nodeCustomUIActive = nodeId;
        // 当自定义UI激活时，设置鼠标捕获以阻止画布平移和窗口拖动
        if (nodeId != null) {
            ImGui.getIO().setWantCaptureMouse(true);
        }
    }

    /**
     * 清除当前有活动自定义UI交互的节点ID。
     * 由 ImGuiNodeRenderer 在渲染自定义UI时调用。
     * @param nodeId 刚刚失去交互的节点ID。
     */
    public void clearNodeCustomUIActive(UUID nodeId) {
        if (Objects.equals(this.nodeCustomUIActive, nodeId)) {
            this.nodeCustomUIActive = null;
            // 当自定义UI不再激活时，释放鼠标捕获
            ImGui.getIO().setWantCaptureMouse(false);
        }
    }

    /**
     * 检查是否有节点的自定义UI处于激活状态。
     * @return true 如果有节点的自定义UI处于激活状态。
     */
    public boolean isNodeCustomUIActive() {
        return nodeCustomUIActive != null;
    }

    /**
     * 获取当前激活的自定义UI节点ID。
     * @return 当前激活的自定义UI节点ID，如果没有则返回null。
     */
    public UUID getNodeCustomUIActive() {
        return nodeCustomUIActive;
    }

    /**
     * 处理点击节点主体时的选择逻辑。
     * 这个方法用于在确认鼠标点击在一个节点非端口区域时，处理节点的选择逻辑。
     * @param nodeId 鼠标下的节点ID。
     * @param isCtrlPressed Ctrl键是否按下。
     */
    public void handleClickOnNodeBody(UUID nodeId, boolean isCtrlPressed) {
        if (nodeId == null) return;

        java.util.Set<UUID> selectedNodeIds = editor.getSelectedNodeIds();

        if (isCtrlPressed) {
            // Ctrl+点击：切换节点的选中状态
            if (selectedNodeIds.contains(nodeId)) {
                selectedNodeIds.remove(nodeId);
                if (editor.getSelectedNodeId() != null && editor.getSelectedNodeId().equals(nodeId)) {
                    editor.setSelectedNodeId(selectedNodeIds.isEmpty() ? null : selectedNodeIds.iterator().next());
                }
                NodeCraft.LOGGER.debug("取消选中节点: {}, 剩余选中: {}", nodeId, selectedNodeIds.size());
            } else {
                selectedNodeIds.add(nodeId);
                editor.setSelectedNodeId(nodeId);
                NodeCraft.LOGGER.debug("多选添加节点: {}, 总共选中: {}", nodeId, selectedNodeIds.size());
            }
        } else {
            // 单击：如果节点已经被选中且有多个节点被选中，则保持当前选择状态
            // 这样用户可以直接拖动选中的节点组
            if (selectedNodeIds.contains(nodeId) && selectedNodeIds.size() > 1) {
                NodeCraft.LOGGER.debug("保持多选状态，准备拖动: 当前选中{}个节点", selectedNodeIds.size());
            } else {
                // 否则清除之前的选择并选中当前节点
                editor.clearSelectedNodes();
                editor.getSelectedNodeIds().add(nodeId); // Use getSelectedNodeIds() to add to the managed set
                editor.setSelectedNodeId(nodeId);
                NodeCraft.LOGGER.debug("单选节点: {}", nodeId);
            }
        }
    }

    /**
     * 尝试启动节点拖拽。
     * 仅当鼠标左键按下且当前状态为 IDLE，并且鼠标位于节点而非端口时才启动。
     * 此方法由 ImGuiNodeRenderer 在 invisibleButton 激活时调用。
     * @param nodeId 正在被拖动的节点ID。
     * @return true 如果拖拽已启动。
     */
    public boolean tryStartNodeDraggingFromNodeBody(UUID nodeId) {
        // 只有在 IDLE 状态才能启动拖拽
        if (currentState != InteractionState.IDLE) {
            return false;
        }

        // 此时 ImGui.isItemActive() 已经为 true，并且鼠标在节点主体上
        // 确保鼠标左键按下（因为 ImGui.isItemActive() 可能是拖动非点击）
        if (ImGuiInputAdapter.isMouseDown(ImGuiMouseButton.Left)) {
            // 额外检查：即使节点主体的invisibleButton被激活，如果鼠标实际在端口区域，
            // 也不启动节点拖拽，优先处理端口连接
            ImVec2 mousePos = ImGui.getIO().getMousePos();
            boolean isMouseOnPort = isMouseOverAnyPortOfNode(nodeId, mousePos, editor.getPortScreenPositions());

            if (isMouseOnPort) {
                NodeCraft.LOGGER.debug("鼠标在端口区域，不启动节点拖拽，优先处理端口连接：{}", nodeId);
                return false;
            }

            currentState = InteractionState.DRAGGING_NODE;
            draggingNodeId = nodeId; // 记录发起拖拽的节点ID
            ImGui.getIO().setWantCaptureMouse(true); // 明确设置捕获鼠标，阻止父窗口移动
            NodeCraft.LOGGER.debug("从节点主体启动拖拽：{}", nodeId);
            return true;
        }
        return false;
    }

    /**
     * 尝试停止节点拖拽。
     * 当鼠标从节点主体 invisible button 或自定义 UI 控件释放时调用。
     */
    public void tryStopNodeDragging() {
        if (currentState == InteractionState.DRAGGING_NODE) {
            // 只有当鼠标左键真的抬起时才停止拖拽
            if (ImGuiInputAdapter.isMouseReleased(ImGuiMouseButton.Left)) {
                currentState = InteractionState.IDLE;
                draggingNodeId = null;
                ImGui.getIO().setWantCaptureMouse(false); // 释放鼠标捕获
                NodeCraft.LOGGER.debug("节点拖动结束（鼠标释放）");
            } else {
                // 如果鼠标没有抬起，但 ImGui.isItemActive() 变为 false (例如鼠标离开了 invisibleButton)
                // 此时不应立即停止拖拽，而是等待鼠标抬起
                // 状态机将保持 DRAGGING_NODE，直到鼠标抬起。
            }
        }
    }

    /**
     * 尝试启动画布平移。
     * 仅当鼠标左键刚刚按下且当前状态为 IDLE，并且鼠标位于画布背景上时才启动。
     * @param mousePos 鼠标屏幕位置。
     */
    public boolean tryStartCanvasPanning(ImVec2 mousePos) {
        if (currentState != InteractionState.IDLE) {
            return false;
        }

        // 确保鼠标左键刚刚按下，且 ImGui 未被其他更高优先级元素捕获
        if (ImGuiInputAdapter.isMouseClicked(ImGuiMouseButton.Left) && !ImGui.getIO().getWantCaptureMouse()) {
            currentState = InteractionState.PANNING_CANVAS;
            panStartMousePos.x = mousePos.x;
            panStartMousePos.y = mousePos.y;
            initialCanvasOffsetX = editor.getCanvasOffsetX();
            initialCanvasOffsetY = editor.getCanvasOffsetY();
            ImGui.getIO().setWantCaptureMouse(true); // 明确设置捕获鼠标，阻止父窗口移动
            NodeCraft.LOGGER.debug("开始画布平移");
            return true;
        }
        return false;
    }

    /**
     * 处理画布平移。
     * 在每帧被调用，用于在 PANNING_CANVAS 状态下更新画布偏移量。
     * @param canvasPos 画布窗口的屏幕位置。
     */
    public void handleCanvasPanning(ImVec2 canvasPos) {
        if (currentState != InteractionState.PANNING_CANVAS) {
            return;
        }

        if (ImGuiInputAdapter.isMouseDown(ImGuiMouseButton.Left)) {
            ImVec2 currentMousePos = ImGui.getIO().getMousePos();

            // 计算鼠标相对于起始点的屏幕位移
            float mouseDeltaX = currentMousePos.x - panStartMousePos.x;
            float mouseDeltaY = currentMousePos.y - panStartMousePos.y;

            // 根据鼠标位移和缩放因子计算新的画布偏移量
            // 注意：平移画布时，鼠标 delta 直接应用于画布偏移量，不需要除以 zoom
            float newOffsetX = initialCanvasOffsetX + mouseDeltaX;
            float newOffsetY = initialCanvasOffsetY + mouseDeltaY;

            editor.setCanvasOffset(newOffsetX, newOffsetY);
        } else {
            // 鼠标左键释放，结束平移
            currentState = InteractionState.IDLE;
            ImGui.getIO().setWantCaptureMouse(false); // 释放鼠标捕获
            NodeCraft.LOGGER.debug("画布平移结束");
        }
    }


    /**
     * 处理框选的启动和进行。
     * 框选的启动应该由ImGuiNodeEditor控制，这里只处理进行中的框选。
     * @param mousePos 当前鼠标屏幕位置。
     * @param canvasPos 画布屏幕位置。
     * @param nodePositions 节点位置映射。
     * @param graph 节点图。
     */
    public void handleBoxSelection(ImVec2 mousePos, ImVec2 canvasPos, Map<UUID, NodePosition> nodePositions, NodeGraph graph) {
        // 处理进行中的框选：只有当当前状态为 BOX_SELECTING 时
        if (currentState == InteractionState.BOX_SELECTING) {
            // 如果检测到节点拖放操作开始，立即取消框选
            if (CanvasComponent.isNodeDragDropActive()) {
                NodeCraft.LOGGER.debug("检测到节点拖放操作开始，取消当前框选");
                currentState = InteractionState.IDLE;
                ImGui.getIO().setWantCaptureMouse(false); // 释放鼠标捕获
                return;
            }

            if (ImGuiInputAdapter.isMouseDown(ImGuiMouseButton.Left)) { // 鼠标左键仍按下，更新框选结束位置
                boxSelectEnd.x = (mousePos.x - canvasPos.x - editor.getCanvasOffsetX()) / editor.getCanvasZoom();
                boxSelectEnd.y = (mousePos.y - canvasPos.y - editor.getCanvasOffsetY()) / editor.getCanvasZoom();
            } else { // 鼠标左键抬起，完成框选
                currentState = InteractionState.IDLE;
                processBoxSelectionResult(nodePositions, graph, editor.getSelectedNodeIds());
                ImGui.getIO().setWantCaptureMouse(false); // 释放鼠标捕获
            }
        }
    }

    /**
     * 启动框选。这个方法由ImGuiNodeEditor调用，确保框选只在画布空白处启动。
     * @param mousePos 当前鼠标屏幕位置。
     * @param canvasPos 画布屏幕位置。
     */
    public void startBoxSelection(ImVec2 mousePos, ImVec2 canvasPos) {
        NodeCraft.LOGGER.debug("startBoxSelection被调用 - currentState: {}", currentState);
        
        if (currentState != InteractionState.IDLE) {
            NodeCraft.LOGGER.debug("startBoxSelection失败 - 当前状态不是IDLE: {}", currentState);
            return;
        }
        
        // 检查是否正在进行节点拖放操作 - 如果是则不启动框选
        if (CanvasComponent.isNodeDragDropActive()) {
            NodeCraft.LOGGER.debug("检测到节点拖放操作正在进行，跳过框选启动");
            return;
        }

        currentState = InteractionState.BOX_SELECTING;
        float worldX = (mousePos.x - canvasPos.x - editor.getCanvasOffsetX()) / editor.getCanvasZoom();
        float worldY = (mousePos.y - canvasPos.y - editor.getCanvasOffsetY()) / editor.getCanvasZoom();
        boxSelectStart = new NodePosition(worldX, worldY);
        boxSelectEnd = new NodePosition(worldX, worldY);
        NodeCraft.LOGGER.debug("框选成功启动，起始点: ({}, {}), 状态设置为: {}", worldX, worldY, currentState);
        ImGui.getIO().setWantCaptureMouse(true); // 确保启动框选时，鼠标事件被捕获，防止主窗口移动
        NodeCraft.LOGGER.debug("框选启动完成 - setWantCaptureMouse(true)");
    }

    /**
     * 实际处理框选结果（私有辅助方法）。
     * @param nodePositions 节点位置映射。
     * @param graph 节点图。
     * @param selectedNodeIds 选中的节点集合。
     */
    private void processBoxSelectionResult(Map<UUID, NodePosition> nodePositions, NodeGraph graph, java.util.Set<UUID> selectedNodeIds) {
        // 计算框选区域的世界坐标边界
        float minX = Math.min(boxSelectStart.x, boxSelectEnd.x);
        float maxX = Math.max(boxSelectStart.x, boxSelectEnd.x);
        float minY = Math.min(boxSelectStart.y, boxSelectEnd.y);
        float maxY = Math.max(boxSelectStart.y, boxSelectEnd.y);

        // 检查框选区域是否太小，如果是则视为简单点击（不进行框选操作）
        float minArea = 25; // 未缩放的最小面积要求
        if ((maxX - minX) * (maxY - minY) < minArea) {
            NodeCraft.LOGGER.debug("框选区域太小 ({}x{}={})，视为点击，不进行框选操作",
                    maxX - minX, maxY - minY, (maxX - minX) * (maxY - minY));
            return; // 视为点击，不进行框选操作，选择清除由ImGuiNodeEditor控制
        }

        // 检查是否按下Ctrl键（用于增量选择）
        boolean isCtrlPressed = ImGui.getIO().getKeyCtrl();
        if (!isCtrlPressed) {
            editor.clearSelectedNodes(); // 如果不是增量选择，则清除之前的选择
        }

        // 遍历所有节点，检查是否在框选区域内
        boolean nodesSelected = false;
        for (INode node : graph.getNodes()) {
            UUID nodeId = node.getId();
            NodePosition pos = nodePositions.get(nodeId);

            if (pos != null) {
                // 节点的边界框 (世界坐标，未缩放)
                float nodeMinX = pos.x;
                float nodeMaxX = pos.x + pos.width; // 使用 NodePosition 中存储的精确未缩放宽度
                float nodeMinY = pos.y;
                float nodeMaxY = pos.y + pos.height; // 使用 NodePosition 中存储的精确未缩放高度

                // 检查节点边界框是否与框选区域相交
                if (nodeMaxX >= minX && nodeMinX <= maxX &&
                        nodeMaxY >= minY && nodeMinY <= maxY) {
                    selectedNodeIds.add(nodeId); // 添加到选中列表
                    // 如果还没有主选中节点，则设置该节点为主选中节点
                    if (editor.getSelectedNodeId() == null) {
                        editor.setSelectedNodeId(nodeId);
                    }
                    nodesSelected = true;
                }
            }
        }

        if (nodesSelected) {
            NodeCraft.LOGGER.debug("框选完成，选中了 {} 个节点", selectedNodeIds.size());
        } else {
            NodeCraft.LOGGER.debug("框选未选中任何节点");
        }
    }

    /**
     * 尝试启动连接创建。
     * 仅当鼠标左键刚刚按下且当前状态为 IDLE，并且鼠标位于端口上时才启动。
     * @param hoveredNodeId 鼠标悬停的节点ID。
     * @param hoveredPortId 鼠标悬停的端口ID。
     * @param isOutputPort 悬停端口是否为输出端口。
     * @param portScreenPositions 端口屏幕位置映射。
     * @return true 如果连接创建已成功启动。
     */
    public boolean tryStartConnectionCreation(UUID hoveredNodeId, String hoveredPortId, boolean isOutputPort,
                                              Map<UUID, Map<String, ImVec2>> portScreenPositions) {
        if (currentState != InteractionState.IDLE || hoveredNodeId == null || hoveredPortId == null) {
            return false;
        }

        // 只有当鼠标左键刚刚点击时才尝试启动
        if (ImGuiInputAdapter.isMouseClicked(ImGuiMouseButton.Left)) {
            // 【关键修改点：】不再检查 ImGui.getIO().getWantCaptureMouse()，
            // 因为在 renderSingleNode 中已经确保端口优先级，
            // 如果到了这里，就说明是点击了端口，应该无条件开始连接。
            // if (ImGui.getIO().getWantCaptureMouse()) return false;

            if (portScreenPositions.containsKey(hoveredNodeId)) {
                Map<String, ImVec2> ports = portScreenPositions.get(hoveredNodeId);
                if (ports.containsKey(hoveredPortId)) {
                    ImVec2 portPos = ports.get(hoveredPortId);
                    currentState = InteractionState.CREATING_CONNECTION;
                    sourceNodeId = hoveredNodeId;
                    sourcePortId = hoveredPortId;
                    dragPreviewLineStartPos = portPos;
                    this.isFromOutputPort = isOutputPort;
                    ImGui.getIO().setWantCaptureMouse(true); // 明确设置捕获鼠标，阻止父窗口移动
                    NodeCraft.LOGGER.debug("开始创建连接从端口：{}({})", hoveredNodeId, hoveredPortId);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 处理进行中的端口连接。
     * 此方法只负责在 CREATING_CONNECTION 状态下处理鼠标左键释放完成连接。
     * @param graph 节点图。
     * @param portScreenPositions 端口屏幕位置映射。
     */
    public void handleActiveConnectionCreation(NodeGraph graph, Map<UUID, Map<String, ImVec2>> portScreenPositions) {
        if (currentState != InteractionState.CREATING_CONNECTION) {
            return;
        }

        // 如果鼠标左键释放，尝试完成连接
        if (ImGuiInputAdapter.isMouseReleased(ImGuiMouseButton.Left)) {
            currentState = InteractionState.IDLE; // 重置状态
            ImGui.getIO().setWantCaptureMouse(false); // 释放鼠标捕获
            float endX = ImGui.getMousePosX();
            float endY = ImGui.getMousePosY();
            NodeCraft.LOGGER.debug("尝试完成连接，鼠标释放@ ({}, {})", endX, endY);

            if (sourceNodeId != null && sourcePortId != null) {
                Map.Entry<UUID, String> targetPortInfo = getClickedPort(new ImVec2(endX, endY), portScreenPositions);

                if (targetPortInfo != null) {
                    UUID targetNodeId = targetPortInfo.getKey();
                    String targetPortId = targetPortInfo.getValue();
                    NodeCraft.LOGGER.debug("检测到目标端口：节点 {} 端口 {}", targetNodeId, targetPortId);

                    // 不允许连接到同一节点
                    if (targetNodeId.equals(sourceNodeId)) {
                        NodeCraft.LOGGER.warn("不能连接到同一节点的不同端口。");
                        return;
                    }

                    INode targetNode = graph.getNode(targetNodeId);
                    if (targetNode == null) {
                        NodeCraft.LOGGER.warn("目标节点未找到: {}", targetNodeId);
                        return;
                    }

                    // 确定连接的方向 (输出 -> 输入)
                    UUID outputNodeId = null;
                    String outputPortId = null;
                    UUID inputNodeId = null;
                    String inputPortId = null;
                    boolean isConnectionValid = false;

                    if (this.isFromOutputPort) { // 从输出端口发起连接
                        // 目标必须是输入端口
                        for (IPort port : targetNode.getInputPorts()) {
                            if (port.getId().equals(targetPortId)) {
                                outputNodeId = sourceNodeId;
                                outputPortId = sourcePortId;
                                inputNodeId = targetNodeId;
                                inputPortId = targetPortId;
                                isConnectionValid = true;
                                break;
                            }
                        }
                        if (!isConnectionValid) {
                            NodeCraft.LOGGER.warn("目标端口 {} 不是输入端口，无法从输出端口连接到非输入端口", targetPortId);
                        }
                    } else { // 从输入端口发起连接
                        // 目标必须是输出端口
                        for (IPort port : targetNode.getOutputPorts()) {
                            if (port.getId().equals(targetPortId)) {
                                outputNodeId = targetNodeId;
                                outputPortId = targetPortId;
                                inputNodeId = sourceNodeId;
                                inputPortId = sourcePortId;
                                isConnectionValid = true;
                                break;
                            }
                        }
                        if (!isConnectionValid) {
                            NodeCraft.LOGGER.warn("目标端口 {} 不是输出端口，无法从输入端口连接到非输出端口", targetPortId);
                        }
                    }

                    if (isConnectionValid) {
                        boolean success = editor.connectPorts(outputNodeId, outputPortId, inputNodeId, inputPortId);
                        if (success) {
                            NodeCraft.LOGGER.info("完成端口连接: {}({}) -> {}({})",
                                    outputNodeId, outputPortId, inputNodeId, inputPortId);
                        } else {
                            NodeCraft.LOGGER.warn("无法完成端口连接 (connectPorts返回false)");
                        }
                    }
                }
            }
            // 重置连接源端口信息
            sourceNodeId = null;
            sourcePortId = null;
        }
    }

    /**
     * 处理连接线断开（右键点击连接线时）。
     * @param graph 节点图。
     * @param portScreenPositions 端口屏幕位置映射。
     */
    public void handleConnectionDisconnection(NodeGraph graph, Map<UUID, Map<String, ImVec2>> portScreenPositions) {
        // 更新鼠标是否悬停在连接线上
        updateHoveredConnection(ImGui.getIO().getMousePos(), portScreenPositions, graph);

        // 如果右键单击并且鼠标悬停在连接线上，则断开连接
        if (isHoveringConnection && ImGuiInputAdapter.isMouseClicked(ImGuiMouseButton.Right)) {
            if (hoveredConnectionSourceNodeId != null && hoveredConnectionSourcePortId != null &&
                    hoveredConnectionTargetNodeId != null && hoveredConnectionTargetPortId != null) {

                boolean success = editor.disconnectPorts(
                        hoveredConnectionSourceNodeId, hoveredConnectionSourcePortId,
                        hoveredConnectionTargetNodeId, hoveredConnectionTargetPortId
                );

                if (success) {
                    NodeCraft.LOGGER.info("通过右键菜单断开连接: {}({}) -> {}({})",
                            hoveredConnectionSourceNodeId, hoveredConnectionSourcePortId,
                            hoveredConnectionTargetNodeId, hoveredConnectionTargetPortId);
                } else {
                    NodeCraft.LOGGER.warn("无法断开连接 (disconnectPorts返回false)");
                }
                resetHoveredConnection(); // 重置连接悬停状态
            }
        }
    }

    /**
     * 检测点击的端口（返回鼠标下的端口信息）。
     * @param mousePos 鼠标屏幕位置。
     * @param portScreenPositions 端口屏幕位置映射。
     * @return 鼠标下的端口信息（节点ID和端口ID），如果没有则返回null。
     */
    public Map.Entry<UUID, String> getClickedPort(ImVec2 mousePos, Map<UUID, Map<String, ImVec2>> portScreenPositions) {
        for (Map.Entry<UUID, Map<String, ImVec2>> nodeEntry : portScreenPositions.entrySet()) {
            UUID nodeId = nodeEntry.getKey();
            Map<String, ImVec2> ports = nodeEntry.getValue();
            float detectionRadius = getPortDetectionRadiusForNode(nodeId, null);

            for (Map.Entry<String, ImVec2> portEntry : ports.entrySet()) {
                float dx = mousePos.x - portEntry.getValue().x;
                float dy = mousePos.y - portEntry.getValue().y;
                if ((dx * dx + dy * dy) < (detectionRadius * detectionRadius)) {
                    return Map.entry(nodeId, portEntry.getKey());
                }
            }
        }
        return null;
    }

    /**
     * 判断鼠标是否在指定节点的任何端口上。
     * @param nodeId 节点ID。
     * @param mousePos 鼠标屏幕位置。
     * @param portScreenPositions 端口屏幕位置映射。
     * @return true 如果鼠标在节点的任何端口上。
     */
    public boolean isMouseOverAnyPortOfNode(UUID nodeId, ImVec2 mousePos, Map<UUID, Map<String, ImVec2>> portScreenPositions) {
        if (!portScreenPositions.containsKey(nodeId)) return false;

        float detectionRadius = getPortDetectionRadiusForNode(nodeId, null);

        Map<String, ImVec2> ports = portScreenPositions.get(nodeId);
        for (ImVec2 portPos : ports.values()) {
            float dx = mousePos.x - portPos.x;
            float dy = mousePos.y - portPos.y;
            if ((dx * dx + dy * dy) < (detectionRadius * detectionRadius)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 更新鼠标悬停在端口上的状态。
     * @param mousePos 鼠标屏幕位置。
     * @param portScreenPositions 端口屏幕位置映射。
     * @param graph 节点图。
     * @return true 如果鼠标悬停在任何端口上。
     */
    public boolean updateHoveredPort(ImVec2 mousePos, Map<UUID, Map<String, ImVec2>> portScreenPositions, NodeGraph graph) {
        hoveredNodeId = null;
        hoveredPortId = null;
        isHoveredPortOutput = false;

        for (Map.Entry<UUID, Map<String, ImVec2>> nodeEntry : portScreenPositions.entrySet()) {
            UUID nodeId = nodeEntry.getKey();
            Map<String, ImVec2> ports = nodeEntry.getValue();
            float detectionRadius = getPortDetectionRadiusForNode(nodeId, graph);

            for (Map.Entry<String, ImVec2> portEntry : ports.entrySet()) {
                float dx = mousePos.x - portEntry.getValue().x;
                float dy = mousePos.y - portEntry.getValue().y;
                if ((dx * dx + dy * dy) < (detectionRadius * detectionRadius)) {
                    hoveredNodeId = nodeId;
                    hoveredPortId = portEntry.getKey();

                    INode node = graph.getNode(nodeId);
                    if (node != null) {
                        for (IPort port : node.getOutputPorts()) {
                            if (port.getId().equals(hoveredPortId)) {
                                isHoveredPortOutput = true;
                                break;
                            }
                        }
                    }
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 根据节点类型返回端口命中半径。
     * 中继节点使用更小半径，以避免小节点难以选中/拖动。
     */
    private float getPortDetectionRadiusForNode(UUID nodeId, NodeGraph graph) {
        float factor = PORT_DETECTION_RADIUS_FACTOR;
        NodeGraph resolvedGraph = graph != null ? graph : editor.getCurrentGraph();
        if (resolvedGraph != null) {
            INode node = resolvedGraph.getNode(nodeId);
            if (node != null && NodeRenderConstants.REROUTE_NODE_TYPE_ID.equalsIgnoreCase(node.getTypeId())) {
                factor = REROUTE_PORT_DETECTION_RADIUS_FACTOR;
            }
        }
        return 4.0f * editor.getCanvasZoom() * factor;
    }

    /**
     * 更新端口高亮动画计时器。
     * @param deltaTime 帧时间差。
     */
    public void updatePortHighlightAnimation(float deltaTime) {
        if (hoveredNodeId != null && hoveredPortId != null) {
            shouldShowPortHighlight = true;
            portHighlightAnimationTimer = (portHighlightAnimationTimer + deltaTime * 2f) % 1.0f;
        } else {
            shouldShowPortHighlight = false;
            portHighlightAnimationTimer = 0f;
        }
    }

    /**
     * 更新鼠标悬停在连接线上的状态。
     * @param mousePos 鼠标屏幕位置。
     * @param portScreenPositions 端口屏幕位置映射。
     * @param graph 节点图。
     * @return true 如果鼠标悬停在连接线上。
     */
    public boolean updateHoveredConnection(ImVec2 mousePos, Map<UUID, Map<String, ImVec2>> portScreenPositions, NodeGraph graph) {
        resetHoveredConnection();
        if (graph == null) return false;

        for (INode node : graph.getNodes()) {
            UUID nodeId = node.getId();
            for (IPort inputPort : node.getInputPorts()) {
                String inputPortId = inputPort.getId();
                UUID connectedNodeId = graph.getConnectedOutputNodeId(nodeId, inputPortId);
                if (connectedNodeId != null) {
                    String connectedPortId = graph.getConnectedOutputPortId(nodeId, inputPortId);
                    if (connectedPortId != null) {
                        ImVec2 targetPos = getPortScreenPosition(nodeId, inputPortId, portScreenPositions);
                        ImVec2 sourcePos = getPortScreenPosition(connectedNodeId, connectedPortId, portScreenPositions);
                        if (targetPos != null && sourcePos != null) {
                            float distance = distanceToLineSegment(
                                    mousePos.x, mousePos.y,
                                    sourcePos.x, sourcePos.y,
                                    targetPos.x, targetPos.y
                            );
                            if (distance < CONNECTION_DETECTION_DISTANCE) {
                                isHoveringConnection = true;
                                hoveredConnectionSourceNodeId = connectedNodeId;
                                hoveredConnectionSourcePortId = connectedPortId;
                                hoveredConnectionTargetNodeId = nodeId;
                                hoveredConnectionTargetPortId = inputPortId;
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * 计算点到线段的距离。
     * @param px 点的X坐标。
     * @param py 点的Y坐标。
     * @param x1 线段起点X坐标。
     * @param y1 线段起点Y坐标。
     * @param x2 线段终点X坐标。
     * @param y2 线段终点Y坐标。
     * @return 点到线段的最短距离。
     */
    private float distanceToLineSegment(float px, float py, float x1, float y1, float x2, float y2) {
        float lineLengthSquared = (x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1);
        if (lineLengthSquared == 0) return (float)Math.sqrt((px - x1) * (px - x1) + (py - y1) * (py - y1));

        float t = ((px - x1) * (x2 - x1) + (py - y1) * (y2 - y1)) / lineLengthSquared;
        t = Math.max(0, Math.min(1, t));

        float projX = x1 + t * (x2 - x1);
        float projY = y1 + t * (y2 - y1);

        return (float)Math.sqrt((px - projX) * (px - projX) + (py - projY) * (py - projY));
    }

    /**
     * 重置连接线悬停状态。
     */
    private void resetHoveredConnection() {
        isHoveringConnection = false;
        hoveredConnectionSourceNodeId = null;
        hoveredConnectionSourcePortId = null;
        hoveredConnectionTargetNodeId = null;
        hoveredConnectionTargetPortId = null;
    }

    /**
     * 获取端口的屏幕位置。
     * @param nodeId 节点ID。
     * @param portId 端口ID。
     * @param portScreenPositions 端口屏幕位置映射。
     * @return 端口的屏幕位置，如果未找到则返回null。
     */
    private ImVec2 getPortScreenPosition(UUID nodeId, String portId, Map<UUID, Map<String, ImVec2>> portScreenPositions) {
        Map<String, ImVec2> ports = portScreenPositions.get(nodeId);
        return (ports != null) ? ports.get(portId) : null;
    }

    /**
     * 检查鼠标是否在节点标题区域内。
     * @param nodeId 节点ID。
     * @param mousePos 鼠标屏幕位置。
     * @param nodePositions 节点位置映射。
     * @return true 如果鼠标在节点标题区域内。
     */
    private boolean isMouseInNodeTitleArea(UUID nodeId, ImVec2 mousePos, Map<UUID, NodePosition> nodePositions) {
        if (nodeId == null) return false;

        NodePosition pos = nodePositions.get(nodeId);
        if (pos == null) return false;

        float canvasZoom = editor.getCanvasZoom();
        ImVec2 canvasWindowPos = ImGui.getWindowPos();
        float nodeScreenX = canvasWindowPos.x + pos.x * canvasZoom + editor.getCanvasOffsetX();
        float nodeScreenY = canvasWindowPos.y + pos.y * canvasZoom + editor.getCanvasOffsetY();

        float nodeWidthScaled = pos.width * canvasZoom;

        float titleHeightScaled = (ImGui.getTextLineHeight() * canvasZoom) + (10.0f * canvasZoom);

        return mousePos.x >= nodeScreenX && mousePos.x <= nodeScreenX + nodeWidthScaled &&
                mousePos.y >= nodeScreenY && mousePos.y <= nodeScreenY + titleHeightScaled;
    }

    /**
     * 检查鼠标是否在节点中心区域内。
     * @param nodeId 节点ID。
     * @param mousePos 鼠标屏幕位置。
     * @param nodePositions 节点位置映射。
     * @return true 如果鼠标在节点中心区域内。
     */
    private boolean isMouseInNodeCenterArea(UUID nodeId, ImVec2 mousePos, Map<UUID, NodePosition> nodePositions) {
        if (nodeId == null) return false;

        NodePosition pos = nodePositions.get(nodeId);
        if (pos == null) return false;

        float canvasZoom = editor.getCanvasZoom();
        float canvasOffsetX = editor.getCanvasOffsetX();
        float canvasOffsetY = editor.getCanvasOffsetY();
        ImVec2 canvasWindowPos = ImGui.getWindowPos();

        float nodeScreenX = canvasWindowPos.x + pos.x * canvasZoom + canvasOffsetX;
        float nodeScreenY = canvasWindowPos.y + pos.y * canvasZoom + canvasOffsetY;

        float nodeWidth = pos.width * canvasZoom;
        float nodeHeight = pos.height * canvasZoom;

        float centerX = nodeScreenX + nodeWidth / 2;
        float centerY = nodeScreenY + nodeHeight / 2;

        float centerAreaWidth = nodeWidth * NODE_CENTER_PRIORITY_FACTOR;
        float centerAreaHeight = nodeHeight * NODE_CENTER_PRIORITY_FACTOR;

        return mousePos.x >= centerX - centerAreaWidth / 2 && mousePos.x <= centerX + centerAreaWidth / 2 &&
                mousePos.y >= centerY - centerAreaHeight / 2 && mousePos.y <= centerY + centerAreaHeight / 2;
    }
}

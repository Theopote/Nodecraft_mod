package com.nodecraft.gui.editor.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.registry.NodeRegistry;

import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;

/**
 * ImGui节点编辑器的菜单和弹窗UI组件
 */
public class ImGuiNodeMenus {
    private final ICanvasEditor editor;
    private final ImGuiNodeIO io;
    
    // 节点右键菜单状态
    private UUID rightClickedNodeId = null;
    
    // 节点搜索弹窗状态
    private float nodeSearchPosX = 0;
    private float nodeSearchPosY = 0;
    private boolean showNodeSearchPopup = false;
    private final ImString searchBuffer = new ImString(256);
    private boolean searchFocusRequested = false;
    
    /**
     * 构造函数
     * @param editor 节点编辑器实例
     * @param io IO组件实例
     */
    public ImGuiNodeMenus(ICanvasEditor editor, ImGuiNodeIO io) {
        this.editor = editor;
        this.io = io;
    }

    /**
     * 渲染节点上下文菜单（右键菜单）
     */
    public void renderNodeContextMenu() {
        if (ImGui.beginPopup("NodeContextMenu")) {
            try {
                if (rightClickedNodeId != null && editor.getCurrentGraph() != null) {
                    INode node = editor.getCurrentGraph().getNode(rightClickedNodeId);
                    if (node != null) {
                        // 显示节点标题
                        ImGui.text(node.getDisplayName());
                        ImGui.separator();
                        
                        // 节点可见性控制
                        boolean isVisible = editor.isNodeVisible(rightClickedNodeId);
                        String visibilityText = isVisible ? "Hide in Game" : "Show in Game";
                        String visibilityShortcut = isVisible ? "Ctrl+H" : "Ctrl+Shift+H";
                        if (ImGui.menuItem(visibilityText, visibilityShortcut)) {
                            toggleNodeVisibility(rightClickedNodeId);
                        }
                        
                        // 节点启用/禁用控制
                        boolean isDisabled = editor.isNodeDisabled(rightClickedNodeId);
                        String disableText = isDisabled ? "Enable Node" : "Disable Node";
                        String disableShortcut = isDisabled ? "Ctrl+E" : "Ctrl+Shift+D";
                        if (ImGui.menuItem(disableText, disableShortcut)) {
                            toggleNodeDisabled(rightClickedNodeId);
                        }
                        
                        ImGui.separator();
                        
                        // 复制节点
                        if (ImGui.menuItem("Duplicate Node", "Ctrl+D")) {
                            duplicateNode(rightClickedNodeId);
                        }
                        
                        // 断开所有连接
                        if (ImGui.menuItem("Disconnect All")) {
                            disconnectAllPorts(rightClickedNodeId);
                        }
                        
                        // 设置节点颜色
                        if (ImGui.beginMenu("Set Color")) {
                            try {
                                // 预定义颜色选项
                                String[] colorNames = {
                                    "Default", "Red", "Green", "Blue", "Yellow", "Purple", 
                                    "Orange", "Pink", "Cyan", "Lime", "Magenta", "Brown"
                                };
                                int[] colorValues = {
                                    ImGui.colorConvertFloat4ToU32(0.6f, 0.6f, 0.6f, 1.0f), // 默认灰色
                                    ImGui.colorConvertFloat4ToU32(0.9f, 0.3f, 0.3f, 1.0f), // 红色
                                    ImGui.colorConvertFloat4ToU32(0.3f, 0.9f, 0.3f, 1.0f), // 绿色
                                    ImGui.colorConvertFloat4ToU32(0.3f, 0.3f, 0.9f, 1.0f), // 蓝色
                                    ImGui.colorConvertFloat4ToU32(0.9f, 0.9f, 0.3f, 1.0f), // 黄色
                                    ImGui.colorConvertFloat4ToU32(0.9f, 0.3f, 0.9f, 1.0f), // 紫色
                                    ImGui.colorConvertFloat4ToU32(1.0f, 0.6f, 0.2f, 1.0f), // 橙色
                                    ImGui.colorConvertFloat4ToU32(1.0f, 0.4f, 0.7f, 1.0f), // 粉色
                                    ImGui.colorConvertFloat4ToU32(0.2f, 0.8f, 0.9f, 1.0f), // 青色
                                    ImGui.colorConvertFloat4ToU32(0.5f, 1.0f, 0.2f, 1.0f), // 柠檬绿
                                    ImGui.colorConvertFloat4ToU32(0.8f, 0.2f, 0.8f, 1.0f), // 洋红色
                                    ImGui.colorConvertFloat4ToU32(0.6f, 0.4f, 0.2f, 1.0f)  // 棕色
                                };
                                
                                for (int i = 0; i < colorNames.length; i++) {
                                    if (ImGui.menuItem(colorNames[i])) {
                                        setNodeColor(rightClickedNodeId, colorValues[i]);
                                    }
                                }
                                
                                ImGui.separator();
                                
                                // 重置颜色选项
                                if (ImGui.menuItem("Reset Color")) {
                                    resetNodeColor(rightClickedNodeId);
                                }
                            } finally {
                                ImGui.endMenu();
                            }
                        }
                        
                        ImGui.separator();
                        
                        // 删除节点
                        if (ImGui.menuItem("Delete", "Del")) {
                            deleteNode(rightClickedNodeId);
                        }
                    }
                }
            } finally {
                ImGui.endPopup();
                
                // 关闭菜单后清除引用
                if (!ImGui.isPopupOpen("NodeContextMenu")) {
                    rightClickedNodeId = null;
                }
            }
        }
    }
    
    /**
     * 渲染节点搜索弹窗 — 从 NodeRegistry 动态获取节点列表，支持模糊搜索
     */
    public void renderNodeSearchPopup() {
        if (showNodeSearchPopup) {
            // 设置弹窗位置和大小
            ImVec2 canvasPos = ImGui.getWindowPos();
            float popupX = canvasPos.x + nodeSearchPosX * editor.getCanvasZoom() + editor.getCanvasOffsetX();
            float popupY = canvasPos.y + nodeSearchPosY * editor.getCanvasZoom() + editor.getCanvasOffsetY();
            
            ImGui.setNextWindowPos(popupX, popupY);
            ImGui.setNextWindowSize(320, 450);
            
            // 打开模态弹窗
            ImGui.openPopup("Node Search");
            
            if (ImGui.beginPopupModal("Node Search", ImGuiWindowFlags.AlwaysAutoResize)) {
                try {
                    ImGui.text("搜索并添加节点:");
                    ImGui.separator();
                    
                    // 搜索输入框
                    if (searchFocusRequested) {
                        ImGui.setKeyboardFocusHere();
                        searchFocusRequested = false;
                    }
                    ImGui.inputText("##search", searchBuffer);
                    String filter = searchBuffer.get().toLowerCase().trim();
                    
                    ImGui.separator();
                    
                    // 从 NodeRegistry 获取所有节点
                    NodeRegistry registry = NodeRegistry.getInstance();
                    List<String> allNodeIds = registry.getAllNodeIds();
                    
                    // 按分类分组，筛选匹配的节点
                    String lastCategory = null;
                    int displayedCount = 0;
                    
                    // 排序后遍历
                    List<String> sortedIds = new ArrayList<>(allNodeIds);
                    sortedIds.sort(String::compareTo);
                    
                    ImGui.beginChild("NodeList", 300, 340, true);
                    for (String nodeId : sortedIds) {
                        NodeInfo info = registry.getNodeInfo(nodeId);
                        if (info == null) continue;
                        
                        String displayName = info.getDisplayName();
                        String categoryId = info.getCategoryId();
                        
                        // 模糊搜索：匹配节点ID、显示名、分类
                        if (!filter.isEmpty()) {
                            boolean matches = nodeId.toLowerCase().contains(filter)
                                || (displayName != null && displayName.toLowerCase().contains(filter))
                                || (categoryId != null && categoryId.toLowerCase().contains(filter));
                            if (!matches) continue;
                        }
                        
                        // 分类标题
                        if (categoryId != null && !categoryId.equals(lastCategory)) {
                            if (lastCategory != null) {
                                ImGui.spacing();
                            }
                            ImGui.textColored(0.6f, 0.8f, 1.0f, 1.0f, "\u25b6 " + categoryId);
                            ImGui.separator();
                            lastCategory = categoryId;
                        }
                        
                        // 节点按钮
                        String label = (displayName != null ? displayName : nodeId);
                        if (ImGui.selectable("  " + label + "##" + nodeId)) {
                            editor.addNode(nodeId, nodeSearchPosX, nodeSearchPosY);
                            showNodeSearchPopup = false;
                            searchBuffer.set("");
                            ImGui.closeCurrentPopup();
                        }
                        
                        // 悬停提示显示节点ID
                        if (ImGui.isItemHovered()) {
                            ImGui.beginTooltip();
                            ImGui.text("ID: " + nodeId);
                            if (info.getDescription() != null) {
                                ImGui.text(info.getDescription());
                            }
                            ImGui.endTooltip();
                        }
                        
                        displayedCount++;
                    }
                    
                    if (displayedCount == 0) {
                        ImGui.textDisabled("没有找到匹配的节点");
                    }
                    ImGui.endChild();
                    
                    ImGui.separator();
                    
                    if (ImGui.button("取消", 300, 28)) {
                        showNodeSearchPopup = false;
                        searchBuffer.set("");
                        ImGui.closeCurrentPopup();
                    }
                } finally {
                    ImGui.endPopup();
                }
            }
            
            // 如果弹窗关闭，重置状态
            if (!ImGui.isPopupOpen("Node Search")) {
                showNodeSearchPopup = false;
            }
        }
    }
    
    /**
     * 处理节点上下文菜单（右键点击）
     * @param mouseX 鼠标X坐标
     * @param mouseY 鼠标Y坐标
     */
    public void handleNodeRightClick(float mouseX, float mouseY) {
        if (editor.getCurrentGraph() == null) return;
        
        // 找出点击的是哪个节点
        UUID clickedNodeId = editor.getNodeIdUnderMouse(mouseX, mouseY);
        if (clickedNodeId != null) {
            rightClickedNodeId = clickedNodeId;
            ImGui.openPopup("NodeContextMenu");
            NodeCraft.LOGGER.debug("节点右键菜单打开, 节点ID: {}", rightClickedNodeId);
        }
    }
    
    /**
     * 请求在指定位置打开节点搜索
     * @param x 世界坐标X
     * @param y 世界坐标Y
     */
    public void requestNodeSearch(float x, float y) {
        nodeSearchPosX = x;
        nodeSearchPosY = y;
        showNodeSearchPopup = true;
        searchBuffer.set("");
        searchFocusRequested = true;
        NodeCraft.LOGGER.debug("节点搜索请求，位置: ({}, {})", x, y);
    }
    
    /**
     * 删除节点
     * @param nodeId 节点ID
     */
    private void deleteNode(UUID nodeId) {
        if (editor.getCurrentGraph() == null || nodeId == null) return;

        Set<UUID> selectedNodeIds = editor.getSelectedNodeIds();
        boolean shouldDeleteSelection = selectedNodeIds.size() > 1 && selectedNodeIds.contains(nodeId);

        if (shouldDeleteSelection) {
            int count = selectedNodeIds.size();
            boolean deleted = editor.deleteSelectedNodes();
            if (deleted) {
                NodeCraft.LOGGER.info("已通过右键菜单删除 {} 个选中节点", count);
            }
            return;
        }

        INode node = editor.getCurrentGraph().getNode(nodeId);
        if (node == null) {
            return;
        }

        editor.clearSelectedNodes();
        editor.setSelectedNodeId(nodeId);

        boolean deleted = editor.deleteSelectedNodes();
        if (deleted) {
            NodeCraft.LOGGER.info("已通过右键菜单删除节点: {}", node.getDisplayName());
        }
    }

    /**
     * 设置节点颜色
     * @param nodeId 节点ID
     * @param color 颜色值
     */
    private void setNodeColor(UUID nodeId, int color) {
        if (nodeId == null || editor.getCurrentGraph() == null) {
            NodeCraft.LOGGER.warn("无法设置节点颜色：节点ID为空或图表为空");
            return;
        }

        INode node = editor.getCurrentGraph().getNode(nodeId);
        if (node == null) {
            NodeCraft.LOGGER.warn("无法设置节点颜色：找不到节点 {}", nodeId);
            return;
        }

        // 设置节点的自定义颜色
        editor.setNodeCustomColor(nodeId, color);
        
        NodeCraft.LOGGER.info("已设置节点 {} ({}) 的颜色为 {}", 
            node.getDisplayName(), nodeId, String.format("0x%08X", color));
    }
    
    /**
     * 重置节点颜色为默认
     * @param nodeId 节点ID
     */
    private void resetNodeColor(UUID nodeId) {
        if (nodeId == null || editor.getCurrentGraph() == null) {
            NodeCraft.LOGGER.warn("无法重置节点颜色：节点ID为空或图表为空");
            return;
        }

        INode node = editor.getCurrentGraph().getNode(nodeId);
        if (node == null) {
            NodeCraft.LOGGER.warn("无法重置节点颜色：找不到节点 {}", nodeId);
            return;
        }

        // 移除节点的自定义颜色
        editor.removeNodeCustomColor(nodeId);
        
        NodeCraft.LOGGER.info("已重置节点 {} ({}) 的颜色为默认", 
            node.getDisplayName(), nodeId);
    }
    
    /**
     * 复制节点
     * @param nodeId 要复制的节点ID
     */
    private void duplicateNode(UUID nodeId) {
        if (editor.getCurrentGraph() == null || nodeId == null) return;
        
        try {
            // 先将要复制的节点设置为选中状态
            if (editor instanceof ImGuiNodeEditor imguiEditor) {
                // 清除当前选择，然后选中要复制的节点
                imguiEditor.clearSelectedNodes();
                imguiEditor.setSelectedNodeId(nodeId);
                // 调用编辑器的复制节点方法
                boolean success = imguiEditor.duplicateSelectedNode();
                if (success) {
                    NodeCraft.LOGGER.info("通过菜单复制节点成功");
                } else {
                    NodeCraft.LOGGER.error("通过菜单复制节点失败");
                }
            } else {
                // 使用原来的方法进行复制（不应该走到这里，但为了健壮性而保留）
                INode sourceNode = editor.getCurrentGraph().getNode(nodeId);
                if (sourceNode != null) {
                    NodePosition pos = editor.getNodePosition(nodeId);
                    if (pos != null) {
                        // 创建新节点，位置稍微偏移
                        String typeId = sourceNode.getTypeId();
                        float newX = pos.x + 30; // 水平偏移30单位
                        float newY = pos.y;      // 垂直位置保持不变
                        
                        try {
                            INode newNode = editor.addNode(typeId, newX, newY);
                            if (newNode != null) {
                                NodeCraft.LOGGER.info("节点复制成功");
                            } else {
                                NodeCraft.LOGGER.error("无法创建新节点");
                            }
                        } catch (Exception e) {
                            NodeCraft.LOGGER.error("复制节点时出错: {}", e.getMessage());
                            // 尝试使用备选方法
                            try {
                                // 提取节点类型的短名称
                                String shortName = typeId;
                                if (typeId.contains(".")) {
                                    shortName = typeId.substring(typeId.lastIndexOf(".") + 1);
                                }
                                
                                // 尝试不同的前缀
                                String[] commonPrefixes = {"data.", "logic.", "math.", "flow.", "io."};
                                for (String prefix : commonPrefixes) {
                                    String alternativeId = prefix + shortName;
                                    try {
                                        INode newNode = editor.addNode(alternativeId, newX, newY);
                                        if (newNode != null) {
                                            NodeCraft.LOGGER.info("使用替代类型复制节点成功: {} -> {}", 
                                                typeId, alternativeId);
                                            break;
                                        }
                                    } catch (Exception ex) {
                                        // 继续尝试下一个前缀
                                    }
                                }
                            } catch (Exception ex) {
                                NodeCraft.LOGGER.error("备选复制方法也失败了: {}", ex.getMessage());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            NodeCraft.LOGGER.error("尝试复制节点时出错: {}", e.getMessage());
        }
    }
    
    /**
     * 断开节点的所有连接
     * @param nodeId 节点ID
     */
    private void disconnectAllPorts(UUID nodeId) {
        if (editor.getCurrentGraph() == null || nodeId == null) return;
        
        INode node = editor.getCurrentGraph().getNode(nodeId);
        if (node != null) {
            int count = 0;
            
            // 断开所有输入端口连接
            for (IPort inputPort : node.getInputPorts()) {
                UUID connectedNodeId = editor.getCurrentGraph().getConnectedOutputNodeId(nodeId, inputPort.getId());
                if (connectedNodeId != null) {
                    String connectedPortId = editor.getCurrentGraph().getConnectedOutputPortId(nodeId, inputPort.getId());
                    editor.disconnectPorts(connectedNodeId, connectedPortId, nodeId, inputPort.getId());
                    count++;
                }
            }
            
            // 断开所有输出端口连接
            for (IPort outputPort : node.getOutputPorts()) {
                // 获取连接到此输出端口的所有节点
                java.util.Map<UUID, String> connectedInputs = editor.getCurrentGraph().getConnectedInputs(nodeId, outputPort.getId());
                if (connectedInputs != null) {
                    for (java.util.Map.Entry<UUID, String> entry : connectedInputs.entrySet()) {
                        UUID targetNodeId = entry.getKey();
                        String targetPortId = entry.getValue();
                        editor.disconnectPorts(nodeId, outputPort.getId(), targetNodeId, targetPortId);
                        count++;
                    }
                }
            }
            
            NodeCraft.LOGGER.info("已断开节点 {} 的所有连接，总计: {}", node.getDisplayName(), count);
        }
    }

    /**
     * 切换节点可见性
     * @param nodeId 节点ID
     */
    private void toggleNodeVisibility(UUID nodeId) {
        if (editor.getCurrentGraph() == null || nodeId == null) return;
        
        INode node = editor.getCurrentGraph().getNode(nodeId);
        if (node != null) {
            boolean isVisible = editor.isNodeVisible(nodeId);
            editor.setNodeVisible(nodeId, !isVisible);
            NodeCraft.LOGGER.info("已切换节点 {} 的可见性为 {}", node.getDisplayName(), !isVisible);
        }
    }

    /**
     * 切换节点启用/禁用状态
     * @param nodeId 节点ID
     */
    private void toggleNodeDisabled(UUID nodeId) {
        if (editor.getCurrentGraph() == null || nodeId == null) return;
        
        INode node = editor.getCurrentGraph().getNode(nodeId);
        if (node != null) {
            boolean isDisabled = editor.isNodeDisabled(nodeId);
            editor.setNodeDisabled(nodeId, !isDisabled);
            NodeCraft.LOGGER.info("已切换节点 {} 的启用/禁用状态为 {}", node.getDisplayName(), !isDisabled);
        }
    }
} 
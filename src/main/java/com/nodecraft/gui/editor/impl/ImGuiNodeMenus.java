package com.nodecraft.gui.editor.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.gui.recommendation.NodeRecommendation;
import com.nodecraft.gui.recommendation.NodeRecommendationContext;
import com.nodecraft.gui.recommendation.NodeRecommendations;
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
    private UUID cachedMenuRecommendationNodeId = null;
    private NodeRecommendationContext cachedMenuRecommendationContext;
    private List<NodeRecommendation> cachedMenuRecommendations = List.of();
    
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
                        Set<UUID> contextTargets = getContextTargetNodeIds(rightClickedNodeId);
                        boolean isBatchOperation = contextTargets.size() > 1;

                        // 显示节点标题
                        if (isBatchOperation) {
                            ImGui.text(node.getDisplayName() + " (" + contextTargets.size() + " selected)");
                        } else {
                            ImGui.text(node.getDisplayName());
                        }
                        ImGui.separator();
                        
                        // 节点可见性控制
                        boolean allVisible = areAllNodesVisible(contextTargets);
                        String visibilityText = isBatchOperation
                                ? (allVisible ? "Hide Selected in Game" : "Show Selected in Game")
                                : (allVisible ? "Hide in Game" : "Show in Game");
                        String visibilityShortcut = allVisible ? "Ctrl+H" : "Ctrl+Shift+H";
                        if (ImGui.menuItem(visibilityText, visibilityShortcut)) {
                            setNodesVisibility(contextTargets, !allVisible);
                        }
                        
                        // 节点启用/禁用控制
                        boolean allDisabled = areAllNodesDisabled(contextTargets);
                        String disableText = isBatchOperation
                                ? (allDisabled ? "Enable Selected" : "Disable Selected")
                                : (allDisabled ? "Enable Node" : "Disable Node");
                        String disableShortcut = allDisabled ? "Ctrl+E" : "Ctrl+Shift+D";
                        if (ImGui.menuItem(disableText, disableShortcut)) {
                            setNodesDisabled(contextTargets, !allDisabled);
                        }
                        
                        ImGui.separator();

                        if (contextTargets.size() > 1) {
                            if (ImGui.menuItem("Create Subgraph From Selection")) {
                                createSubgraphFromTargets(contextTargets, rightClickedNodeId);
                            }
                            ImGui.separator();
                        } else if (isSubgraphNode(node)) {
                            if (ImGui.menuItem("Open Subgraph")) {
                                openSubgraphTarget(rightClickedNodeId);
                            }
                            if (ImGui.menuItem("Dissolve Subgraph")) {
                                dissolveSubgraphTarget(rightClickedNodeId);
                            }
                            ImGui.separator();
                        }

                        if (ImGui.menuItem(isBatchOperation ? "Copy Selected" : "Copy", "Ctrl+C")) {
                            copyNodes(contextTargets, rightClickedNodeId);
                        }
                        
                        // 复制节点
                        if (ImGui.menuItem(isBatchOperation ? "Duplicate Selected" : "Duplicate Node", "Ctrl+D")) {
                            duplicateNodes(contextTargets, rightClickedNodeId);
                        }
                        
                        // 断开所有连接
                        if (ImGui.menuItem(isBatchOperation ? "Disconnect Selected" : "Disconnect All")) {
                            disconnectAllPorts(contextTargets);
                        }

                        if (contextTargets.size() > 1 && ImGui.beginMenu("Align Selected")) {
                            try {
                                if (ImGui.menuItem("Align Left")) {
                                    editor.alignNodes(contextTargets, ICanvasEditor.NodeAlignmentAction.ALIGN_LEFT);
                                }
                                if (ImGui.menuItem("Align Center")) {
                                    editor.alignNodes(contextTargets, ICanvasEditor.NodeAlignmentAction.ALIGN_CENTER);
                                }
                                boolean canDistribute = contextTargets.size() >= 3;
                                if (!canDistribute) {
                                    ImGui.beginDisabled();
                                }
                                if (ImGui.menuItem("Distribute") && canDistribute) {
                                    editor.alignNodes(contextTargets, ICanvasEditor.NodeAlignmentAction.DISTRIBUTE_HORIZONTAL);
                                }
                                if (!canDistribute) {
                                    ImGui.endDisabled();
                                }
                            } finally {
                                ImGui.endMenu();
                            }
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
                                        setNodesColor(contextTargets, colorValues[i]);
                                    }
                                }
                                
                                ImGui.separator();
                                
                                // 重置颜色选项
                                if (ImGui.menuItem("Reset Color")) {
                                    resetNodesColor(contextTargets);
                                }
                            } finally {
                                ImGui.endMenu();
                            }
                        }
                        
                        ImGui.separator();

                        if (!isBatchOperation && editor.getCurrentGraph() != null) {
                            renderDownstreamRecommendations(node);
                        }

                        ImGui.separator();
                        
                        // 删除节点
                        if (ImGui.menuItem("Delete", "Del")) {
                            deleteNodes(contextTargets, rightClickedNodeId);
                        }
                    }
                }
            } finally {
                ImGui.endPopup();
            }
        } else {
            // 弹窗已关闭，安全清除引用
            rightClickedNodeId = null;
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

    private void deleteNodes(Set<UUID> nodeIds, UUID contextNodeId) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            return;
        }
        if (nodeIds.size() == 1) {
            deleteNode(contextNodeId != null ? contextNodeId : nodeIds.iterator().next());
            return;
        }

        Set<UUID> backupSelection = new HashSet<>(editor.getSelectedNodeIds());
        UUID backupPrimary = editor.getSelectedNodeId();

        try {
            editor.clearSelectedNodes();
            editor.getSelectedNodeIds().addAll(nodeIds);
            if (contextNodeId != null && nodeIds.contains(contextNodeId)) {
                editor.setSelectedNodeId(contextNodeId);
            } else {
                editor.setSelectedNodeId(nodeIds.iterator().next());
            }

            int count = nodeIds.size();
            if (editor.deleteSelectedNodes()) {
                NodeCraft.LOGGER.info("已通过右键菜单批量删除 {} 个节点", count);
            }
        } catch (Exception e) {
            NodeCraft.LOGGER.error("批量删除节点失败: {}", e.getMessage(), e);
            editor.clearSelectedNodes();
            editor.getSelectedNodeIds().addAll(backupSelection);
            editor.setSelectedNodeId(backupPrimary);
        }
    }

    /**
     * 设置节点颜色
     */
    private void createSubgraphFromTargets(Set<UUID> nodeIds, UUID contextNodeId) {
        if (nodeIds == null || nodeIds.size() <= 1) {
            return;
        }

        Set<UUID> backupSelection = new HashSet<>(editor.getSelectedNodeIds());
        UUID backupPrimary = editor.getSelectedNodeId();

        try {
            editor.clearSelectedNodes();
            editor.getSelectedNodeIds().addAll(nodeIds);
            if (contextNodeId != null && nodeIds.contains(contextNodeId)) {
                editor.setSelectedNodeId(contextNodeId);
            } else {
                editor.setSelectedNodeId(nodeIds.iterator().next());
            }

            if (!editor.createSubgraphFromSelection()) {
                NodeCraft.LOGGER.warn("Failed to create subgraph from {} selected nodes", nodeIds.size());
                editor.clearSelectedNodes();
                editor.getSelectedNodeIds().addAll(backupSelection);
                editor.setSelectedNodeId(backupPrimary);
            }
        } catch (Exception e) {
            NodeCraft.LOGGER.error("Failed to create subgraph from context selection: {}", e.getMessage(), e);
            editor.clearSelectedNodes();
            editor.getSelectedNodeIds().addAll(backupSelection);
            editor.setSelectedNodeId(backupPrimary);
        }
    }

    private void dissolveSubgraphTarget(UUID nodeId) {
        if (nodeId == null) {
            return;
        }

        Set<UUID> backupSelection = new HashSet<>(editor.getSelectedNodeIds());
        UUID backupPrimary = editor.getSelectedNodeId();

        try {
            editor.clearSelectedNodes();
            editor.getSelectedNodeIds().add(nodeId);
            editor.setSelectedNodeId(nodeId);
            if (!editor.dissolveSelectedSubgraph()) {
                editor.clearSelectedNodes();
                editor.getSelectedNodeIds().addAll(backupSelection);
                editor.setSelectedNodeId(backupPrimary);
            }
        } catch (Exception e) {
            NodeCraft.LOGGER.error("Failed to dissolve subgraph from context menu: {}", e.getMessage(), e);
            editor.clearSelectedNodes();
            editor.getSelectedNodeIds().addAll(backupSelection);
            editor.setSelectedNodeId(backupPrimary);
        }
    }

    private void openSubgraphTarget(UUID nodeId) {
        if (nodeId == null) {
            return;
        }

        Set<UUID> backupSelection = new HashSet<>(editor.getSelectedNodeIds());
        UUID backupPrimary = editor.getSelectedNodeId();

        try {
            editor.clearSelectedNodes();
            editor.getSelectedNodeIds().add(nodeId);
            editor.setSelectedNodeId(nodeId);
            if (!editor.openSelectedSubgraph()) {
                editor.clearSelectedNodes();
                editor.getSelectedNodeIds().addAll(backupSelection);
                editor.setSelectedNodeId(backupPrimary);
            }
        } catch (Exception e) {
            NodeCraft.LOGGER.error("Failed to open subgraph from context menu: {}", e.getMessage(), e);
            editor.clearSelectedNodes();
            editor.getSelectedNodeIds().addAll(backupSelection);
            editor.setSelectedNodeId(backupPrimary);
        }
    }

    private static boolean isSubgraphNode(INode node) {
        if (node == null || node.getTypeId() == null) {
            return false;
        }
        String canonicalId = NodeRegistry.getInstance().resolveCanonicalNodeId(node.getTypeId());
        return "utilities.organization.subgraph".equals(canonicalId);
    }

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

    private void setNodesColor(Set<UUID> nodeIds, int color) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            return;
        }
        for (UUID nodeId : nodeIds) {
            setNodeColor(nodeId, color);
        }
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

    private void resetNodesColor(Set<UUID> nodeIds) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            return;
        }
        for (UUID nodeId : nodeIds) {
            resetNodeColor(nodeId);
        }
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
                        String typeId = NodeRegistry.getInstance().resolveCanonicalNodeId(sourceNode.getTypeId());
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
                                
                                String alternativeId = NodeRegistry.getInstance().resolveCanonicalNodeId(typeId);
                                if (!typeId.equals(alternativeId)) {
                                    try {
                                        INode newNode = editor.addNode(alternativeId, newX, newY);
                                        if (newNode != null) {
                                            NodeCraft.LOGGER.info("使用显式兼容类型复制节点成功: {} -> {}",
                                                typeId, alternativeId);
                                        }
                                    } catch (Exception ex) {
                                        NodeCraft.LOGGER.debug("使用显式兼容类型复制节点失败: {}", ex.getMessage());
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

    private void duplicateNodes(Set<UUID> nodeIds, UUID contextNodeId) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            return;
        }
        if (nodeIds.size() == 1) {
            duplicateNode(contextNodeId != null ? contextNodeId : nodeIds.iterator().next());
            return;
        }

        Set<UUID> backupSelection = new HashSet<>(editor.getSelectedNodeIds());
        UUID backupPrimary = editor.getSelectedNodeId();

        try {
            editor.clearSelectedNodes();
            editor.getSelectedNodeIds().addAll(nodeIds);

            UUID anchorNodeId = contextNodeId != null && nodeIds.contains(contextNodeId)
                    ? contextNodeId
                    : nodeIds.iterator().next();
            editor.setSelectedNodeId(anchorNodeId);

            boolean copied = editor.copySelectedNodes();
            if (!copied) {
                NodeCraft.LOGGER.warn("批量复制节点失败：无法复制选中集合");
                return;
            }

            NodePosition anchorPos = editor.getNodePosition(anchorNodeId);
            if (anchorPos != null) {
                editor.pasteNodesAtPosition(anchorPos.x + 30.0f, anchorPos.y);
            } else {
                editor.pasteNodesAtPosition(30.0f, 0.0f);
            }
            NodeCraft.LOGGER.info("已通过右键菜单批量复制 {} 个节点", nodeIds.size());
        } catch (Exception e) {
            NodeCraft.LOGGER.error("批量复制节点失败: {}", e.getMessage(), e);
            editor.clearSelectedNodes();
            editor.getSelectedNodeIds().addAll(backupSelection);
            editor.setSelectedNodeId(backupPrimary);
        }
    }

    private void copyNodes(Set<UUID> nodeIds, UUID contextNodeId) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            return;
        }
        Set<UUID> backupSelection = new HashSet<>(editor.getSelectedNodeIds());
        UUID backupPrimary = editor.getSelectedNodeId();

        try {
            editor.clearSelectedNodes();
            editor.getSelectedNodeIds().addAll(nodeIds);
            if (contextNodeId != null && nodeIds.contains(contextNodeId)) {
                editor.setSelectedNodeId(contextNodeId);
            } else {
                editor.setSelectedNodeId(nodeIds.iterator().next());
            }

            if (editor.copySelectedNodes()) {
                NodeCraft.LOGGER.info("已通过右键菜单复制 {} 个节点", nodeIds.size());
            }
        } finally {
            editor.clearSelectedNodes();
            editor.getSelectedNodeIds().addAll(backupSelection);
            editor.setSelectedNodeId(backupPrimary);
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

    private void disconnectAllPorts(Set<UUID> nodeIds) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            return;
        }
        for (UUID nodeId : nodeIds) {
            disconnectAllPorts(nodeId);
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

    private void setNodesVisibility(Set<UUID> nodeIds, boolean visible) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            return;
        }
        for (UUID nodeId : nodeIds) {
            editor.setNodeVisible(nodeId, visible);
        }
        NodeCraft.LOGGER.info("已批量设置 {} 个节点可见性为 {}", nodeIds.size(), visible);
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

    private void setNodesDisabled(Set<UUID> nodeIds, boolean disabled) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            return;
        }
        for (UUID nodeId : nodeIds) {
            editor.setNodeDisabled(nodeId, disabled);
        }
        NodeCraft.LOGGER.info("已批量设置 {} 个节点禁用状态为 {}", nodeIds.size(), disabled);
    }

    private boolean areAllNodesVisible(Set<UUID> nodeIds) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            return false;
        }
        for (UUID nodeId : nodeIds) {
            if (!editor.isNodeVisible(nodeId)) {
                return false;
            }
        }
        return true;
    }

    private boolean areAllNodesDisabled(Set<UUID> nodeIds) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            return false;
        }
        for (UUID nodeId : nodeIds) {
            if (!editor.isNodeDisabled(nodeId)) {
                return false;
            }
        }
        return true;
    }

    private Set<UUID> getContextTargetNodeIds(UUID clickedNodeId) {
        Set<UUID> selected = editor.getSelectedNodeIds();
        if (selected != null && selected.size() > 1 && clickedNodeId != null && selected.contains(clickedNodeId)) {
            return new HashSet<>(selected);
        }

        Set<UUID> single = new HashSet<>();
        if (clickedNodeId != null) {
            single.add(clickedNodeId);
        }
        return single;
    }

    private void renderDownstreamRecommendations(INode node) {
        if (node == null || editor.getCurrentGraph() == null) {
            return;
        }

        UUID nodeId = node.getId();
        if (!nodeId.equals(cachedMenuRecommendationNodeId) || cachedMenuRecommendationContext == null) {
            NodeRecommendations.get().initialize();
            NodePosition nodePos = editor.getNodePosition(nodeId);
            float placementX = nodePos != null ? (float) nodePos.x : (float) node.getPositionX();
            float placementY = nodePos != null ? (float) nodePos.y : (float) node.getPositionY();
            cachedMenuRecommendationContext = NodeRecommendationContext.forSelectedNode(
                    nodeId,
                    placementX,
                    placementY,
                    8);
            cachedMenuRecommendations = NodeRecommendations.get()
                    .recommend(editor.getCurrentGraph(), cachedMenuRecommendationContext);
            cachedMenuRecommendationNodeId = nodeId;
        }

        if (cachedMenuRecommendations.isEmpty()) {
            return;
        }

        if (ImGui.beginMenu("添加下游节点")) {
            try {
                for (NodeRecommendation recommendation : cachedMenuRecommendations) {
                    if (ImGui.menuItem(recommendation.displayName())) {
                        if (editor instanceof ImGuiNodeEditor imguiEditor) {
                            imguiEditor.applyRecommendation(cachedMenuRecommendationContext, recommendation);
                        }
                    }
                }
            } finally {
                ImGui.endMenu();
            }
        }
    }
}

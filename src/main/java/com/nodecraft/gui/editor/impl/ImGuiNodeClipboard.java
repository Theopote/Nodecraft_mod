package com.nodecraft.gui.editor.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.nodecraft.core.NodeCraft;
import com.nodecraft.core.exception.NodeValidationException;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.graph.NodeGraph.Connection;
import com.nodecraft.nodesystem.registry.NodeRegistry;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;

/**
 * 节点图剪贴板管理器，处理节点的复制、剪切和粘贴
 */
public class ImGuiNodeClipboard implements ClipboardOwner {
    
    private final ICanvasEditor editor;
    private final Gson gson;
    private static final String CLIPBOARD_FORMAT = "application/nodecraft-nodes+json";
    
    // 内部剪贴板，用于存储最后一次复制的数据，避免使用系统剪贴板
    private String internalClipboardContent = null;
    
    public ImGuiNodeClipboard(ICanvasEditor editor) {
        this.editor = editor;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }
    
    /**
     * 复制选中的节点到剪贴板
     * @return 是否复制成功
     */
    public boolean copySelectedNodes() {
        Set<UUID> selectedNodeIds = editor.getSelectedNodeIds();
        if (selectedNodeIds.isEmpty()) {
            NodeCraft.LOGGER.warn("没有选中的节点可复制");
            return false;
        }
        
        try {
            // 获取当前节点图
            NodeGraph graph = editor.getCurrentGraph();
            NodeRegistry registry = NodeRegistry.getInstance();
            if (graph == null) {
                NodeCraft.LOGGER.error("复制失败：当前没有节点图");
                return false;
            }
            
            // 创建节点数据列表
            List<NodeData> nodes = new ArrayList<>();
            Map<UUID, Integer> nodeIndexMap = new HashMap<>(); // 用于映射节点ID到索引
            
            NodeCraft.LOGGER.debug("开始收集 {} 个选中节点的数据", selectedNodeIds.size());
            
            int index = 0;
            for (UUID nodeId : selectedNodeIds) {
                INode node = graph.getNode(nodeId);
                if (node == null) {
                    NodeCraft.LOGGER.warn("跳过无效节点ID: {}", nodeId);
                    continue;
                }
                
                // 获取节点位置
                NodePosition pos = editor.getNodePosition(nodeId);
                if (pos == null) {
                    NodeCraft.LOGGER.warn("跳过没有位置信息的节点: {}", node.getDisplayName());
                    continue;
                }
                
                // 创建节点数据
                NodeData nodeData = new NodeData();
                nodeData.id = nodeId.toString();
                nodeData.typeId = registry.resolveCanonicalNodeId(node.getTypeId());
                nodeData.x = pos.x;
                nodeData.y = pos.y;
                nodeData.state = node.getNodeState();
                
                // 添加到列表并记录索引
                nodes.add(nodeData);
                nodeIndexMap.put(nodeId, index);
                
                NodeCraft.LOGGER.debug("收集节点 #{}: {} (类型: {}, 位置: ({}, {}))", 
                                     index, node.getDisplayName(), nodeData.typeId, pos.x, pos.y);
                index++;
            }
            
            if (nodes.isEmpty()) {
                NodeCraft.LOGGER.error("复制失败：没有有效的节点数据");
                return false;
            }
            
            // 创建连接数据列表
            List<ConnectionData> connections = new ArrayList<>();
            
            // 遍历所有连接，筛选出选中节点之间的连接
            for (Connection conn : graph.getConnections()) {
                UUID sourceId = conn.sourceNode.getId();
                UUID targetId = conn.targetNode.getId();
                
                // 只保留两端都在选中节点中的连接
                if (selectedNodeIds.contains(sourceId) && selectedNodeIds.contains(targetId)) {
                    // 确保源节点和目标节点都有有效的索引
                    if (!nodeIndexMap.containsKey(sourceId) || !nodeIndexMap.containsKey(targetId)) {
                        continue;
                    }
                    
                    ConnectionData connData = new ConnectionData();
                    connData.sourceNodeIndex = nodeIndexMap.get(sourceId);
                    connData.sourcePortId = conn.sourcePort.getId();
                    connData.targetNodeIndex = nodeIndexMap.get(targetId);
                    connData.targetPortId = conn.targetPort.getId();
                    connections.add(connData);
                    
                    NodeCraft.LOGGER.debug("收集连接: 节点{}({}) -> 节点{}({})", 
                                         connData.sourceNodeIndex, connData.sourcePortId,
                                         connData.targetNodeIndex, connData.targetPortId);
                }
            }
            
            // 创建剪贴板数据
            ClipboardData clipboardData = new ClipboardData();
            clipboardData.format = CLIPBOARD_FORMAT;
            clipboardData.nodes = nodes;
            clipboardData.connections = connections;
            
            // 序列化为JSON
            String json = gson.toJson(clipboardData);
            
            // 打印JSON数据（调试用）
            NodeCraft.LOGGER.debug("生成的JSON数据: {}", json.length() > 200 ? json.substring(0, 200) + "..." : json);
            
            // 存储到内部剪贴板
            internalClipboardContent = json;
            
            // 尝试写入系统剪贴板（如果可用）
            try {
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                StringSelection selection = new StringSelection(json);
                clipboard.setContents(selection, this);
            } catch (Exception e) {
                // 系统剪贴板不可用，但我们已经保存到内部剪贴板，所以继续
                NodeCraft.LOGGER.info("系统剪贴板不可用，使用内部剪贴板: {}", e.getMessage());
            }
            
            NodeCraft.LOGGER.info("已复制 {} 个节点和 {} 个连接到剪贴板", nodes.size(), connections.size());
            return true;
        } catch (Exception e) {
            NodeCraft.LOGGER.error("复制节点时出错: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 剪切选中的节点到剪贴板
     * @return 是否剪切成功
     */
    public boolean cutSelectedNodes() {
        // 先复制，然后删除
        boolean copied = copySelectedNodes();
        if (!copied) {
            NodeCraft.LOGGER.error("剪切失败：无法复制选中的节点");
            return false;
        }
        
        try {
            // 使用deleteSelectedNodes方法进行删除，该方法已经包含了历史记录功能
            boolean deleted = deleteSelectedNodes();
            
            if (deleted) {
                NodeCraft.LOGGER.info("已剪切节点到剪贴板");
                return true;
            } else {
                NodeCraft.LOGGER.error("剪切操作部分失败：节点已复制到剪贴板，但无法删除节点");
                return false;
            }
        } catch (Exception e) {
            NodeCraft.LOGGER.error("剪切节点时出错: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 从剪贴板粘贴节点
     * @param x 粘贴位置X坐标
     * @param y 粘贴位置Y坐标
     * @return 是否粘贴成功
     */
    public boolean pasteNodes(float x, float y) {
        try {
            // 获取当前节点图
            NodeGraph graph = editor.getCurrentGraph();
            if (graph == null) {
                NodeCraft.LOGGER.error("粘贴失败：当前没有节点图");
                return false;
            }
            
            String clipboardContent;
            
            // 首先尝试从内部剪贴板获取数据
            if (internalClipboardContent != null) {
                clipboardContent = internalClipboardContent;
                NodeCraft.LOGGER.debug("从内部剪贴板读取数据");
            } else {
                // 如果内部剪贴板为空，尝试从系统剪贴板获取
                try {
                    Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                    Transferable transferable = clipboard.getContents(null);
                    
                    if (transferable == null) {
                        NodeCraft.LOGGER.error("粘贴失败：剪贴板为空");
                        return false;
                    }
                    
                    if (!transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                        NodeCraft.LOGGER.error("粘贴失败：剪贴板数据不是文本格式");
                        return false;
                    }
                    
                    clipboardContent = (String) transferable.getTransferData(DataFlavor.stringFlavor);
                    NodeCraft.LOGGER.debug("从系统剪贴板读取数据");
                } catch (Exception e) {
                    NodeCraft.LOGGER.error("从系统剪贴板读取数据失败: {}", e.getMessage());
                    return false;
                }
            }
            
            if (clipboardContent.isEmpty()) {
                NodeCraft.LOGGER.error("粘贴失败：剪贴板内容为空");
                return false;
            }
            
            // 解析JSON数据
            ClipboardData clipboardData;
            try {
                clipboardData = gson.fromJson(clipboardContent, ClipboardData.class);
                
                // 验证数据格式
                if (clipboardData == null || clipboardData.format == null || 
                    !clipboardData.format.equals(CLIPBOARD_FORMAT) || 
                    clipboardData.nodes == null || clipboardData.nodes.isEmpty()) {
                    NodeCraft.LOGGER.error("粘贴失败：剪贴板数据格式不正确");
                    return false;
                }
            } catch (Exception e) {
                NodeCraft.LOGGER.error("解析剪贴板数据失败: {}", e.getMessage(), e);
                return false;
            }
            
            // 计算偏移量，使节点位于鼠标位置
            float minX = Float.MAX_VALUE;
            float minY = Float.MAX_VALUE;
            
            for (NodeData nodeData : clipboardData.nodes) {
                minX = Math.min(minX, nodeData.x);
                minY = Math.min(minY, nodeData.y);
            }
            
            float offsetX = x - minX;
            float offsetY = y - minY;
            
            NodeCraft.LOGGER.debug("粘贴位置: ({}, {}), 偏移量: ({}, {})", x, y, offsetX, offsetY);
            
            // 创建新节点
            List<INode> newNodes = new ArrayList<>();
            Map<Integer, UUID> indexToNodeIdMap = new HashMap<>();
            Map<String, String> typeIdMap = new HashMap<>(); // 存储可能的类型ID映射
            
            // 创建节点前先扫描所有节点类型，建立可能的映射关系
            NodeRegistry registry = NodeRegistry.getInstance();
            for (int i = 0; i < clipboardData.nodes.size(); i++) {
                NodeData nodeData = clipboardData.nodes.get(i);
                String typeId = nodeData.typeId;
                
                // 尝试找到匹配的替代类型（简化版匹配，仅按名称最后部分）
                if (!typeIdMap.containsKey(typeId)) {
                    // 先假设类型存在
                    typeIdMap.put(typeId, typeId);
                    
                    try {
                        // 验证类型是否真的存在
                        registry.createNodeInstance(typeId);
                    } catch (NodeValidationException e) {
                        // 类型不存在，尝试查找替代
                        NodeCraft.LOGGER.warn("节点类型 '{}' 不存在，尝试查找替代类型", typeId);
                        
                        // 从typeId提取最后的部分（例如从data.lists.combine_lists提取combine_lists）
                        String shortName = typeId;
                        if (typeId.contains(".")) {
                            shortName = typeId.substring(typeId.lastIndexOf(".") + 1);
                        }
                        
                        String resolvedCanonicalId = registry.resolveCanonicalNodeId(typeId);
                        if (!typeId.equals(resolvedCanonicalId)) {
                            try {
                                registry.createNodeInstance(resolvedCanonicalId);
                                typeIdMap.put(typeId, resolvedCanonicalId);
                                NodeCraft.LOGGER.info("找到显式兼容类型: {} -> {}", typeId, resolvedCanonicalId);
                            } catch (Exception ex) {
                                NodeCraft.LOGGER.debug("显式兼容类型不可用: {} -> {}", typeId, resolvedCanonicalId);
                            }
                        }

                        if (typeIdMap.get(typeId).equals(typeId) && "panel".equals(shortName)) {
                            String alternativeId = "output.debug.data_inspector";
                            try {
                                registry.createNodeInstance(alternativeId);
                                typeIdMap.put(typeId, alternativeId);
                                NodeCraft.LOGGER.info("找到面板兼容类型: {} -> {}", typeId, alternativeId);
                            } catch (Exception ex) {
                                NodeCraft.LOGGER.debug("面板兼容类型不可用: {}", alternativeId);
                            }
                        }
                    }
                }
            }
            
            // 现在尝试创建节点
            for (int i = 0; i < clipboardData.nodes.size(); i++) {
                NodeData nodeData = clipboardData.nodes.get(i);
                
                // 创建新节点
                String originalTypeId = nodeData.typeId;
                String effectiveTypeId = typeIdMap.getOrDefault(originalTypeId, originalTypeId);
                float newX = nodeData.x + offsetX;
                float newY = nodeData.y + offsetY;
                
                NodeCraft.LOGGER.debug("创建节点 (原始类型: {}, 实际使用类型: {}, 位置: ({}, {}))",
                                    originalTypeId, effectiveTypeId, newX, newY);
                
                try {
                    // 使用addNodeWithState方法，不指定旧UUID，让它生成新的UUID
                    // 这里暂时不传递节点状态，因为剪贴板数据中没有存储状态信息
                    INode newNode = editor.addNodeWithState(effectiveTypeId, null, newX, newY, nodeData.state);
                    if (newNode != null) {
                        newNodes.add(newNode);
                        indexToNodeIdMap.put(i, newNode.getId());
                    } else {
                        NodeCraft.LOGGER.error("创建节点失败: {}", effectiveTypeId);
                        
                        // 尝试克隆机制作为备选方案
                        NodeCraft.LOGGER.info("尝试使用克隆机制创建节点");
                        
                        try {
                            // 在当前图中寻找同类型的节点
                            NodeGraph currentGraph = editor.getCurrentGraph();
                            if (currentGraph != null) {
                                // 寻找第一个能够克隆的节点
                                for (INode existingNode : currentGraph.getNodes()) {
                                    if (existingNode != null) {
                                        // 尝试克隆现有节点
                                        try {
                                            Class<?> nodeClass = existingNode.getClass();
                                            INode clonedNode = (INode) nodeClass.getDeclaredConstructor().newInstance();

                                            currentGraph.addNode(clonedNode);
                                            clonedNode.setNodeState(nodeData.state);
                                            editor.getNodePositions().put(clonedNode.getId(),
                                                new NodePosition(newX, newY));

                                            // 添加到结果列表
                                            newNodes.add(clonedNode);
                                            indexToNodeIdMap.put(i, clonedNode.getId());

                                            NodeCraft.LOGGER.info("通过克隆成功创建节点: {}", clonedNode.getDisplayName());
                                            break;
                                        } catch (Exception ex) {
                                            // 继续尝试下一个节点
                                        }
                                    }
                                }
                            }
                        } catch (Exception ex) {
                            NodeCraft.LOGGER.error("尝试使用克隆机制失败: {}", ex.getMessage());
                        }
                    }
                } catch (Exception e) {
                    NodeCraft.LOGGER.error("创建节点出错: {} - {}", effectiveTypeId, e.getMessage());
                    
                    // 当常规方法失败时，尝试使用克隆机制
                    NodeCraft.LOGGER.info("尝试使用克隆机制创建节点");
                    
                    try {
                        // 在当前图中寻找能够克隆的节点
                        NodeGraph editorGraph = editor.getCurrentGraph();
                        if (editorGraph != null && !editorGraph.getNodes().isEmpty()) {
                            // 获取第一个可用节点尝试克隆
                            INode existingNode = editorGraph.getNodes().getFirst();
                            if (existingNode != null) {
                                try {
                                    Class<?> nodeClass = existingNode.getClass();
                                    INode clonedNode = (INode) nodeClass.getDeclaredConstructor().newInstance();

                                    editorGraph.addNode(clonedNode);
                                    clonedNode.setNodeState(nodeData.state);
                                    editor.getNodePositions().put(clonedNode.getId(),
                                        new NodePosition(newX, newY));

                                    // 添加到结果列表
                                    newNodes.add(clonedNode);
                                    indexToNodeIdMap.put(i, clonedNode.getId());

                                    NodeCraft.LOGGER.info("通过克隆成功创建节点: {}", clonedNode.getDisplayName());
                                } catch (Exception ex) {
                                    NodeCraft.LOGGER.error("节点克隆失败: {}", ex.getMessage());
                                }
                            }
                        }
                    } catch (Exception ex) {
                        NodeCraft.LOGGER.error("备选方法创建节点失败: {}", ex.getMessage());
                    }
                }
            }
            
            NodeCraft.LOGGER.info("成功创建 {} 个节点", newNodes.size());
            
            // 创建连接
            int connectionCount = 0;
            if (clipboardData.connections != null && !newNodes.isEmpty()) {
                for (ConnectionData connData : clipboardData.connections) {
                    // 验证连接数据
                    if (connData.sourceNodeIndex < 0 || connData.sourceNodeIndex >= clipboardData.nodes.size() ||
                        connData.targetNodeIndex < 0 || connData.targetNodeIndex >= clipboardData.nodes.size() ||
                        connData.sourcePortId == null || connData.targetPortId == null) {
                        NodeCraft.LOGGER.warn("跳过无效连接数据: {} -> {}", connData.sourceNodeIndex, connData.targetNodeIndex);
                        continue;
                    }
                    
                    // 获取对应的节点ID
                    UUID sourceNodeId = indexToNodeIdMap.get(connData.sourceNodeIndex);
                    UUID targetNodeId = indexToNodeIdMap.get(connData.targetNodeIndex);
                    
                    if (sourceNodeId == null || targetNodeId == null) {
                        NodeCraft.LOGGER.warn("连接失败: 找不到对应的节点ID");
                        continue;
                    }
                    
                    // 创建连接
                    boolean connected = editor.connectPorts(
                        sourceNodeId, connData.sourcePortId,
                        targetNodeId, connData.targetPortId
                    );
                    
                    if (connected) {
                        connectionCount++;
                        NodeCraft.LOGGER.debug("创建连接: {}({}) -> {}({})", 
                                            connData.sourceNodeIndex, connData.sourcePortId,
                                            connData.targetNodeIndex, connData.targetPortId);
                    } else {
                        NodeCraft.LOGGER.warn("连接失败: {}({}) -> {}({})", 
                                           connData.sourceNodeIndex, connData.sourcePortId,
                                           connData.targetNodeIndex, connData.targetPortId);
                    }
                }
            }
            
            // 选中新粘贴的节点
            editor.clearSelectedNodes();
            for (INode node : newNodes) {
                editor.getSelectedNodeIds().add(node.getId());
            }
            
            NodeCraft.LOGGER.info("成功粘贴 {} 个节点和 {} 个连接", newNodes.size(), connectionCount);
            return !newNodes.isEmpty();
        } catch (Exception e) {
            NodeCraft.LOGGER.error("粘贴节点时出错: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 删除选中的节点
     * @return 是否删除成功
     */
    public boolean deleteSelectedNodes() {
        try {
            // 获取当前节点图
            NodeGraph graph = editor.getCurrentGraph();
            if (graph == null) return false;
            
            // 获取选中的节点
            Set<UUID> selectedNodeIds = editor.getSelectedNodeIds();
            if (selectedNodeIds.isEmpty()) {
                return false;
            }
            
            // 获取历史记录组件
            ImGuiNodeHistory history = null;
            if (editor instanceof ImGuiNodeEditor) {
                history = editor.getHistory();
            }

            List<ImGuiNodeHistory.RemovedNodeSnapshot> snapshots = new ArrayList<>();
            if (history != null && history.isRecording()) {
                for (UUID nodeId : new ArrayList<>(selectedNodeIds)) {
                    INode node = graph.getNode(nodeId);
                    if (node == null) {
                        continue;
                    }
                    NodePosition pos = editor.getNodePosition(nodeId);
                    if (pos == null) {
                        pos = new NodePosition(0, 0);
                    }
                    ImGuiNodeHistory.RemovedNodeSnapshot snapshot = history.captureRemovedNodeSnapshot(node, pos.x, pos.y);
                    if (snapshot != null) {
                        snapshots.add(snapshot);
                    }
                }
                if (!snapshots.isEmpty()) {
                    history.recordRemoveNodes(snapshots);
                }
            }
            
            // 删除选中的节点
            for (UUID nodeId : new ArrayList<>(selectedNodeIds)) {
                INode node = graph.getNode(nodeId);
                if (node != null) {
                    // 删除节点
                    graph.removeNode(nodeId);
                    editor.removeNodePosition(nodeId);
                    editor.removeSelectedNode(nodeId);
                }
            }
            
            NodeCraft.LOGGER.info("已删除选中的节点");
            return true;
        } catch (Exception e) {
            NodeCraft.LOGGER.error("删除节点时出错: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 复制单个节点（复制/粘贴的简化版本，直接在编辑器中操作）
     * @param nodeId 要复制的节点ID
     * @param offsetX X偏移量
     * @param offsetY Y偏移量
     * @return 新创建的节点，如果失败则返回null
     */
    public INode duplicateNode(UUID nodeId, float offsetX, float offsetY) {
        try {
            // 获取当前节点图
            NodeGraph graph = editor.getCurrentGraph();
            if (graph == null) return null;
            
            // 获取源节点
            INode sourceNode = graph.getNode(nodeId);
            if (sourceNode == null) return null;
            
            // 获取源节点位置
            NodePosition sourcePos = editor.getNodePosition(nodeId);
            if (sourcePos == null) return null;
            
            // 获取节点类型
            String typeId = NodeRegistry.getInstance().resolveCanonicalNodeId(sourceNode.getTypeId());
            
            // 检查节点类型是否有效
            if (typeId == null || typeId.isEmpty()) {
                NodeCraft.LOGGER.error("复制节点失败: 节点类型ID为空");
                return null;
            }
            
            NodeCraft.LOGGER.info("尝试复制节点类型: {}", typeId);
            
            // 计算新节点位置
            float newX = sourcePos.x + offsetX;
            float newY = sourcePos.y + offsetY;
            
            // 直接创建与源节点相同类型的新节点
            INode newNode = null;
            
            try {
                // 第一种方法：尝试使用标准方式添加节点
                newNode = editor.addNode(typeId, newX, newY);
            } catch (Exception e) {
                NodeCraft.LOGGER.warn("标准方式创建节点失败，尝试使用替代方式: {}", e.getMessage());
                
                // 已知类型ID映射表
                Map<String, String> knownTypeIdMappings = new HashMap<>();
                knownTypeIdMappings.put("visualization.debugging.panel", "output.debug.data_inspector");
                knownTypeIdMappings.put("output.debug.data_inspector", "output.debug.data_inspector");
                knownTypeIdMappings.put("panel", "output.debug.data_inspector");
                
                // 检查是否有已知映射
                if (knownTypeIdMappings.containsKey(typeId)) {
                    String mappedTypeId = knownTypeIdMappings.get(typeId);
                    try {
                        NodeCraft.LOGGER.info("尝试使用已知映射ID: {} -> {}", typeId, mappedTypeId);
                        newNode = editor.addNode(mappedTypeId, newX, newY);
                    } catch (Exception ex) {
                        NodeCraft.LOGGER.debug("使用映射ID创建节点失败: {}", ex.getMessage());
                    }
                }
                
                // 如果映射方式也失败了，尝试使用显示名称
                if (newNode == null) {
                    String displayName = sourceNode.getDisplayName();
                    if (displayName != null && !displayName.isEmpty()) {
                        String possibleId = displayName.toLowerCase().replace(" ", "") + "node";
                        try {
                            NodeCraft.LOGGER.info("尝试使用推断的ID: {}", possibleId);
                            newNode = editor.addNode(possibleId, newX, newY);
                        } catch (Exception ex) {
                            NodeCraft.LOGGER.debug("使用推断ID创建节点失败: {}", ex.getMessage());
                        }
                    }
                }
                
                // 如果以上方法都失败，尝试直接克隆源节点（这是一种回退机制）
                if (newNode == null) {
                    try {
                        // 尝试创建源节点的克隆
                        Class<?> nodeClass = sourceNode.getClass();
                        INode clonedNode = (INode) nodeClass.getDeclaredConstructor().newInstance();
                        
                        // 将克隆的节点添加到图中
                        if (clonedNode != null) {
                            graph.addNode(clonedNode);
                            // 设置新节点的位置
                            editor.getNodePositions().put(clonedNode.getId(), new NodePosition(newX, newY));
                            newNode = clonedNode;
                            NodeCraft.LOGGER.info("成功通过克隆创建节点: {}", clonedNode.getDisplayName());
                        }
                    } catch (Exception ex) {
                        NodeCraft.LOGGER.error("克隆节点失败: {}", ex.getMessage());
                    }
                }
            }
            
            if (newNode == null) {
                NodeCraft.LOGGER.error("复制节点失败: 无法创建类型为 '{}' 的新节点", typeId);
                return null;
            }
            
            newNode.setNodeState(sourceNode.getNodeState());
            
            NodeCraft.LOGGER.info("已复制节点: {} -> {}", sourceNode.getDisplayName(), newNode.getDisplayName());
            return newNode;
        } catch (Exception e) {
            NodeCraft.LOGGER.error("复制节点时出错: {}", e.getMessage(), e);
            return null;
        }
    }
    
    @Override
    public void lostOwnership(Clipboard clipboard, Transferable contents) {
        // 当剪贴板所有权丢失时的处理，不需要特殊操作
    }
    
    /**
     * 节点数据类
     */
    private static class NodeData {
        public String id;
        public String typeId;
        public float x;
        public float y;
        public Object state;
    }
    
    /**
     * 连接数据类
     */
    private static class ConnectionData {
        public int sourceNodeIndex;
        public String sourcePortId;
        public int targetNodeIndex;
        public String targetPortId;
    }
    
    /**
     * 剪贴板数据类
     */
    private static class ClipboardData {
        public String format;
        public List<NodeData> nodes;
        public List<ConnectionData> connections;
    }
} 

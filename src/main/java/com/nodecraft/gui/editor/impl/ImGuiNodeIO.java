package com.nodecraft.gui.editor.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.io.SavedConnection;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import com.nodecraft.nodesystem.io.SavedPosition;
import com.nodecraft.nodesystem.registry.NodeRegistry;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;

/**
 * ImGui节点编辑器的IO组件，处理节点图的保存和加载
 */
public class ImGuiNodeIO {
    private final ICanvasEditor editor;
    private final Gson gson;
    private final Path defaultSavePath;
    private Path lastSavedPath = null;
    private String lastOperationError = null;
    private boolean dirty = false; // 跟踪是否有未保存的更改
    private long dirtyVersion = 0L;
    
    /**
     * 构造函数
     * @param editor 节点编辑器实例
     */
    public ImGuiNodeIO(ICanvasEditor editor) {
        this.editor = editor;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.defaultSavePath = Paths.get("nodecraft_graph.json");
    }
    
    /**
     * 保存节点图到默认位置
     */
    public boolean saveGraph() {
        return saveGraph(defaultSavePath);
    }
    
    /**
     * 加载默认位置的节点图
     */
    public boolean loadGraph() {
        return loadGraph(defaultSavePath);
    }
    
    /**
     * 获取默认保存路径
     */
    public Path getDefaultSavePath() {
        return defaultSavePath;
    }
    
    /**
     * 保存节点图到指定文件
     * @param filePath 文件保存路径
     */
    public boolean saveGraph(Path filePath) {
        lastOperationError = null;
        NodeGraph currentGraph = editor.getCurrentGraph();
        Map<UUID, NodePosition> nodePositions = editor.getNodePositions();
        
        if (currentGraph == null) {
            lastOperationError = "当前没有节点图可保存。";
            NodeCraft.LOGGER.warn("无法保存：{}", lastOperationError);
            return false;
        }

        NodeCraft.LOGGER.info("开始保存节点图到: {}", filePath);
        SavedGraph savedGraph = new SavedGraph();
        savedGraph.graphName = currentGraph.getName();
        savedGraph.nodes = new ArrayList<>();
        savedGraph.connections = new ArrayList<>();
        savedGraph.nodePositions = new HashMap<>();
        NodeRegistry registry = NodeRegistry.getInstance();

        // Save nodes and their states
        for (INode node : currentGraph.getNodes()) {
            if (node instanceof BaseNode baseNode) {
                SavedNode savedNode = new SavedNode();
                savedNode.nodeId = baseNode.getId().toString();
                savedNode.typeId = registry.resolveCanonicalNodeId(baseNode.getTypeId());
                savedNode.state = baseNode.getNodeState();
                savedGraph.nodes.add(savedNode);
            } else {
                NodeCraft.LOGGER.warn("跳过保存非 BaseNode 节点: {}", node.getId());
            }
        }

        // Save connections
        for (NodeGraph.Connection connection : currentGraph.getConnections()) {
            SavedConnection savedConnection = new SavedConnection();
            savedConnection.sourceNodeId = connection.sourceNode.getId().toString();
            savedConnection.sourcePortId = connection.sourcePort.getId();
            savedConnection.targetNodeId = connection.targetNode.getId().toString();
            savedConnection.targetPortId = connection.targetPort.getId();
            savedGraph.connections.add(savedConnection);
        }

        // Save positions
        for (Map.Entry<UUID, NodePosition> entry : nodePositions.entrySet()) {
            savedGraph.nodePositions.put(
                entry.getKey().toString(), 
                new SavedPosition(entry.getValue().x, entry.getValue().y)
            );
        }

        // Serialize to JSON and write to file
        try {
            String json = gson.toJson(savedGraph);
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(filePath, json, StandardCharsets.UTF_8);
            lastSavedPath = filePath;
            dirty = false;
            NodeCraft.LOGGER.info("节点图成功保存到: {}", filePath);
            return true;
        } catch (IOException e) {
            lastOperationError = "保存节点图时发生 IO 错误: " + e.getMessage();
            NodeCraft.LOGGER.error("保存节点图时发生 IO 错误: {}", e.getMessage(), e);
            return false;
        } catch (Exception e) {
            lastOperationError = "序列化节点图时发生错误: " + e.getMessage();
            NodeCraft.LOGGER.error("序列化节点图时发生错误: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 从指定文件加载节点图
     * @param filePath 文件路径
     */
    public boolean loadGraph(Path filePath) {
        lastOperationError = null;
        if (!Files.exists(filePath)) {
            lastOperationError = "文件不存在: " + filePath;
            NodeCraft.LOGGER.warn("无法加载：{}", lastOperationError);
            return false;
        }

        NodeCraft.LOGGER.info("开始从文件加载节点图: {}", filePath);
        try {
            String json = Files.readString(filePath, StandardCharsets.UTF_8);

            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) {
                lastOperationError = "文件格式无效：根节点必须是 JSON 对象（请确认选择的是 .nodecraft 节点图文件）。";
                NodeCraft.LOGGER.error("{}", lastOperationError);
                return false;
            }

            SavedGraph savedGraph = gson.fromJson(json, SavedGraph.class);

            if (savedGraph == null) {
                lastOperationError = "加载失败：无法解析 JSON 文件。";
                NodeCraft.LOGGER.error("{}", lastOperationError);
                return false;
            }

            if (savedGraph.nodes == null) {
                savedGraph.nodes = new ArrayList<>();
            }
            if (savedGraph.connections == null) {
                savedGraph.connections = new ArrayList<>();
            }

            // 创建新图
            String graphName = savedGraph.graphName != null ? savedGraph.graphName : "Loaded Graph";
            NodeGraph newGraph = new NodeGraph(graphName);
            Map<UUID, NodePosition> newPositions = new HashMap<>();

            // Map to track old node IDs to new node instances
            Map<String, BaseNode> loadedNodesMap = new HashMap<>();

            // 1. Recreate nodes
            NodeRegistry registry = NodeRegistry.getInstance();
            int skippedUnknownNodeTypes = 0;
            for (SavedNode savedNode : savedGraph.nodes) {
                try {
                    INode iNode = registry.createNodeInstance(savedNode.typeId);
                    if (iNode instanceof BaseNode newNode) {
                        try {
                            newNode.setNodeState(savedNode.state);
                            loadedNodesMap.put(savedNode.nodeId, newNode);
                            newGraph.addNode(newNode);
                        } catch (Exception e) {
                            NodeCraft.LOGGER.error("恢复节点状态或添加到图时出错: Node Type={}, Saved ID={}, Error={}",
                                savedNode.typeId, savedNode.nodeId, e.getMessage(), e);
                        }
                    } else if (iNode == null) {
                        NodeCraft.LOGGER.warn("无法创建节点实例 (返回为null): Type ID={}, Saved ID={}",
                            savedNode.typeId, savedNode.nodeId);
                    } else {
                        NodeCraft.LOGGER.warn("无法加载节点：实例不是 BaseNode 类型。Type ID={}, Saved ID={}, Actual Type={}",
                            savedNode.typeId, savedNode.nodeId, iNode.getClass().getName());
                    }
                } catch (IllegalArgumentException e) {
                    skippedUnknownNodeTypes++;
                    NodeCraft.LOGGER.warn("跳过未注册节点类型: {} (Saved ID: {})", savedNode.typeId, savedNode.nodeId);
                }
            }

            // 2. Recreate positions
             if (savedGraph.nodePositions != null) {
                for (Map.Entry<String, SavedPosition> entry : savedGraph.nodePositions.entrySet()) {
                    String oldNodeId = entry.getKey();
                    SavedPosition savedPos = entry.getValue();
                    BaseNode nodeInstance = loadedNodesMap.get(oldNodeId); 
                    if (nodeInstance != null && savedPos != null) {
                        newPositions.put(nodeInstance.getId(), new NodePosition(savedPos.x, savedPos.y));
                    } else {
                         NodeCraft.LOGGER.warn("无法恢复节点位置：找不到节点实例或位置数据为空 for old ID {}", oldNodeId);
                    }
                }
            }

            // 3. Recreate connections
            for (SavedConnection savedConnection : savedGraph.connections) {
                BaseNode sourceNode = loadedNodesMap.get(savedConnection.sourceNodeId);
                BaseNode targetNode = loadedNodesMap.get(savedConnection.targetNodeId);

                if (sourceNode != null && targetNode != null) {
                    boolean success = newGraph.connect(
                        sourceNode.getId(),
                        savedConnection.sourcePortId,
                        targetNode.getId(),
                        savedConnection.targetPortId
                    );
                    if (!success) {
                        NodeCraft.LOGGER.warn("无法重新连接端口: {} ({}) -> {} ({})",
                            savedConnection.sourceNodeId, savedConnection.sourcePortId,
                            savedConnection.targetNodeId, savedConnection.targetPortId);
                    }
                } else {
                    NodeCraft.LOGGER.warn("无法重新连接端口：找不到源节点或目标节点实例 for connection {} -> {}",
                        savedConnection.sourceNodeId, savedConnection.targetNodeId);
                }
            }

            if (!savedGraph.nodes.isEmpty() && loadedNodesMap.isEmpty()) {
                lastOperationError = "加载失败：文件中的节点类型在当前版本中均不可用。";
                NodeCraft.LOGGER.error("{}", lastOperationError);
                return false;
            }

            // 最后将加载的图和位置设置到编辑器
            editor.setCurrentGraph(newGraph);
            editor.setNodePositions(newPositions);
            lastSavedPath = filePath;
            dirty = false;

            if (skippedUnknownNodeTypes > 0) {
                lastOperationError = "已部分加载：" + skippedUnknownNodeTypes + " 个节点类型未注册，已跳过。";
                NodeCraft.LOGGER.warn("{}", lastOperationError);
            }

            NodeCraft.LOGGER.info("节点图成功从文件加载: {}", filePath);
            return true;

        } catch (IOException e) {
            lastOperationError = "加载节点图时发生 IO 错误: " + e.getMessage();
            NodeCraft.LOGGER.error("加载节点图时发生 IO 错误: {}", e.getMessage(), e);
            return false;
        } catch (Exception e) {
            lastOperationError = "解析或重建节点图时发生错误: " + e.getMessage();
            NodeCraft.LOGGER.error("解析或重建节点图时发生错误: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 获取最近一次保存/加载失败或告警信息。
     */
    public String getLastOperationError() {
        return lastOperationError;
    }

    /**
     * 获取最后保存的路径
     * @return 最后保存的路径
     */
    public Path getLastSavedPath() {
        return lastSavedPath;
    }
    
    /**
     * 检查是否有未保存的更改
     * @return 如果有未保存的更改返回true，否则返回false
     */
    public boolean isDirty() {
        return dirty;
    }

    /**
     * 获取当前脏状态版本号。
     * 每次调用 markDirty() 时递增，用于触发自动预览重算。
     */
    public long getDirtyVersion() {
        return dirtyVersion;
    }
    
    /**
     * 标记节点图已修改
     */
    public void markDirty() {
        dirty = true;
        dirtyVersion++;
    }
    
    /**
     * 从JSON中加载节点位置
     * @param jsonContent JSON内容
     * @return 节点位置映射
     */
    private Map<UUID, NodePosition> loadNodePositions(String jsonContent) {
        try {
            Map<UUID, NodePosition> positions = new HashMap<>();
            
            JsonElement element = JsonParser.parseString(jsonContent);
            if (element.isJsonObject()) {
                JsonObject json = element.getAsJsonObject();
                if (json.has("nodePositions") && json.get("nodePositions").isJsonObject()) {
                    JsonObject positionsJson = json.getAsJsonObject("nodePositions");
                    
                    for (Map.Entry<String, JsonElement> entry : positionsJson.entrySet()) {
                        try {
                            UUID nodeId = UUID.fromString(entry.getKey());
                            JsonObject posObj = entry.getValue().getAsJsonObject();
                            float x = posObj.has("x") ? posObj.get("x").getAsFloat() : 0;
                            float y = posObj.has("y") ? posObj.get("y").getAsFloat() : 0;
                            positions.put(nodeId, new NodePosition(x, y));
                        } catch (Exception e) {
                            NodeCraft.LOGGER.warn("解析节点位置时出错: {}", e.getMessage());
                        }
                    }
                }
            }
            
            return positions;
        } catch (Exception e) {
            NodeCraft.LOGGER.warn("解析节点位置失败: {}", e.getMessage());
            return new HashMap<>();
        }
    }
    
    /**
     * 将节点位置添加到JSON中
     * @param graphJson 节点图JSON
     * @param positions 节点位置映射
     * @return 更新后的JSON
     */
    private String addNodePositionsToJson(String graphJson, Map<UUID, NodePosition> positions) {
        try {
            JsonElement element = JsonParser.parseString(graphJson);
            if (element.isJsonObject()) {
                JsonObject json = getJsonObject(positions, element);

                // 使用美化输出
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                return gson.toJson(json);
            }
            
            return graphJson;
        } catch (Exception e) {
            NodeCraft.LOGGER.warn("添加节点位置信息时出错: {}", e.getMessage());
            return graphJson;
        }
    }

    private static @NotNull JsonObject getJsonObject(Map<UUID, NodePosition> positions, JsonElement element) {
        JsonObject json = element.getAsJsonObject();

        // 创建节点位置对象
        JsonObject positionsJson = new JsonObject();
        for (Map.Entry<UUID, NodePosition> entry : positions.entrySet()) {
            JsonObject posObj = new JsonObject();
            posObj.addProperty("x", entry.getValue().x);
            posObj.addProperty("y", entry.getValue().y);
            positionsJson.add(entry.getKey().toString(), posObj);
        }

        // 添加到主JSON对象
        json.add("nodePositions", positionsJson);
        return json;
    }
} 

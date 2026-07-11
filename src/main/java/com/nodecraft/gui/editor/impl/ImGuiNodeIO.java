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
import com.nodecraft.nodesystem.graph.GraphLoadResult;
import com.nodecraft.nodesystem.graph.GraphSerializer;
import com.nodecraft.nodesystem.io.GraphFormat;
import com.nodecraft.nodesystem.io.SavedConnection;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import com.nodecraft.nodesystem.io.SavedPosition;
import com.nodecraft.nodesystem.registry.NodeRegistry;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

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
        this.defaultSavePath = resolveDefaultSavePath();
    }

    private Path resolveDefaultSavePath() {
        try {
            Path gameDir = FabricLoader.getInstance().getGameDir();
            return gameDir.resolve("nodecraft").resolve("graphs").resolve("nodecraft_graph.json");
        } catch (IllegalStateException e) {
            NodeCraft.LOGGER.warn("Fabric game directory is not available yet. Falling back to a local graph path.");
            return Paths.get("nodecraft", "graphs", "nodecraft_graph.json");
        }
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
        savedGraph.formatVersion = GraphFormat.CURRENT;
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

            if (savedGraph.nodes == null) {
                savedGraph.nodes = new ArrayList<>();
            }
            if (savedGraph.connections == null) {
                savedGraph.connections = new ArrayList<>();
            }

            if (savedGraph.connections == null) {
                savedGraph.connections = new ArrayList<>();
            }
            if (savedGraph.nodePositions == null) {
                savedGraph.nodePositions = new HashMap<>();
            }

            GraphLoadResult loadResult = GraphSerializer.loadFromSavedGraph(savedGraph);
            if (!savedGraph.nodes.isEmpty() && !loadResult.hasLoadedNodes()) {
                lastOperationError = "加载失败：文件中的节点类型在当前版本中均不可用。";
                NodeCraft.LOGGER.error("{}", lastOperationError);
                return false;
            }

            Map<UUID, NodePosition> newPositions = buildEditorPositions(savedGraph, loadResult.nodesBySavedId());

            editor.setCurrentGraph(loadResult.graph());
            editor.setNodePositions(newPositions);
            lastSavedPath = filePath;
            dirty = false;

            String userMessage = loadResult.userMessage();
            if (userMessage != null) {
                lastOperationError = userMessage;
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

    static Map<UUID, NodePosition> buildEditorPositions(
            SavedGraph savedGraph,
            Map<String, BaseNode> nodesBySavedId) {
        Map<UUID, NodePosition> positions = new HashMap<>();
        if (savedGraph.nodePositions == null) {
            return positions;
        }

        for (Map.Entry<String, SavedPosition> entry : savedGraph.nodePositions.entrySet()) {
            BaseNode nodeInstance = nodesBySavedId.get(entry.getKey());
            SavedPosition savedPos = entry.getValue();
            if (nodeInstance != null && savedPos != null) {
                positions.put(nodeInstance.getId(), new NodePosition(savedPos.x, savedPos.y));
            } else {
                NodeCraft.LOGGER.warn("无法恢复节点位置：找不到节点实例或位置数据为空 for old ID {}", entry.getKey());
            }
        }
        return positions;
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
} 

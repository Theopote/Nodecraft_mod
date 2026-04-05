package com.nodecraft.nodesystem.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.core.BasePort;

/**
 * 节点图类，表示一组相互连接的节点网络
 */
public class NodeGraph {
    
    private final UUID id;
    private String name;
    private final List<INode> nodes = new ArrayList<>();
    private final Map<UUID, INode> nodeMap = new HashMap<>();
    private final List<Connection> connections = new ArrayList<>();
    
    /**
     * 构造一个空的节点图
     */
    public NodeGraph() {
        this.id = UUID.randomUUID();
        this.name = "新节点图 " + id.toString().substring(0, 8);
    }
    
    /**
     * 构造一个有名称的节点图
     * @param name 节点图名称
     */
    public NodeGraph(String name) {
        this.id = UUID.randomUUID();
        this.name = name;
    }
    
    /**
     * 获取节点图ID
     * @return 节点图ID
     */
    public UUID getId() {
        return id;
    }
    
    /**
     * 获取节点图名称
     * @return 节点图名称
     */
    public String getName() {
        return name;
    }
    
    /**
     * 设置节点图名称
     * @param name 新名称
     */
    public void setName(String name) {
        this.name = name;
    }
    
    /**
     * 添加节点到图中
     * @param node 要添加的节点
     * @return 添加成功返回true，否则为false
     */
    public boolean addNode(INode node) {
        if (node == null || nodeMap.containsKey(node.getId())) {
            return false;
        }
        
        nodes.add(node);
        nodeMap.put(node.getId(), node);
        return true;
    }
    
    /**
     * 从图中移除节点
     * @param nodeId 要移除的节点ID
     * @return 移除成功返回true，否则为false
     */
    public boolean removeNode(UUID nodeId) {
        INode node = nodeMap.get(nodeId);
        if (node == null) {
            return false;
        }
        
        // 移除与此节点相关的所有连接
        List<Connection> connectionsToRemove = new ArrayList<>();
        for (Connection connection : connections) {
            if (connection.sourceNode.getId().equals(nodeId) ||
                connection.targetNode.getId().equals(nodeId)) {
                connectionsToRemove.add(connection);
            }
        }
        
        for (Connection connection : connectionsToRemove) {
            removeConnection(connection);
        }
        
        // 移除节点
        nodes.remove(node);
        nodeMap.remove(nodeId);
        return true;
    }
    
    /**
     * 获取图中的所有节点
     * @return 节点列表
     */
    public List<INode> getNodes() {
        return new ArrayList<>(nodes);
    }
    
    /**
     * 根据ID获取节点
     * @param nodeId 节点ID
     * @return 找到的节点，如果不存在则返回null
     */
    public INode getNode(UUID nodeId) {
        return nodeMap.get(nodeId);
    }
    
    /**
     * 连接两个端口
     * @param sourceNodeId 源节点ID
     * @param sourcePortId 源端口ID
     * @param targetNodeId 目标节点ID
     * @param targetPortId 目标端口ID
     * @return 如果连接成功返回true，否则为false
     */
    public boolean connect(UUID sourceNodeId, String sourcePortId, UUID targetNodeId, String targetPortId) {
        if (!canConnect(sourceNodeId, sourcePortId, targetNodeId, targetPortId)) {
            return false;
        }

        INode sourceNode = nodeMap.get(sourceNodeId);
        INode targetNode = nodeMap.get(targetNodeId);
        
        if (sourceNode == null || targetNode == null) {
            return false;
        }
        
        // 查找源端口
        IPort sourcePort = null;
        for (IPort port : sourceNode.getOutputPorts()) {
            if (port.getId().equals(sourcePortId)) {
                sourcePort = port;
                break;
            }
        }
        
        // 查找目标端口
        IPort targetPort = null;
        for (IPort port : targetNode.getInputPorts()) {
            if (port.getId().equals(targetPortId)) {
                targetPort = port;
                break;
            }
        }
        
        // 创建连接并添加到列表
        Connection connection = new Connection(sourceNode, sourcePort, targetNode, targetPort);
        connections.add(connection);
        
        // 建立端口间连接
        if (!sourcePort.connectTo(targetPort)) {
            connections.remove(connection);
            return false;
        }

        return true;
    }

    public boolean canConnect(UUID sourceNodeId, String sourcePortId, UUID targetNodeId, String targetPortId) {
        INode sourceNode = nodeMap.get(sourceNodeId);
        INode targetNode = nodeMap.get(targetNodeId);

        if (sourceNode == null || targetNode == null) {
            return false;
        }

        if (sourceNodeId.equals(targetNodeId)) {
            return false;
        }

        IPort sourcePort = findOutputPort(sourceNode, sourcePortId);
        IPort targetPort = findInputPort(targetNode, targetPortId);
        if (sourcePort == null || targetPort == null) {
            return false;
        }

        if (!com.nodecraft.nodesystem.api.NodeDataType.isConnectableTo(sourcePort.getDataType(), targetPort.getDataType())) {
            return false;
        }

        Connection existingExactConnection = findConnection(sourceNodeId, sourcePortId, targetNodeId, targetPortId);
        if (existingExactConnection != null) {
            return true;
        }

        if (targetPort.allowsMultipleIncomingConnections()) {
            return true;
        }

        return findConnectionToInput(targetNodeId, targetPortId) == null;
    }
    
    /**
     * 删除连接
     * @param connection 要删除的连接
     */
    public void removeConnection(Connection connection) {
        if (connection != null) {
            // 断开端口间连接
            if (connection.targetPort instanceof BasePort baseTargetPort) {
                baseTargetPort.disconnectFrom(connection.sourcePort);
            } else {
                connection.targetPort.disconnect();
            }
            
            // 移除连接
            connections.remove(connection);
        }
    }
    
    /**
     * 断开两个端口之间的连接
     * @param sourceNodeId 源节点ID
     * @param sourcePortId 源端口ID
     * @param targetNodeId 目标节点ID
     * @param targetPortId 目标端口ID
     * @return 如果断开成功返回true，否则为false
     */
    public boolean disconnectPorts(UUID sourceNodeId, String sourcePortId, UUID targetNodeId, String targetPortId) {
        Connection connectionToRemove = findConnection(sourceNodeId, sourcePortId, targetNodeId, targetPortId);
        
        if (connectionToRemove != null) {
            removeConnection(connectionToRemove);
            return true;
        }
        return false;
    }
    
    /**
     * 检查两个端口是否已连接
     */
    public boolean isConnected(UUID sourceNodeId, String sourcePortId, UUID targetNodeId, String targetPortId) {
        return findConnection(sourceNodeId, sourcePortId, targetNodeId, targetPortId) != null;
    }
    
    /**
     * 获取连接到指定输入端口的输出节点ID
     * @param nodeId 输入节点的ID
     * @param inputPortId 输入端口的ID
     * @return 连接的输出节点ID，如果没有连接则返回null
     */
    public UUID getConnectedOutputNodeId(UUID nodeId, String inputPortId) {
        for (Connection connection : connections) {
            if (connection.targetNode.getId().equals(nodeId) && 
                connection.targetPort.getId().equals(inputPortId)) {
                return connection.sourceNode.getId();
            }
        }
        return null;
    }
    
    /**
     * 获取连接到指定输入端口的输出端口ID
     * @param nodeId 输入节点的ID
     * @param inputPortId 输入端口的ID
     * @return 连接的输出端口ID，如果没有连接则返回null
     */
    public String getConnectedOutputPortId(UUID nodeId, String inputPortId) {
        for (Connection connection : connections) {
            if (connection.targetNode.getId().equals(nodeId) && 
                connection.targetPort.getId().equals(inputPortId)) {
                return connection.sourcePort.getId();
            }
        }
        return null;
    }
    
    /**
     * 获取连接到指定输出端口的所有输入端口
     * @param nodeId 输出节点的ID
     * @param outputPortId 输出端口的ID
     * @return 映射表，键为连接的节点ID，值为连接的端口ID；如果没有连接则返回空映射
     */
    public Map<UUID, String> getConnectedInputs(UUID nodeId, String outputPortId) {
        Map<UUID, String> result = new HashMap<>();
        for (Connection connection : connections) {
            if (connection.sourceNode.getId().equals(nodeId) && 
                connection.sourcePort.getId().equals(outputPortId)) {
                result.put(connection.targetNode.getId(), connection.targetPort.getId());
            }
        }
        return result;
    }
    
    /**
     * 获取图中的所有连接
     * @return 连接列表
     */
    public List<Connection> getConnections() {
        return new ArrayList<>(connections);
    }

    private IPort findOutputPort(INode node, String portId) {
        for (IPort port : node.getOutputPorts()) {
            if (port.getId().equals(portId)) {
                return port;
            }
        }
        return null;
    }

    private IPort findInputPort(INode node, String portId) {
        for (IPort port : node.getInputPorts()) {
            if (port.getId().equals(portId)) {
                return port;
            }
        }
        return null;
    }

    private Connection findConnection(UUID sourceNodeId, String sourcePortId, UUID targetNodeId, String targetPortId) {
        for (Connection connection : connections) {
            if (connection.sourceNode.getId().equals(sourceNodeId) &&
                connection.sourcePort.getId().equals(sourcePortId) &&
                connection.targetNode.getId().equals(targetNodeId) &&
                connection.targetPort.getId().equals(targetPortId)) {
                return connection;
            }
        }
        return null;
    }

    private Connection findConnectionToInput(UUID targetNodeId, String targetPortId) {
        for (Connection connection : connections) {
            if (connection.targetNode.getId().equals(targetNodeId) &&
                connection.targetPort.getId().equals(targetPortId)) {
                return connection;
            }
        }
        return null;
    }
    
    /**
     * 表示节点图中两个端口之间的连接
     */
    public static class Connection {
        public final INode sourceNode;
        public final IPort sourcePort;
        public final INode targetNode;
        public final IPort targetPort;
        
        public Connection(INode sourceNode, IPort sourcePort, INode targetNode, IPort targetPort) {
            this.sourceNode = sourceNode;
            this.sourcePort = sourcePort;
            this.targetNode = targetNode;
            this.targetPort = targetPort;
        }
    }
} 

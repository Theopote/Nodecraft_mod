package com.nodecraft.nodesystem.execution;

import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.core.BaseNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 节点执行器类，负责按照拓扑顺序执行节点图中的节点。
 * 支持传递 ExecutionContext 给每个节点，使世界操作节点能访问 World 和 Player。
 */
public class NodeExecutor {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(NodeExecutor.class);
    private final NodeGraph graph;
    private final ExecutionContext context;
    private final Map<UUID, NodeState> nodeStates = new HashMap<>();
    private boolean isExecuting = false;
    private CompletableFuture<Boolean> executionFuture;
    private Executor executorService;
    
    // 节点执行状态
    private enum NodeState {
        NOT_VISITED,
        VISITING,
        VISITED,
        ERROR
    }
    
    /**
     * 构造节点执行器（带执行上下文）
     * @param graph 要执行的节点图
     * @param context 执行上下文，包含 World 和 Player 信息
     */
    public NodeExecutor(NodeGraph graph, ExecutionContext context) {
        this.graph = graph;
        this.context = context;
        this.executorService = Executors.newSingleThreadExecutor();
    }
    
    /**
     * 构造节点执行器（无上下文，向后兼容）
     * @param graph 要执行的节点图
     */
    public NodeExecutor(NodeGraph graph) {
        this(graph, null);
    }
    
    /**
     * 异步执行节点图
     * @return 返回执行结果的CompletableFuture
     */
    public CompletableFuture<Boolean> executeAsync() {
        if (isExecuting) {
            return CompletableFuture.completedFuture(false);
        }
        
        isExecuting = true;
        executionFuture = new CompletableFuture<>();
        
        CompletableFuture.runAsync(() -> {
            try {
                boolean result = executeGraph();
                executionFuture.complete(result);
            } catch (Exception e) {
                LOGGER.error("节点图执行错误", e);
                executionFuture.completeExceptionally(e);
            } finally {
                isExecuting = false;
            }
        }, executorService);
        
        return executionFuture;
    }
    
    /**
     * 同步执行节点图（阻塞调用线程直到执行完成）
     * @return 执行成功返回true，否则为false
     */
    public boolean executeSync() {
        if (isExecuting) {
            return false;
        }
        
        try {
            isExecuting = true;
            return executeGraph();
        } catch (Exception e) {
            LOGGER.error("节点图执行错误", e);
            return false;
        } finally {
            isExecuting = false;
        }
    }
    
    /**
     * 带超时的同步执行
     * @param timeout 超时时间（毫秒）
     * @return 执行成功返回true，否则为false
     */
    public boolean executeSync(long timeout) {
        CompletableFuture<Boolean> future = executeAsync();
        try {
            return future.get(timeout, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            LOGGER.error("节点图执行超时或出错", e);
            future.cancel(true);
            return false;
        }
    }
    
    /**
     * 停止正在执行的操作
     */
    public void stop() {
        if (isExecuting && executionFuture != null && !executionFuture.isDone()) {
            executionFuture.cancel(true);
            isExecuting = false;
        }
    }
    
    /**
     * 获取当前是否正在执行
     */
    public boolean isExecuting() {
        return isExecuting;
    }
    
    /**
     * 执行节点图
     * @return 执行成功返回true，否则为false
     */
    private boolean executeGraph() {
        // 初始化节点状态
        nodeStates.clear();
        for (INode node : graph.getNodes()) {
            nodeStates.put(node.getId(), NodeState.NOT_VISITED);
        }
        
        // 尝试按拓扑排序执行节点
        List<INode> sortedNodes = topologicalSort();
        if (sortedNodes == null) {
            LOGGER.error("节点图中存在循环依赖，无法执行");
            return false;
        }
        
        LOGGER.info("开始执行节点图，共 {} 个节点", sortedNodes.size());
        
        // 执行节点
        int executedCount = 0;
        for (INode node : sortedNodes) {
            Map<String, Object> inputs = collectNodeInputs(node);
            try {
                // 优先使用带 context 的 compute 方法
                if (node instanceof BaseNode baseNode && context != null) {
                    baseNode.compute(inputs, context);
                } else {
                    node.compute(inputs);
                }
                executedCount++;
                nodeStates.put(node.getId(), NodeState.VISITED);
            } catch (Exception e) {
                LOGGER.error("节点 {} 执行出错: {}", node.getDisplayName(), e.getMessage(), e);
                nodeStates.put(node.getId(), NodeState.ERROR);
                return false;
            }
        }
        
        LOGGER.info("节点图执行完成，成功执行 {}/{} 个节点", executedCount, sortedNodes.size());
        return true;
    }
    
    /**
     * 对节点图进行拓扑排序
     * @return 排序后的节点列表，如果存在循环依赖则返回null
     */
    private List<INode> topologicalSort() {
        List<INode> result = new ArrayList<>();
        Set<UUID> visited = new HashSet<>();
        Set<UUID> temporaryMarked = new HashSet<>();
        
        for (INode node : graph.getNodes()) {
            if (!visited.contains(node.getId())) {
                if (!visit(node, visited, temporaryMarked, result)) {
                    return null; // 检测到循环依赖
                }
            }
        }
        
        return result;
    }
    
    /**
     * 拓扑排序的递归访问函数
     */
    private boolean visit(INode node, Set<UUID> visited, Set<UUID> temporaryMarked, List<INode> result) {
        if (temporaryMarked.contains(node.getId())) {
            return false; // 检测到循环依赖
        }
        
        if (!visited.contains(node.getId())) {
            temporaryMarked.add(node.getId());
            
            // 查找此节点的所有依赖（输入连接的源节点）
            for (NodeGraph.Connection connection : graph.getConnections()) {
                if (connection.targetNode.getId().equals(node.getId())) {
                    if (!visit(connection.sourceNode, visited, temporaryMarked, result)) {
                        return false;
                    }
                }
            }
            
            temporaryMarked.remove(node.getId());
            visited.add(node.getId());
            result.add(node);
        }
        
        return true;
    }
    
    /**
     * 收集节点的所有输入值
     * @param node 要收集输入的节点
     * @return 输入值映射
     */
    private Map<String, Object> collectNodeInputs(INode node) {
        Map<String, Object> inputs = new HashMap<>();
        
        // 遍历所有连接，找到所有连接到此节点的输入
        for (NodeGraph.Connection connection : graph.getConnections()) {
            if (connection.targetNode.getId().equals(node.getId())) {
                Object value = connection.sourceNode.getOutput(connection.sourcePort.getId());
                inputs.put(connection.targetPort.getId(), value);
            }
        }
        
        return inputs;
    }
} 

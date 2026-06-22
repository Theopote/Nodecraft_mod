package com.nodecraft.gui.editor.impl;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.UUID;
import java.util.stream.Collectors;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.registry.NodeRegistry;

/**
 * 节点图历史记录管理类，用于实现撤销和重做功能
 * 
 * 重构说明：
 * 1. 使用完整的节点状态序列化而非复杂的类型猜测
 * 2. 修复了UUID不匹配问题，通过记录和更新节点ID映射
 * 3. 优化了日志级别，减少生产环境噪音
 * 4. 移除了不安全的节点克隆机制
 * 5. 修正了历史记录触发时机，确保在正确的地方记录操作
 */
public class ImGuiNodeHistory {
    
    private final ICanvasEditor editor;
    private final Stack<HistoryAction> undoStack = new Stack<>();
    private final Stack<HistoryAction> redoStack = new Stack<>();
    private final Deque<HistoryScope> scopeStack = new ArrayDeque<>();
    private final int maxHistorySize = 30; // 最大历史记录数量
    private boolean isRecording = true; // 是否记录历史
    
    public ImGuiNodeHistory(ICanvasEditor editor) {
        this.editor = editor;
    }
    
    /**
     * 清空历史记录
     */
    public void clear() {
        undoStack.clear();
        redoStack.clear();
        NodeCraft.LOGGER.info("历史记录已清空");
    }
    
    /**
     * 暂时禁用历史记录
     */
    public void pauseRecording() {
        isRecording = false;
        NodeCraft.LOGGER.debug("历史记录暂停");
    }
    
    /**
     * 恢复历史记录
     */
    public void resumeRecording() {
        isRecording = true;
        NodeCraft.LOGGER.debug("历史记录恢复");
    }
    
    /**
     * 检查是否正在记录历史
     * @return 如果正在记录历史返回true
     */
    public boolean isRecording() {
        return isRecording;
    }

    public void enterScope() {
        scopeStack.push(new HistoryScope(copyStack(undoStack), copyStack(redoStack), isRecording));
        undoStack.clear();
        redoStack.clear();
        scopeStack.clear();
        isRecording = true;
        isRecording = true;
        NodeCraft.LOGGER.debug("Entered nested graph history scope. depth={}", scopeStack.size());
    }

    public boolean exitScope() {
        if (scopeStack.isEmpty()) {
            return false;
        }
        HistoryScope scope = scopeStack.pop();
        undoStack.clear();
        undoStack.addAll(scope.undoStack());
        redoStack.clear();
        redoStack.addAll(scope.redoStack());
        isRecording = scope.recording();
        NodeCraft.LOGGER.debug("Exited nested graph history scope. depth={}", scopeStack.size());
        return true;
    }

    public void recordGraphTransaction(String label, SavedGraph before, SavedGraph after) {
        if (!isRecording || before == null || after == null) {
            return;
        }
        addAction(new GraphTransactionAction(label, deepCopyGraph(before), deepCopyGraph(after)));
    }

    private static Stack<HistoryAction> copyStack(Stack<HistoryAction> source) {
        Stack<HistoryAction> copy = new Stack<>();
        copy.addAll(source);
        return copy;
    }

    private static SavedGraph deepCopyGraph(SavedGraph graph) {
        com.google.gson.Gson gson = new com.google.gson.Gson();
        return gson.fromJson(gson.toJson(graph), SavedGraph.class);
    }
    
    /**
     * 判断是否可以撤销
     * @return 如果可以撤销返回true
     */
    public boolean canUndo() {
        return !undoStack.isEmpty();
    }
    
    /**
     * 判断是否可以重做
     * @return 如果可以重做返回true
     */
    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    /**
     * Checks whether the top action in undo stack matches the specified type.
     */
    public boolean isUndoTopActionType(ActionType actionType) {
        if (actionType == null || undoStack.isEmpty()) {
            return false;
        }
        return undoStack.peek().getType() == actionType;
    }

    /**
     * Returns current undo stack size for diagnostics.
     */
    public int getUndoStackSize() {
        return undoStack.size();
    }

    /**
     * Returns current redo stack size for diagnostics.
     */
    public int getRedoStackSize() {
        return redoStack.size();
    }

    /**
     * Returns top action type of undo stack for diagnostics, or null if empty.
     */
    public ActionType getUndoTopActionType() {
        return undoStack.isEmpty() ? null : undoStack.peek().getType();
    }
    
    /**
     * 执行撤销操作
     * @return 撤销是否成功
     */
    public boolean undo() {
        NodeCraft.LOGGER.info("=== 开始撤销操作 ===");
        NodeCraft.LOGGER.info("撤销前状态 - 撤销栈: {}, 重做栈: {}, 记录状态: {}", 
            undoStack.size(), redoStack.size(), isRecording);
        
        if (!canUndo()) {
            NodeCraft.LOGGER.debug("无法撤销：撤销栈为空");
            return false;
        }
        
        try {
            pauseRecording(); // 暂停记录，防止撤销操作本身被记录
            NodeCraft.LOGGER.info("已暂停历史记录");
            
            HistoryAction action = undoStack.pop();
            NodeCraft.LOGGER.info("执行撤销操作：{}", action.getType());
            boolean result = action.undo(editor);
            
            if (result) {
                redoStack.push(action); // 将动作添加到重做栈
                NodeCraft.LOGGER.info("撤销操作成功：{} - 撤销后状态: 撤销栈: {}, 重做栈: {}", 
                    action.getType(), undoStack.size(), redoStack.size());
                markEditorDirty();
            } else {
                NodeCraft.LOGGER.error("撤销操作失败：{}", action.getType());
                // 撤销失败时不放回栈，避免死循环
            }
            
            return result;
        } catch (Exception e) {
            NodeCraft.LOGGER.error("执行撤销操作时发生错误", e);
            return false;
        } finally {
            resumeRecording(); // 恢复记录
            NodeCraft.LOGGER.info("已恢复历史记录");
            NodeCraft.LOGGER.info("=== 撤销操作结束 ===");
            // 强制输出最终状态
            NodeCraft.LOGGER.info("最终状态检查: canUndo={}, canRedo={}, 撤销栈={}, 重做栈={}", 
                canUndo(), canRedo(), undoStack.size(), redoStack.size());
        }
    }
    
    /**
     * 执行重做操作
     * @return 重做是否成功
     */
    public boolean redo() {
        NodeCraft.LOGGER.info("=== 开始重做操作 ===");
        NodeCraft.LOGGER.info("重做前状态 - 撤销栈: {}, 重做栈: {}, 记录状态: {}", 
            undoStack.size(), redoStack.size(), isRecording);
        
        if (!canRedo()) {
            NodeCraft.LOGGER.debug("无法重做：重做栈为空");
            return false;
        }
        
        try {
            pauseRecording(); // 暂停记录，防止重做操作本身被记录
            NodeCraft.LOGGER.info("已暂停历史记录");
            
            HistoryAction action = redoStack.pop();
            NodeCraft.LOGGER.info("执行重做操作：{}", action.getType());
            boolean result = action.redo(editor);
            
            if (result) {
                undoStack.push(action); // 将动作添加到撤销栈
                NodeCraft.LOGGER.info("重做操作成功：{} - 重做后状态: 撤销栈: {}, 重做栈: {}", 
                    action.getType(), undoStack.size(), redoStack.size());
                markEditorDirty();
            } else {
                NodeCraft.LOGGER.error("重做操作失败：{}", action.getType());
                // 重做失败时不放回栈，避免死循环
            }
            
            return result;
        } catch (Exception e) {
            NodeCraft.LOGGER.error("执行重做操作时发生错误", e);
            return false;
        } finally {
            resumeRecording(); // 恢复记录
            NodeCraft.LOGGER.info("已恢复历史记录");
            NodeCraft.LOGGER.info("=== 重做操作结束 ===");
            // 强制输出最终状态
            NodeCraft.LOGGER.info("最终状态检查: canUndo={}, canRedo={}, 撤销栈={}, 重做栈={}", 
                canUndo(), canRedo(), undoStack.size(), redoStack.size());
        }
    }
    
    /**
     * 记录添加节点操作
     * @param node 添加的节点
     * @param x 节点X坐标
     * @param y 节点Y坐标
     */
    public void recordAddNode(INode node, float x, float y) {
        if (!isRecording || node == null) {
            NodeCraft.LOGGER.debug("跳过记录添加节点操作 - 记录状态: {}, 节点: {}", isRecording, node != null ? "有效" : "null");
            return;
        }
        
        // 获取节点状态进行序列化
        Object nodeState = null;
        try {
            nodeState = node.getNodeState();
        } catch (Exception e) {
            NodeCraft.LOGGER.debug("获取节点状态失败：{}", e.getMessage());
        }
        
        NodeCraft.LOGGER.info("记录节点添加操作 - ID: {}, 类型: {}, 位置: ({}, {}), 当前撤销栈大小: {}",
            node.getId(), NodeRegistry.getInstance().resolveCanonicalNodeId(node.getTypeId()), x, y, undoStack.size());
        
        AddNodeAction action = new AddNodeAction(
            node.getId(),
            NodeRegistry.getInstance().resolveCanonicalNodeId(node.getTypeId()),
            x,
            y,
            nodeState
        );
        addAction(action);
        
        NodeCraft.LOGGER.info("添加节点操作已记录，撤销栈大小: {}, 重做栈大小: {}", undoStack.size(), redoStack.size());
    }
    
    /**
     * 记录删除节点操作
     * @param node 删除的节点
     * @param x 节点X坐标
     * @param y 节点Y坐标
     */
    public void recordRemoveNode(INode node, float x, float y) {
        if (!isRecording || node == null) return;
        
        // 收集与该节点相关的连接
        NodeGraph graph = editor.getCurrentGraph();
        List<ConnectionInfo> connectionInfos = new ArrayList<>();
        
        if (graph != null) {
            connectionInfos = graph.getConnections().stream()
                .filter(conn -> conn.sourceNode.getId().equals(node.getId()) || 
                               conn.targetNode.getId().equals(node.getId()))
                .map(conn -> new ConnectionInfo(
                    conn.sourceNode.getId(), conn.sourcePort.getId(),
                    conn.targetNode.getId(), conn.targetPort.getId()
                ))
                .collect(Collectors.toList());
        }
        
        // 获取节点状态进行序列化
        Object nodeState = null;
        try {
            nodeState = node.getNodeState();
        } catch (Exception e) {
            NodeCraft.LOGGER.debug("获取节点状态失败：{}", e.getMessage());
        }
        
        NodeCraft.LOGGER.debug("记录节点删除操作 - ID: {}, 类型: {}, 位置: ({}, {}), 连接数: {}",
            node.getId(), NodeRegistry.getInstance().resolveCanonicalNodeId(node.getTypeId()), x, y, connectionInfos.size());
        
        RemoveNodeAction action = new RemoveNodeAction(
            node.getId(),
            NodeRegistry.getInstance().resolveCanonicalNodeId(node.getTypeId()),
            x,
            y,
            connectionInfos,
            nodeState
        );
        addAction(action);
    }

    public RemovedNodeSnapshot captureRemovedNodeSnapshot(INode node, float x, float y) {
        if (node == null) {
            return null;
        }

        NodeGraph graph = editor.getCurrentGraph();
        List<ConnectionInfo> connectionInfos = new ArrayList<>();
        if (graph != null) {
            connectionInfos = graph.getConnections().stream()
                .filter(conn -> conn.sourceNode.getId().equals(node.getId()) ||
                               conn.targetNode.getId().equals(node.getId()))
                .map(conn -> new ConnectionInfo(
                    conn.sourceNode.getId(), conn.sourcePort.getId(),
                    conn.targetNode.getId(), conn.targetPort.getId()
                ))
                .collect(Collectors.toList());
        }

        Object nodeState = null;
        try {
            nodeState = node.getNodeState();
        } catch (Exception e) {
            NodeCraft.LOGGER.debug("获取节点状态失败：{}", e.getMessage());
        }

        return new RemovedNodeSnapshot(
            node.getId(),
            NodeRegistry.getInstance().resolveCanonicalNodeId(node.getTypeId()),
            x,
            y,
            connectionInfos,
            nodeState
        );
    }

    public void recordRemoveNodes(List<RemovedNodeSnapshot> snapshots) {
        if (!isRecording || snapshots == null || snapshots.isEmpty()) {
            return;
        }

        RemoveNodesAction action = new RemoveNodesAction(snapshots);
        addAction(action);
    }
    
    /**
     * 记录创建连接操作
     * @param sourceNodeId 源节点ID
     * @param sourcePortId 源端口ID
     * @param targetNodeId 目标节点ID
     * @param targetPortId 目标端口ID
     */
    public void recordAddConnection(UUID sourceNodeId, String sourcePortId, UUID targetNodeId, String targetPortId) {
        if (!isRecording) return;
        
        AddConnectionAction action = new AddConnectionAction(
            sourceNodeId, sourcePortId, targetNodeId, targetPortId
        );
        addAction(action);
    }
    
    /**
     * 记录删除连接操作
     * @param sourceNodeId 源节点ID
     * @param sourcePortId 源端口ID
     * @param targetNodeId 目标节点ID
     * @param targetPortId 目标端口ID
     */
    public void recordRemoveConnection(UUID sourceNodeId, String sourcePortId, UUID targetNodeId, String targetPortId) {
        if (!isRecording) return;
        
        RemoveConnectionAction action = new RemoveConnectionAction(
            sourceNodeId, sourcePortId, targetNodeId, targetPortId
        );
        addAction(action);
    }
    
    /**
     * 添加操作到历史记录
     * @param action 要添加的操作
     */
    private void addAction(HistoryAction action) {
        NodeCraft.LOGGER.info("添加历史操作: {} - 当前撤销栈: {}, 重做栈: {}", 
            action.getType(), undoStack.size(), redoStack.size());
        
        undoStack.push(action); // 添加到撤销栈
        
        if (!redoStack.isEmpty()) {
            NodeCraft.LOGGER.info("清空重做栈 - 原因: 有新操作 {}, 重做栈大小: {}", 
                action.getType(), redoStack.size());
            redoStack.clear();      // 清空重做栈，因为有新操作
        }
        
        // 如果历史记录超过最大值，移除最旧的
        if (undoStack.size() > maxHistorySize) {
            undoStack.removeFirst(); // 移除栈底元素
            NodeCraft.LOGGER.debug("历史记录超出最大容量，移除最旧记录");
        }
        
        NodeCraft.LOGGER.info("历史操作添加完成 - 撤销栈: {}, 重做栈: {}", undoStack.size(), redoStack.size());
        markEditorDirty(); // 标记编辑器已修改
    }
    
    /**
     * 标记编辑器为脏，表示图已修改
     */
    private void markEditorDirty() {
        if (editor instanceof ImGuiNodeEditor nodeEditor) {
            nodeEditor.notifyGraphStructureChanged();
        }
    }

    private static class GraphTransactionAction extends HistoryAction {
        private final String label;
        private final SavedGraph before;
        private final SavedGraph after;

        private GraphTransactionAction(String label, SavedGraph before, SavedGraph after) {
            this.label = label != null ? label : "Graph Transaction";
            this.before = before;
            this.after = after;
        }

        @Override
        public ActionType getType() {
            return ActionType.GRAPH_TRANSACTION;
        }

        @Override
        public boolean undo(ICanvasEditor editor) {
            boolean restored = editor.restoreGraphSnapshot(deepCopyGraph(before));
            if (restored) {
                NodeCraft.LOGGER.info("Undid graph transaction: {}", label);
            }
            return restored;
        }

        @Override
        public boolean redo(ICanvasEditor editor) {
            boolean restored = editor.restoreGraphSnapshot(deepCopyGraph(after));
            if (restored) {
                NodeCraft.LOGGER.info("Redid graph transaction: {}", label);
            }
            return restored;
        }
    }

    public void recordAiPatch(String summary, Map<UUID, Object> previousStates, int undoStepsTaken) {
        if (!isRecording) return;
        
        AiPatchAction action = new AiPatchAction(summary, previousStates, undoStepsTaken);
        addAction(action);
    }

    public void recordNodeStateChange(UUID nodeId, Object beforeState, Object afterState, String label) {
        if (!isRecording || nodeId == null || beforeState == null || afterState == null) {
            return;
        }
        addAction(new NodeStateChangeAction(nodeId, beforeState, afterState, label));
    }

    private static class AiPatchAction extends HistoryAction {
        private final String summary;
        private final Map<UUID, Object> previousStates;
        private final Map<UUID, Object> postStates;
        private final int undoStepsTaken;

        public AiPatchAction(String summary, Map<UUID, Object> previousStates, int undoStepsTaken) {
            this.summary = summary;
            this.previousStates = new HashMap<>(previousStates);
            this.postStates = new HashMap<>(); // Captured during first undo or setup
            this.undoStepsTaken = undoStepsTaken;
        }

        @Override
        public ActionType getType() {
            return ActionType.AI_PATCH;
        }

        @Override
        public boolean undo(ICanvasEditor editor) {
            NodeGraph graph = editor.getCurrentGraph();
            if (graph == null) return false;

            // Capture post-states for redo if not already captured
            if (postStates.isEmpty()) {
                for (UUID nodeId : previousStates.keySet()) {
                    INode node = graph.getNode(nodeId);
                    if (node instanceof BaseNode baseNode) {
                        postStates.put(nodeId, deepCopyState(baseNode.getNodeState()));
                    }
                }
            }

            // 1. Rollback structural changes (additions/connections)
            boolean structuralRollbackComplete = true;
            for (int i = 0; i < undoStepsTaken; i++) {
                if (!editor.undo()) {
                    NodeCraft.LOGGER.warn("AI Patch undo partially failed during structural rollback at step {}", i);
                    structuralRollbackComplete = false;
                    break;
                }
            }

            // 2. Restore reused node states
            for (Map.Entry<UUID, Object> entry : previousStates.entrySet()) {
                INode node = graph.getNode(entry.getKey());
                if (node instanceof BaseNode baseNode) {
                    baseNode.setNodeState(deepCopyState(entry.getValue()));
                }
            }

            if (structuralRollbackComplete) {
                NodeCraft.LOGGER.info("AI Patch undone: {}", summary);
            } else {
                NodeCraft.LOGGER.warn("AI Patch undo incomplete: {}", summary);
            }
            return structuralRollbackComplete;
        }

        @Override
        public boolean redo(ICanvasEditor editor) {
            // 1. Re-apply structural changes
            for (int i = 0; i < undoStepsTaken; i++) {
                if (!editor.redo()) break;
            }

            // 2. Restore post-patch states
            NodeGraph graph = editor.getCurrentGraph();
            if (graph != null && !postStates.isEmpty()) {
                for (Map.Entry<UUID, Object> entry : postStates.entrySet()) {
                    INode node = graph.getNode(entry.getKey());
                    if (node instanceof BaseNode baseNode) {
                        baseNode.setNodeState(deepCopyState(entry.getValue()));
                    }
                }
            }

            NodeCraft.LOGGER.info("AI Patch redone: {}", summary);
            return true;
        }
        
        private Object deepCopyState(Object state) {
            if (state == null) return null;
            try {
                com.google.gson.Gson gson = new com.google.gson.Gson();
                return gson.fromJson(gson.toJson(state), Object.class);
            } catch (Exception e) {
                return state;
            }
        }
    }

    private static class NodeStateChangeAction extends HistoryAction {
        private final UUID nodeId;
        private final Object beforeState;
        private final Object afterState;
        private final String label;

        private NodeStateChangeAction(UUID nodeId, Object beforeState, Object afterState, String label) {
            this.nodeId = nodeId;
            this.beforeState = deepCopyState(beforeState);
            this.afterState = deepCopyState(afterState);
            this.label = label != null ? label : "Node State Change";
        }

        @Override
        public ActionType getType() {
            return ActionType.NODE_STATE_CHANGE;
        }

        @Override
        public boolean undo(ICanvasEditor editor) {
            return applyState(editor, beforeState);
        }

        @Override
        public boolean redo(ICanvasEditor editor) {
            return applyState(editor, afterState);
        }

        private boolean applyState(ICanvasEditor editor, Object state) {
            NodeGraph graph = editor.getCurrentGraph();
            if (graph == null) {
                return false;
            }
            INode node = graph.getNode(nodeId);
            if (!(node instanceof BaseNode baseNode)) {
                return false;
            }
            baseNode.setNodeState(deepCopyState(state));
            baseNode.markDirty();
            NodeCraft.LOGGER.info("Applied {} for node {}", label, nodeId);
            return true;
        }

        private static Object deepCopyState(Object state) {
            if (state == null) {
                return null;
            }
            com.google.gson.Gson gson = new com.google.gson.Gson();
            return gson.fromJson(gson.toJson(state), Object.class);
        }
    }

    /**
     * 历史操作基类
     */
    private abstract static class HistoryAction {
        public abstract ActionType getType();
        public abstract boolean undo(ICanvasEditor editor);
        public abstract boolean redo(ICanvasEditor editor);
    }
    
    /**
     * 添加节点操作
     */
    private static class AddNodeAction extends HistoryAction {
        private final UUID originalNodeId; // 记录原始节点ID
        private final String nodeTypeId;
        private final float x;
        private final float y;
        private final Object nodeState; // 节点序列化状态
        
        // 在重做时生成的新节点ID，用于后续撤销
        private UUID currentNodeId;
        
        public AddNodeAction(UUID nodeId, String nodeTypeId, float x, float y, Object nodeState) {
            this.originalNodeId = nodeId;
            this.nodeTypeId = nodeTypeId;
            this.x = x;
            this.y = y;
            this.nodeState = nodeState;
            this.currentNodeId = nodeId; // 初始时使用原始ID
        }
        
        @Override
        public ActionType getType() {
            return ActionType.ADD_NODE;
        }
        
        @Override
        public boolean undo(ICanvasEditor editor) {
            // 撤销添加节点 = 删除节点
            NodeGraph graph = editor.getCurrentGraph();
            if (graph == null) {
                NodeCraft.LOGGER.debug("撤销添加节点失败：当前图为空");
                return false;
            }
            
            boolean success = graph.removeNode(currentNodeId);
            if (success) {
                editor.removeNodePosition(currentNodeId);
                NodeCraft.LOGGER.debug("撤销添加节点成功：删除节点 {}", currentNodeId);
            } else {
                NodeCraft.LOGGER.debug("撤销添加节点失败：无法删除节点 {}", currentNodeId);
            }
            return success;
        }
        
        @Override
        public boolean redo(ICanvasEditor editor) {
            // 重做添加节点 = 再次添加节点
            try {
                // 使用addNodeWithState方法，传入旧ID和状态
                INode newNode = editor.addNodeWithState(nodeTypeId, originalNodeId, x, y, nodeState);
                if (newNode == null) {
                    NodeCraft.LOGGER.error("重做添加节点失败：无法创建节点类型 {}", nodeTypeId);
                    return false;
                }
                
                // 更新当前节点ID为新生成的ID
                this.currentNodeId = newNode.getId();
                
                NodeCraft.LOGGER.debug("重做添加节点成功：{}", newNode.getDisplayName());
                return true;
            } catch (Exception e) {
                NodeCraft.LOGGER.error("重做添加节点时出错：{} - {}", nodeTypeId, e.getMessage());
                return false;
            }
        }
    }
    
    /**
     * 删除节点操作
     */
    private static class RemoveNodeAction extends HistoryAction {
        private final UUID originalNodeId;
        private final String nodeTypeId;
        private final float x;
        private final float y;
        private final List<ConnectionInfo> connections;
        private final Object nodeState;
        
        // 在撤销时生成的新节点ID，用于后续重做
        private UUID currentNodeId;
        
        public RemoveNodeAction(UUID nodeId, String nodeTypeId, float x, float y, 
                               List<ConnectionInfo> connections, Object nodeState) {
            this.originalNodeId = nodeId;
            this.nodeTypeId = nodeTypeId;
            this.x = x;
            this.y = y;
            this.connections = connections;
            this.nodeState = nodeState;
            this.currentNodeId = nodeId; // 初始时使用原始ID
        }
        
        @Override
        public ActionType getType() {
            return ActionType.REMOVE_NODE;
        }
        
        @Override
        public boolean undo(ICanvasEditor editor) {
            // 撤销删除节点 = 重新添加节点并恢复连接
            try {
                // 使用addNodeWithState方法，传入旧ID和状态
                INode newNode = editor.addNodeWithState(nodeTypeId, originalNodeId, x, y, nodeState);
                if (newNode == null) {
                    NodeCraft.LOGGER.error("撤销删除节点失败：无法重新创建节点类型 {}", nodeTypeId);
                    return false;
                }
                
                // 更新当前节点ID为新生成的ID
                this.currentNodeId = newNode.getId();
                
                NodeCraft.LOGGER.debug("撤销删除节点成功：{}", newNode.getDisplayName());
                
                // 恢复连接
                restoreConnections(editor, originalNodeId, currentNodeId);
                
                return true;
            } catch (Exception e) {
                NodeCraft.LOGGER.error("撤销删除节点时出错：{} - {}", nodeTypeId, e.getMessage());
                return false;
            }
        }
        
        /**
         * 恢复连接，将旧节点ID替换为新节点ID
         */
        private void restoreConnections(ICanvasEditor editor, UUID oldNodeId, UUID newNodeId) {
            if (newNodeId == null) return;
            
            NodeGraph graph = editor.getCurrentGraph();
            if (graph == null) return;
            
            for (ConnectionInfo conn : connections) {
                UUID sourceId = conn.sourceNodeId.equals(oldNodeId) ? newNodeId : conn.sourceNodeId;
                UUID targetId = conn.targetNodeId.equals(oldNodeId) ? newNodeId : conn.targetNodeId;
                
                // 检查连接的节点是否存在
                INode sourceNode = graph.getNode(sourceId);
                INode targetNode = graph.getNode(targetId);
                
                if (sourceNode != null && targetNode != null) {
                    boolean connected = editor.connectPorts(sourceId, conn.sourcePortId, 
                                                          targetId, conn.targetPortId);
                    if (connected) {
                        NodeCraft.LOGGER.debug("恢复连接成功：{}({}) -> {}({})", 
                            sourceId, conn.sourcePortId, targetId, conn.targetPortId);
                    } else {
                        NodeCraft.LOGGER.debug("恢复连接失败：{}({}) -> {}({})", 
                            sourceId, conn.sourcePortId, targetId, conn.targetPortId);
                    }
                } else {
                    NodeCraft.LOGGER.debug("无法恢复连接：源节点 {} 或目标节点 {} 不存在", sourceId, targetId);
                }
            }
        }
        
        @Override
        public boolean redo(ICanvasEditor editor) {
            // 重做删除节点 = 再次删除节点
            NodeGraph graph = editor.getCurrentGraph();
            if (graph == null) {
                NodeCraft.LOGGER.debug("重做删除节点失败：当前图为空");
                return false;
            }
            
            boolean success = graph.removeNode(currentNodeId);
            if (success) {
                editor.removeNodePosition(currentNodeId);
                NodeCraft.LOGGER.debug("重做删除节点成功：删除节点 {}", currentNodeId);
            } else {
                NodeCraft.LOGGER.debug("重做删除节点失败：无法删除节点 {}", currentNodeId);
            }
            return success;
        }
    }
    
    /**
     * 添加连接操作
     */
    private static class AddConnectionAction extends HistoryAction {
        private final UUID sourceNodeId;
        private final String sourcePortId;
        private final UUID targetNodeId;
        private final String targetPortId;
        
        public AddConnectionAction(UUID sourceNodeId, String sourcePortId, UUID targetNodeId, String targetPortId) {
            this.sourceNodeId = sourceNodeId;
            this.sourcePortId = sourcePortId;
            this.targetNodeId = targetNodeId;
            this.targetPortId = targetPortId;
        }
        
        @Override
        public ActionType getType() {
            return ActionType.ADD_CONNECTION;
        }
        
        @Override
        public boolean undo(ICanvasEditor editor) {
            // 撤销添加连接 = 断开连接
            boolean success = editor.disconnectPorts(sourceNodeId, sourcePortId, targetNodeId, targetPortId);
            NodeCraft.LOGGER.debug("撤销添加连接{}：{}({}) -> {}({})", 
                success ? "成功" : "失败", sourceNodeId, sourcePortId, targetNodeId, targetPortId);
            return success;
        }
        
        @Override
        public boolean redo(ICanvasEditor editor) {
            // 重做添加连接 = 再次建立连接
            boolean success = editor.connectPorts(sourceNodeId, sourcePortId, targetNodeId, targetPortId);
            NodeCraft.LOGGER.debug("重做添加连接{}：{}({}) -> {}({})", 
                success ? "成功" : "失败", sourceNodeId, sourcePortId, targetNodeId, targetPortId);
            return success;
        }
    }
    
    /**
     * 删除连接操作
     */
    private static class RemoveConnectionAction extends HistoryAction {
        private final UUID sourceNodeId;
        private final String sourcePortId;
        private final UUID targetNodeId;
        private final String targetPortId;
        
        public RemoveConnectionAction(UUID sourceNodeId, String sourcePortId, UUID targetNodeId, String targetPortId) {
            this.sourceNodeId = sourceNodeId;
            this.sourcePortId = sourcePortId;
            this.targetNodeId = targetNodeId;
            this.targetPortId = targetPortId;
        }
        
        @Override
        public ActionType getType() {
            return ActionType.REMOVE_CONNECTION;
        }
        
        @Override
        public boolean undo(ICanvasEditor editor) {
            // 撤销删除连接 = 重新建立连接
            boolean success = editor.connectPorts(sourceNodeId, sourcePortId, targetNodeId, targetPortId);
            NodeCraft.LOGGER.debug("撤销删除连接{}：{}({}) -> {}({})", 
                success ? "成功" : "失败", sourceNodeId, sourcePortId, targetNodeId, targetPortId);
            return success;
        }
        
        @Override
        public boolean redo(ICanvasEditor editor) {
            // 重做删除连接 = 再次断开连接
            boolean success = editor.disconnectPorts(sourceNodeId, sourcePortId, targetNodeId, targetPortId);
            NodeCraft.LOGGER.debug("重做删除连接{}：{}({}) -> {}({})", 
                success ? "成功" : "失败", sourceNodeId, sourcePortId, targetNodeId, targetPortId);
            return success;
        }
    }

    public static class RemovedNodeSnapshot {
        public final UUID nodeId;
        public final String nodeTypeId;
        public final float x;
        public final float y;
        public final List<ConnectionInfo> connections;
        public final Object nodeState;

        public RemovedNodeSnapshot(UUID nodeId, String nodeTypeId, float x, float y,
                                  List<ConnectionInfo> connections, Object nodeState) {
            this.nodeId = nodeId;
            this.nodeTypeId = nodeTypeId;
            this.x = x;
            this.y = y;
            this.connections = connections;
            this.nodeState = nodeState;
        }
    }

    private static class RemoveNodesAction extends HistoryAction {
        private final List<RemovedNodeSnapshot> snapshots;
        private final Map<UUID, UUID> currentNodeIdMap = new HashMap<>();

        public RemoveNodesAction(List<RemovedNodeSnapshot> snapshots) {
            this.snapshots = new ArrayList<>(snapshots);
            for (RemovedNodeSnapshot snapshot : this.snapshots) {
                currentNodeIdMap.put(snapshot.nodeId, snapshot.nodeId);
            }
        }

        @Override
        public ActionType getType() {
            return ActionType.REMOVE_NODES;
        }

        @Override
        public boolean undo(ICanvasEditor editor) {
            Map<UUID, UUID> restoredIdMap = new HashMap<>();
            for (RemovedNodeSnapshot snapshot : snapshots) {
                INode restoredNode = editor.addNodeWithState(
                    snapshot.nodeTypeId,
                    snapshot.nodeId,
                    snapshot.x,
                    snapshot.y,
                    snapshot.nodeState
                );
                if (restoredNode == null) {
                    NodeCraft.LOGGER.error("批量撤销删除失败：无法恢复节点类型 {}", snapshot.nodeTypeId);
                    return false;
                }
                restoredIdMap.put(snapshot.nodeId, restoredNode.getId());
            }

            Set<String> restoredConnections = new HashSet<>();
            for (RemovedNodeSnapshot snapshot : snapshots) {
                for (ConnectionInfo connection : snapshot.connections) {
                    UUID sourceId = restoredIdMap.getOrDefault(connection.sourceNodeId, connection.sourceNodeId);
                    UUID targetId = restoredIdMap.getOrDefault(connection.targetNodeId, connection.targetNodeId);
                    String key = sourceId + "|" + connection.sourcePortId + "|" + targetId + "|" + connection.targetPortId;
                    if (restoredConnections.contains(key)) {
                        continue;
                    }

                    boolean connected = editor.connectPorts(sourceId, connection.sourcePortId, targetId, connection.targetPortId);
                    if (connected) {
                        restoredConnections.add(key);
                    }
                }
            }

            currentNodeIdMap.clear();
            currentNodeIdMap.putAll(restoredIdMap);
            return true;
        }

        @Override
        public boolean redo(ICanvasEditor editor) {
            NodeGraph graph = editor.getCurrentGraph();
            if (graph == null) {
                return false;
            }

            boolean success = true;
            for (RemovedNodeSnapshot snapshot : snapshots) {
                UUID currentNodeId = currentNodeIdMap.getOrDefault(snapshot.nodeId, snapshot.nodeId);
                boolean removed = graph.removeNode(currentNodeId);
                if (removed) {
                    editor.removeNodePosition(currentNodeId);
                    editor.removeSelectedNode(currentNodeId);
                } else {
                    success = false;
                }
            }
            return success;
        }
    }
    
    /**
     * 连接信息类
     */
    public static class ConnectionInfo {
        public final UUID sourceNodeId;
        public final String sourcePortId;
        public final UUID targetNodeId;
        public final String targetPortId;
        
        public ConnectionInfo(UUID sourceNodeId, String sourcePortId, UUID targetNodeId, String targetPortId) {
            this.sourceNodeId = sourceNodeId;
            this.sourcePortId = sourcePortId;
            this.targetNodeId = targetNodeId;
            this.targetPortId = targetPortId;
        }
    }

    private record HistoryScope(Stack<HistoryAction> undoStack, Stack<HistoryAction> redoStack, boolean recording) {
    }
    
    /**
     * 操作类型枚举
     */
    public enum ActionType {
        ADD_NODE,
        REMOVE_NODE,
        REMOVE_NODES,
        ADD_CONNECTION,
        REMOVE_CONNECTION,
        GRAPH_TRANSACTION,
        AI_PATCH,
        NODE_STATE_CHANGE
    }
    
    /**
     * 获取历史记录统计信息（用于调试和监控）
     * @return 包含撤销栈和重做栈大小的字符串
     */
    public String getHistoryStats() {
        return String.format("撤销栈: %d, 重做栈: %d, 最大容量: %d", 
            undoStack.size(), redoStack.size(), maxHistorySize);
    }
    
    /**
     * 检查历史记录是否为空
     * @return 如果撤销栈和重做栈都为空则返回true
     */
    public boolean isEmpty() {
        return undoStack.isEmpty() && redoStack.isEmpty();
    }
    
    /**
     * 测试历史记录功能（仅用于调试）
     * @return 测试结果描述
     */
    public String testHistoryFunction() {
        StringBuilder result = new StringBuilder();
        result.append("历史记录功能测试:\n");
        result.append(String.format("- 记录状态: %s\n", isRecording ? "启用" : "禁用"));
        result.append(String.format("- 撤销栈大小: %d\n", undoStack.size()));
        result.append(String.format("- 重做栈大小: %d\n", redoStack.size()));
        result.append(String.format("- 最大容量: %d\n", maxHistorySize));
        result.append(String.format("- 可以撤销: %s\n", canUndo() ? "是" : "否"));
        result.append(String.format("- 可以重做: %s\n", canRedo() ? "是" : "否"));
        
        // 详细显示撤销栈内容
        if (!undoStack.isEmpty()) {
            result.append("- 撤销栈内容 (从顶到底):\n");
            for (int i = undoStack.size() - 1; i >= 0; i--) {
                HistoryAction action = undoStack.get(i);
                result.append(String.format("  [%d] %s\n", undoStack.size() - i, action.getType()));
            }
        } else {
            result.append("- 撤销栈为空\n");
        }
        
        // 详细显示重做栈内容
        if (!redoStack.isEmpty()) {
            result.append("- 重做栈内容 (从顶到底):\n");
            for (int i = redoStack.size() - 1; i >= 0; i--) {
                HistoryAction action = redoStack.get(i);
                result.append(String.format("  [%d] %s\n", redoStack.size() - i, action.getType()));
            }
        } else {
            result.append("- 重做栈为空\n");
        }
        
        return result.toString();
    }
}

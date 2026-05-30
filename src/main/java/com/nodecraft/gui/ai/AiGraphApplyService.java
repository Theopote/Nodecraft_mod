package com.nodecraft.gui.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.nodecraft.core.NodeCraft;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.gui.editor.impl.ImGuiNodeEditor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class AiGraphApplyService {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private AiGraphApplyService() {
    }

    public record ApplyNode(String ref, String typeId, float offsetX, float offsetY, Object nodeState) {
    }

    public record ApplyConnection(String sourceRef, String sourcePortId, String targetRef, String targetPortId) {
    }

    public record ApplyResult(boolean success, int undoSteps, String statusMessage) {
    }

    private record CurrentNodeInfo(UUID id, String typeId, String paramSignature) {
    }

    public static ApplyResult applyPatch(
            ImGuiNodeEditor editor,
            NodeGraph graph,
            List<ApplyNode> nodesToApply,
            List<ApplyConnection> connections,
            float[] anchor,
            boolean removeScopedConnections,
            boolean mergeExistingNodeState
    ) {
        if (graph == null) {
            return new ApplyResult(false, 0, "Patch apply failed: current graph is unavailable.");
        }
        if (editor == null) {
            return new ApplyResult(false, 0, "Patch apply failed: editor is unavailable.");
        }
        if (nodesToApply == null) {
            nodesToApply = List.of();
        }
        if (connections == null) {
            connections = List.of();
        }
        if (anchor == null || anchor.length < 2) {
            anchor = new float[]{0.0f, 0.0f};
        }

        List<CurrentNodeInfo> currentNodes = new ArrayList<>();
        Map<String, List<CurrentNodeInfo>> byType = new HashMap<>();
        for (INode node : graph.getNodes()) {
            Object state = node instanceof BaseNode baseNode ? baseNode.getNodeState() : null;
            CurrentNodeInfo info = new CurrentNodeInfo(node.getId(), node.getTypeId(), normalizeStateForSignature(state));
            currentNodes.add(info);
            byType.computeIfAbsent(info.typeId(), key -> new ArrayList<>()).add(info);
        }

        Set<UUID> usedCurrent = new HashSet<>();
        Map<String, UUID> planRefToNodeId = new HashMap<>();
        Map<UUID, Object> previousStates = new HashMap<>();
        int undoSteps = 0;
        int successfulConnections = 0;
        int replacedIncomingConnections = 0;
        int removedScopedConnectionsCount = 0;
        int reusedNodes = 0;
        int createdNodes = 0;
        int updatedNodes = 0;

        try {
            for (ApplyNode node : nodesToApply) {
                CurrentNodeInfo matched = matchCurrentNode(node, byType, usedCurrent);
                if (matched != null) {
                    usedCurrent.add(matched.id());
                    planRefToNodeId.put(node.ref(), matched.id());
                    reusedNodes++;

                    INode existing = graph.getNode(matched.id());
                    if (existing instanceof BaseNode existingBaseNode) {
                        String plannedSig = normalizeStateForSignature(node.nodeState());
                        if (!plannedSig.equals(matched.paramSignature())) {
                            previousStates.putIfAbsent(matched.id(), deepCopyNodeState(existingBaseNode.getNodeState()));
                            Object nextState = mergeNodeState(
                                    existingBaseNode.getNodeState(),
                                    node.nodeState(),
                                    mergeExistingNodeState
                            );
                            existingBaseNode.setNodeState(nextState);
                            updatedNodes++;
                        }
                    }
                    continue;
                }

                float x = anchor[0] + node.offsetX();
                float y = anchor[1] + node.offsetY();
                INode created = node.nodeState() == null
                        ? editor.addNode(node.typeId(), x, y)
                        : editor.addNodeWithState(node.typeId(), null, x, y, node.nodeState());

                if (created == null) {
                    rollbackAiApply(editor, undoSteps);
                    restoreNodeStates(graph, previousStates);
                    return new ApplyResult(false, undoSteps, "Patch apply failed to create node: " + node.ref() + " (" + node.typeId() + "). Auto-rolled back.");
                }

                planRefToNodeId.put(node.ref(), created.getId());
                undoSteps++;
                createdNodes++;
            }

            for (ApplyConnection connection : connections) {
                UUID sourceNodeId = planRefToNodeId.get(connection.sourceRef());
                UUID targetNodeId = planRefToNodeId.get(connection.targetRef());
                if (sourceNodeId == null || targetNodeId == null) {
                    rollbackAiApply(editor, undoSteps);
                    restoreNodeStates(graph, previousStates);
                    return new ApplyResult(false, undoSteps, "Patch apply connection failed due to missing mapped node ref: "
                            + connection.sourceRef() + " -> " + connection.targetRef() + ". Auto-rolled back.");
                }

                if (graph.isConnected(sourceNodeId, connection.sourcePortId(), targetNodeId, connection.targetPortId())) {
                    continue;
                }

                INode targetNode = graph.getNode(targetNodeId);
                IPort targetPort = findInputPortById(targetNode, connection.targetPortId());
                if (targetPort == null) {
                    rollbackAiApply(editor, undoSteps);
                    restoreNodeStates(graph, previousStates);
                    return new ApplyResult(false, undoSteps, "Patch apply failed: target input port not found for "
                            + connection.targetRef() + "." + connection.targetPortId() + ".");
                }

                if (!targetPort.allowsMultipleIncomingConnections()) {
                    UUID oldSourceNodeId = graph.getConnectedOutputNodeId(targetNodeId, targetPort.getId());
                    String oldSourcePortId = graph.getConnectedOutputPortId(targetNodeId, targetPort.getId());
                    boolean hasDifferentExisting = oldSourceNodeId != null
                            && oldSourcePortId != null
                            && (!oldSourceNodeId.equals(sourceNodeId) || !oldSourcePortId.equals(connection.sourcePortId()));

                    if (hasDifferentExisting) {
                        boolean disconnected = editor.disconnectPorts(
                                oldSourceNodeId,
                                oldSourcePortId,
                                targetNodeId,
                                targetPort.getId()
                        );
                        if (!disconnected) {
                            rollbackAiApply(editor, undoSteps);
                            restoreNodeStates(graph, previousStates);
                            return new ApplyResult(false, undoSteps, "Patch apply failed to replace existing connection on "
                                    + connection.targetRef() + "." + connection.targetPortId()
                                    + ". Try Dry Run for details.");
                        }
                        undoSteps++;
                        replacedIncomingConnections++;
                    }
                }

                boolean connected = editor.connectPorts(
                        sourceNodeId,
                        connection.sourcePortId(),
                        targetNodeId,
                        connection.targetPortId()
                );
                if (!connected) {
                    rollbackAiApply(editor, undoSteps);
                    restoreNodeStates(graph, previousStates);
                    return new ApplyResult(false, undoSteps, "Patch apply failed to connect: "
                            + connection.sourceRef() + "." + connection.sourcePortId()
                            + " -> " + connection.targetRef() + "." + connection.targetPortId()
                            + ". Try disabling patch apply mode or run Dry Run to inspect conflicts.");
                }

                successfulConnections++;
                undoSteps++;
            }

            if (removeScopedConnections) {
                Map<String, Integer> plannedScopedCounts = buildPlannedScopedConnectionCounts(connections, planRefToNodeId);
                List<NodeGraph.Connection> existingConnections = graph.getConnections();
                for (NodeGraph.Connection conn : existingConnections) {
                    UUID currentSource = conn.sourceNode.getId();
                    UUID currentTarget = conn.targetNode.getId();
                    if (!usedCurrent.contains(currentSource) || !usedCurrent.contains(currentTarget)) {
                        continue;
                    }

                    String signature = buildMappedConnectionSignature(
                            "CUR:" + currentSource,
                            conn.sourcePort.getId(),
                            "CUR:" + currentTarget,
                            conn.targetPort.getId()
                    );

                    int remain = plannedScopedCounts.getOrDefault(signature, 0);
                    if (remain > 0) {
                        plannedScopedCounts.put(signature, remain - 1);
                        continue;
                    }

                    boolean disconnected = editor.disconnectPorts(
                            currentSource,
                            conn.sourcePort.getId(),
                            currentTarget,
                            conn.targetPort.getId()
                    );
                    if (!disconnected) {
                        rollbackAiApply(editor, undoSteps);
                        restoreNodeStates(graph, previousStates);
                        return new ApplyResult(false, undoSteps, "Patch apply failed while removing scoped stale connection. Try Dry Run first.");
                    }
                    undoSteps++;
                    removedScopedConnectionsCount++;
                }
            }

            String status = "Patch apply completed: reused " + reusedNodes
                    + ", created " + createdNodes
                    + ", updated " + updatedNodes
                    + ", connected " + successfulConnections
                    + ", replacedIncoming " + replacedIncomingConnections
                    + ", removedScoped " + removedScopedConnectionsCount
                    + ". Undo available: 1 grouped AI patch action.";

            if (editor.getHistory() != null) {
                editor.getHistory().recordAiPatch(status, previousStates, undoSteps);
            }

            return new ApplyResult(true, undoSteps, status);
        } catch (Exception e) {
            rollbackAiApply(editor, undoSteps);
            restoreNodeStates(graph, previousStates);
            NodeCraft.LOGGER.error("Failed to patch-apply AI plan", e);
            return new ApplyResult(false, undoSteps, "Patch apply failed: " + e.getMessage() + ". Auto-rolled back.");
        }
    }

    private static CurrentNodeInfo matchCurrentNode(
            ApplyNode planned,
            Map<String, List<CurrentNodeInfo>> byType,
            Set<UUID> usedCurrent
    ) {
        List<CurrentNodeInfo> candidates = byType.get(planned.typeId());
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        String plannedSig = normalizeStateForSignature(planned.nodeState());
        for (CurrentNodeInfo candidate : candidates) {
            if (!usedCurrent.contains(candidate.id()) && plannedSig.equals(candidate.paramSignature())) {
                return candidate;
            }
        }
        for (CurrentNodeInfo candidate : candidates) {
            if (!usedCurrent.contains(candidate.id())) {
                return candidate;
            }
        }
        return null;
    }

    private static Object deepCopyNodeState(Object state) {
        if (state == null) {
            return null;
        }
        try {
            return GSON.fromJson(GSON.toJsonTree(state), Object.class);
        } catch (Exception e) {
            return state;
        }
    }

    private static Object mergeNodeState(Object existingState, Object plannedState, boolean mergeExistingNodeState) {
        if (!mergeExistingNodeState) {
            return deepCopyNodeState(plannedState);
        }
        if (plannedState == null) {
            return deepCopyNodeState(existingState);
        }
        if (existingState instanceof Map<?, ?> existingMap && plannedState instanceof Map<?, ?> plannedMap) {
            return mergeMapState(existingMap, plannedMap);
        }
        return deepCopyNodeState(plannedState);
    }

    private static Map<String, Object> mergeMapState(Map<?, ?> existingMap, Map<?, ?> plannedMap) {
        Map<String, Object> merged = new HashMap<>();
        for (Map.Entry<?, ?> entry : existingMap.entrySet()) {
            merged.put(String.valueOf(entry.getKey()), deepCopyNodeState(entry.getValue()));
        }
        for (Map.Entry<?, ?> entry : plannedMap.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object plannedValue = entry.getValue();
            Object existingValue = merged.get(key);
            if (existingValue instanceof Map<?, ?> existingNested && plannedValue instanceof Map<?, ?> plannedNested) {
                merged.put(key, mergeMapState(existingNested, plannedNested));
                continue;
            }
            merged.put(key, deepCopyNodeState(plannedValue));
        }
        return merged;
    }

    private static void restoreNodeStates(NodeGraph graph, Map<UUID, Object> previousStates) {
        if (graph == null || previousStates == null || previousStates.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, Object> entry : previousStates.entrySet()) {
            INode node = graph.getNode(entry.getKey());
            if (node instanceof BaseNode baseNode) {
                baseNode.setNodeState(deepCopyNodeState(entry.getValue()));
            }
        }
    }

    private static IPort findInputPortById(INode node, String portId) {
        if (node == null || portId == null || portId.isBlank()) {
            return null;
        }
        for (IPort port : node.getInputPorts()) {
            if (portId.equals(port.getId())) {
                return port;
            }
        }
        return null;
    }

    private static Map<String, Integer> buildPlannedScopedConnectionCounts(
            List<ApplyConnection> connections,
            Map<String, UUID> planRefToNodeId
    ) {
        Map<String, Integer> counts = new HashMap<>();
        if (connections == null) {
            return counts;
        }

        for (ApplyConnection conn : connections) {
            UUID sourceId = planRefToNodeId.get(conn.sourceRef());
            UUID targetId = planRefToNodeId.get(conn.targetRef());
            if (sourceId == null || targetId == null) {
                continue;
            }

            String signature = buildMappedConnectionSignature(
                    "CUR:" + sourceId,
                    conn.sourcePortId(),
                    "CUR:" + targetId,
                    conn.targetPortId()
            );
            counts.merge(signature, 1, Integer::sum);
        }
        return counts;
    }

    private static int rollbackAiApply(ImGuiNodeEditor editor, int undoSteps) {
        int undone = 0;
        for (int i = 0; i < undoSteps; i++) {
            if (!editor.undo()) {
                break;
            }
            undone++;
        }
        return undone;
    }

    private static String buildMappedConnectionSignature(String sourceToken, String sourcePort, String targetToken, String targetPort) {
        return nullToEmpty(sourceToken) + "." + nullToEmpty(sourcePort)
                + " -> " + nullToEmpty(targetToken) + "." + nullToEmpty(targetPort);
    }

    private static String normalizeStateForSignature(Object state) {
        if (state == null) {
            return "{}";
        }
        try {
            return GSON.toJson(canonicalizeForSignature(state));
        } catch (Exception e) {
            return "{\"_error\":\"state-normalize-failed\"}";
        }
    }

    private static Object canonicalizeForSignature(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> canonical = new java.util.TreeMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                canonical.put(String.valueOf(entry.getKey()), canonicalizeForSignature(entry.getValue()));
            }
            return canonical;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> canonical = new ArrayList<>();
            for (Object item : iterable) {
                canonical.add(canonicalizeForSignature(item));
            }
            return canonical;
        }
        return String.valueOf(value);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

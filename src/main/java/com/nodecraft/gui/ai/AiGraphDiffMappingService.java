package com.nodecraft.gui.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.graph.NodeGraph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

final class AiGraphDiffMappingService {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private record CurrentNodeInfo(UUID id, String typeId, String paramSignature) {
    }

    private AiGraphDiffMappingService() {
    }

    static AiGraphDiffService.MappedDiffSummary buildMappedDiffSummary(AiGraphDiffService.GraphPlan plan, NodeGraph graph) {
        if (plan == null) {
            return new AiGraphDiffService.MappedDiffSummary(0, 0, 0, 0, 0, 0, 0,
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }
        if (graph == null) {
            int nodeCount = plan.nodes() == null ? 0 : plan.nodes().size();
            int connCount = plan.connections() == null ? 0 : plan.connections().size();
            return new AiGraphDiffService.MappedDiffSummary(0, nodeCount, 0, 0, connCount, 0, 0,
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }

        Map<UUID, String> currentTypeById = new HashMap<>();
        Map<String, List<CurrentNodeInfo>> currentNodesByType = indexCurrentNodes(graph, currentTypeById);
        Set<UUID> usedCurrent = new HashSet<>();
        Map<String, CurrentNodeInfo> refToMatched = new HashMap<>();

        List<String> reuseSamples = new ArrayList<>();
        List<String> createSamples = new ArrayList<>();
        List<String> paramUpdateSamples = new ArrayList<>();
        int unchanged = 0;
        int paramUpdates = 0;

        for (AiGraphDiffService.PlanNode planned : safeNodes(plan)) {
            CurrentNodeInfo matched = matchCurrentNode(planned, currentNodesByType, usedCurrent);
            if (matched == null) {
                if (createSamples.size() < 12) {
                    createSamples.add(planned.ref() + " -> " + planned.typeId());
                }
                continue;
            }

            refToMatched.put(planned.ref(), matched);
            usedCurrent.add(matched.id());
            if (reuseSamples.size() < 12) {
                reuseSamples.add(planned.ref() + " -> existing " + shortUuid(matched.id()) + " (" + matched.typeId() + ")");
            }

            String plannedSig = normalizeStateForSignature(planned.nodeState());
            if (plannedSig.equals(matched.paramSignature())) {
                unchanged++;
            } else {
                paramUpdates++;
                if (paramUpdateSamples.size() < 12) {
                    paramUpdateSamples.add(planned.ref() + " -> " + matched.typeId());
                }
            }
        }

        Set<String> currentConnAll = new HashSet<>();
        Set<String> currentConnScoped = new HashSet<>();
        for (NodeGraph.Connection conn : graph.getConnections()) {
            String signature = buildMappedConnectionSignature(
                    "CUR:" + conn.sourceNode.getId(),
                    conn.sourcePort.getId(),
                    "CUR:" + conn.targetNode.getId(),
                    conn.targetPort.getId()
            );
            currentConnAll.add(signature);
            if (usedCurrent.contains(conn.sourceNode.getId()) && usedCurrent.contains(conn.targetNode.getId())) {
                currentConnScoped.add(signature);
            }
        }

        Set<String> plannedConnMappedAll = new HashSet<>();
        Set<String> plannedConnMappedScoped = new HashSet<>();
        List<String> incomingReplacementSamples = new ArrayList<>();
        int incomingReplacementCandidates = 0;
        for (AiGraphDiffService.PlanConnection conn : safeConnections(plan)) {
            String sourceToken = tokenForPlanRef(conn.sourceRef(), refToMatched);
            String targetToken = tokenForPlanRef(conn.targetRef(), refToMatched);
            String mapped = buildMappedConnectionSignature(sourceToken, conn.sourcePortId(), targetToken, conn.targetPortId());
            plannedConnMappedAll.add(mapped);

            if (sourceToken.startsWith("CUR:") && targetToken.startsWith("CUR:")) {
                plannedConnMappedScoped.add(mapped);
            }

            if (targetToken.startsWith("CUR:")) {
                UUID targetId = parseCurrentTokenUuid(targetToken);
                UUID sourceId = parseCurrentTokenUuid(sourceToken);
                if (targetId != null && sourceId != null) {
                    INode targetNode = graph.getNode(targetId);
                    IPort targetPort = findInputPortById(targetNode, conn.targetPortId());
                    if (targetPort != null && !targetPort.allowsMultipleIncomingConnections()) {
                        UUID oldSourceNodeId = graph.getConnectedOutputNodeId(targetId, targetPort.getId());
                        String oldSourcePortId = graph.getConnectedOutputPortId(targetId, targetPort.getId());
                        boolean replaceNeeded = oldSourceNodeId != null
                                && oldSourcePortId != null
                                && (!oldSourceNodeId.equals(sourceId) || !oldSourcePortId.equals(conn.sourcePortId()));
                        if (replaceNeeded) {
                            incomingReplacementCandidates++;
                            if (incomingReplacementSamples.size() < 12) {
                                incomingReplacementSamples.add(
                                        shortUuid(oldSourceNodeId) + "." + oldSourcePortId
                                                + " => " + shortUuid(sourceId) + "." + conn.sourcePortId()
                                                + " @ " + shortUuid(targetId) + "." + conn.targetPortId()
                                );
                            }
                        }
                    }
                }
            }
        }

        List<String> connectionAddSamples = new ArrayList<>();
        int connAdd = 0;
        List<String> sortedPlannedConn = new ArrayList<>(plannedConnMappedAll);
        Collections.sort(sortedPlannedConn);
        for (String conn : sortedPlannedConn) {
            if (currentConnAll.contains(conn)) {
                continue;
            }
            connAdd++;
            if (connectionAddSamples.size() < 12) {
                connectionAddSamples.add(formatMappedConnectionForDisplay(conn, currentTypeById));
            }
        }

        List<String> connectionRemoveSamples = new ArrayList<>();
        int connRemove = 0;
        List<String> sortedCurrentScoped = new ArrayList<>(currentConnScoped);
        Collections.sort(sortedCurrentScoped);
        for (String conn : sortedCurrentScoped) {
            if (plannedConnMappedScoped.contains(conn)) {
                continue;
            }
            connRemove++;
            if (connectionRemoveSamples.size() < 12) {
                connectionRemoveSamples.add(formatMappedConnectionForDisplay(conn, currentTypeById));
            }
        }

        return new AiGraphDiffService.MappedDiffSummary(
                refToMatched.size(),
                safeNodes(plan).size() - refToMatched.size(),
                unchanged,
                paramUpdates,
                connAdd,
                connRemove,
                incomingReplacementCandidates,
                reuseSamples,
                createSamples,
                paramUpdateSamples,
                connectionAddSamples,
                connectionRemoveSamples,
                incomingReplacementSamples
        );
    }

    private static Map<String, List<CurrentNodeInfo>> indexCurrentNodes(NodeGraph graph, Map<UUID, String> currentTypeById) {
        Map<String, List<CurrentNodeInfo>> byType = new HashMap<>();
        for (INode node : graph.getNodes()) {
            Object state = node instanceof BaseNode baseNode ? baseNode.getNodeState() : null;
            String signature = normalizeStateForSignature(state);
            CurrentNodeInfo info = new CurrentNodeInfo(node.getId(), node.getTypeId(), signature);
            byType.computeIfAbsent(info.typeId(), key -> new ArrayList<>()).add(info);
            currentTypeById.put(node.getId(), node.getTypeId());
        }
        return byType;
    }

    private static List<AiGraphDiffService.PlanNode> safeNodes(AiGraphDiffService.GraphPlan plan) {
        return plan.nodes() == null ? List.of() : plan.nodes();
    }

    private static List<AiGraphDiffService.PlanConnection> safeConnections(AiGraphDiffService.GraphPlan plan) {
        return plan.connections() == null ? List.of() : plan.connections();
    }

    private static CurrentNodeInfo matchCurrentNode(
            AiGraphDiffService.PlanNode planned,
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

    private static String tokenForPlanRef(String ref, Map<String, CurrentNodeInfo> refToMatched) {
        CurrentNodeInfo matched = refToMatched.get(ref);
        if (matched != null) {
            return "CUR:" + matched.id();
        }
        return "NEW:" + nullToEmpty(ref);
    }

    private static UUID parseCurrentTokenUuid(String token) {
        if (token == null || !token.startsWith("CUR:")) {
            return null;
        }
        try {
            return UUID.fromString(token.substring(4));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String buildMappedConnectionSignature(String sourceToken, String sourcePort, String targetToken, String targetPort) {
        return nullToEmpty(sourceToken) + "." + nullToEmpty(sourcePort)
                + " -> " + nullToEmpty(targetToken) + "." + nullToEmpty(targetPort);
    }

    private static String formatMappedConnectionForDisplay(String signature, Map<UUID, String> currentTypeById) {
        if (signature == null || signature.isBlank()) {
            return "(empty)";
        }
        String[] halves = signature.split(" -> ", 2);
        if (halves.length != 2) {
            return signature;
        }
        return decorateMappedEndpoint(halves[0], currentTypeById) + " -> " + decorateMappedEndpoint(halves[1], currentTypeById);
    }

    private static String decorateMappedEndpoint(String endpoint, Map<UUID, String> currentTypeById) {
        if (endpoint == null || endpoint.isBlank()) {
            return "?";
        }
        int dot = endpoint.lastIndexOf('.');
        if (dot < 0) {
            return endpoint;
        }

        String token = endpoint.substring(0, dot);
        String port = endpoint.substring(dot + 1);
        if (token.startsWith("CUR:")) {
            UUID id = parseCurrentTokenUuid(token);
            if (id == null) {
                return token + "." + port;
            }
            String type = currentTypeById.getOrDefault(id, "unknown");
            return "CUR(" + type + ":" + shortUuid(id) + ")." + port;
        }
        return endpoint;
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
            Map<String, Object> canonical = new TreeMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                canonical.put(String.valueOf(entry.getKey()), canonicalizeForSignature(entry.getValue()));
            }
            return canonical;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> canonical = new ArrayList<>(collection.size());
            for (Object item : collection) {
                canonical.add(canonicalizeForSignature(item));
            }
            return canonical;
        }
        return String.valueOf(value);
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

    private static String shortUuid(UUID id) {
        if (id == null) {
            return "unknown";
        }
        String text = id.toString();
        return text.length() <= 8 ? text : text.substring(0, 8);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

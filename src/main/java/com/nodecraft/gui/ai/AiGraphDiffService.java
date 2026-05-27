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

public final class AiGraphDiffService {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private AiGraphDiffService() {
    }

    public record PlanNode(String ref, String typeId, Object nodeState) {
    }

    public record PlanConnection(String sourceRef, String sourcePortId, String targetRef, String targetPortId) {
    }

    public record GraphPlan(List<PlanNode> nodes, List<PlanConnection> connections) {
    }

    private record CurrentNodeInfo(UUID id, String typeId, String paramSignature) {
    }

    public record GraphDiffSummary(
            int nodeAdditions,
            int nodeMissingFromPlan,
            int connectionAdditions,
            int connectionMissingFromPlan,
            List<String> nodeAdditionSamples,
            List<String> nodeMissingSamples,
            List<String> connectionAdditionSamples,
            List<String> connectionMissingSamples
    ) {
    }

    public record MappedDiffSummary(
            int reusableNodeMatches,
            int newNodesToCreate,
            int unchangedReusableNodes,
            int paramUpdateCandidates,
            int connectionAdditions,
            int connectionRemovalCandidates,
            int incomingReplacementCandidates,
            List<String> nodeReuseSamples,
            List<String> nodeCreationSamples,
            List<String> paramUpdateSamples,
            List<String> connectionAdditionSamples,
            List<String> connectionRemovalSamples,
            List<String> incomingReplacementSamples
    ) {
    }

    public static GraphDiffSummary buildGraphDiffSummary(GraphPlan plan, NodeGraph graph) {
        if (plan == null || graph == null) {
            return new GraphDiffSummary(0, 0, 0, 0, List.of(), List.of(), List.of(), List.of());
        }

        Map<String, Integer> currentNodeCounts = buildCurrentNodeSignatureCounts(graph);
        Map<String, Integer> plannedNodeCounts = buildPlannedNodeSignatureCounts(plan);

        Map<String, Integer> currentConnectionCounts = buildCurrentConnectionSignatureCounts(graph);
        Map<String, Integer> plannedConnectionCounts = buildPlannedConnectionSignatureCounts(plan);

        List<String> nodeAdds = new ArrayList<>();
        List<String> nodeMissing = new ArrayList<>();
        int nodeAddTotal = collectMultisetDelta(plannedNodeCounts, currentNodeCounts, nodeAdds, true);
        int nodeMissingTotal = collectMultisetDelta(currentNodeCounts, plannedNodeCounts, nodeMissing, false);

        List<String> connAdds = new ArrayList<>();
        List<String> connMissing = new ArrayList<>();
        int connAddTotal = collectMultisetDelta(plannedConnectionCounts, currentConnectionCounts, connAdds, false);
        int connMissingTotal = collectMultisetDelta(currentConnectionCounts, plannedConnectionCounts, connMissing, false);

        return new GraphDiffSummary(
                nodeAddTotal,
                nodeMissingTotal,
                connAddTotal,
                connMissingTotal,
                nodeAdds,
                nodeMissing,
                connAdds,
                connMissing
        );
    }

    public static MappedDiffSummary buildMappedDiffSummary(GraphPlan plan, NodeGraph graph) {
        if (plan == null) {
            return new MappedDiffSummary(0, 0, 0, 0, 0, 0, 0,
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }
        if (graph == null) {
            int nodeCount = plan.nodes() == null ? 0 : plan.nodes().size();
            int connCount = plan.connections() == null ? 0 : plan.connections().size();
            return new MappedDiffSummary(0, nodeCount, 0, 0, connCount, 0, 0,
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }

        List<CurrentNodeInfo> currentNodes = new ArrayList<>();
        Map<UUID, String> currentTypeById = new HashMap<>();
        for (INode node : graph.getNodes()) {
            Object state = node instanceof BaseNode baseNode ? baseNode.getNodeState() : null;
            String signature = normalizeStateForSignature(state);
            currentNodes.add(new CurrentNodeInfo(node.getId(), node.getTypeId(), signature));
            currentTypeById.put(node.getId(), node.getTypeId());
        }

        Map<String, List<CurrentNodeInfo>> byType = new HashMap<>();
        for (CurrentNodeInfo info : currentNodes) {
            byType.computeIfAbsent(info.typeId(), key -> new ArrayList<>()).add(info);
        }

        Set<UUID> usedCurrent = new HashSet<>();
        Map<String, CurrentNodeInfo> refToMatched = new HashMap<>();
        Set<String> newRefs = new HashSet<>();
        List<String> reuseSamples = new ArrayList<>();
        List<String> createSamples = new ArrayList<>();
        List<String> paramUpdateSamples = new ArrayList<>();

        int unchanged = 0;
        int paramUpdates = 0;

        for (PlanNode planned : safeNodes(plan)) {
            CurrentNodeInfo matched = matchCurrentNode(planned, byType, usedCurrent);
            if (matched == null) {
                newRefs.add(planned.ref());
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
            String sig = buildMappedConnectionSignature(
                    "CUR:" + conn.sourceNode.getId(),
                    conn.sourcePort.getId(),
                    "CUR:" + conn.targetNode.getId(),
                    conn.targetPort.getId()
            );
            currentConnAll.add(sig);

            if (usedCurrent.contains(conn.sourceNode.getId()) && usedCurrent.contains(conn.targetNode.getId())) {
                currentConnScoped.add(sig);
            }
        }

        Set<String> plannedConnMappedAll = new HashSet<>();
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

        public final class AiGraphDiffService {

            private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

            private AiGraphDiffService() {
            }

            public record PlanNode(String ref, String typeId, Object nodeState) {
            }

            public record PlanConnection(String sourceRef, String sourcePortId, String targetRef, String targetPortId) {
            }

            public record GraphPlan(List<PlanNode> nodes, List<PlanConnection> connections) {
            }

            private record CurrentNodeInfo(UUID id, String typeId, String paramSignature) {
            }

            public record GraphDiffSummary(
                    int nodeAdditions,
                    int nodeMissingFromPlan,
                    int connectionAdditions,
                    int connectionMissingFromPlan,
                    List<String> nodeAdditionSamples,
                    List<String> nodeMissingSamples,
                    List<String> connectionAdditionSamples,
                    List<String> connectionMissingSamples
            ) {
            }

            public record MappedDiffSummary(
                    int reusableNodeMatches,
                    int newNodesToCreate,
                    int unchangedReusableNodes,
                    int paramUpdateCandidates,
                    int connectionAdditions,
                    int connectionRemovalCandidates,
                    int incomingReplacementCandidates,
                    List<String> nodeReuseSamples,
                    List<String> nodeCreationSamples,
                    List<String> paramUpdateSamples,
                    List<String> connectionAdditionSamples,
                    List<String> connectionRemovalSamples,
                    List<String> incomingReplacementSamples
            ) {
            }

            public static GraphDiffSummary buildGraphDiffSummary(GraphPlan plan, NodeGraph graph) {
                if (plan == null || graph == null) {
                    return new GraphDiffSummary(0, 0, 0, 0, List.of(), List.of(), List.of(), List.of());
                }

                Map<String, Integer> currentNodeCounts = buildCurrentNodeSignatureCounts(graph);
                Map<String, Integer> plannedNodeCounts = buildPlannedNodeSignatureCounts(plan);

                Map<String, Integer> currentConnectionCounts = buildCurrentConnectionSignatureCounts(graph);
                Map<String, Integer> plannedConnectionCounts = buildPlannedConnectionSignatureCounts(plan);

                List<String> nodeAdds = new ArrayList<>();
                List<String> nodeMissing = new ArrayList<>();
                int nodeAddTotal = collectMultisetDelta(plannedNodeCounts, currentNodeCounts, nodeAdds, true);
                int nodeMissingTotal = collectMultisetDelta(currentNodeCounts, plannedNodeCounts, nodeMissing, false);

                List<String> connAdds = new ArrayList<>();
                List<String> connMissing = new ArrayList<>();
                int connAddTotal = collectMultisetDelta(plannedConnectionCounts, currentConnectionCounts, connAdds, false);
                int connMissingTotal = collectMultisetDelta(currentConnectionCounts, plannedConnectionCounts, connMissing, false);

                return new GraphDiffSummary(
                        nodeAddTotal,
                        nodeMissingTotal,
                        connAddTotal,
                        connMissingTotal,
                        nodeAdds,
                        nodeMissing,
                        connAdds,
                        connMissing
                );
            }

            public static MappedDiffSummary buildMappedDiffSummary(GraphPlan plan, NodeGraph graph) {
                if (plan == null) {
                    return new MappedDiffSummary(0, 0, 0, 0, 0, 0, 0,
                            List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
                }
                if (graph == null) {
                    int nodeCount = plan.nodes() == null ? 0 : plan.nodes().size();
                    int connCount = plan.connections() == null ? 0 : plan.connections().size();
                    return new MappedDiffSummary(0, nodeCount, 0, 0, connCount, 0, 0,
                            List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
                }

                List<CurrentNodeInfo> currentNodes = new ArrayList<>();
                Map<UUID, String> currentTypeById = new HashMap<>();
                for (INode node : graph.getNodes()) {
                    Object state = node instanceof BaseNode baseNode ? baseNode.getNodeState() : null;
                    String signature = normalizeStateForSignature(state);
                    currentNodes.add(new CurrentNodeInfo(node.getId(), node.getTypeId(), signature));
                    currentTypeById.put(node.getId(), node.getTypeId());
                }

                Map<String, List<CurrentNodeInfo>> byType = new HashMap<>();
                for (CurrentNodeInfo info : currentNodes) {
                    byType.computeIfAbsent(info.typeId(), key -> new ArrayList<>()).add(info);
                }

                Set<UUID> usedCurrent = new HashSet<>();
                Map<String, CurrentNodeInfo> refToMatched = new HashMap<>();
                Set<String> newRefs = new HashSet<>();
                List<String> reuseSamples = new ArrayList<>();
                List<String> createSamples = new ArrayList<>();
                List<String> paramUpdateSamples = new ArrayList<>();

                int unchanged = 0;
                int paramUpdates = 0;

                for (PlanNode planned : safeNodes(plan)) {
                    CurrentNodeInfo matched = matchCurrentNode(planned, byType, usedCurrent);
                    if (matched == null) {
                        newRefs.add(planned.ref());
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
                    String sig = buildMappedConnectionSignature(
                            "CUR:" + conn.sourceNode.getId(),
                            conn.sourcePort.getId(),
                            "CUR:" + conn.targetNode.getId(),
                            conn.targetPort.getId()
                    );
                    currentConnAll.add(sig);

                    if (usedCurrent.contains(conn.sourceNode.getId()) && usedCurrent.contains(conn.targetNode.getId())) {
                        currentConnScoped.add(sig);
                    }
                }

                Set<String> plannedConnMappedAll = new HashSet<>();
                Set<String> plannedConnMappedScoped = new HashSet<>();
                List<String> incomingReplacementSamples = new ArrayList<>();
                int incomingReplacementCandidates = 0;
                for (PlanConnection conn : safeConnections(plan)) {
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

                return new MappedDiffSummary(
                        refToMatched.size(),
                        newRefs.size(),
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

            private static List<PlanNode> safeNodes(GraphPlan plan) {
                return plan.nodes() == null ? List.of() : plan.nodes();
            }

            private static List<PlanConnection> safeConnections(GraphPlan plan) {
                return plan.connections() == null ? List.of() : plan.connections();
            }

            private static Map<String, Integer> buildCurrentNodeSignatureCounts(NodeGraph graph) {
                Map<String, Integer> counts = new HashMap<>();
                for (INode node : graph.getNodes()) {
                    Object state = node instanceof BaseNode baseNode ? baseNode.getNodeState() : null;
                    String signature = buildNodeSignature(node.getTypeId(), state);
                    counts.merge(signature, 1, Integer::sum);
                }
                return counts;
            }

            private static Map<String, Integer> buildPlannedNodeSignatureCounts(GraphPlan plan) {
                Map<String, Integer> counts = new HashMap<>();
                for (PlanNode node : safeNodes(plan)) {
                    String signature = buildNodeSignature(node.typeId(), node.nodeState());
                    counts.merge(signature, 1, Integer::sum);
                }
                return counts;
            }

            private static Map<String, Integer> buildCurrentConnectionSignatureCounts(NodeGraph graph) {
                Map<String, Integer> counts = new HashMap<>();
                for (NodeGraph.Connection conn : graph.getConnections()) {
                    String signature = buildConnectionSignature(
                            conn.sourceNode.getTypeId(),
                            conn.sourcePort.getId(),
                            conn.targetNode.getTypeId(),
                            conn.targetPort.getId()
                    );
                    counts.merge(signature, 1, Integer::sum);
                }
                return counts;
            }

            private static Map<String, Integer> buildPlannedConnectionSignatureCounts(GraphPlan plan) {
                Map<String, String> refToType = new HashMap<>();
                for (PlanNode node : safeNodes(plan)) {
                    refToType.put(node.ref(), node.typeId());
                }

                Map<String, Integer> counts = new HashMap<>();
                for (PlanConnection conn : safeConnections(plan)) {
                    String sourceType = refToType.getOrDefault(conn.sourceRef(), "unknown");
                    String targetType = refToType.getOrDefault(conn.targetRef(), "unknown");
                    String signature = buildConnectionSignature(
                            sourceType,
                            conn.sourcePortId(),
                            targetType,
                            conn.targetPortId()
                    );
                    counts.merge(signature, 1, Integer::sum);
                }
                return counts;
            }

            private static int collectMultisetDelta(
                    Map<String, Integer> lhs,
                    Map<String, Integer> rhs,
                    List<String> samples,
                    boolean nodeSignature
            ) {
                int total = 0;
                List<String> keys = new ArrayList<>(lhs.keySet());
                Collections.sort(keys);

                for (String key : keys) {
                    int delta = lhs.getOrDefault(key, 0) - rhs.getOrDefault(key, 0);
                    if (delta <= 0) {
                        continue;
                    }
                    total += delta;
                    if (samples.size() < 12) {
                        String label = nodeSignature ? simplifyNodeSignature(key) : key;
                        samples.add(delta + " x " + truncate(label, 180));
                    }
                }
                return total;
            }

            private static CurrentNodeInfo matchCurrentNode(
                    PlanNode planned,
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

            private static String buildNodeSignature(String typeId, Object state) {
                return nullToEmpty(typeId) + "|" + normalizeStateForSignature(state);
            }

            private static String simplifyNodeSignature(String signature) {
                if (signature == null || signature.isBlank()) {
                    return "(empty)";
                }
                int split = signature.indexOf('|');
                if (split < 0) {
                    return signature;
                }
                return signature.substring(0, split) + " params=" + truncate(signature.substring(split + 1), 120);
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

            private static String buildConnectionSignature(String sourceType, String sourcePort, String targetType, String targetPort) {
                return nullToEmpty(sourceType) + "." + nullToEmpty(sourcePort)
                        + " -> " + nullToEmpty(targetType) + "." + nullToEmpty(targetPort);
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

            private static String truncate(String value, int maxChars) {
                if (value == null) {
                    return "";
                }
                if (value.length() <= maxChars) {
                    return value;
                }
                return value.substring(0, maxChars) + "...";
            }

            private static String nullToEmpty(String value) {
                return value == null ? "" : value;
            }
        }

package com.nodecraft.gui.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.graph.NodeGraph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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
        return AiGraphDiffMappingService.buildMappedDiffSummary(plan, graph);
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

    private static List<PlanNode> safeNodes(GraphPlan plan) {
        return plan.nodes() == null ? List.of() : plan.nodes();
    }

    private static List<PlanConnection> safeConnections(GraphPlan plan) {
        return plan.connections() == null ? List.of() : plan.connections();
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
        switch (value) {
            case Map<?, ?> map -> {
                Map<String, Object> canonical = new TreeMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    canonical.put(String.valueOf(entry.getKey()), canonicalizeForSignature(entry.getValue()));
                }
                return canonical;
            }
            case List<?> list -> {
                List<Object> canonical = new ArrayList<>(list.size());
                for (Object item : list) {
                    canonical.add(canonicalizeForSignature(item));
                }
                return canonical;
            }
            case Iterable<?> iterable -> {
                List<Object> canonical = new ArrayList<>();
                for (Object item : iterable) {
                    canonical.add(canonicalizeForSignature(item));
                }
                return canonical;
            }
            default -> {
            }
        }
        return String.valueOf(value);
    }

    private static String buildConnectionSignature(String sourceType, String sourcePort, String targetType, String targetPort) {
        return nullToEmpty(sourceType) + "." + nullToEmpty(sourcePort)
                + " -> " + nullToEmpty(targetType) + "." + nullToEmpty(targetPort);
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

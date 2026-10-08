package com.nodecraft.gui.ai;

import com.nodecraft.gui.editor.base.GraphNodeAnchor;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.graph.NodeGraph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class AiPromptContextService {

    private static final int MAX_NEIGHBORHOOD_NODE_LINES = 28;
    private static final int MAX_CONNECTION_LINES = 40;
    private static final int FALLBACK_MAX_NODES = 20;
    private static final int SUMMARY_PARAM_LIMIT = 6;

    private AiPromptContextService() {
    }

    public static String buildSelectionContextSummary(
            boolean useSelectionContext,
            boolean includeGraphContext,
            INode selectedNode,
            GraphNodeAnchor selectedNodePosition,
            NodeGraph graph
    ) {
        StringBuilder context = new StringBuilder(2048);

        if (!useSelectionContext) {
            context.append("Selection context disabled.");
        } else if (selectedNode == null) {
            context.append("No node selected.");
        } else {
            appendSelectedNodeBlock(context, selectedNode, selectedNodePosition, graph);
        }

        context.append("\n");
        context.append(includeGraphContext
                ? buildCurrentGraphContextSummary(graph, selectedNode)
                : "Current canvas graph summary disabled.");

        return context.toString();
    }

    /**
     * @deprecated Prefer {@link #buildCurrentGraphContextSummary(NodeGraph, INode)}.
     */
    @Deprecated
    public static String buildCurrentGraphContextSummary(NodeGraph graph) {
        return buildCurrentGraphContextSummary(graph, null);
    }

    public static String buildCurrentGraphContextSummary(NodeGraph graph, INode selectedNode) {
        if (graph == null) {
            return "Current canvas graph: unavailable.";
        }

        List<INode> nodes = graph.getNodes();
        List<NodeGraph.Connection> connections = graph.getConnections();
        if (nodes.isEmpty()) {
            return "Current canvas graph: empty.";
        }

        if (selectedNode == null || graph.getNode(selectedNode.getId()) == null) {
            return buildFallbackGraphContextSummary(graph, nodes, connections);
        }

        return buildNeighborhoodGraphContextSummary(graph, selectedNode, nodes, connections);
    }

    public static String buildAiPlanReply(
            String prompt,
            String source,
            boolean useSelectionContext,
            INode selectedNode,
            int nodeCount,
            int connectionCount,
            boolean valid,
            List<String> validationErrors
    ) {
        String contextSummary;
        if (useSelectionContext && selectedNode != null) {
            contextSummary = "Using selected node context: " + shortNodeId(selectedNode)
                    + " " + selectedNode.getDisplayName() + " (" + selectedNode.getTypeId() + ").";
        } else if (useSelectionContext) {
            contextSummary = "Selection context requested, but no node is selected.";
        } else {
            contextSummary = "Selection context disabled.";
        }

        return "Plan received: '" + prompt + "'\n"
                + "Source: " + source + "\n"
                + contextSummary + "\n"
                + "Generated preview: " + nodeCount + " nodes, " + connectionCount + " connections."
                + (valid ? "" : " Validation issues: " + String.join("; ", validationErrors));
    }

    private static void appendSelectedNodeBlock(
            StringBuilder context,
            INode selectedNode,
            GraphNodeAnchor selectedNodePosition,
            NodeGraph graph
    ) {
        context.append("Selected node:\n");
        context.append("  id: ").append(shortNodeId(selectedNode)).append("\n");
        context.append("  fullId: ").append(selectedNode.getId()).append("\n");
        context.append("  type: ").append(nullToEmpty(selectedNode.getTypeId())).append("\n");
        context.append("  displayName: ").append(nullToEmpty(selectedNode.getDisplayName())).append("\n");
        if (selectedNodePosition != null) {
            context.append("  position: (")
                    .append(Math.round(selectedNodePosition.x()))
                    .append(", ")
                    .append(Math.round(selectedNodePosition.y()))
                    .append(")\n");
        }

        if (selectedNode instanceof BaseNode baseNode) {
            Map<String, Object> state = castNodeState(baseNode.getNodeState());
            context.append("  params: ").append(formatFullNodeState(state)).append("\n");
        } else {
            context.append("  params: {}\n");
        }

        context.append("  connections:\n");
        if (graph == null) {
            context.append("    (graph unavailable)\n");
            return;
        }
        List<NodeGraph.Connection> incoming = graph.getIncomingConnections(selectedNode.getId());
        List<NodeGraph.Connection> outgoing = graph.getOutgoingConnections(selectedNode.getId());
        if (incoming.isEmpty() && outgoing.isEmpty()) {
            context.append("    (none)\n");
            return;
        }
        for (NodeGraph.Connection conn : incoming) {
            context.append("    - in: ")
                    .append(shortNodeId(conn.sourceNode()))
                    .append(".")
                    .append(conn.sourcePort().getId())
                    .append(" -> ")
                    .append(conn.targetPort().getId())
                    .append("\n");
        }
        for (NodeGraph.Connection conn : outgoing) {
            context.append("    - out: ")
                    .append(conn.sourcePort().getId())
                    .append(" -> ")
                    .append(shortNodeId(conn.targetNode()))
                    .append(".")
                    .append(conn.targetPort().getId())
                    .append("\n");
        }
    }

    private static String buildNeighborhoodGraphContextSummary(
            NodeGraph graph,
            INode selectedNode,
            List<INode> nodes,
            List<NodeGraph.Connection> connections
    ) {
        UUID selectedId = selectedNode.getId();
        Set<UUID> hop1 = collectNeighbors(graph, Set.of(selectedId));
        Set<UUID> hop2 = collectNeighbors(graph, hop1);
        hop2.remove(selectedId);
        hop2.removeAll(hop1);

        LinkedHashSet<UUID> ordered = new LinkedHashSet<>();
        ordered.add(selectedId);
        ordered.addAll(sortedByShortId(hop1, graph));
        for (UUID id : sortedByShortId(hop2, graph)) {
            if (ordered.size() >= MAX_NEIGHBORHOOD_NODE_LINES) {
                break;
            }
            ordered.add(id);
        }

        StringBuilder sb = new StringBuilder(2400);
        sb.append("Current canvas graph snapshot (selected-neighborhood-first):\n");
        sb.append("nodes=").append(nodes.size())
                .append(", connections=").append(connections.size())
                .append("\n");

        int listedNodes = 0;
        for (UUID id : ordered) {
            if (listedNodes >= MAX_NEIGHBORHOOD_NODE_LINES) {
                break;
            }
            INode node = graph.getNode(id);
            if (node == null) {
                continue;
            }
            boolean fullState = id.equals(selectedId);
            String hopLabel = id.equals(selectedId) ? "selected" : (hop1.contains(id) ? "1-hop" : "2-hop");
            sb.append("- [").append(hopLabel).append("] ")
                    .append(shortNodeId(node))
                    .append(": ")
                    .append(node.getTypeId());
            if (node instanceof BaseNode baseNode) {
                Map<String, Object> state = castNodeState(baseNode.getNodeState());
                String stateText = fullState ? formatFullNodeState(state) : summarizeNodeState(state);
                if (!stateText.isBlank()) {
                    sb.append(" ").append(fullState ? "params=" + stateText : stateText);
                }
            }
            sb.append("\n");
            listedNodes++;
        }

        int omittedNodes = Math.max(0, nodes.size() - listedNodes);
        if (omittedNodes > 0) {
            sb.append("omittedNodes=").append(omittedNodes).append("\n");
        }

        Set<UUID> priority = new HashSet<>();
        priority.add(selectedId);
        priority.addAll(hop1);
        Set<UUID> secondary = new HashSet<>(hop2);

        List<NodeGraph.Connection> prioritized = new ArrayList<>();
        List<NodeGraph.Connection> secondaryConns = new ArrayList<>();
        for (NodeGraph.Connection conn : connections) {
            UUID sourceId = conn.sourceNode().getId();
            UUID targetId = conn.targetNode().getId();
            boolean touchesPriority = priority.contains(sourceId) || priority.contains(targetId);
            boolean touchesSecondary = secondary.contains(sourceId) || secondary.contains(targetId);
            if (touchesPriority) {
                prioritized.add(conn);
            } else if (touchesSecondary) {
                secondaryConns.add(conn);
            }
        }

        sb.append("Connections:\n");
        int listedConnections = 0;
        listedConnections = appendConnections(sb, prioritized, listedConnections, MAX_CONNECTION_LINES);
        listedConnections = appendConnections(sb, secondaryConns, listedConnections, MAX_CONNECTION_LINES);
        int omittedConnections = Math.max(0, connections.size() - listedConnections);
        if (omittedConnections > 0) {
            sb.append("omittedConnections=").append(omittedConnections).append("\n");
        }

        return sb.toString();
    }

    private static String buildFallbackGraphContextSummary(
            NodeGraph graph,
            List<INode> nodes,
            List<NodeGraph.Connection> connections
    ) {
        Map<UUID, Integer> degree = new HashMap<>();
        for (NodeGraph.Connection conn : connections) {
            degree.merge(conn.sourceNode().getId(), 1, Integer::sum);
            degree.merge(conn.targetNode().getId(), 1, Integer::sum);
        }

        List<INode> ranked = new ArrayList<>(nodes);
        ranked.sort(Comparator
                .comparingInt((INode n) -> degree.getOrDefault(n.getId(), 0))
                .reversed()
                .thenComparing(n -> shortNodeId(n), String.CASE_INSENSITIVE_ORDER));

        StringBuilder sb = new StringBuilder(1600);
        sb.append("Current canvas graph snapshot:\n");
        sb.append("nodes=").append(nodes.size())
                .append(", connections=").append(connections.size())
                .append("\n");

        int listed = 0;
        for (INode node : ranked) {
            if (listed >= FALLBACK_MAX_NODES) {
                break;
            }
            sb.append("- ")
                    .append(shortNodeId(node))
                    .append(": ")
                    .append(node.getTypeId());
            if (node instanceof BaseNode baseNode) {
                Map<String, Object> state = castNodeState(baseNode.getNodeState());
                String stateSummary = summarizeNodeState(state);
                if (!stateSummary.isBlank()) {
                    sb.append(" ").append(stateSummary);
                }
            }
            sb.append("\n");
            listed++;
        }
        int omittedNodes = Math.max(0, nodes.size() - listed);
        if (omittedNodes > 0) {
            sb.append("omittedNodes=").append(omittedNodes).append("\n");
        }

        sb.append("Connections:\n");
        int listedConnections = appendConnections(sb, connections, 0, MAX_CONNECTION_LINES);
        int omittedConnections = Math.max(0, connections.size() - listedConnections);
        if (omittedConnections > 0) {
            sb.append("omittedConnections=").append(omittedConnections).append("\n");
        }
        return sb.toString();
    }

    private static Set<UUID> collectNeighbors(NodeGraph graph, Set<UUID> seeds) {
        Set<UUID> neighbors = new LinkedHashSet<>();
        if (graph == null || seeds == null) {
            return neighbors;
        }
        for (UUID seed : seeds) {
            for (NodeGraph.Connection conn : graph.getIncomingConnections(seed)) {
                neighbors.add(conn.sourceNode().getId());
            }
            for (NodeGraph.Connection conn : graph.getOutgoingConnections(seed)) {
                neighbors.add(conn.targetNode().getId());
            }
        }
        neighbors.removeAll(seeds);
        return neighbors;
    }

    private static List<UUID> sortedByShortId(Set<UUID> ids, NodeGraph graph) {
        List<UUID> sorted = new ArrayList<>(ids);
        sorted.sort(Comparator.comparing(id -> {
            INode node = graph.getNode(id);
            return shortNodeId(node);
        }, String.CASE_INSENSITIVE_ORDER));
        return sorted;
    }

    private static int appendConnections(
            StringBuilder sb,
            List<NodeGraph.Connection> connections,
            int alreadyListed,
            int max
    ) {
        int listed = alreadyListed;
        for (NodeGraph.Connection conn : connections) {
            if (listed >= max) {
                break;
            }
            sb.append("- ")
                    .append(shortNodeId(conn.sourceNode()))
                    .append(".")
                    .append(conn.sourcePort().getId())
                    .append(" -> ")
                    .append(shortNodeId(conn.targetNode()))
                    .append(".")
                    .append(conn.targetPort().getId())
                    .append("\n");
            listed++;
        }
        return listed;
    }

    static String shortNodeId(INode node) {
        if (node == null || node.getId() == null) {
            return "unknown";
        }
        String text = node.getId().toString();
        return text.length() <= 8 ? text : text.substring(0, 8);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castNodeState(Object nodeState) {
        return nodeState instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    private static String formatFullNodeState(Map<String, Object> state) {
        if (state == null || state.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder(128);
        sb.append("{");
        int index = 0;
        for (Map.Entry<String, Object> entry : state.entrySet()) {
            if (index > 0) {
                sb.append(", ");
            }
            sb.append(entry.getKey()).append("=").append(formatStateValue(entry.getValue()));
            index++;
        }
        sb.append("}");
        return sb.toString();
    }

    private static String summarizeNodeState(Map<String, Object> state) {
        if (state == null || state.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder(128);
        sb.append("params{");
        int index = 0;
        for (Map.Entry<String, Object> entry : state.entrySet()) {
            if (index > 0) {
                sb.append(", ");
            }
            if (index >= SUMMARY_PARAM_LIMIT) {
                sb.append("...");
                break;
            }
            sb.append(entry.getKey()).append("=").append(formatStateValue(entry.getValue()));
            index++;
        }
        sb.append("}");
        return sb.toString();
    }

    private static String formatStateValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        return switch (value) {
            case String text -> text.length() <= 32 ? text : text.substring(0, 32) + "...";
            case Collection<?> collection -> "list(size=" + collection.size() + ")";
            case Map<?, ?> map -> formatNestedMap(map);
            default -> value.getClass().getSimpleName();
        };
    }

    private static String formatNestedMap(Map<?, ?> map) {
        if (map.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        int index = 0;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (index > 0) {
                sb.append(", ");
            }
            if (index >= SUMMARY_PARAM_LIMIT) {
                sb.append("...");
                break;
            }
            sb.append(entry.getKey()).append("=");
            Object nestedValue = entry.getValue();
            if (nestedValue instanceof Number || nestedValue instanceof Boolean) {
                sb.append(nestedValue);
            } else if (nestedValue instanceof String text) {
                sb.append(text.length() <= 32 ? text : text.substring(0, 32) + "...");
            } else {
                sb.append(nestedValue == null ? "null" : nestedValue.getClass().getSimpleName());
            }
            index++;
        }
        return sb.append("}").toString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

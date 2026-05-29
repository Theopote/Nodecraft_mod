package com.nodecraft.gui.ai;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.gui.editor.impl.NodePosition;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public final class AiPromptContextService {

    private AiPromptContextService() {
    }

    public static String buildSelectionContextSummary(
            boolean useSelectionContext,
            boolean includeGraphContext,
            INode selectedNode,
            NodePosition selectedNodePosition,
            NodeGraph graph
    ) {
        StringBuilder context = new StringBuilder(1024);

        if (!useSelectionContext) {
            context.append("Selection context disabled.");
        } else if (selectedNode == null) {
            context.append("No node selected.");
        } else {
            context.append("Selected node: ")
                    .append(selectedNode.getDisplayName())
                    .append(" (")
                    .append(selectedNode.getTypeId())
                    .append(")");

            if (selectedNodePosition != null) {
                context.append(" at canvas position (")
                        .append(Math.round(selectedNodePosition.x))
                        .append(", ")
                        .append(Math.round(selectedNodePosition.y))
                        .append(")");
            }
        }

        context.append("\n");
        context.append(includeGraphContext
                ? buildCurrentGraphContextSummary(graph)
                : "Current canvas graph summary disabled.");

        return context.toString();
    }

    public static String buildCurrentGraphContextSummary(NodeGraph graph) {
        if (graph == null) {
            return "Current canvas graph: unavailable.";
        }

        List<INode> nodes = graph.getNodes();
        List<NodeGraph.Connection> connections = graph.getConnections();
        if (nodes.isEmpty()) {
            return "Current canvas graph: empty.";
        }

        StringBuilder sb = new StringBuilder(1600);
        sb.append("Current canvas graph snapshot:\n");
        sb.append("nodes=").append(nodes.size())
                .append(", connections=").append(connections.size())
                .append("\n");

        final int maxNodes = 20;
        for (int i = 0; i < nodes.size() && i < maxNodes; i++) {
            INode node = nodes.get(i);
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
        }
        if (nodes.size() > maxNodes) {
            sb.append("- ... ").append(nodes.size() - maxNodes).append(" more nodes\n");
        }

        final int maxConnections = 30;
        sb.append("Connections:\n");
        for (int i = 0; i < connections.size() && i < maxConnections; i++) {
            NodeGraph.Connection conn = connections.get(i);
            sb.append("- ")
                    .append(shortNodeId(conn.sourceNode))
                    .append(".")
                    .append(conn.sourcePort.getId())
                    .append(" -> ")
                    .append(shortNodeId(conn.targetNode))
                    .append(".")
                    .append(conn.targetPort.getId())
                    .append("\n");
        }
        if (connections.size() > maxConnections) {
            sb.append("- ... ").append(connections.size() - maxConnections).append(" more connections\n");
        }

        return sb.toString();
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
            contextSummary = "Using selected node context: " + selectedNode.getDisplayName() + " (" + selectedNode.getTypeId() + ").";
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

    private static String shortNodeId(INode node) {
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

    private static String summarizeNodeState(Map<String, Object> state) {
        if (state == null || state.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder(128);
        sb.append("params{");
        int limit = 6;
        int index = 0;
        for (Map.Entry<String, Object> entry : state.entrySet()) {
            if (index > 0) {
                sb.append(", ");
            }
            if (index >= limit) {
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
            case Map<?, ?> map -> "map(size=" + map.size() + ")";
            default -> value.getClass().getSimpleName();
        };
    }
}

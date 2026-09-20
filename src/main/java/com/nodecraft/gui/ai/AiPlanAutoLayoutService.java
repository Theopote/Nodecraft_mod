package com.nodecraft.gui.ai;

import com.nodecraft.gui.layout.GraphNodeAutoLayout;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * AI-facing wrapper around {@link GraphNodeAutoLayout}.
 */
public final class AiPlanAutoLayoutService {

    private AiPlanAutoLayoutService() {
    }

    public record PlanNode(String ref, String typeId, Object nodeState) {
    }

    public record PlanConnection(String sourceRef, String targetRef) {
    }

    public record ArrangedNode(String ref, String typeId, float offsetX, float offsetY, Object nodeState) {
    }

    public static List<ArrangedNode> autoLayout(List<PlanNode> nodes, List<PlanConnection> connections) {
        if (nodes == null || nodes.isEmpty()) {
            return List.of();
        }

        List<GraphNodeAutoLayout.NodeRef> refs = new ArrayList<>(nodes.size());
        for (PlanNode node : nodes) {
            if (node != null && node.ref() != null) {
                refs.add(new GraphNodeAutoLayout.NodeRef(node.ref()));
            }
        }

        List<GraphNodeAutoLayout.Edge> edges = new ArrayList<>();
        if (connections != null) {
            for (PlanConnection connection : connections) {
                if (connection != null) {
                    edges.add(new GraphNodeAutoLayout.Edge(connection.sourceRef(), connection.targetRef()));
                }
            }
        }

        List<GraphNodeAutoLayout.Arranged> arranged = GraphNodeAutoLayout.autoLayout(refs, edges);
        return toArrangedNodes(nodes, arranged);
    }

    private static @NonNull List<ArrangedNode> toArrangedNodes(
            List<PlanNode> nodes,
            List<GraphNodeAutoLayout.Arranged> arranged
    ) {
        MapByRef lookup = MapByRef.from(nodes);
        List<ArrangedNode> result = new ArrayList<>(arranged.size());
        for (GraphNodeAutoLayout.Arranged item : arranged) {
            PlanNode node = lookup.get(item.ref());
            if (node == null) {
                continue;
            }
            result.add(new ArrangedNode(node.ref(), node.typeId(), item.offsetX(), item.offsetY(), node.nodeState()));
        }
        return result;
    }

    private static final class MapByRef {
        private final java.util.Map<String, PlanNode> byRef = new java.util.LinkedHashMap<>();

        static MapByRef from(List<PlanNode> nodes) {
            MapByRef map = new MapByRef();
            for (PlanNode node : nodes) {
                if (node != null && node.ref() != null) {
                    map.byRef.putIfAbsent(node.ref(), node);
                }
            }
            return map;
        }

        PlanNode get(String ref) {
            return byRef.get(ref);
        }
    }
}

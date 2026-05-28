package com.nodecraft.gui.ai;

import org.jspecify.annotations.NonNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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

        Map<String, PlanNode> nodeByRef = new LinkedHashMap<>();
        Map<String, Integer> indegree = new HashMap<>();
        Map<String, Integer> depth = new HashMap<>();
        Map<String, List<String>> edges = new HashMap<>();

        for (PlanNode node : nodes) {
            nodeByRef.put(node.ref(), node);
            indegree.put(node.ref(), 0);
            depth.put(node.ref(), 0);
            edges.put(node.ref(), new ArrayList<>());
        }

        if (connections != null) {
            for (PlanConnection connection : connections) {
                if (!nodeByRef.containsKey(connection.sourceRef()) || !nodeByRef.containsKey(connection.targetRef())) {
                    continue;
                }
                edges.get(connection.sourceRef()).add(connection.targetRef());
                indegree.put(connection.targetRef(), indegree.get(connection.targetRef()) + 1);
            }
        }

        ArrayDeque<String> queue = new ArrayDeque<>();
        for (PlanNode node : nodes) {
            if (indegree.get(node.ref()) == 0) {
                queue.add(node.ref());
            }
        }

        while (!queue.isEmpty()) {
            String current = queue.poll();
            int currentDepth = depth.getOrDefault(current, 0);
            for (String next : edges.getOrDefault(current, List.of())) {
                depth.put(next, Math.max(depth.getOrDefault(next, 0), currentDepth + 1));
                int nextIn = indegree.getOrDefault(next, 0) - 1;
                indegree.put(next, nextIn);
                if (nextIn == 0) {
                    queue.add(next);
                }
            }
        }

        Map<Integer, List<PlanNode>> layerMap = new TreeMap<>();
        for (PlanNode node : nodes) {
            int layer = Math.max(0, depth.getOrDefault(node.ref(), 0));
            layerMap.computeIfAbsent(layer, ignored -> new ArrayList<>()).add(node);
        }

        return toArrangedNodes(layerMap);
    }

    private static @NonNull List<ArrangedNode> toArrangedNodes(Map<Integer, List<PlanNode>> layerMap) {
        float layerSpacingX = 320.0f;
        float baseLayerSpacingY = 180.0f;
        List<ArrangedNode> arranged = new ArrayList<>();

        for (Map.Entry<Integer, List<PlanNode>> layerEntry : layerMap.entrySet()) {
            int layer = layerEntry.getKey();
            List<PlanNode> layerNodes = layerEntry.getValue();
            float effectiveSpacingY = resolveLayerSpacingY(baseLayerSpacingY, layerNodes.size());
            for (int i = 0; i < layerNodes.size(); i++) {
                PlanNode node = layerNodes.get(i);
                float x = layer * layerSpacingX;
                float y = (i - (layerNodes.size() - 1) / 2.0f) * effectiveSpacingY;
                arranged.add(new ArrangedNode(node.ref(), node.typeId(), x, y, node.nodeState()));
            }
        }

        return arranged;
    }

    private static float resolveLayerSpacingY(float baseSpacing, int layerNodeCount) {
        if (layerNodeCount <= 4) {
            return baseSpacing;
        }

        float denseSpacing = 220.0f + (layerNodeCount - 5) * 12.0f;
        return Math.max(baseSpacing, Math.min(320.0f, denseSpacing));
    }
}

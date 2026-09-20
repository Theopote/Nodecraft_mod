package com.nodecraft.gui.layout;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Layered left-to-right graph layout for dropped presets / AI plans.
 * <p>
 * Nodes are placed by topological depth (columns) and ordered within each column
 * with a barycenter heuristic so connected edges tend not to cross.
 */
public final class GraphNodeAutoLayout {

    public static final float DEFAULT_LAYER_SPACING_X = 320.0f;
    public static final float DEFAULT_BASE_LAYER_SPACING_Y = 180.0f;

    public record NodeRef(String ref) {
    }

    public record Edge(String sourceRef, String targetRef) {
    }

    public record Arranged(String ref, float offsetX, float offsetY) {
    }

    private GraphNodeAutoLayout() {
    }

    public static List<Arranged> autoLayout(List<NodeRef> nodes, List<Edge> edges) {
        return autoLayout(nodes, edges, DEFAULT_LAYER_SPACING_X, DEFAULT_BASE_LAYER_SPACING_Y);
    }

    public static List<Arranged> autoLayout(
            List<NodeRef> nodes,
            List<Edge> edges,
            float layerSpacingX,
            float baseLayerSpacingY
    ) {
        if (nodes == null || nodes.isEmpty()) {
            return List.of();
        }

        Map<String, NodeRef> nodeByRef = new LinkedHashMap<>();
        Map<String, Integer> indegree = new HashMap<>();
        Map<String, Integer> depth = new HashMap<>();
        Map<String, List<String>> outgoing = new HashMap<>();
        Map<String, List<String>> incoming = new HashMap<>();

        for (NodeRef node : nodes) {
            if (node == null || node.ref() == null || node.ref().isBlank()) {
                continue;
            }
            if (nodeByRef.containsKey(node.ref())) {
                continue;
            }
            nodeByRef.put(node.ref(), node);
            indegree.put(node.ref(), 0);
            depth.put(node.ref(), 0);
            outgoing.put(node.ref(), new ArrayList<>());
            incoming.put(node.ref(), new ArrayList<>());
        }

        if (nodeByRef.isEmpty()) {
            return List.of();
        }

        if (edges != null) {
            for (Edge edge : edges) {
                if (edge == null
                        || !nodeByRef.containsKey(edge.sourceRef())
                        || !nodeByRef.containsKey(edge.targetRef())
                        || edge.sourceRef().equals(edge.targetRef())) {
                    continue;
                }
                outgoing.get(edge.sourceRef()).add(edge.targetRef());
                incoming.get(edge.targetRef()).add(edge.sourceRef());
                indegree.put(edge.targetRef(), indegree.get(edge.targetRef()) + 1);
            }
        }

        ArrayDeque<String> queue = new ArrayDeque<>();
        for (String ref : nodeByRef.keySet()) {
            if (indegree.getOrDefault(ref, 0) == 0) {
                queue.add(ref);
            }
        }

        // Cycle fallback: seed remaining nodes so every node gets a depth.
        if (queue.isEmpty()) {
            queue.add(nodeByRef.keySet().iterator().next());
        }

        Set<String> visited = new LinkedHashSet<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (!visited.add(current)) {
                continue;
            }
            int currentDepth = depth.getOrDefault(current, 0);
            for (String next : outgoing.getOrDefault(current, List.of())) {
                depth.put(next, Math.max(depth.getOrDefault(next, 0), currentDepth + 1));
                int nextIn = indegree.getOrDefault(next, 0) - 1;
                indegree.put(next, nextIn);
                if (nextIn <= 0 && !visited.contains(next)) {
                    queue.add(next);
                }
            }
        }

        for (String ref : nodeByRef.keySet()) {
            if (!visited.contains(ref)) {
                depth.putIfAbsent(ref, 0);
            }
        }

        Map<Integer, List<String>> layerMap = new TreeMap<>();
        for (String ref : nodeByRef.keySet()) {
            int layer = Math.max(0, depth.getOrDefault(ref, 0));
            layerMap.computeIfAbsent(layer, ignored -> new ArrayList<>()).add(ref);
        }

        // Preserve authoring order as a stable baseline before barycenter passes.
        Map<String, Integer> authorOrder = new HashMap<>();
        int orderIndex = 0;
        for (String ref : nodeByRef.keySet()) {
            authorOrder.put(ref, orderIndex++);
        }
        for (List<String> layerNodes : layerMap.values()) {
            layerNodes.sort(Comparator.comparingInt(ref -> authorOrder.getOrDefault(ref, 0)));
        }

        // Two barycenter sweeps reduce crossings without a full Sugiyama solver.
        orderLayersByBarycenter(layerMap, incoming, true);
        orderLayersByBarycenter(layerMap, outgoing, false);
        orderLayersByBarycenter(layerMap, incoming, true);

        return toArranged(layerMap, layerSpacingX, baseLayerSpacingY);
    }

    private static void orderLayersByBarycenter(
            Map<Integer, List<String>> layerMap,
            Map<String, List<String>> neighbors,
            boolean forward
    ) {
        List<Integer> layers = new ArrayList<>(layerMap.keySet());
        if (!forward) {
            layers.sort(Comparator.reverseOrder());
        }

        Map<String, Integer> indexInLayer = new HashMap<>();
        for (List<String> layerNodes : layerMap.values()) {
            for (int i = 0; i < layerNodes.size(); i++) {
                indexInLayer.put(layerNodes.get(i), i);
            }
        }

        for (int layer : layers) {
            List<String> layerNodes = layerMap.get(layer);
            if (layerNodes == null || layerNodes.size() <= 1) {
                continue;
            }

            int adjacentLayer = forward ? layer - 1 : layer + 1;
            List<String> adjacent = layerMap.get(adjacentLayer);
            if (adjacent == null || adjacent.isEmpty()) {
                continue;
            }

            Map<String, Double> barycenter = new HashMap<>();
            for (String ref : layerNodes) {
                List<String> related = neighbors.getOrDefault(ref, List.of());
                double sum = 0.0;
                int count = 0;
                for (String other : related) {
                    Integer idx = indexInLayer.get(other);
                    if (idx != null) {
                        sum += idx;
                        count++;
                    }
                }
                if (count > 0) {
                    barycenter.put(ref, sum / count);
                } else {
                    barycenter.put(ref, (double) indexInLayer.getOrDefault(ref, 0));
                }
            }

            layerNodes.sort(Comparator
                    .comparingDouble((String ref) -> barycenter.getOrDefault(ref, 0.0))
                    .thenComparingInt(ref -> indexInLayer.getOrDefault(ref, 0)));

            for (int i = 0; i < layerNodes.size(); i++) {
                indexInLayer.put(layerNodes.get(i), i);
            }
        }
    }

    private static List<Arranged> toArranged(
            Map<Integer, List<String>> layerMap,
            float layerSpacingX,
            float baseLayerSpacingY
    ) {
        List<Arranged> arranged = new ArrayList<>();
        for (Map.Entry<Integer, List<String>> layerEntry : layerMap.entrySet()) {
            int layer = layerEntry.getKey();
            List<String> layerNodes = layerEntry.getValue();
            float effectiveSpacingY = resolveLayerSpacingY(baseLayerSpacingY, layerNodes.size());
            for (int i = 0; i < layerNodes.size(); i++) {
                String ref = layerNodes.get(i);
                float x = layer * layerSpacingX;
                float y = (i - (layerNodes.size() - 1) / 2.0f) * effectiveSpacingY;
                arranged.add(new Arranged(ref, x, y));
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

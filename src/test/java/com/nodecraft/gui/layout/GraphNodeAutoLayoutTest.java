package com.nodecraft.gui.layout;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphNodeAutoLayoutTest {

    @Test
    void layersFollowTopologyAndAvoidOverlap() {
        List<GraphNodeAutoLayout.NodeRef> nodes = List.of(
                new GraphNodeAutoLayout.NodeRef("a"),
                new GraphNodeAutoLayout.NodeRef("b"),
                new GraphNodeAutoLayout.NodeRef("c")
        );
        List<GraphNodeAutoLayout.Edge> edges = List.of(
                new GraphNodeAutoLayout.Edge("a", "b"),
                new GraphNodeAutoLayout.Edge("b", "c")
        );

        List<GraphNodeAutoLayout.Arranged> arranged = GraphNodeAutoLayout.autoLayout(nodes, edges);
        Map<String, GraphNodeAutoLayout.Arranged> byRef = arranged.stream()
                .collect(Collectors.toMap(GraphNodeAutoLayout.Arranged::ref, Function.identity()));

        assertEquals(3, arranged.size());
        assertTrue(byRef.get("a").offsetX() < byRef.get("b").offsetX());
        assertTrue(byRef.get("b").offsetX() < byRef.get("c").offsetX());

        // No two nodes share the same slot.
        long distinctSlots = arranged.stream()
                .map(item -> Math.round(item.offsetX()) + ":" + Math.round(item.offsetY()))
                .distinct()
                .count();
        assertEquals(3, distinctSlots);
    }

    @Test
    void barycenterOrdersSiblingsToReduceCrossingTendency() {
        // a→c, a→d, b→c, b→d — after barycenter, parents and children stay aligned by index.
        List<GraphNodeAutoLayout.NodeRef> nodes = List.of(
                new GraphNodeAutoLayout.NodeRef("a"),
                new GraphNodeAutoLayout.NodeRef("b"),
                new GraphNodeAutoLayout.NodeRef("c"),
                new GraphNodeAutoLayout.NodeRef("d")
        );
        List<GraphNodeAutoLayout.Edge> edges = List.of(
                new GraphNodeAutoLayout.Edge("a", "c"),
                new GraphNodeAutoLayout.Edge("b", "d")
        );

        List<GraphNodeAutoLayout.Arranged> arranged = GraphNodeAutoLayout.autoLayout(nodes, edges);
        Map<String, GraphNodeAutoLayout.Arranged> byRef = arranged.stream()
                .collect(Collectors.toMap(GraphNodeAutoLayout.Arranged::ref, Function.identity()));

        // Same-layer parents/children should keep relative order (a above b ⇒ c above d).
        assertTrue(byRef.get("a").offsetY() <= byRef.get("b").offsetY());
        assertTrue(byRef.get("c").offsetY() <= byRef.get("d").offsetY());
        assertEquals(byRef.get("a").offsetX(), byRef.get("b").offsetX(), 0.01f);
        assertEquals(byRef.get("c").offsetX(), byRef.get("d").offsetX(), 0.01f);
    }
}

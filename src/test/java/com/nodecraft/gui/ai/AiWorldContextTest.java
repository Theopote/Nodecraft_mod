package com.nodecraft.gui.ai;

import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.nodes.world.selection.SelectedRegionNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiWorldContextTest {

    @Test
    void resolvesSingleCompletedSelectedRegion() {
        NodeGraph graph = new NodeGraph("test");
        SelectedRegionNode node = region(12, 70, -4, 10, 72, 1);
        graph.addNode(node);

        AiWorldContextSnapshot.SelectedRegionContext result = AiWorldRegionResolver.resolve(graph, null);

        assertEquals("available", result.status());
        assertEquals(new AiWorldContextSnapshot.BlockCoordinate(10, 70, -4), result.min());
        assertEquals(new AiWorldContextSnapshot.BlockCoordinate(12, 72, 1), result.max());
        assertEquals(new AiWorldContextSnapshot.BlockCoordinate(3, 3, 6), result.size());
        assertEquals(54L, result.volume());
    }

    @Test
    void selectedRegionNodeWinsWhenGraphContainsMultipleRegions() {
        NodeGraph graph = new NodeGraph("test");
        SelectedRegionNode first = region(0, 0, 0, 1, 1, 1);
        SelectedRegionNode selected = region(10, 20, 30, 12, 22, 32);
        graph.addNode(first);
        graph.addNode(selected);

        AiWorldContextSnapshot.SelectedRegionContext result = AiWorldRegionResolver.resolve(graph, selected);

        assertEquals("available", result.status());
        assertEquals(selected.getId().toString(), result.sourceNodeId());
        assertEquals(new AiWorldContextSnapshot.BlockCoordinate(10, 20, 30), result.min());
    }

    @Test
    void reportsAmbiguousWhenMultipleRegionsExistWithoutSelectedRegionNode() {
        NodeGraph graph = new NodeGraph("test");
        graph.addNode(region(0, 0, 0, 1, 1, 1));
        graph.addNode(region(10, 10, 10, 11, 11, 11));

        assertEquals("ambiguous", AiWorldRegionResolver.resolve(graph, null).status());
    }

    @Test
    void promptContainsStructuredWorldContextOnlyWhenProvided() {
        AiWorldContextSnapshot snapshot = new AiWorldContextSnapshot(
                true,
                null,
                123L,
                null,
                null,
                new AiWorldContextSnapshot.SelectedRegionContext(
                        "available",
                        "node-1",
                        new AiWorldContextSnapshot.BlockCoordinate(1, 2, 3),
                        new AiWorldContextSnapshot.BlockCoordinate(4, 5, 6),
                        new AiWorldContextSnapshot.BlockCoordinate(4, 4, 4),
                        new AiWorldContextSnapshot.Vec3(2.5, 3.5, 4.5),
                        64L
                )
        );

        String enabled = AiPromptBuilder.buildUserPrompt("build here", "graph", snapshot);
        String disabled = AiPromptBuilder.buildUserPrompt("build here", "graph", null);

        assertTrue(enabled.contains("CURRENT_WORLD_CONTEXT (JSON):"));
        assertTrue(enabled.contains("\"sourceNodeId\":\"node-1\""));
        assertTrue(enabled.contains("\"volume\":64"));
        assertFalse(disabled.contains("node-1"));
        assertTrue(disabled.contains("{\"enabled\":false}"));
    }

    private static SelectedRegionNode region(int x1, int y1, int z1, int x2, int y2, int z2) {
        SelectedRegionNode node = new SelectedRegionNode();
        node.setPos1(x1, y1, z1);
        node.setPos2(x2, y2, z2);
        return node;
    }
}

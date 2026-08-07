package com.nodecraft.gui.components.panel;

import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.nodes.geometry.boolops.SdfBoxNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class NodeLibraryDisplayCacheTest {

    @Test
    void sortsByOrderThenDisplayNameAndCachesWithinEpoch() {
        NodeLibraryDisplayCache cache = new NodeLibraryDisplayCache();
        List<NodeInfo> source = List.of(
                info("b", "Beta", 20),
                info("a", "Alpha", 10),
                info("c", "Charlie", 10)
        );

        List<NodeInfo> first = cache.getSortedNodes(1L, "geometry", source);
        List<NodeInfo> second = cache.getSortedNodes(1L, "geometry", source);

        assertSame(first, second);
        assertEquals(List.of("a", "c", "b"), first.stream().map(NodeInfo::getId).toList());
        assertEquals(1, cache.size());
    }

    @Test
    void epochChangeInvalidatesCache() {
        NodeLibraryDisplayCache cache = new NodeLibraryDisplayCache();
        List<NodeInfo> source = List.of(info("a", "Alpha", 1));

        List<NodeInfo> epoch1 = cache.getSortedNodes(1L, "math", source);
        List<NodeInfo> epoch2 = cache.getSortedNodes(2L, "math", source);

        assertNotSame(epoch1, epoch2);
        assertEquals(2L, cache.boundEpoch());
        assertEquals(1, cache.size());
    }

    private static NodeInfo info(String id, String displayName, int order) {
        return new NodeInfo(id, displayName, "", "test", order, SdfBoxNode.class);
    }
}

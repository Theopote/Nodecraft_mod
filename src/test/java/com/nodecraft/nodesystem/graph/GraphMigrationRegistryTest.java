package com.nodecraft.nodesystem.graph;

import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedConnection;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Stamp-only format alignment — no historical remaps.
 */
class GraphMigrationRegistryTest {

    @Test
    void olderVersionIsStampedToCurrentPreservingNodesAndWires() {
        SavedGraph legacy = new SavedGraph();
        legacy.graphName = "legacy";
        legacy.formatVersion = 0;
        SavedNode node = new SavedNode();
        node.nodeId = "node-1";
        node.typeId = "output.preview.preview_blocks";
        legacy.nodes = List.of(node);

        SavedConnection connection = new SavedConnection();
        connection.sourceNodeId = "node-1";
        connection.sourcePortId = "legacy_port";
        connection.targetNodeId = "node-1";
        connection.targetPortId = "also_legacy";
        legacy.connections = List.of(connection);
        legacy.nodePositions = Map.of();

        SavedGraph stamped = GraphMigrationRegistry.migrateToCurrent(legacy);
        assertEquals(GraphFormatVersion.CURRENT, stamped.formatVersion);
        assertEquals("output.preview.preview_blocks", stamped.nodes.getFirst().typeId);
        assertEquals("legacy_port", stamped.connections.getFirst().sourcePortId);
        assertEquals("also_legacy", stamped.connections.getFirst().targetPortId);
    }

    @Test
    void currentVersionIsUnchanged() {
        SavedGraph current = new SavedGraph();
        current.formatVersion = GraphFormatVersion.CURRENT;
        current.nodes = List.of();
        current.connections = List.of();
        current.nodePositions = Map.of();

        SavedGraph result = GraphMigrationRegistry.migrateToCurrent(current);
        assertEquals(GraphFormatVersion.CURRENT, result.formatVersion);
        assertSame(current.nodes, result.nodes);
    }
}

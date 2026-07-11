package com.nodecraft.nodesystem.graph;

import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.io.GraphFormat;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphMigrationRegistryTest {

    private final NodeRegistry registry = NodeRegistry.getInstance();

    @BeforeEach
    void registerNodes() {
        registry.clear();
        registry.registerNode(new NodeInfo(
            "test.pass",
            "Pass",
            "pass-through test node",
            "test",
            0,
            GraphSerializerTest.PassNode.class
        ));
    }

    @AfterEach
    void clearRegistry() {
        registry.clear();
    }

    @Test
    void legacyGraphIsMigratedToCurrentVersion() {
        SavedGraph legacy = new SavedGraph();
        legacy.graphName = "legacy";
        legacy.formatVersion = GraphFormat.LEGACY_UNSPECIFIED;
        legacy.nodes = null;
        legacy.connections = null;
        legacy.nodePositions = null;

        SavedNode node = new SavedNode();
        node.nodeId = UUID.randomUUID().toString();
        node.typeId = "TEST.PASS";
        legacy.nodes = java.util.List.of(node);

        SavedGraph migrated = GraphMigrationRegistry.migrateToCurrent(legacy);
        assertEquals(GraphFormat.CURRENT, migrated.formatVersion);
        assertNotNull(migrated.nodes);
        assertNotNull(migrated.connections);
        assertNotNull(migrated.nodePositions);
        assertEquals("test.pass", migrated.nodes.getFirst().typeId);
    }

    @Test
    void migrateToCurrentLeavesFutureVersionsUntouched() {
        SavedGraph future = new SavedGraph();
        future.formatVersion = GraphFormat.CURRENT + 5;
        future.graphName = "future";

        SavedGraph migrated = GraphMigrationRegistry.migrateToCurrent(future);
        assertEquals(GraphFormat.CURRENT + 5, migrated.formatVersion);
        assertEquals("future", migrated.graphName);
    }

    @Test
    void loadFromSavedGraphMigratesLegacyPayload() {
        SavedGraph legacy = new SavedGraph();
        legacy.graphName = "legacy-load";
        SavedNode node = new SavedNode();
        node.nodeId = UUID.randomUUID().toString();
        node.typeId = "test.pass";
        legacy.nodes = java.util.List.of(node);
        legacy.connections = java.util.List.of();
        legacy.nodePositions = java.util.Map.of();

        GraphLoadResult result = GraphSerializer.loadFromSavedGraph(legacy);
        assertEquals(1, result.graph().getNodes().size());
        assertEquals(GraphFormat.CURRENT, legacy.formatVersion);
    }
}

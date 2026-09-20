package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.graph.GraphMigrationRegistry;
import com.nodecraft.nodesystem.graph.GraphSerializer;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedGraph;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Freeze fence for {@link GraphFormatVersion} policy.
 */
class GraphFormatVersionContractTest {

    @Test
    void currentVersionIdentityIsFrozen() {
        assertEquals(0, GraphFormatVersion.V0);
        assertEquals(1, GraphFormatVersion.V1);
        assertEquals(2, GraphFormatVersion.V2);
        assertEquals(GraphFormatVersion.V2, GraphFormatVersion.CURRENT);
    }

    @Test
    void policyHelpersMatchFrozenRules() {
        assertEquals(GraphFormatVersion.V0, GraphFormatVersion.normalize(-3));
        assertTrue(GraphFormatVersion.isLegacy(0));
        assertTrue(GraphFormatVersion.needsMigration(0));
        assertFalse(GraphFormatVersion.needsMigration(GraphFormatVersion.CURRENT));
        assertTrue(GraphFormatVersion.isCurrent(GraphFormatVersion.CURRENT));
        assertTrue(GraphFormatVersion.isNewerThanCurrent(GraphFormatVersion.CURRENT + 1));
    }

    @Test
    void serializerWritesCurrentVersion() {
        NodeGraph graph = new NodeGraph("format-contract");
        SavedGraph saved = GraphSerializer.toSavedGraph(graph);
        assertEquals(GraphFormatVersion.CURRENT, saved.formatVersion);
        assertTrue(GraphFormatVersion.isCurrent(saved.formatVersion));
    }

    @Test
    void legacyPayloadMigratesToCurrent() {
        SavedGraph legacy = new SavedGraph();
        legacy.formatVersion = GraphFormatVersion.V0;
        legacy.nodes = null;
        legacy.connections = null;
        legacy.nodePositions = null;

        SavedGraph migrated = GraphMigrationRegistry.migrateToCurrent(legacy);
        assertEquals(GraphFormatVersion.CURRENT, migrated.formatVersion);
        assertNotNull(migrated.nodes);
        assertNotNull(migrated.connections);
        assertNotNull(migrated.nodePositions);
        assertFalse(GraphFormatVersion.needsMigration(migrated.formatVersion));
    }

    @Test
    void futureVersionsAreLeftUntouched() {
        SavedGraph future = new SavedGraph();
        future.formatVersion = GraphFormatVersion.CURRENT + 3;
        future.nodes = new ArrayList<>();
        future.connections = new ArrayList<>();
        future.nodePositions = new HashMap<>();

        SavedGraph migrated = GraphMigrationRegistry.migrateToCurrent(future);
        assertEquals(GraphFormatVersion.CURRENT + 3, migrated.formatVersion);
        assertTrue(GraphFormatVersion.isNewerThanCurrent(migrated.formatVersion));
    }
}

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
 * Current-only graph format policy (no historical migration ladder).
 */
class GraphFormatVersionContractTest {

    @Test
    void currentVersionIdentity() {
        assertEquals(1, GraphFormatVersion.CURRENT);
        assertEquals(0, GraphFormatVersion.UNSPECIFIED);
        assertTrue(GraphFormatVersion.isCurrent(GraphFormatVersion.CURRENT));
    }

    @Test
    void policyHelpersMatchStampOnlyRules() {
        assertEquals(GraphFormatVersion.UNSPECIFIED, GraphFormatVersion.normalize(-3));
        assertTrue(GraphFormatVersion.isLegacy(0));
        assertTrue(GraphFormatVersion.needsMigration(0));
        assertFalse(GraphFormatVersion.needsMigration(GraphFormatVersion.CURRENT));
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
    void olderPayloadIsStampedToCurrentWithoutRemaps() {
        SavedGraph legacy = new SavedGraph();
        legacy.formatVersion = GraphFormatVersion.UNSPECIFIED;
        legacy.nodes = null;
        legacy.connections = null;
        legacy.nodePositions = null;

        SavedGraph stamped = GraphMigrationRegistry.migrateToCurrent(legacy);
        assertEquals(GraphFormatVersion.CURRENT, stamped.formatVersion);
        assertNotNull(stamped.nodes);
        assertNotNull(stamped.connections);
        assertNotNull(stamped.nodePositions);
        assertFalse(GraphFormatVersion.needsMigration(stamped.formatVersion));
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

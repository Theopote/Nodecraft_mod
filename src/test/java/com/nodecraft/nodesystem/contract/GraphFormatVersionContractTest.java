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
        assertEquals(3, GraphFormatVersion.V3);
        assertEquals(4, GraphFormatVersion.V4);
        assertEquals(5, GraphFormatVersion.V5);
        assertEquals(6, GraphFormatVersion.V6);
        assertEquals(7, GraphFormatVersion.V7);
        assertEquals(8, GraphFormatVersion.V8);
        assertEquals(9, GraphFormatVersion.V9);
        assertEquals(10, GraphFormatVersion.V10);
        assertEquals(11, GraphFormatVersion.V11);
        assertEquals(12, GraphFormatVersion.V12);
        assertEquals(13, GraphFormatVersion.V13);
        assertEquals(14, GraphFormatVersion.V14);
        assertEquals(15, GraphFormatVersion.V15);
        assertEquals(16, GraphFormatVersion.V16);
        assertEquals(17, GraphFormatVersion.V17);
        assertEquals(18, GraphFormatVersion.V18);
        assertEquals(19, GraphFormatVersion.V19);
        assertEquals(20, GraphFormatVersion.V20);
        assertEquals(22, GraphFormatVersion.V22);
        assertEquals(23, GraphFormatVersion.V23);
        assertEquals(24, GraphFormatVersion.V24);
        assertEquals(25, GraphFormatVersion.V25);
        assertEquals(26, GraphFormatVersion.V26);
        assertEquals(27, GraphFormatVersion.V27);
        assertEquals(28, GraphFormatVersion.V28);
        assertEquals(29, GraphFormatVersion.V29);
        assertEquals(30, GraphFormatVersion.V30);
        assertEquals(31, GraphFormatVersion.V31);
        assertEquals(32, GraphFormatVersion.V32);
        assertEquals(33, GraphFormatVersion.V33);
        assertEquals(34, GraphFormatVersion.V34);
        assertEquals(35, GraphFormatVersion.V35);
        assertEquals(36, GraphFormatVersion.V36);
        assertEquals(37, GraphFormatVersion.V37);
        assertEquals(38, GraphFormatVersion.V38);
        assertEquals(39, GraphFormatVersion.V39);
        assertEquals(40, GraphFormatVersion.V40);
        assertEquals(41, GraphFormatVersion.V41);
        assertEquals(42, GraphFormatVersion.V42);
        assertEquals(43, GraphFormatVersion.V43);
        assertEquals(44, GraphFormatVersion.V44);
        assertEquals(45, GraphFormatVersion.V45);
        assertEquals(46, GraphFormatVersion.V46);
        assertEquals(47, GraphFormatVersion.V47);
        assertEquals(48, GraphFormatVersion.V48);
        assertEquals(49, GraphFormatVersion.V49);
        assertEquals(50, GraphFormatVersion.V50);
        assertEquals(51, GraphFormatVersion.V51);
        assertEquals(52, GraphFormatVersion.V52);
        assertEquals(53, GraphFormatVersion.V53);
        assertEquals(54, GraphFormatVersion.V54);
        assertEquals(55, GraphFormatVersion.V55);
        assertEquals(56, GraphFormatVersion.V56);
        assertEquals(57, GraphFormatVersion.V57);
        assertEquals(58, GraphFormatVersion.V58);
        assertEquals(59, GraphFormatVersion.V59);
        assertEquals(60, GraphFormatVersion.V60);
        assertEquals(61, GraphFormatVersion.V61);
        assertEquals(62, GraphFormatVersion.V62);
        assertEquals(GraphFormatVersion.V62, GraphFormatVersion.CURRENT);
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

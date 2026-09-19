package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.graph.GraphSerializer;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.graph.SavedGraphNormalizer;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedGraph;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Freeze fence for {@link GraphFormatVersion} policy.
 */
class GraphFormatVersionContractTest {

    @Test
    void currentVersionIdentityIsFrozen() {
        assertEquals(1, GraphFormatVersion.V1);
        assertEquals(GraphFormatVersion.V1, GraphFormatVersion.CURRENT);
    }

    @Test
    void policyHelpersMatchFrozenRules() {
        assertEquals(GraphFormatVersion.CURRENT, GraphFormatVersion.normalize(-3));
        assertEquals(GraphFormatVersion.CURRENT, GraphFormatVersion.normalize(0));
        assertEquals(2, GraphFormatVersion.normalize(2));
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
    void legacyPayloadIsNormalizedToCurrent() {
        SavedGraph legacy = new SavedGraph();
        legacy.formatVersion = 0;
        legacy.nodes = null;
        legacy.connections = null;
        legacy.nodePositions = null;

        SavedGraph normalized = SavedGraphNormalizer.normalize(legacy);
        assertEquals(GraphFormatVersion.CURRENT, normalized.formatVersion);
        assertNotNull(normalized.nodes);
        assertNotNull(normalized.connections);
        assertNotNull(normalized.nodePositions);
    }

    @Test
    void futureVersionsAreLeftUntouched() {
        SavedGraph future = new SavedGraph();
        future.formatVersion = GraphFormatVersion.CURRENT + 3;
        future.nodes = new ArrayList<>();
        future.connections = new ArrayList<>();
        future.nodePositions = new HashMap<>();

        SavedGraph normalized = SavedGraphNormalizer.normalize(future);
        assertEquals(GraphFormatVersion.CURRENT + 3, normalized.formatVersion);
        assertTrue(GraphFormatVersion.isNewerThanCurrent(normalized.formatVersion));
    }
}

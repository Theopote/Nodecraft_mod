package com.nodecraft.nodesystem.graph;

import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SavedGraphNormalizerTest {

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
    void missingCollectionsAreInitialized() {
        SavedGraph legacy = new SavedGraph();
        legacy.graphName = "legacy";
        legacy.formatVersion = GraphFormatVersion.V0;
        legacy.nodes = null;
        legacy.connections = null;
        legacy.nodePositions = null;

        SavedGraph normalized = SavedGraphNormalizer.normalizeStructure(legacy);
        assertNotNull(normalized.nodes);
        assertNotNull(normalized.connections);
        assertNotNull(normalized.nodePositions);
    }

    @Test
    void loadFromSavedGraphMigratesLegacyPayload() {
        SavedGraph legacy = new SavedGraph();
        legacy.graphName = "legacy-load";
        legacy.formatVersion = GraphFormatVersion.V0;
        SavedNode node = new SavedNode();
        node.nodeId = UUID.randomUUID().toString();
        node.typeId = "test.pass";
        legacy.nodes = java.util.List.of(node);
        legacy.connections = java.util.List.of();
        legacy.nodePositions = java.util.Map.of();

        GraphLoadResult result = GraphSerializer.loadFromSavedGraph(legacy);
        assertEquals(1, result.graph().getNodes().size());
        assertEquals(GraphFormatVersion.CURRENT, legacy.formatVersion);
    }
}

package com.nodecraft.nodesystem.contract;

import com.nodecraft.gui.recommendation.NodePortIndex;
import com.nodecraft.gui.recommendation.NodeRecommendation;
import com.nodecraft.gui.recommendation.NodeRecommendationContext;
import com.nodecraft.gui.recommendation.NodeRecommendations;
import com.nodecraft.gui.recommendation.RecommendationDirection;
import com.nodecraft.gui.recommendation.RecommendationTrigger;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.TypeConversionRegistry;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.nodes.math.list_sequence.CreateListNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Batch 14 freeze: search/favorites plumbing + suggested connections honor TypeConversionRegistry.
 */
class SuggestedConnectionsContractTest {

    private static NodeRegistry registry;

    @BeforeAll
    static void ensureRegistry() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
        NodeRecommendations.get().initialize();
        NodeRecommendations.get().invalidateCache();
    }

    @Test
    void portIndexIncludesExplicitConversionPairs() {
        assertTrue(TypeConversionRegistry.requiresExplicitConversion(
                NodeDataType.LIST, NodeDataType.DATA_TREE));

        NodePortIndex index = new NodePortIndex();
        List<NodePortIndex.CandidatePort> candidates =
                index.findDownstreamCandidates(NodeDataType.LIST);

        assertFalse(candidates.isEmpty());
        assertTrue(candidates.stream().anyMatch(port ->
                port.dataType() == NodeDataType.DATA_TREE),
                "LIST output should suggest DATA_TREE inputs via explicit conversion");
    }

    @Test
    void listToDataTreeSuggestionUsesViaConversionPlan() {
        assertNotNull(TypeConversionRegistry.getSuggestedConversion(
                NodeDataType.LIST, NodeDataType.DATA_TREE));

        NodePortIndex index = new NodePortIndex();
        NodePortIndex.CandidatePort treeCandidate = index.findDownstreamCandidates(NodeDataType.LIST).stream()
                .filter(port -> port.dataType() == NodeDataType.DATA_TREE)
                .findFirst()
                .orElse(null);
        assertNotNull(treeCandidate, "Port index must expose LIST â†?DATA_TREE candidates");

        NodeGraph graph = new NodeGraph();
        INode listNode = new CreateListNode();
        graph.addNode(listNode);

        NodeRecommendationContext context = new NodeRecommendationContext(
                RecommendationTrigger.SELECTION_PANEL,
                RecommendationDirection.DOWNSTREAM,
                listNode.getId(),
                "output_list",
                NodeDataType.LIST,
                0f,
                0f,
                8);

        List<NodeRecommendation> recommendations =
                NodeRecommendations.get().recommend(graph, context);

        NodeRecommendation viaTree = recommendations.stream()
                .filter(rec -> treeCandidate.nodeId().equalsIgnoreCase(rec.nodeId()))
                .filter(rec -> rec.connectPortType() == NodeDataType.DATA_TREE)
                .findFirst()
                .orElseGet(() -> recommendations.stream()
                        .filter(rec -> rec.connectPortType() == NodeDataType.DATA_TREE)
                        .findFirst()
                        .orElse(null));

        assertNotNull(viaTree, "Expected DATA_TREE downstream suggestion from LIST (candidate="
                + treeCandidate.nodeId() + ", total=" + recommendations.size() + ")");
        assertEquals(NodeRecommendation.ConnectionPlan.VIA_CONVERSION, viaTree.connectionPlan());
        assertTrue(viaTree.reason().toLowerCase().contains("via")
                        || viaTree.reason().toLowerCase().contains("conversion"),
                "Reason should mention conversion: " + viaTree.reason());
    }

    @Test
    void suggestedCompatibilityHelperCoversDirectAndExplicit() {
        assertTrue(NodePortIndex.isSuggestedCompatible(NodeDataType.DOUBLE, NodeDataType.DOUBLE));
        assertTrue(NodePortIndex.isSuggestedCompatible(NodeDataType.LIST, NodeDataType.DATA_TREE));
        assertFalse(NodePortIndex.isSuggestedCompatible(NodeDataType.EXEC, NodeDataType.GEOMETRY));
    }
}

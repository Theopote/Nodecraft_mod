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
        assertNotNull(treeCandidate, "Port index must expose LIST→DATA_TREE candidates");

        // PORT_DRAG: selection LIST gate would hide DATA_TREE consumers.
        List<NodeRecommendation> recommendations = recommendListPortDrag(24);

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
    void directMatchRanksAboveExplicitConversion() {
        List<NodeRecommendation> recommendations = recommendListPortDrag(24);

        int directIdx = -1;
        int conversionIdx = -1;
        for (int i = 0; i < recommendations.size(); i++) {
            NodeRecommendation rec = recommendations.get(i);
            if (directIdx < 0
                    && rec.connectionPlan() == NodeRecommendation.ConnectionPlan.DIRECT
                    && rec.connectPortType() == NodeDataType.LIST) {
                directIdx = i;
            }
            if (conversionIdx < 0
                    && rec.connectionPlan() == NodeRecommendation.ConnectionPlan.VIA_CONVERSION
                    && rec.connectPortType() == NodeDataType.DATA_TREE) {
                conversionIdx = i;
            }
        }

        assertTrue(directIdx >= 0, "expected a DIRECT LIST consumer, got " + summarize(recommendations));
        assertTrue(conversionIdx >= 0,
            "expected a VIA_CONVERSION DATA_TREE consumer, got " + summarize(recommendations));
        assertTrue(directIdx < conversionIdx,
            "DIRECT LIST must rank above VIA_CONVERSION DATA_TREE, got direct@"
                + directIdx + " conversion@" + conversionIdx + " in " + summarize(recommendations));
    }

    @Test
    void portIndexRebuildsAfterInvalidate() {
        NodePortIndex index = new NodePortIndex();
        List<NodePortIndex.CandidatePort> before =
            index.findDownstreamCandidates(NodeDataType.LIST);
        assertFalse(before.isEmpty());
        long epochBefore = registry.getIntrospectionEpoch();

        index.invalidate();
        List<NodePortIndex.CandidatePort> after =
            index.findDownstreamCandidates(NodeDataType.LIST);
        assertFalse(after.isEmpty(), "index must rebuild after invalidate");
        assertTrue(after.stream().anyMatch(port -> port.dataType() == NodeDataType.DATA_TREE),
            "rebuilt index must still expose LIST→DATA_TREE conversion candidates");
        assertEquals(epochBefore, registry.getIntrospectionEpoch(),
            "invalidate alone must not bump registry introspection epoch");
    }

    @Test
    void suggestedCompatibilityHelperCoversDirectAndExplicit() {
        assertTrue(NodePortIndex.isSuggestedCompatible(NodeDataType.DOUBLE, NodeDataType.DOUBLE));
        assertTrue(NodePortIndex.isSuggestedCompatible(NodeDataType.LIST, NodeDataType.DATA_TREE));
        assertFalse(NodePortIndex.isSuggestedCompatible(NodeDataType.EXEC, NodeDataType.GEOMETRY));
    }

    private static List<NodeRecommendation> recommendListPortDrag(int limit) {
        NodeGraph graph = new NodeGraph();
        INode listNode = new CreateListNode();
        graph.addNode(listNode);
        NodeRecommendationContext context = new NodeRecommendationContext(
            RecommendationTrigger.PORT_DRAG,
            RecommendationDirection.DOWNSTREAM,
            listNode.getId(),
            "output_list",
            NodeDataType.LIST,
            0f,
            0f,
            limit);
        List<NodeRecommendation> recommendations = NodeRecommendations.get().recommend(graph, context);
        assertFalse(recommendations.isEmpty(), "expected LIST Port Drag recommendations");
        return recommendations;
    }

    private static String summarize(List<NodeRecommendation> recs) {
        return recs.stream()
            .map(rec -> rec.nodeId() + "/" + rec.connectionPlan() + "/" + rec.connectPortType())
            .toList()
            .toString();
    }
}

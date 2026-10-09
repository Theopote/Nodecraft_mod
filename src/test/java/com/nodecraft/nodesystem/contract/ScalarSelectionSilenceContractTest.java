package com.nodecraft.nodesystem.contract;

import com.nodecraft.gui.recommendation.NodeRecommendation;
import com.nodecraft.gui.recommendation.NodeRecommendationContext;
import com.nodecraft.nodesystem.recommendation.NodeRecommendationRules;
import com.nodecraft.nodesystem.recommendation.NodeRecommendationRulesLoader;
import com.nodecraft.gui.recommendation.NodeRecommendations;
import com.nodecraft.gui.recommendation.RecommendationDirection;
import com.nodecraft.gui.recommendation.RecommendationTrigger;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.nodes.input.context.PlayerPositionNode;
import com.nodecraft.nodesystem.nodes.input.numeric.FloatSliderNode;
import com.nodecraft.nodesystem.nodes.input.numeric.IntegerSliderNode;
import com.nodecraft.nodesystem.nodes.input.values.BooleanToggleNode;
import com.nodecraft.nodesystem.nodes.input.values.TextInputNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Generic scalar Selection silence + Port Drag compatibility contracts.
 */
class ScalarSelectionSilenceContractTest {

    private static final String BOX = "geometry.primitives.box";
    private static final String SNAP = "world.selection.snap_point_to_block";

    @BeforeAll
    static void ensureRegistry() {
        NodeRegistry registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
        NodeRecommendations.get().initialize();
        NodeRecommendations.get().reloadRules();
        NodeRecommendations.get().invalidateCache();
    }

    @Test
    void selectionSilentTypesConfigured() {
        NodeRecommendationRules rules = NodeRecommendationRulesLoader.load();
        List<String> silent = rules.defaults.selectionSilentTypes;
        assertFalse(silent == null || silent.isEmpty(), "selectionSilentTypes must be configured");
        assertTrue(silent.stream().anyMatch(s -> "double".equalsIgnoreCase(s)));
        assertTrue(silent.stream().anyMatch(s -> "integer".equalsIgnoreCase(s)));
        assertTrue(silent.stream().anyMatch(s -> "boolean".equalsIgnoreCase(s)));
        assertTrue(silent.stream().anyMatch(s -> "string".equalsIgnoreCase(s)));
        assertTrue(silent.stream().anyMatch(s -> "float".equalsIgnoreCase(s)));
    }

    @Test
    void numberSliderSelectionIsEmptyWithoutSemanticRule() {
        List<NodeRecommendation> recs = recommendSelection(new FloatSliderNode(), 8);
        assertTrue(recs.isEmpty(),
            "Number Slider selection should be silent without exact rules, got " + ids(recs));
    }

    @Test
    void integerSliderSelectionIsEmptyWithoutSemanticRule() {
        List<NodeRecommendation> recs = recommendSelection(new IntegerSliderNode(), 8);
        assertTrue(recs.isEmpty(),
            "Integer Slider selection should be silent without exact rules, got " + ids(recs));
    }

    @Test
    void booleanToggleSelectionIsEmptyWithoutSemanticRule() {
        List<NodeRecommendation> recs = recommendSelection(new BooleanToggleNode(), 8);
        assertTrue(recs.isEmpty(),
            "Boolean Toggle selection should be silent without exact rules, got " + ids(recs));
    }

    @Test
    void textInputSelectionIsEmptyWithoutSemanticRule() {
        List<NodeRecommendation> recs = recommendSelection(new TextInputNode(), 8);
        assertTrue(recs.isEmpty(),
            "Text Input selection should be silent without exact rules, got " + ids(recs));
    }

    @Test
    void numberSliderPortDragStillReturnsCompatibleCandidates() {
        List<NodeRecommendation> recs = recommendPort(
            new FloatSliderNode(), "output_value", NodeDataType.DOUBLE);
        assertFalse(recs.isEmpty(), "Port Drag from Number Slider must still show compatible DOUBLE consumers");
    }

    @Test
    void playerPositionSelectionStillUsesExactSemanticRules() {
        List<NodeRecommendation> recs = recommendSelection(new PlayerPositionNode(), 8);
        assertFalse(recs.isEmpty(), "Player Position must keep exact semantic suggestions");
        assertRank(recs, BOX, 0);
        assertTopContains(recs, BOX, SNAP);
    }

    private static List<NodeRecommendation> recommendSelection(INode source, int limit) {
        NodeGraph graph = new NodeGraph();
        graph.addNode(source);
        return NodeRecommendations.get().recommendForSelectedNode(graph, source, limit);
    }

    private static List<NodeRecommendation> recommendPort(INode source, String portId, NodeDataType type) {
        NodeGraph graph = new NodeGraph();
        graph.addNode(source);
        NodeRecommendationContext context = new NodeRecommendationContext(
            RecommendationTrigger.PORT_DRAG,
            RecommendationDirection.DOWNSTREAM,
            source.getId(),
            portId,
            type,
            0f,
            0f,
            8);
        return NodeRecommendations.get().recommend(graph, context);
    }

    private static List<String> ids(List<NodeRecommendation> recs) {
        return recs.stream().map(NodeRecommendation::nodeId).map(String::toLowerCase).toList();
    }

    private static void assertTopContains(List<NodeRecommendation> recs, String... nodeIds) {
        List<String> topIds = ids(recs);
        for (String nodeId : nodeIds) {
            assertTrue(topIds.contains(nodeId.toLowerCase(Locale.ROOT)),
                "expected " + nodeId + " in top recommendations, got " + topIds);
        }
    }

    private static void assertRank(List<NodeRecommendation> recs, String nodeId, int expectedIndex) {
        List<String> topIds = ids(recs);
        int actual = topIds.indexOf(nodeId.toLowerCase(Locale.ROOT));
        assertTrue(actual >= 0, "expected " + nodeId + " in recommendations, got " + topIds);
        assertEquals(expectedIndex, actual,
            "expected " + nodeId + " at rank " + expectedIndex + ", got " + actual + " in " + topIds);
    }
}

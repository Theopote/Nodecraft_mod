package com.nodecraft.nodesystem.contract;

import com.nodecraft.gui.recommendation.NodeRecommendation;
import com.nodecraft.gui.recommendation.NodeRecommendationContext;
import com.nodecraft.gui.recommendation.NodeRecommendations;
import com.nodecraft.gui.recommendation.RecommendationDirection;
import com.nodecraft.gui.recommendation.RecommendationTrigger;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.nodes.material.basic_assignment.AssignBlockTypeNode;
import com.nodecraft.nodesystem.nodes.material.basic_assignment.BlockPaletteNode;
import com.nodecraft.nodesystem.nodes.material.block_state.ApplyBlockStateNode;
import com.nodecraft.nodesystem.nodes.material.gradient_mapping.GradientRampMapNode;
import com.nodecraft.nodesystem.nodes.material.pattern_mapping.BrickPatternMapNode;
import com.nodecraft.nodesystem.nodes.material.surface_aging.WeatheringNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Material pipeline stage recommendation contracts:
 * Assignment → Mapping → Aging → Block State → Preview / Apply.
 */
class MaterialPipelineRecommendationContractTest {

    private static final String ASSIGN = "material.basic_assignment.assign_block_type";
    private static final String GRADIENT = "material.gradient_mapping.gradient_ramp_map";
    private static final String WEATHERING = "material.surface_aging.weathering";
    private static final String MOSS = "material.surface_aging.moss_growth";
    private static final String APPLY_BLOCK_STATE = "material.block_state.apply_block_state";
    private static final String PREVIEW_BLOCKS = "output.preview.preview_blocks";
    private static final String MERGE = "output.execute.merge_block_placements";
    private static final String APPLY = "output.execute.apply_changes";

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
    void assignPlacementsPreferPreviewThenMaterialPipeline() {
        List<NodeRecommendation> recs = recommendPort(
            new AssignBlockTypeNode(), "output_placements", NodeDataType.BLOCK_PLACEMENT_LIST);
        assertRank(recs, PREVIEW_BLOCKS, 0);
        assertTopContains(recs, PREVIEW_BLOCKS, GRADIENT, WEATHERING, APPLY_BLOCK_STATE, MERGE);
        assertNotInTopN(recs, APPLY, 2);
        assertEquals("input_placements", connectPort(recs, GRADIENT));
    }

    @Test
    void weatheringPrefersAgingPeersNotAssign() {
        List<NodeRecommendation> recs = recommendPort(
            new WeatheringNode(), "output_placements", NodeDataType.BLOCK_PLACEMENT_LIST);
        assertTopContains(recs, MOSS, PREVIEW_BLOCKS);
        assertNotInTopN(recs, ASSIGN, 5);
        assertEquals("input_placements", connectPort(recs, MOSS));
    }

    @Test
    void weatheringSelectionIncludesApply() {
        List<NodeRecommendation> recs = recommendSelection(new WeatheringNode(), 8);
        assertTopContains(recs, APPLY);
        assertEquals("input_block_placements", connectPort(recs, APPLY));
    }

    @Test
    void gradientPrefersWeatheringNotAssign() {
        List<NodeRecommendation> recs = recommendPort(
            new GradientRampMapNode(), "output_placements", NodeDataType.BLOCK_PLACEMENT_LIST);
        assertTopContains(recs, WEATHERING);
        assertNotInTopN(recs, ASSIGN, 5);
        assertEquals("input_placements", connectPort(recs, WEATHERING));
    }

    @Test
    void brickPatternPrefersWeatheringNotAssign() {
        List<NodeRecommendation> recs = recommendPort(
            new BrickPatternMapNode(), "output_placements", NodeDataType.BLOCK_PLACEMENT_LIST);
        assertTopContains(recs, WEATHERING);
        assertNotInTopN(recs, ASSIGN, 5);
    }

    @Test
    void applyBlockStateIsTerminalTowardPreviewMergeApply() {
        List<NodeRecommendation> recs = recommendPort(
            new ApplyBlockStateNode(), "output_placements", NodeDataType.BLOCK_PLACEMENT_LIST);
        assertRank(recs, PREVIEW_BLOCKS, 0);
        assertTopContains(recs, PREVIEW_BLOCKS, MERGE, APPLY);
        assertNotInTopN(recs, GRADIENT, 3);
        assertNotInTopN(recs, WEATHERING, 3);
    }

    @Test
    void blockPaletteSelectionIncludesApplyAndPreview() {
        List<NodeRecommendation> recs = recommendSelection(new BlockPaletteNode(), 8);
        assertTopContains(recs, PREVIEW_BLOCKS, APPLY);
        assertEquals("input_block_placements", connectPort(recs, PREVIEW_BLOCKS));
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
        List<NodeRecommendation> recommendations = NodeRecommendations.get().recommend(graph, context);
        assertFalse(recommendations.isEmpty(),
            "expected recommendations for " + source.getTypeId() + "#" + portId);
        return recommendations;
    }

    private static List<NodeRecommendation> recommendSelection(INode source, int limit) {
        NodeGraph graph = new NodeGraph();
        graph.addNode(source);
        return NodeRecommendations.get().recommendForSelectedNode(graph, source, limit);
    }

    private static void assertTopContains(List<NodeRecommendation> recs, String... nodeIds) {
        List<String> topIds = recs.stream().map(NodeRecommendation::nodeId).map(String::toLowerCase).toList();
        for (String nodeId : nodeIds) {
            assertTrue(topIds.contains(nodeId.toLowerCase(Locale.ROOT)),
                "expected " + nodeId + " in top recommendations, got " + topIds);
        }
    }

    private static void assertRank(List<NodeRecommendation> recs, String nodeId, int expectedIndex) {
        List<String> topIds = recs.stream().map(NodeRecommendation::nodeId).map(String::toLowerCase).toList();
        String needle = nodeId.toLowerCase(Locale.ROOT);
        int actual = topIds.indexOf(needle);
        assertTrue(actual >= 0, "expected " + nodeId + " in recommendations, got " + topIds);
        assertEquals(expectedIndex, actual,
            "expected " + nodeId + " at rank " + expectedIndex + ", got " + actual + " in " + topIds);
    }

    private static void assertNotInTopN(List<NodeRecommendation> recs, String nodeId, int n) {
        List<String> topN = recs.stream()
            .limit(Math.max(0, n))
            .map(NodeRecommendation::nodeId)
            .map(String::toLowerCase)
            .toList();
        assertFalse(topN.contains(nodeId.toLowerCase(Locale.ROOT)),
            "expected " + nodeId + " NOT in top " + n + ", got " + topN);
    }

    private static String connectPort(List<NodeRecommendation> recs, String nodeId) {
        return recs.stream()
            .filter(rec -> nodeId.equalsIgnoreCase(rec.nodeId()))
            .map(NodeRecommendation::connectPortId)
            .findFirst()
            .orElseThrow(() -> new AssertionError("missing recommendation for " + nodeId));
    }
}

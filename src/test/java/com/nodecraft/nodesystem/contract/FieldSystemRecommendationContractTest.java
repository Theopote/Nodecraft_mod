package com.nodecraft.nodesystem.contract;

import com.nodecraft.gui.recommendation.NodeRecommendation;
import com.nodecraft.gui.recommendation.NodeRecommendationContext;
import com.nodecraft.gui.recommendation.NodeRecommendations;
import com.nodecraft.gui.recommendation.RecommendationDirection;
import com.nodecraft.gui.recommendation.RecommendationTrigger;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.nodes.math.fields.PointAttractorFieldNode;
import com.nodecraft.nodesystem.nodes.math.fields.ScalarFieldFromSdfNode;
import com.nodecraft.nodesystem.nodes.math.fields.ScalarFieldNoiseNode;
import com.nodecraft.nodesystem.nodes.math.fields.VectorFieldFromSdfGradientNode;
import com.nodecraft.nodesystem.nodes.pattern.linear.CurveArrayNode;
import com.nodecraft.nodesystem.nodes.world.terrain.HeightSeedFieldNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Generic math.fields + CurveArray origins recommendation contracts.
 */
class FieldSystemRecommendationContractTest {

    private static final String SCALAR_BINARY = "math.fields.scalar_binary_op";
    private static final String SCALAR_SAMPLE_POINT = "math.fields.scalar_sample_point";
    private static final String SCALAR_SAMPLE_POINTS = "math.fields.scalar_sample_points";
    private static final String VECTOR_BINARY = "math.fields.vector_binary_op";
    private static final String VECTOR_SAMPLE_POINT = "math.fields.vector_sample_point";
    private static final String VECTOR_SAMPLE_POINTS = "math.fields.vector_sample_points";
    private static final String ATTRACTOR_BLEND = "math.fields.attractor_blend";
    private static final String FLOW_DIRECTION = "world.terrain.flow_direction_field";
    private static final String BIOME_CLASSIFY = "world.terrain.biome_classify";
    private static final String THERMAL_EROSION = "world.terrain.thermal_erosion_step";
    private static final String HEIGHTFIELD_TO_BLOCKS = "world.terrain.heightfield_to_blocks";
    private static final String POINTS_TO_PATH = "geometry.curves.points_to_path";

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
    void scalarNoisePrefersGenericOpsNotTerrain() {
        List<NodeRecommendation> recs = recommend(
            new ScalarFieldNoiseNode(), "output_field", NodeDataType.SCALAR_FIELD);
        assertRank(recs, SCALAR_BINARY, 0);
        assertTopContains(recs, SCALAR_BINARY, SCALAR_SAMPLE_POINT, SCALAR_SAMPLE_POINTS);
        // Only three non-terrain SCALAR_FIELD consumers exist; type-compat terrain may fill later slots.
        assertNotInTopN(recs, BIOME_CLASSIFY, 3);
        assertNotInTopN(recs, THERMAL_EROSION, 3);
        assertNotInTopN(recs, HEIGHTFIELD_TO_BLOCKS, 3);
        assertEquals("input_a", connectPort(recs, SCALAR_BINARY));
    }

    @Test
    void pointAttractorPrefersVectorFieldChain() {
        List<NodeRecommendation> recs = recommend(
            new PointAttractorFieldNode(), "output_field", NodeDataType.VECTOR_FIELD);
        assertRank(recs, VECTOR_BINARY, 0);
        assertTopContains(recs, VECTOR_BINARY, VECTOR_SAMPLE_POINT, VECTOR_SAMPLE_POINTS, ATTRACTOR_BLEND);
        assertEquals("input_field_a", connectPort(recs, ATTRACTOR_BLEND));
    }

    @Test
    void heightSeedStillPrefersFlowDirection() {
        List<NodeRecommendation> recs = recommend(
            new HeightSeedFieldNode(), "output_height_field", NodeDataType.SCALAR_FIELD);
        assertRank(recs, FLOW_DIRECTION, 0);
        assertEquals("input_height_field", connectPort(recs, FLOW_DIRECTION));
    }

    @Test
    void curveArrayOriginsPreferPointsToPathAndFieldSampling() {
        List<NodeRecommendation> recs = recommend(
            new CurveArrayNode(), "output_origins", NodeDataType.POINT_LIST);
        assertRank(recs, POINTS_TO_PATH, 0);
        assertTopContains(recs, POINTS_TO_PATH, SCALAR_SAMPLE_POINTS, VECTOR_SAMPLE_POINTS);
        assertEquals("input_points", connectPort(recs, POINTS_TO_PATH));
        assertEquals("input_points", connectPort(recs, SCALAR_SAMPLE_POINTS));
    }

    @Test
    void scalarFromSdfPrefersCombineWithReadableReason() {
        List<NodeRecommendation> recs = recommend(
            new ScalarFieldFromSdfNode(), "output_field", NodeDataType.SCALAR_FIELD);
        assertRank(recs, SCALAR_BINARY, 0);
        assertTopContains(recs, SCALAR_BINARY, SCALAR_SAMPLE_POINT, SCALAR_SAMPLE_POINTS);
        String reason = reasonOf(recs, SCALAR_BINARY);
        assertTrue(reason.contains("SDF") || reason.contains("组合"),
            "expected SDF/combine reason, got: " + reason);
    }

    @Test
    void vectorFromSdfGradientPrefersSampleBlendAndCombine() {
        List<NodeRecommendation> recs = recommend(
            new VectorFieldFromSdfGradientNode(), "output_field", NodeDataType.VECTOR_FIELD);
        assertRank(recs, VECTOR_SAMPLE_POINTS, 0);
        assertTopContains(recs, VECTOR_SAMPLE_POINTS, ATTRACTOR_BLEND, VECTOR_BINARY);
        String reason = reasonOf(recs, VECTOR_SAMPLE_POINTS);
        assertTrue(reason.contains("梯度") || reason.contains("SDF"),
            "expected gradient/SDF reason, got: " + reason);
    }

    private static List<NodeRecommendation> recommend(INode source, String portId, NodeDataType type) {
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

    private static String reasonOf(List<NodeRecommendation> recs, String nodeId) {
        return recs.stream()
            .filter(rec -> nodeId.equalsIgnoreCase(rec.nodeId()))
            .map(NodeRecommendation::reason)
            .findFirst()
            .orElse("");
    }
}

package com.nodecraft.nodesystem.contract;

import com.nodecraft.gui.recommendation.NodeRecommendation;
import com.nodecraft.gui.recommendation.NodeRecommendationContext;
import com.nodecraft.gui.recommendation.NodeRecommendations;
import com.nodecraft.gui.recommendation.RecommendationDirection;
import com.nodecraft.gui.recommendation.RecommendationTrigger;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.nodes.geometry.solids.ExtrudeRegionNode;
import com.nodecraft.nodesystem.nodes.geometry.solids.SweepProfileAlongPathNode;
import com.nodecraft.nodesystem.nodes.geometry.voxel.VoxelizeGeometryNode;
import com.nodecraft.nodesystem.nodes.math.data_tree.GraftListNode;
import com.nodecraft.nodesystem.nodes.pattern.linear.LinearArrayNode;
import com.nodecraft.nodesystem.nodes.world.read.GetBlocksInRegionNode;
import com.nodecraft.nodesystem.nodes.world.read.GetSurfaceBlocksNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DATA_TREE structure ops + world.read analysis recommendation contracts.
 */
class DataTreeWorldReadRecommendationContractTest {

    private static final String BRANCH = "math.data_tree.branch";
    private static final String ITEM = "math.data_tree.item";
    private static final String STATISTICS = "math.data_tree.statistics";
    private static final String VIEWER = "math.data_tree.viewer";
    private static final String BLOCK_BOUNDS = "geometry.analysis.block_bounds";
    private static final String PREVIEW_BLOCKS = "output.preview.preview_blocks";
    private static final String ASSIGN = "material.basic_assignment.assign_block_type";
    private static final String PROFILE_TO_REGION = "geometry.profiles.profile_to_region";
    private static final String RECTANGLE = "geometry.profiles.rectangle_profile";
    private static final String CIRCLE = "geometry.profiles.circle_profile";

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
    void dataTreePrefersStructureOpsNotAssign() {
        List<NodeRecommendation> recs = recommend(
            new LinearArrayNode(), "output_geometry_tree", NodeDataType.DATA_TREE,
            RecommendationDirection.DOWNSTREAM);
        assertRank(recs, BRANCH, 0);
        assertTopContains(recs, BRANCH, ITEM, STATISTICS, VIEWER);
        // Only four structure-safe DATA_TREE consumers; payload-specific Assign may fill later slots.
        assertNotInTopN(recs, ASSIGN, 4);
        assertEquals("input_tree", connectPort(recs, BRANCH));
    }

    @Test
    void graftListTreeAlsoPrefersBranch() {
        List<NodeRecommendation> recs = recommend(
            new GraftListNode(), "output_tree", NodeDataType.DATA_TREE,
            RecommendationDirection.DOWNSTREAM);
        assertRank(recs, BRANCH, 0);
        assertTopContains(recs, BRANCH, ITEM, STATISTICS, VIEWER);
    }

    @Test
    void getBlocksInRegionCoordinatesPreferBlockBounds() {
        List<NodeRecommendation> recs = recommend(
            new GetBlocksInRegionNode(), "output_coordinates", NodeDataType.BLOCK_LIST,
            RecommendationDirection.DOWNSTREAM);
        assertRank(recs, BLOCK_BOUNDS, 0);
        assertTopContains(recs, BLOCK_BOUNDS, PREVIEW_BLOCKS);
        assertNotInTopN(recs, ASSIGN, 1);
        assertEquals("input_blocks", connectPort(recs, BLOCK_BOUNDS));
    }

    @Test
    void getSurfaceBlocksPositionsPreferBlockBounds() {
        List<NodeRecommendation> recs = recommend(
            new GetSurfaceBlocksNode(), "output_surface_positions", NodeDataType.BLOCK_LIST,
            RecommendationDirection.DOWNSTREAM);
        assertTopContains(recs, BLOCK_BOUNDS);
        assertRank(recs, BLOCK_BOUNDS, 0);
    }

    @Test
    void voxelizeBlocksStillPreferAssignFirst() {
        List<NodeRecommendation> recs = recommend(
            new VoxelizeGeometryNode(), "output_blocks", NodeDataType.BLOCK_LIST,
            RecommendationDirection.DOWNSTREAM);
        assertRank(recs, ASSIGN, 0);
    }

    @Test
    void extrudeRegionUpstreamPrefersProfileToRegion() {
        List<NodeRecommendation> recs = recommend(
            new ExtrudeRegionNode(), "input_region", NodeDataType.PLANAR_REGION,
            RecommendationDirection.UPSTREAM);
        assertRank(recs, PROFILE_TO_REGION, 0);
        assertEquals("output_region", connectPort(recs, PROFILE_TO_REGION));
    }

    @Test
    void sweepProfileUpstreamPrefersRectangleThenCircle() {
        List<NodeRecommendation> recs = recommend(
            new SweepProfileAlongPathNode(), "input_profile", NodeDataType.POLYGON_PROFILE,
            RecommendationDirection.UPSTREAM);
        assertRank(recs, RECTANGLE, 0);
        assertTopContains(recs, RECTANGLE, CIRCLE);
        assertEquals("output_profile", connectPort(recs, RECTANGLE));
    }

    private static List<NodeRecommendation> recommend(
            INode source,
            String portId,
            NodeDataType type,
            RecommendationDirection direction) {
        NodeGraph graph = new NodeGraph();
        graph.addNode(source);
        NodeRecommendationContext context = new NodeRecommendationContext(
            RecommendationTrigger.PORT_DRAG,
            direction,
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
}

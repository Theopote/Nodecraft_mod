package com.nodecraft.nodesystem.contract;

import com.nodecraft.gui.recommendation.NodeRecommendation;
import com.nodecraft.gui.recommendation.NodeRecommendationContext;
import com.nodecraft.gui.recommendation.NodeRecommendations;
import com.nodecraft.gui.recommendation.RecommendationDirection;
import com.nodecraft.gui.recommendation.RecommendationTrigger;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.nodes.geometry.boolops.DifferenceNode;
import com.nodecraft.nodesystem.nodes.geometry.combine.CombineGeometryNode;
import com.nodecraft.nodesystem.nodes.geometry.primitives.BoxCenterSizeNode;
import com.nodecraft.nodesystem.nodes.geometry.primitives.SphereByCenterRadiusNode;
import com.nodecraft.nodesystem.nodes.geometry.voxel.VoxelizeGeometryNode;
import com.nodecraft.nodesystem.nodes.material.basic_assignment.AssignBlockTypeNode;
import com.nodecraft.nodesystem.nodes.output.execute.ApplyChangesNode;
import com.nodecraft.nodesystem.nodes.output.execute.MergeBlockPlacementsNode;
import com.nodecraft.nodesystem.nodes.output.preview.PreviewBlocksNode;
import com.nodecraft.nodesystem.nodes.reference.points.CoordinateInputNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bake-chain Suggested Connections contracts (Batch 3):
 * Geometry → Voxelize → Assign → Preview Blocks → Apply.
 */
class BakeChainRecommendationContractTest {

    private static final String ASSIGN = "material.basic_assignment.assign_block_type";
    private static final String VOXELIZE = "geometry.voxel.voxelize_geometry";
    private static final String PREVIEW_GEOMETRY = "output.preview.preview_geometry";
    private static final String PREVIEW_BLOCKS = "output.preview.preview_blocks";
    private static final String APPLY = "output.execute.apply_changes";
    private static final String MERGE = "output.execute.merge_block_placements";
    private static final String COMBINE = "geometry.combine.geometry";
    private static final String TRANSFORM = "transform.basic_transforms.transform_geometry";
    private static final String GET_BLOCK = "world.read.get_block";
    private static final String SET_BLOCK = "world.write.set_block";

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
    void boxGeometryPrefersVoxelizeOverAssign() {
        List<NodeRecommendation> recs = recommendPort(
            new BoxCenterSizeNode(), "output_geometry", NodeDataType.GEOMETRY);
        assertTopContains(recs, VOXELIZE);
        assertAssignNotAheadOfVoxelize(recs);
    }

    @Test
    void sphereGeometryPrefersVoxelizeOverAssign() {
        List<NodeRecommendation> recs = recommendPort(
            new SphereByCenterRadiusNode(), "output_geometry", NodeDataType.GEOMETRY);
        assertTopContains(recs, VOXELIZE);
        assertAssignNotAheadOfVoxelize(recs);
        assertNotInTopN(recs, ASSIGN, 5);
    }

    @Test
    void voxelizeBlocksRankAssignFirst() {
        List<NodeRecommendation> recs = recommendPort(
            new VoxelizeGeometryNode(), "output_blocks", NodeDataType.BLOCK_LIST);
        assertRank(recs, ASSIGN, 0);
        assertEquals("input_coordinates", connectPort(recs, ASSIGN));
        assertTopContains(recs, PREVIEW_BLOCKS);
    }

    @Test
    void assignPlacementsPreferPreviewThenMergeNotApplyFirst() {
        List<NodeRecommendation> recs = recommendPort(
            new AssignBlockTypeNode(), "output_placements", NodeDataType.BLOCK_PLACEMENT_LIST);
        assertRank(recs, PREVIEW_BLOCKS, 0);
        assertEquals("input_block_placements", connectPort(recs, PREVIEW_BLOCKS));
        assertNotInTopN(recs, APPLY, 2);
        assertTopContains(recs, MERGE);
    }

    @Test
    void mergePlacementsPreferPreviewThenApply() {
        List<NodeRecommendation> recs = recommendPort(
            new MergeBlockPlacementsNode(), "output_placements", NodeDataType.BLOCK_PLACEMENT_LIST);
        assertRank(recs, PREVIEW_BLOCKS, 0);
        assertTopContains(recs, APPLY);
        assertNotInTopN(recs, MERGE, 5);
    }

    @Test
    void selectionOnPreviewBlocksIsEmpty() {
        PreviewBlocksNode preview = new PreviewBlocksNode();
        NodeGraph graph = new NodeGraph();
        graph.addNode(preview);
        List<NodeRecommendation> recs = NodeRecommendations.get().recommendForSelectedNode(graph, preview, 8);
        assertTrue(recs.isEmpty(), "Preview Blocks selection should be a recommendation sink, got " + ids(recs));
    }

    @Test
    void selectionOnApplyChangesIsEmpty() {
        ApplyChangesNode apply = new ApplyChangesNode();
        NodeGraph graph = new NodeGraph();
        graph.addNode(apply);
        List<NodeRecommendation> recs = NodeRecommendations.get().recommendForSelectedNode(graph, apply, 8);
        assertTrue(recs.isEmpty(), "Apply Changes selection should be a recommendation sink, got " + ids(recs));
    }

    @Test
    void portDragFromVoxelizeBlocksStillRecommends() {
        List<NodeRecommendation> recs = recommendPort(
            new VoxelizeGeometryNode(), "output_blocks", NodeDataType.BLOCK_LIST);
        assertFalse(recs.isEmpty());
        assertRank(recs, ASSIGN, 0);
    }

    @Test
    void blockPosPrefersGetBlockAndDoesNotSurfaceSetBlock() {
        List<NodeRecommendation> recs = recommendPort(
            new CoordinateInputNode(), "output_block_pos", NodeDataType.BLOCK_POS);
        assertTopContains(recs, GET_BLOCK);
        assertFalse(ids(recs).contains(SET_BLOCK),
            "Set Block must not appear from generic BLOCK_POS rules, got " + ids(recs));
    }

    @Test
    void combineGeometryDoesNotRankCombineFirst() {
        List<NodeRecommendation> recs = recommendPort(
            new CombineGeometryNode(), "output_geometry", NodeDataType.GEOMETRY);
        assertTrue(
            TRANSFORM.equalsIgnoreCase(recs.get(0).nodeId())
                || PREVIEW_GEOMETRY.equalsIgnoreCase(recs.get(0).nodeId()),
            "expected Transform or Preview first, got " + ids(recs));
        assertNotInTopN(recs, COMBINE, 1);
    }

    @Test
    void differenceGeometryPrefersPreviewAndOmitsAssignFromTop() {
        List<NodeRecommendation> recs = recommendPort(
            new DifferenceNode(), "output_geometry", NodeDataType.GEOMETRY);
        assertRank(recs, PREVIEW_GEOMETRY, 0);
        assertNotInTopN(recs, ASSIGN, 3);
        assertTopContains(recs, VOXELIZE);
    }

    private static void assertAssignNotAheadOfVoxelize(List<NodeRecommendation> recs) {
        List<String> top = ids(recs);
        int voxel = top.indexOf(VOXELIZE);
        int assign = top.indexOf(ASSIGN);
        assertTrue(voxel >= 0, "expected Voxelize in recommendations, got " + top);
        if (assign >= 0) {
            assertTrue(assign > voxel,
                "Assign must not rank ahead of Voxelize, got " + top);
        }
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

    private static List<String> ids(List<NodeRecommendation> recs) {
        return recs.stream().map(NodeRecommendation::nodeId).map(String::toLowerCase).toList();
    }

    private static void assertTopContains(List<NodeRecommendation> recs, String... nodeIds) {
        List<String> topIds = ids(recs);
        for (String nodeId : nodeIds) {
            assertTrue(topIds.contains(nodeId.toLowerCase()),
                "expected " + nodeId + " in top recommendations, got " + topIds);
        }
    }

    private static void assertRank(List<NodeRecommendation> recs, String nodeId, int expectedIndex) {
        List<String> topIds = ids(recs);
        int actual = topIds.indexOf(nodeId.toLowerCase());
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
        assertFalse(topN.contains(nodeId.toLowerCase()),
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

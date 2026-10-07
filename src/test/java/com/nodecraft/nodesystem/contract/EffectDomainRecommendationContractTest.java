package com.nodecraft.nodesystem.contract;

import com.nodecraft.gui.recommendation.NodeRecommendation;
import com.nodecraft.gui.recommendation.NodeRecommendationContext;
import com.nodecraft.gui.recommendation.NodeRecommendations;
import com.nodecraft.gui.recommendation.RecommendationDirection;
import com.nodecraft.gui.recommendation.RecommendationTrigger;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.nodes.geometry.boolops.SdfBooleanNode;
import com.nodecraft.nodesystem.nodes.geometry.boolops.SdfSphereNode;
import com.nodecraft.nodesystem.nodes.geometry.boolops.SdfToGeometryNode;
import com.nodecraft.nodesystem.nodes.material.basic_assignment.AssignBlockTypeNode;
import com.nodecraft.nodesystem.nodes.output.execute.ApplyChangesNode;
import com.nodecraft.nodesystem.nodes.utilities.fileio.ImageSamplerNode;
import com.nodecraft.nodesystem.nodes.world.read.GetHeightmapNode;
import com.nodecraft.nodesystem.nodes.world.selection.SelectedRegionNode;
import com.nodecraft.nodesystem.nodes.world.terrain.BiomeClassifyNode;
import com.nodecraft.nodesystem.nodes.world.terrain.FlowDirectionFieldNode;
import com.nodecraft.nodesystem.nodes.world.terrain.HeightfieldToBlocksNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Effect policy + domain workflow contracts (roadmap Phases A–C):
 * SDF, Terrain, World Selection/Read, Export/File IO, Execution.
 */
class EffectDomainRecommendationContractTest {

    private static final String SDF_BOOLEAN = "geometry.boolean.sdf_boolean";
    private static final String SDF_TO_GEOMETRY = "geometry.boolean.sdf_to_geometry";
    private static final String SDF_SAMPLE = "geometry.boolean.sdf_sample_point";
    private static final String SDF_TO_BLOCKS = "output.execute.sdf_to_blocks";
    private static final String PREVIEW_GEOMETRY = "output.preview.preview_geometry";
    private static final String VOXELIZE = "geometry.voxel.voxelize_geometry";
    private static final String FLOW_ACCUM = "world.terrain.flow_accumulation_field";
    private static final String BIOME_TO_BLOCKS = "world.terrain.biome_field_to_blocks";
    private static final String PREVIEW_BLOCKS = "output.preview.preview_blocks";
    private static final String MERGE = "output.execute.merge_block_placements";
    private static final String APPLY = "output.execute.apply_changes";
    private static final String ASSIGN = "material.basic_assignment.assign_block_type";
    private static final String GET_HEIGHTMAP = "world.read.get_heightmap";
    private static final String GET_BLOCKS = "world.read.get_blocks_in_region";
    private static final String EXPORT_LITEMATIC = "output.export.export_litematic";
    private static final String UNDO = "output.execute.undo_last_bake";
    private static final String REDO = "output.execute.redo_last_bake";
    private static final String CLEAR_PREVIEW = "output.execute.clear_preview";
    private static final String BAKE_STATUS = "output.execute.bake_status";
    private static final String CANCEL_BAKE = "output.execute.cancel_bake";
    private static final String READ_IMAGE = "utilities.fileio.read_image";

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
    void e1_selectionOmitsWorldWriteAndFileIoWithoutExactRule() {
        List<NodeRecommendation> recs = recommendSelection(new SdfSphereNode(), 8);
        assertFalse(ids(recs).contains(SDF_TO_BLOCKS),
            "SDF To Blocks (WORLD_WRITE) must not appear on selection without exact, got " + ids(recs));
        assertFalse(ids(recs).contains(EXPORT_LITEMATIC),
            "Export (FILE_IO) must not appear on selection without exact, got " + ids(recs));
        assertFalse(ids(recs).contains(APPLY),
            "Apply must not appear from SDF selection, got " + ids(recs));
    }

    @Test
    void e2_portDragMaySurfaceSdfToBlocksButSelectionTopDoesNot() {
        List<NodeRecommendation> selection = recommendSelection(new SdfSphereNode(), 3);
        assertFalse(ids(selection).contains(SDF_TO_BLOCKS),
            "Selection Top must not include sdf_to_blocks, got " + ids(selection));

        List<NodeRecommendation> drag = recommendPort(
            new SdfSphereNode(), "output_sdf", NodeDataType.SDF, RecommendationDirection.DOWNSTREAM, 12);
        // Port Drag may include the execute shortcut; it must not outrank the safe modeling chain.
        if (ids(drag).contains(SDF_TO_BLOCKS)) {
            int blocks = ids(drag).indexOf(SDF_TO_BLOCKS);
            int booleanIdx = ids(drag).indexOf(SDF_BOOLEAN);
            int toGeom = ids(drag).indexOf(SDF_TO_GEOMETRY);
            assertTrue(booleanIdx >= 0 || toGeom >= 0,
                "expected safe SDF chain in Port Drag, got " + ids(drag));
            if (booleanIdx >= 0) {
                assertTrue(blocks > booleanIdx,
                    "sdf_to_blocks must rank behind SDF Boolean, got " + ids(drag));
            }
            if (toGeom >= 0) {
                assertTrue(blocks > toGeom,
                    "sdf_to_blocks must rank behind SDF To Geometry, got " + ids(drag));
            }
        }
    }

    @Test
    void e3_heightmapSurfacePointsPreferAssignPreviewNotApply() {
        List<NodeRecommendation> recs = recommendPort(
            new GetHeightmapNode(), "output_surface_points", NodeDataType.BLOCK_LIST,
            RecommendationDirection.DOWNSTREAM, 8);
        assertTopContains(recs, ASSIGN, PREVIEW_BLOCKS);
        assertNotInTopN(recs, APPLY, 8);

        List<NodeRecommendation> selection = recommendSelection(new GetHeightmapNode(), 8);
        assertTopContains(selection, ASSIGN, PREVIEW_BLOCKS);
        assertFalse(ids(selection).contains(APPLY),
            "Apply must not appear from Heightmap selection, got " + ids(selection));
    }

    @Test
    void s1_sdfSphereAndBooleanPreferSafeModelingChain() {
        List<NodeRecommendation> sphere = recommendPort(
            new SdfSphereNode(), "output_sdf", NodeDataType.SDF,
            RecommendationDirection.DOWNSTREAM, 8);
        assertRank(sphere, SDF_BOOLEAN, 0);
        assertTopContains(sphere, SDF_TO_GEOMETRY, SDF_SAMPLE);

        List<NodeRecommendation> bool = recommendPort(
            new SdfBooleanNode(), "output_sdf", NodeDataType.SDF,
            RecommendationDirection.DOWNSTREAM, 8);
        assertRank(bool, SDF_TO_GEOMETRY, 0);
        assertTopContains(bool, SDF_BOOLEAN, SDF_SAMPLE);
    }

    @Test
    void s2_sdfToGeometryReentersGeometryMainChain() {
        List<NodeRecommendation> recs = recommendPort(
            new SdfToGeometryNode(), "output_geometry", NodeDataType.GEOMETRY,
            RecommendationDirection.DOWNSTREAM, 8);
        assertTopContains(recs, PREVIEW_GEOMETRY, VOXELIZE);
    }

    @Test
    void t1_flowDirectionPrefersFlowAccumulation() {
        List<NodeRecommendation> recs = recommendPort(
            new FlowDirectionFieldNode(), "output_flow_field", NodeDataType.VECTOR_FIELD,
            RecommendationDirection.DOWNSTREAM, 8);
        assertRank(recs, FLOW_ACCUM, 0);
    }

    @Test
    void t2_biomeClassifyPrefersBiomeFieldToBlocks() {
        List<NodeRecommendation> recs = recommendPort(
            new BiomeClassifyNode(), "output_biome_id_field", NodeDataType.SCALAR_FIELD,
            RecommendationDirection.DOWNSTREAM, 8);
        assertRank(recs, BIOME_TO_BLOCKS, 0);
    }

    @Test
    void t3_heightfieldToBlocksPlacementsPreferPreviewMergeApply() {
        List<NodeRecommendation> recs = recommendPort(
            new HeightfieldToBlocksNode(), "output_block_placements", NodeDataType.BLOCK_PLACEMENT_LIST,
            RecommendationDirection.DOWNSTREAM, 8);
        assertRank(recs, PREVIEW_BLOCKS, 0);
        assertTopContains(recs, MERGE, APPLY);
        assertNotInTopN(recs, ASSIGN, 3);
    }

    @Test
    void w1_selectedRegionPrefersWorldReadConsumers() {
        List<NodeRecommendation> recs = recommendSelection(new SelectedRegionNode(), 8);
        assertTopContains(recs, GET_HEIGHTMAP, GET_BLOCKS);
    }

    @Test
    void x1_selectionNeverSuggestsHistoryCommandsOrExportFromPlacements() {
        List<NodeRecommendation> recs = recommendSelection(new AssignBlockTypeNode(), 8);
        assertFalse(ids(recs).contains(UNDO), "Undo must be excluded, got " + ids(recs));
        assertFalse(ids(recs).contains(REDO), "Redo must be excluded, got " + ids(recs));
        assertFalse(ids(recs).contains(CLEAR_PREVIEW), "Clear Preview must be excluded, got " + ids(recs));
        assertFalse(ids(recs).contains(EXPORT_LITEMATIC),
            "Export Litematic must not appear on placement selection, got " + ids(recs));
    }

    @Test
    void x2_applyTaskIdPrefersBakeStatusThenCancel() {
        // Apply is a selection sink; task_id chain is Port Drag.
        List<NodeRecommendation> recs = recommendPort(
            new ApplyChangesNode(), "output_task_id", NodeDataType.STRING,
            RecommendationDirection.DOWNSTREAM, 8);
        assertRank(recs, BAKE_STATUS, 0);
        assertTopContains(recs, CANCEL_BAKE);
    }

    @Test
    void f1_imageSamplerUpstreamPrefersReadImage() {
        List<NodeRecommendation> recs = recommendPort(
            new ImageSamplerNode(), "input_image", NodeDataType.IMAGE,
            RecommendationDirection.UPSTREAM, 8);
        assertRank(recs, READ_IMAGE, 0);
        assertEquals("output_image", connectPort(recs, READ_IMAGE));
    }

    private static List<NodeRecommendation> recommendSelection(INode source, int limit) {
        NodeGraph graph = new NodeGraph();
        graph.addNode(source);
        List<NodeRecommendation> recs =
            NodeRecommendations.get().recommendForSelectedNode(graph, source, limit);
        return recs;
    }

    private static List<NodeRecommendation> recommendPort(
            INode source,
            String portId,
            NodeDataType type,
            RecommendationDirection direction,
            int limit) {
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
            limit);
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

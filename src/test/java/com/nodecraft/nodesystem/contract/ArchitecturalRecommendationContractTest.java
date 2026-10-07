package com.nodecraft.nodesystem.contract;

import com.nodecraft.gui.recommendation.NodeRecommendation;
import com.nodecraft.gui.recommendation.NodeRecommendationContext;
import com.nodecraft.gui.recommendation.NodeRecommendations;
import com.nodecraft.gui.recommendation.RecommendationDirection;
import com.nodecraft.gui.recommendation.RecommendationTrigger;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.BeamGridNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.DoorArrayNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.FloorSlabNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.RoofBaseNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.WallWithOpeningsNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.WindowArrayNode;
import com.nodecraft.nodesystem.nodes.reference.points.GetBoxFaceNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Architectural suggested-connection rank contracts (Batch 1 recommendation quality).
 */
class ArchitecturalRecommendationContractTest {

    private static final String DIFFERENCE = "geometry.boolean.difference";
    private static final String FLOOR_SLAB = "geometry.architectural_primitives.floor_slab";
    private static final String COLUMN_GRID = "geometry.architectural_primitives.column_grid";
    private static final String ROOF_BASE = "geometry.architectural_primitives.roof_base";
    private static final String BEAM_GRID = "geometry.architectural_primitives.beam_grid";
    private static final String WALL_SLAB = "geometry.architectural_primitives.wall_slab";
    private static final String WINDOW_ARRAY = "geometry.architectural_primitives.window_array";
    private static final String DOOR_ARRAY = "geometry.architectural_primitives.door_array";
    private static final String WALL_WITH_OPENINGS = "geometry.architectural_primitives.wall_with_openings";
    private static final String PLACE_ON_FRAMES = "transform.placement.place_geometry_on_frames";
    private static final String PREVIEW_CURVES = "output.preview.preview_curves";
    private static final String RAILING = "geometry.architectural_primitives.railing";
    private static final String BEAM_ALONG_PATH = "geometry.architectural_primitives.beam_along_path";

    private static NodeRegistry registry;

    @BeforeAll
    static void ensureRegistry() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
        NodeRecommendations.get().initialize();
        NodeRecommendations.get().reloadRules();
        NodeRecommendations.get().invalidateCache();
    }

    @Test
    void roofEavePathPrefersRailingAndBeamAlongPath() {
        List<NodeRecommendation> recs = recommend(new RoofBaseNode(), "output_eave_path", NodeDataType.PATH);
        assertRank(recs, RAILING, 0);
        assertTopContains(recs, RAILING, BEAM_ALONG_PATH);
        assertEquals("input_path", connectPort(recs, RAILING));
    }

    @Test
    void floorTopFacePrefersColumnGridAndBeamGrid() {
        List<NodeRecommendation> recs = recommend(new FloorSlabNode(), "output_top_face", NodeDataType.BOX_FACE);
        assertRank(recs, COLUMN_GRID, 0);
        assertTopContains(recs, COLUMN_GRID, BEAM_GRID);
        assertNotInTopN(recs, WALL_SLAB, 3);
        assertNotInTopN(recs, WINDOW_ARRAY, 3);
    }

    @Test
    void floorBottomFacePrefersBeamGridFirst() {
        List<NodeRecommendation> recs = recommend(new FloorSlabNode(), "output_bottom_face", NodeDataType.BOX_FACE);
        assertRank(recs, BEAM_GRID, 0);
        assertEquals("input_face", connectPort(recs, BEAM_GRID));
    }

    @Test
    void wallOpeningsPreferDifferenceCutter() {
        List<NodeRecommendation> recs = recommend(
            new WallWithOpeningsNode(), "output_openings", NodeDataType.GEOMETRY);
        assertRank(recs, DIFFERENCE, 0);
        assertEquals("input_cutter", connectPort(recs, DIFFERENCE));
    }

    @Test
    void windowOpeningsRankDifferenceFirst() {
        List<NodeRecommendation> recs = recommend(
            new WindowArrayNode(), "output_openings", NodeDataType.GEOMETRY);
        assertRank(recs, DIFFERENCE, 0);
        assertEquals("input_cutter", connectPort(recs, DIFFERENCE));
        assertTrue(recs.get(0).reason().contains("窗洞") || recs.get(0).reason().toLowerCase().contains("cut"),
            "expected rule reason passthrough, got: " + recs.get(0).reason());
    }

    @Test
    void doorOpeningsRankDifferenceFirst() {
        List<NodeRecommendation> recs = recommend(
            new DoorArrayNode(), "output_openings", NodeDataType.GEOMETRY);
        assertRank(recs, DIFFERENCE, 0);
        assertEquals("input_cutter", connectPort(recs, DIFFERENCE));
    }

    @Test
    void windowFramesPreferPlaceOnFrames() {
        List<NodeRecommendation> recs = recommend(
            new WindowArrayNode(), "output_frames", NodeDataType.FRAME_LIST);
        assertRank(recs, PLACE_ON_FRAMES, 0);
        assertEquals("input_frames", connectPort(recs, PLACE_ON_FRAMES));
    }

    @Test
    void beamCenterLinesPreferPreviewCurvesPathsPort() {
        List<NodeRecommendation> recs = recommend(
            new BeamGridNode(), "output_center_lines", NodeDataType.PATH_LIST);
        assertRank(recs, PREVIEW_CURVES, 0);
        assertEquals("input_paths", connectPort(recs, PREVIEW_CURVES));
    }

    @Test
    void getBoxFaceHorizontalPrefersFloorColumnRoof() {
        GetBoxFaceNode node = new GetBoxFaceNode();
        node.setDefaultFaceName("top");
        List<NodeRecommendation> recs = recommend(node, "output_face", NodeDataType.BOX_FACE);
        assertRank(recs, FLOOR_SLAB, 0);
        assertTopContains(recs, FLOOR_SLAB, COLUMN_GRID, ROOF_BASE);
        assertNotInTopN(recs, WINDOW_ARRAY, 3);
        assertNotInTopN(recs, DOOR_ARRAY, 3);
    }

    @Test
    void getBoxFaceVerticalPrefersWallWindowDoor() {
        GetBoxFaceNode node = new GetBoxFaceNode();
        node.setDefaultFaceName("front");
        List<NodeRecommendation> recs = recommend(node, "output_face", NodeDataType.BOX_FACE);
        assertRank(recs, WALL_SLAB, 0);
        assertTopContains(recs, WALL_SLAB, WINDOW_ARRAY, DOOR_ARRAY);
        assertNotInTopN(recs, FLOOR_SLAB, 3);
        assertNotInTopN(recs, ROOF_BASE, 3);
    }

    @Test
    void boxFaceGlobalDefaultsPreferFacadeOverWallWithOpenings() {
        // Unknown face orientation falls through to global/category box_face (facade-first).
        GetBoxFaceNode node = new GetBoxFaceNode();
        List<NodeRecommendation> recs = recommend(node, "output_face", NodeDataType.BOX_FACE);
        assertTopContains(recs, WALL_SLAB, WINDOW_ARRAY);
        assertNotInTopN(recs, WALL_WITH_OPENINGS, 5);
    }

    @Test
    void previewCurvesExposesPathListInput() {
        INode preview = registry.createNodeInstance("output.preview.preview_curves");
        assertEquals(NodeDataType.PATH_LIST,
            preview.getInputPorts().stream()
                .filter(port -> "input_paths".equals(port.getId()))
                .findFirst()
                .orElseThrow()
                .getDataType());
        assertEquals(NodeDataType.POINT_LIST,
            preview.getInputPorts().stream()
                .filter(port -> "input_points".equals(port.getId()))
                .findFirst()
                .orElseThrow()
                .getDataType());
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
        assertFalse(recommendations.isEmpty(), "expected recommendations for " + source.getTypeId() + "#" + portId);
        return recommendations;
    }

    private static void assertTopContains(List<NodeRecommendation> recs, String... nodeIds) {
        List<String> topIds = recs.stream().map(NodeRecommendation::nodeId).map(String::toLowerCase).toList();
        for (String nodeId : nodeIds) {
            assertTrue(topIds.contains(nodeId.toLowerCase()),
                "expected " + nodeId + " in top recommendations, got " + topIds);
        }
    }

    private static void assertRank(List<NodeRecommendation> recs, String nodeId, int expectedIndex) {
        List<String> topIds = recs.stream().map(NodeRecommendation::nodeId).map(String::toLowerCase).toList();
        String needle = nodeId.toLowerCase();
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

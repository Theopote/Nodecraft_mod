package com.nodecraft.nodesystem.contract;

import com.nodecraft.gui.recommendation.NodeRecommendation;
import com.nodecraft.gui.recommendation.NodeRecommendationContext;
import com.nodecraft.gui.recommendation.NodeRecommendations;
import com.nodecraft.gui.recommendation.RecommendationDirection;
import com.nodecraft.gui.recommendation.RecommendationTrigger;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.BeamAlongPathNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.BeamGridNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.ColumnNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.DoorArrayNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.FloorSlabNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.RailingNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.RoofBaseNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.StaircaseNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.WallAlongPathNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.WallSlabNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.WallWithOpeningsNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.WindowArrayNode;
import com.nodecraft.nodesystem.nodes.reference.points.GetBoxFaceNode;
import com.nodecraft.nodesystem.nodes.transform.basic_transforms.MoveGeometryNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Architectural suggested-connection rank contracts (Batch 1–2 recommendation quality).
 */
class ArchitecturalRecommendationContractTest {

    private static final String DIFFERENCE = "geometry.boolean.difference";
    private static final String COMBINE = "geometry.combine.geometry";
    private static final String FLOOR_SLAB = "geometry.architectural_primitives.floor_slab";
    private static final String COLUMN = "geometry.architectural_primitives.column";
    private static final String COLUMN_GRID = "geometry.architectural_primitives.column_grid";
    private static final String ROOF_BASE = "geometry.architectural_primitives.roof_base";
    private static final String BEAM_GRID = "geometry.architectural_primitives.beam_grid";
    private static final String WALL_SLAB = "geometry.architectural_primitives.wall_slab";
    private static final String WINDOW_ARRAY = "geometry.architectural_primitives.window_array";
    private static final String DOOR_ARRAY = "geometry.architectural_primitives.door_array";
    private static final String WALL_WITH_OPENINGS = "geometry.architectural_primitives.wall_with_openings";
    private static final String STAIRCASE = "geometry.architectural_primitives.staircase";
    private static final String PLACE_ON_FRAMES = "transform.placement.place_geometry_on_frames";
    private static final String PREVIEW_CURVES = "output.preview.preview_curves";
    private static final String PREVIEW_GEOMETRY = "output.preview.preview_geometry";
    private static final String RAILING = "geometry.architectural_primitives.railing";
    private static final String BEAM_ALONG_PATH = "geometry.architectural_primitives.beam_along_path";
    private static final String TRANSFORM_GEOMETRY = "transform.basic_transforms.transform_geometry";

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
    void getBoxFacePrefersLastResolvedOutputNameOverStaleDefault() {
        // Simulate: node previously resolved front, then defaultFaceName flipped to top
        // without re-process. Recommendation must keep vertical (output_name > default).
        GetBoxFaceNode node = new GetBoxFaceNode();
        node.setDefaultFaceName("front");
        node.setInput("input_box_geometry", new BoxGeometryData(new Vector3d(), new Vector3d(1, 1, 1)));
        node.processNode(null);
        assertEquals("Front", node.getOutput("output_name"));

        node.setDefaultFaceName("top");
        List<NodeRecommendation> recs = recommend(node, "output_face", NodeDataType.BOX_FACE);
        assertRank(recs, WALL_SLAB, 0);
        assertNotInTopN(recs, FLOOR_SLAB, 3);
    }

    @Test
    void getBoxFaceConnectedUpstreamFaceNameBeatsDefaultWhenLocalInputStale() {
        // Face Name is wired but local inputValues not refreshed; upstream port still holds "front".
        // Must not fall back to defaultFaceName "top" (horizontal).
        GetBoxFaceNode node = new GetBoxFaceNode();
        node.setDefaultFaceName("top");
        StringPortStubNode stub = new StringPortStubNode("front");
        connectString(stub, node, "input_face_name");

        List<NodeRecommendation> recs = recommend(node, "output_face", NodeDataType.BOX_FACE);
        assertRank(recs, WALL_SLAB, 0);
        assertNotInTopN(recs, FLOOR_SLAB, 3);
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
    void railingGeometryPrefersCombineOverDifference() {
        List<NodeRecommendation> recs = recommend(new RailingNode(), "output_geometry", NodeDataType.GEOMETRY);
        assertRank(recs, COMBINE, 0);
        assertNotInTopN(recs, DIFFERENCE, 2);
        assertTopContains(recs, PREVIEW_GEOMETRY);
    }

    @Test
    void staircaseGeometryPrefersCombineOverDifference() {
        List<NodeRecommendation> recs = recommend(new StaircaseNode(), "output_geometry", NodeDataType.GEOMETRY);
        assertRank(recs, COMBINE, 0);
        assertNotInTopN(recs, DIFFERENCE, 2);
    }

    @Test
    void wallSlabGeometryStillRanksDifferenceFirst() {
        List<NodeRecommendation> recs = recommend(new WallSlabNode(), "output_geometry", NodeDataType.GEOMETRY);
        assertRank(recs, DIFFERENCE, 0);
        assertEquals("input_base", connectPort(recs, DIFFERENCE));
    }

    @Test
    void columnTopPrefersStackingOntoColumnBase() {
        List<NodeRecommendation> recs = recommend(new ColumnNode(), "output_top", NodeDataType.POINT);
        assertRank(recs, COLUMN, 0);
        assertEquals("input_base", connectPort(recs, COLUMN));
        assertTrue(recs.get(0).reason().contains("叠接"),
            "expected stacking reason, got: " + recs.get(0).reason());
    }

    @Test
    void staircaseStepFramesPreferPlaceOnFrames() {
        List<NodeRecommendation> recs = recommend(
            new StaircaseNode(), "output_step_frames", NodeDataType.FRAME_LIST);
        assertRank(recs, PLACE_ON_FRAMES, 0);
        assertEquals("input_frames", connectPort(recs, PLACE_ON_FRAMES));
    }

    @Test
    void staircaseLandingFramesPreferPlaceOnFrames() {
        List<NodeRecommendation> recs = recommend(
            new StaircaseNode(), "output_landing_frames", NodeDataType.FRAME_LIST);
        assertRank(recs, PLACE_ON_FRAMES, 0);
        assertEquals("input_frames", connectPort(recs, PLACE_ON_FRAMES));
    }

    @Test
    void staircaseWalkPathPrefersPreviewAndRailingNotAnotherStaircase() {
        List<NodeRecommendation> recs = recommend(
            new StaircaseNode(), "output_walk_path", NodeDataType.PATH);
        assertRank(recs, PREVIEW_CURVES, 0);
        assertTopContains(recs, PREVIEW_CURVES, RAILING);
        assertNotInTopN(recs, STAIRCASE, 3);
        assertEquals("input_path", connectPort(recs, PREVIEW_CURVES));
    }

    @Test
    void wallAlongPathTopPathPrefersRailing() {
        List<NodeRecommendation> recs = recommend(
            new WallAlongPathNode(), "output_top_path", NodeDataType.PATH);
        assertRank(recs, RAILING, 0);
        assertTopContains(recs, RAILING, BEAM_ALONG_PATH, PREVIEW_CURVES);
    }

    @Test
    void wallAlongPathFramesPreferPlaceOnFrames() {
        List<NodeRecommendation> recs = recommend(
            new WallAlongPathNode(), "output_frames", NodeDataType.FRAME_LIST);
        assertRank(recs, PLACE_ON_FRAMES, 0);
        assertEquals("input_frames", connectPort(recs, PLACE_ON_FRAMES));
    }

    @Test
    void beamAlongPathFramesPreferPlaceOnFrames() {
        List<NodeRecommendation> recs = recommend(
            new BeamAlongPathNode(), "output_frames", NodeDataType.FRAME_LIST);
        assertRank(recs, PLACE_ON_FRAMES, 0);
        assertEquals("input_frames", connectPort(recs, PLACE_ON_FRAMES));
        assertTrue(recs.get(0).reason().contains("梁"),
            "expected beam-placement reason, got: " + recs.get(0).reason());
    }

    @Test
    void moveGeometryDoesNotRankTransformGeometryFirst() {
        List<NodeRecommendation> recs = recommend(
            new MoveGeometryNode(), "output_geometry", NodeDataType.GEOMETRY);
        assertRank(recs, COMBINE, 0);
        assertNotInTopN(recs, TRANSFORM_GEOMETRY, 1);
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

    private static void connectString(BaseNode source, BaseNode target, String inputPortId) {
        BasePort output = (BasePort) source.getOutputPorts().getFirst();
        BasePort input = (BasePort) target.getInputPorts().stream()
            .filter(port -> inputPortId.equals(port.getId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError(target.getTypeId() + " missing port " + inputPortId));
        assertTrue(output.connectTo(input), inputPortId + " connect failed");
    }

    private static final class StringPortStubNode extends BaseNode {
        StringPortStubNode(String value) {
            super(UUID.randomUUID(), "test.string_stub");
            BasePort output = new BasePort("output_stub", "Stub", "", NodeDataType.STRING, this);
            addOutputPort(output);
            output.setValue(value);
            // Mirror onto node outputs so recommendation can read via getOutput(portId).
            outputValues.put("output_stub", value);
        }

        @Override
        public void processNode(ExecutionContext context) {
        }
    }
}

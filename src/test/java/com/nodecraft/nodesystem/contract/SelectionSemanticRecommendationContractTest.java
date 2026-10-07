package com.nodecraft.nodesystem.contract;

import com.nodecraft.gui.recommendation.NodeRecommendation;
import com.nodecraft.gui.recommendation.NodeRecommendationContext;
import com.nodecraft.gui.recommendation.NodeRecommendations;
import com.nodecraft.gui.recommendation.RecommendationDirection;
import com.nodecraft.gui.recommendation.RecommendationTrigger;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.StaircaseNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.WallAlongPathNode;
import com.nodecraft.nodesystem.nodes.geometry.curves.BoxFaceBoundaryPathNode;
import com.nodecraft.nodesystem.nodes.geometry.voxel.VoxelizeGeometryNode;
import com.nodecraft.nodesystem.nodes.input.values.TextInputNode;
import com.nodecraft.nodesystem.nodes.math.list_sequence.CreateListNode;
import com.nodecraft.nodesystem.nodes.reference.frames.ConstructFrameNode;
import com.nodecraft.nodesystem.nodes.reference.frames.WorldFrameNode;
import com.nodecraft.nodesystem.nodes.reference.planes.PlaneSelectorNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Batch 4: selection-level exact-rule ports, LIST gate, assist exclude, FRAME/PLANE/boundary/tree.
 */
class SelectionSemanticRecommendationContractTest {

    private static final String RAILING = "geometry.architectural_primitives.railing";
    private static final String PLACE_ON_FRAMES = "transform.placement.place_geometry_on_frames";
    private static final String ORIENT_TO_FRAME = "transform.placement.orient_geometry_to_frame";
    private static final String WALL_ALONG_PATH = "geometry.architectural_primitives.wall_along_path";
    private static final String RECTANGLE_PROFILE = "geometry.profiles.rectangle_profile";
    private static final String STRING_FORMAT = "utilities.assist.string_format";
    private static final String GET_ITEM = "math.list.get_item";
    private static final String LIST_LENGTH = "math.list.list_length";
    private static final String FILTER_LIST = "math.list.filter_list";
    private static final String ASSIGN = "material.basic_assignment.assign_block_type";

    private static final Set<String> LIST_SELECTION_ALLOW =
            Set.of(GET_ITEM, LIST_LENGTH, FILTER_LIST);

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
    void wallAlongPathSelectionSurfacesTopPathRailing() {
        WallAlongPathNode wall = new WallAlongPathNode();
        List<NodeRecommendation> recs = recommendSelection(wall, 8);
        assertTopContains(recs, RAILING);
        assertEquals("output_top_path", sourcePort(recs, RAILING),
            "Railing must come from Top Path exact rule, not first PATH collapse");
        assertEquals("input_path", connectPort(recs, RAILING));
    }

    @Test
    void staircaseSelectionSurfacesPlaceOnFramesFromStepFrames() {
        StaircaseNode stair = new StaircaseNode();
        List<NodeRecommendation> recs = recommendSelection(stair, 8);
        assertTopContains(recs, PLACE_ON_FRAMES);
        assertEquals("output_step_frames", sourcePort(recs, PLACE_ON_FRAMES),
            "PlaceOnFrames must come from Step Frames exact rule, not first FRAME_LIST collapse");
        assertEquals("input_frames", connectPort(recs, PLACE_ON_FRAMES));
    }

    @Test
    void stringSourceDoesNotSuggestAssistStringFormat() {
        List<NodeRecommendation> recs = recommendPort(
            new TextInputNode(), "output_text", NodeDataType.STRING);
        assertFalse(ids(recs).contains(STRING_FORMAT),
            "utilities.assist.string_format must be excluded from Suggested, got " + ids(recs));
    }

    @Test
    void listSelectionOnlyAllowsHighConfidenceOps() {
        CreateListNode list = new CreateListNode();
        List<NodeRecommendation> selection = recommendSelection(list, 8);
        assertFalse(selection.isEmpty());
        for (String id : ids(selection)) {
            assertTrue(LIST_SELECTION_ALLOW.contains(id),
                "unexpected LIST selection candidate " + id + " in " + ids(selection));
        }
    }

    @Test
    void listPortDragIsBroaderThanSelectionAllowlist() {
        List<NodeRecommendation> drag = recommendPort(
            new CreateListNode(), "output_list", NodeDataType.LIST);
        assertFalse(drag.isEmpty());
        Set<String> dragIds = new HashSet<>(ids(drag));
        assertTrue(dragIds.stream().anyMatch(id -> !LIST_SELECTION_ALLOW.contains(id)),
            "PORT_DRAG LIST should include candidates beyond selection allowlist, got " + dragIds);
        assertTrue(dragIds.stream().anyMatch(LIST_SELECTION_ALLOW::contains),
            "expected at least one high-confidence list op in PORT_DRAG, got " + dragIds);
    }

    @Test
    void worldFramePrefersPlacementOrOrient() {
        List<NodeRecommendation> recs = recommendPort(
            new WorldFrameNode(), "output_frame", NodeDataType.FRAME);
        String first = recs.get(0).nodeId().toLowerCase(Locale.ROOT);
        assertTrue(PLACE_ON_FRAMES.equals(first) || ORIENT_TO_FRAME.equals(first),
            "expected PlaceOnFrames or Orient first, got " + ids(recs));
    }

    @Test
    void constructFramePrefersPlacementOrOrient() {
        List<NodeRecommendation> recs = recommendPort(
            new ConstructFrameNode(), "output_frame", NodeDataType.FRAME);
        String first = recs.get(0).nodeId().toLowerCase(Locale.ROOT);
        assertTrue(PLACE_ON_FRAMES.equals(first) || ORIENT_TO_FRAME.equals(first),
            "expected PlaceOnFrames or Orient first, got " + ids(recs));
    }

    @Test
    void worldPlanePrefersRectangleProfile() {
        List<NodeRecommendation> recs = recommendPort(
            new PlaneSelectorNode(), "output_plane", NodeDataType.PLANE);
        assertTopContains(recs, RECTANGLE_PROFILE);
    }

    @Test
    void faceBoundaryPathPrefersWallAlongPath() {
        List<NodeRecommendation> recs = recommendPort(
            new BoxFaceBoundaryPathNode(), "output_path", NodeDataType.PATH);
        assertRank(recs, WALL_ALONG_PATH, 0);
        assertEquals("input_path", connectPort(recs, WALL_ALONG_PATH));
    }

    @Test
    void voxelizeBlocksTreePrefersAssignBlocksTree() {
        List<NodeRecommendation> recs = recommendPort(
            new VoxelizeGeometryNode(), "output_blocks_tree", NodeDataType.DATA_TREE);
        assertRank(recs, ASSIGN, 0);
        assertEquals("input_blocks_tree", connectPort(recs, ASSIGN));
    }

    private static List<NodeRecommendation> recommendSelection(INode source, int limit) {
        NodeGraph graph = new NodeGraph();
        graph.addNode(source);
        List<NodeRecommendation> recs =
            NodeRecommendations.get().recommendForSelectedNode(graph, source, limit);
        assertFalse(recs.isEmpty(),
            "expected selection recommendations for " + source.getTypeId());
        return recs;
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

    private static String connectPort(List<NodeRecommendation> recs, String nodeId) {
        return recs.stream()
            .filter(rec -> nodeId.equalsIgnoreCase(rec.nodeId()))
            .map(NodeRecommendation::connectPortId)
            .findFirst()
            .orElseThrow(() -> new AssertionError("missing recommendation for " + nodeId));
    }

    private static String sourcePort(List<NodeRecommendation> recs, String nodeId) {
        return recs.stream()
            .filter(rec -> nodeId.equalsIgnoreCase(rec.nodeId()))
            .map(NodeRecommendation::sourcePortId)
            .findFirst()
            .orElseThrow(() -> new AssertionError("missing recommendation for " + nodeId));
    }
}

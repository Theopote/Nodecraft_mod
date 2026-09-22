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
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.FloorSlabNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.RoofBaseNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.WallWithOpeningsNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.WindowArrayNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Batch 13.2: architectural suggested connections prefer composable hosts / placement / path consumers.
 */
class ArchitecturalRecommendationContractTest {

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
        assertTopContains(recs,
            "geometry.architectural_primitives.railing",
            "geometry.architectural_primitives.beam_along_path");
        assertEquals("input_path", connectPort(recs, "geometry.architectural_primitives.railing"));
    }

    @Test
    void floorTopFacePrefersColumnGridAndBeamGrid() {
        List<NodeRecommendation> recs = recommend(new FloorSlabNode(), "output_top_face", NodeDataType.BOX_FACE);
        assertTopContains(recs,
            "geometry.architectural_primitives.column_grid",
            "geometry.architectural_primitives.beam_grid");
    }

    @Test
    void wallOpeningsPreferDifferenceCutter() {
        List<NodeRecommendation> recs = recommend(
            new WallWithOpeningsNode(), "output_openings", NodeDataType.GEOMETRY);
        assertTopContains(recs, "geometry.boolean.difference");
        assertEquals("input_cutter", connectPort(recs, "geometry.boolean.difference"));
    }

    @Test
    void windowFramesPreferPlaceOnFrames() {
        List<NodeRecommendation> recs = recommend(
            new WindowArrayNode(), "output_frames", NodeDataType.FRAME_LIST);
        assertTopContains(recs, "transform.placement.place_geometry_on_frames");
        assertEquals("input_frames", connectPort(recs, "transform.placement.place_geometry_on_frames"));
    }

    @Test
    void beamCenterLinesPreferPreviewCurvesPathsPort() {
        List<NodeRecommendation> recs = recommend(
            new BeamGridNode(), "output_center_lines", NodeDataType.PATH_LIST);
        assertTopContains(recs, "output.preview.preview_curves");
        assertEquals("input_paths", connectPort(recs, "output.preview.preview_curves"));
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

    private static String connectPort(List<NodeRecommendation> recs, String nodeId) {
        return recs.stream()
            .filter(rec -> nodeId.equalsIgnoreCase(rec.nodeId()))
            .map(NodeRecommendation::connectPortId)
            .findFirst()
            .orElseThrow(() -> new AssertionError("missing recommendation for " + nodeId));
    }
}

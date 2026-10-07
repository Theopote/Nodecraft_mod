package com.nodecraft.gui.recommendation;

import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.nodes.reference.points.GetBoxFaceNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Overlay cache fingerprint + Get Box Face semantic key contracts (UI round).
 */
class RecommendationUiCacheContractTest {

    private static final String FLOOR_SLAB = "geometry.architectural_primitives.floor_slab";
    private static final String WALL_SLAB = "geometry.architectural_primitives.wall_slab";

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
    void getBoxFaceOrientationFlipChangesSemanticKeyAndFingerprint() {
        GetBoxFaceNode face = new GetBoxFaceNode();
        face.setDefaultFaceName("top");

        String keyHorizontal = NodeRecommendations.get().resolveSelectionSemanticKey(face);
        assertEquals("face:horizontal", keyHorizontal);

        long revision = NodeRecommendations.get().getRulesRevision();
        String fpHorizontal = RecommendationCacheKey.build(face, revision, keyHorizontal);

        face.setDefaultFaceName("front");
        String keyVertical = NodeRecommendations.get().resolveSelectionSemanticKey(face);
        assertEquals("face:vertical", keyVertical);

        String fpVertical = RecommendationCacheKey.build(face, revision, keyVertical);
        assertNotEquals(fpHorizontal, fpVertical,
            "fingerprint must change when Get Box Face orientation flips");
    }

    @Test
    void getBoxFaceOrientationFlipChangesSelectionRecommendations() {
        GetBoxFaceNode face = new GetBoxFaceNode();
        NodeGraph graph = new NodeGraph();
        graph.addNode(face);

        face.setDefaultFaceName("top");
        List<NodeRecommendation> horizontal =
            NodeRecommendations.get().recommendForSelectedNode(graph, face, 8);
        assertFalse(horizontal.isEmpty());
        assertTrue(ids(horizontal).contains(FLOOR_SLAB),
            "top/horizontal should suggest Floor Slab, got " + ids(horizontal));

        face.setDefaultFaceName("front");
        List<NodeRecommendation> vertical =
            NodeRecommendations.get().recommendForSelectedNode(graph, face, 8);
        assertFalse(vertical.isEmpty());
        assertTrue(ids(vertical).contains(WALL_SLAB),
            "front/vertical should suggest Wall Slab, got " + ids(vertical));
        assertNotEquals(ids(horizontal), ids(vertical),
            "horizontal and vertical face recommendations should differ");
    }

    @Test
    void uiPresentationHumanizePort() {
        assertEquals("Top Path", RecommendationUiPresentation.humanizePortId("output_top_path"));
        assertEquals("Step Frames", RecommendationUiPresentation.humanizePortId("output_step_frames"));
    }

    private static List<String> ids(List<NodeRecommendation> recs) {
        return recs.stream()
            .map(NodeRecommendation::nodeId)
            .map(id -> id.toLowerCase(Locale.ROOT))
            .toList();
    }
}

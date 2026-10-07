package com.nodecraft.nodesystem.contract;

import com.nodecraft.gui.recommendation.NodeRecommendation;
import com.nodecraft.gui.recommendation.NodeRecommendationContext;
import com.nodecraft.gui.recommendation.NodeRecommendations;
import com.nodecraft.gui.recommendation.RecommendationDirection;
import com.nodecraft.gui.recommendation.RecommendationTrigger;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.nodes.geometry.curves.BezierNode;
import com.nodecraft.nodesystem.nodes.geometry.curves.BoxFaceBoundaryPathNode;
import com.nodecraft.nodesystem.nodes.geometry.profiles.ProfileToRegionNode;
import com.nodecraft.nodesystem.nodes.geometry.profiles.RectangleOnPlaneNode;
import com.nodecraft.nodesystem.nodes.geometry.solids.ExtrudeRegionNode;
import com.nodecraft.nodesystem.nodes.geometry.solids.MultiSectionLoftNode;
import com.nodecraft.nodesystem.nodes.geometry.solids.SweepProfileAlongPathNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Profile → Region → Sweep/Extrude/Loft → Surface Strip modeling-chain recommendation contracts.
 */
class ModelingChainRecommendationContractTest {

    private static final String EXTRUDE = "geometry.solids.extrude";
    private static final String SWEEP = "geometry.solids.sweep";
    private static final String EXTRUDE_REGION = "geometry.solids.extrude_region";
    private static final String REGION_OFFSET = "geometry.profiles.region_offset_plane";
    private static final String LOFT_MULTI = "geometry.solids.loft_multi_section";
    private static final String LATTICE = "geometry.solids.surface_strip_to_lattice";
    private static final String OFFSET_STRIP = "geometry.solids.offset_surface_strip";
    private static final String PREVIEW_CURVES = "output.preview.preview_curves";
    private static final String CURVE_ARRAY = "pattern.linear.curve_array";
    private static final String RAILING = "geometry.architectural_primitives.railing";
    private static final String WALL_ALONG_PATH = "geometry.architectural_primitives.wall_along_path";

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
    void rectangleProfilePrefersExtrudeAndSweepNotArchitecture() {
        List<NodeRecommendation> recs = recommend(
            new RectangleOnPlaneNode(), "output_profile", NodeDataType.POLYGON_PROFILE);
        assertTopContains(recs, EXTRUDE, SWEEP);
        assertRank(recs, EXTRUDE, 0);
        assertNotInTopN(recs, RAILING, 5);
        assertNotInTopN(recs, WALL_ALONG_PATH, 5);
        assertEquals("input_profile", connectPort(recs, EXTRUDE));
    }

    @Test
    void profileToRegionPrefersExtrudeRegionFirst() {
        List<NodeRecommendation> recs = recommend(
            new ProfileToRegionNode(), "output_region", NodeDataType.PLANAR_REGION);
        assertRank(recs, EXTRUDE_REGION, 0);
        assertEquals("input_region", connectPort(recs, EXTRUDE_REGION));
        assertTopContains(recs, EXTRUDE_REGION, REGION_OFFSET);
    }

    @Test
    void extrudeRegionTopRegionPrefersMassingContinuation() {
        List<NodeRecommendation> recs = recommend(
            new ExtrudeRegionNode(), "output_top_region", NodeDataType.PLANAR_REGION);
        assertRank(recs, EXTRUDE_REGION, 0);
        assertTopContains(recs, EXTRUDE_REGION, REGION_OFFSET);
        assertEquals("input_region", connectPort(recs, EXTRUDE_REGION));
    }

    @Test
    void sweepSurfaceStripPrefersLatticeNotSolidWording() {
        List<NodeRecommendation> recs = recommend(
            new SweepProfileAlongPathNode(), "output_surface_strip", NodeDataType.SURFACE_STRIP);
        assertRank(recs, LATTICE, 0);
        assertTopContains(recs, LATTICE, OFFSET_STRIP);
        String reason = reasonOf(recs, LATTICE);
        assertFalse(reason.contains("实体"), "lattice reason must not say 实体, got: " + reason);
        assertTrue(reason.contains("格架") || reason.contains("线框"),
            "expected lattice/wireframe reason, got: " + reason);
    }

    @Test
    void loftSideSurfacePrefersLattice() {
        List<NodeRecommendation> recs = recommend(
            new MultiSectionLoftNode(), "output_side_surface", NodeDataType.SURFACE_STRIP);
        assertRank(recs, LATTICE, 0);
        assertEquals("input_surface_strip", connectPort(recs, LATTICE));
    }

    @Test
    void polygonProfileListPrefersMultiSectionLoft() {
        List<NodeRecommendation> recs = recommend(
            new SweepProfileAlongPathNode(), "output_section_profiles", NodeDataType.POLYGON_PROFILE_LIST);
        assertRank(recs, LOFT_MULTI, 0);
        assertEquals("input_profiles", connectPort(recs, LOFT_MULTI));
    }

    @Test
    void bezierPathPrefersCurveWorkflowOverRailingFirst() {
        List<NodeRecommendation> recs = recommend(
            new BezierNode(), "output_path", NodeDataType.PATH);
        assertRank(recs, PREVIEW_CURVES, 0);
        assertTopContains(recs, PREVIEW_CURVES, SWEEP, CURVE_ARRAY);
        assertNotInTopN(recs, RAILING, 1);
    }

    @Test
    void faceBoundaryPathStillPrefersWallAlongPath() {
        List<NodeRecommendation> recs = recommend(
            new BoxFaceBoundaryPathNode(), "output_path", NodeDataType.PATH);
        assertRank(recs, WALL_ALONG_PATH, 0);
        assertEquals("input_path", connectPort(recs, WALL_ALONG_PATH));
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

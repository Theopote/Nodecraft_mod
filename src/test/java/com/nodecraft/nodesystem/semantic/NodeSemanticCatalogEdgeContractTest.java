package com.nodecraft.nodesystem.semantic;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.recommendation.NodeRecommendationRules;
import com.nodecraft.nodesystem.recommendation.NodeRecommendationRulesLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Catalog effective edges must cover exact + category + type tiers from recommendation rules.
 */
class NodeSemanticCatalogEdgeContractTest {

    private NodeSemanticCatalog catalog;
    private NodeRecommendationRules rules;

    @BeforeEach
    void setUp() {
        catalog = NodeSemanticCatalog.get();
        catalog.invalidateRules();
        catalog.forceRefresh();
        rules = NodeRecommendationRulesLoader.load();
    }

    @Test
    void catalogIncludesExactSourceNodeEdges() {
        String wall = "geometry.architectural_primitives.wall_slab";
        List<NodeSemanticEdge> exact = catalog.exactDownstream(wall);
        assertTrue(exact.stream().anyMatch(e ->
                "geometry.boolean.difference".equalsIgnoreCase(e.targetNodeId())
                        && e.kind() == NodeSemanticEdgeKind.EXACT));
        assertTrue(exact.stream().anyMatch(e ->
                "output.preview.preview_geometry".equalsIgnoreCase(e.targetNodeId())));
    }

    @Test
    void catalogIncludesSourceCategoryEdges() {
        // Category rules for architecture/geometry families typically wire GEOMETRY → preview/combine.
        List<NodeSemanticEdge> edges = catalog.effectiveDownstream(
                "geometry.primitives.sphere",
                "output_geometry",
                NodeDataType.GEOMETRY);
        assertTrue(edges.stream().anyMatch(e -> e.kind() == NodeSemanticEdgeKind.CATEGORY
                        || e.kind() == NodeSemanticEdgeKind.TYPE),
                "expected category or type edges for geometry output; got=" + summarize(edges));
        assertTrue(edges.stream().anyMatch(e ->
                        e.targetNodeId() != null
                                && (e.targetNodeId().contains("preview")
                                || e.targetNodeId().contains("combine")
                                || e.targetNodeId().contains("transform")
                                || e.targetNodeId().contains("difference"))),
                "expected workflow neighbor for GEOMETRY; got=" + summarize(edges));
    }

    @Test
    void catalogIncludesOutputTypeEdges() {
        // Use POINT so CATEGORY geometry rules do not dominate; TYPE edges remain visible after merge.
        List<NodeSemanticEdge> edges = catalog.effectiveDownstream(
                "reference.points.point",
                "output_point",
                NodeDataType.POINT);
        assertTrue(edges.stream().anyMatch(e -> e.kind() == NodeSemanticEdgeKind.TYPE),
                "global outputTypes.point should contribute TYPE edges; got=" + summarize(edges));
        assertTrue(edges.stream().anyMatch(e ->
                e.targetNodeId() != null
                        && e.targetNodeId().toLowerCase(Locale.ROOT).contains("snap_point")),
                "TYPE edge should include snap_point_to_block; got=" + summarize(edges));
    }

    @Test
    void catalogPreservesEdgeKindAndPriority() {
        String wall = "geometry.architectural_primitives.wall_slab";
        List<NodeSemanticEdge> exact = catalog.exactDownstream(wall);
        assertFalse(exact.isEmpty());
        for (NodeSemanticEdge edge : exact) {
            assertTrue(edge.kind() == NodeSemanticEdgeKind.EXACT);
        }
        // Exact difference should win over any lower-tier duplicate to same target+ports.
        List<NodeSemanticEdge> effective = catalog.effectiveDownstream(wall);
        long differenceExact = effective.stream()
                .filter(e -> "geometry.boolean.difference".equalsIgnoreCase(e.targetNodeId()))
                .filter(e -> e.kind() == NodeSemanticEdgeKind.EXACT)
                .count();
        assertTrue(differenceExact >= 1);
    }

    @Test
    void catalogEffectiveContainsCanonicalExactRulesSubset() {
        assertExactSubset("geometry.architectural_primitives.wall_slab");
        assertExactSubset("geometry.architectural_primitives.window_array");
        assertExactSubset("geometry.voxel.voxelize_geometry");
        assertExactSubset("geometry.voxel.surface_strip_to_blocks");
        assertExactSubset("material.basic_assignment.assign_block_type");
    }

    private void assertExactSubset(String nodeId) {
        if (rules.sourceNodes == null || !rules.sourceNodes.containsKey(nodeId.toLowerCase(Locale.ROOT))) {
            // Skip nodes without exact rules in this rules revision.
            return;
        }
        Set<String> catalogTargets = new HashSet<>();
        for (NodeSemanticEdge edge : catalog.exactDownstream(nodeId)) {
            if (edge.targetNodeId() != null) {
                catalogTargets.add(edge.targetNodeId().toLowerCase(Locale.ROOT));
            }
        }
        NodeRecommendationRules.SourceNodeRule rule =
                rules.sourceNodes.get(nodeId.toLowerCase(Locale.ROOT));
        if (rule.outputs == null) {
            return;
        }
        for (NodeRecommendationRules.PortDirectionRule portRule : rule.outputs.values()) {
            if (portRule == null || portRule.downstream == null) {
                continue;
            }
            for (NodeRecommendationRules.RuleEntry entry : portRule.downstream) {
                if (entry == null || entry.nodeId == null || entry.nodeId.isBlank()) {
                    continue;
                }
                assertTrue(catalogTargets.contains(entry.nodeId.toLowerCase(Locale.ROOT)),
                        nodeId + " missing exact edge to " + entry.nodeId);
            }
        }
    }

    private static String summarize(List<NodeSemanticEdge> edges) {
        StringBuilder sb = new StringBuilder();
        for (NodeSemanticEdge edge : edges) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(edge.kind()).append(':').append(edge.targetNodeId());
        }
        return sb.toString();
    }
}

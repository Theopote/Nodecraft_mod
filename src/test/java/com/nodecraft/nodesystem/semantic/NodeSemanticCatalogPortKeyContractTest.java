package com.nodecraft.nodesystem.semantic;

import com.nodecraft.nodesystem.api.NodeDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Synthetic semantic port keys (GetBoxFace) and port-aware query contracts.
 */
class NodeSemanticCatalogPortKeyContractTest {

    private static final String GET_BOX_FACE = "reference.points.get_box_face";

    private NodeSemanticCatalog catalog;

    @BeforeEach
    void setUp() {
        catalog = NodeSemanticCatalog.get();
        catalog.invalidateRules();
        catalog.forceRefresh();
    }

    @Test
    void semanticPortKeyPreservesGetBoxFaceHorizontalRules() {
        List<NodeSemanticEdge> edges = catalog.effectiveDownstream(
                GET_BOX_FACE, "output_face:horizontal", NodeDataType.BOX_FACE);
        Set<String> exactTargets = exactTargets(edges);
        assertTrue(exactTargets.contains("geometry.architectural_primitives.floor_slab"),
                "got=" + exactTargets);
        assertTrue(exactTargets.contains("geometry.architectural_primitives.column_grid")
                        || exactTargets.contains("geometry.architectural_primitives.roof_base"),
                "got=" + exactTargets);
        assertFalse(exactTargets.contains("geometry.architectural_primitives.wall_slab"),
                "horizontal must not include vertical wall exact; got=" + exactTargets);
    }

    @Test
    void semanticPortKeyPreservesGetBoxFaceVerticalRules() {
        List<NodeSemanticEdge> edges = catalog.effectiveDownstream(
                GET_BOX_FACE, "output_face:vertical", NodeDataType.BOX_FACE);
        Set<String> exactTargets = exactTargets(edges);
        assertTrue(exactTargets.contains("geometry.architectural_primitives.wall_slab"),
                "got=" + exactTargets);
        assertTrue(exactTargets.contains("geometry.architectural_primitives.window_array")
                        || exactTargets.contains("geometry.architectural_primitives.door_array"),
                "got=" + exactTargets);
        assertFalse(exactTargets.contains("geometry.architectural_primitives.floor_slab"),
                "vertical must not include horizontal floor exact; got=" + exactTargets);
    }

    @Test
    void physicalOutputFaceDoesNotExpandOrientationExactRules() {
        List<NodeSemanticEdge> edges = catalog.effectiveDownstream(
                GET_BOX_FACE, "output_face", NodeDataType.BOX_FACE);
        Set<String> exactTargets = exactTargets(edges);
        assertFalse(exactTargets.contains("geometry.architectural_primitives.floor_slab"));
        assertFalse(exactTargets.contains("geometry.architectural_primitives.wall_slab"));
        assertFalse(exactTargets.contains("geometry.architectural_primitives.window_array"));
    }

    @Test
    void exactEdgeOverridesCategoryAndTypeDuplicate() {
        String wall = "geometry.architectural_primitives.wall_slab";
        List<NodeSemanticEdge> edges = catalog.effectiveDownstream(
                wall, "output_geometry", NodeDataType.GEOMETRY);
        List<NodeSemanticEdge> toDifference = edges.stream()
                .filter(e -> "geometry.boolean.difference".equalsIgnoreCase(e.targetNodeId()))
                .filter(e -> "input_base".equalsIgnoreCase(e.targetPortId()))
                .toList();
        assertFalse(toDifference.isEmpty(), "expected wall→difference edges; got=" + summarize(edges));
        assertTrue(toDifference.stream().anyMatch(e -> e.kind() == NodeSemanticEdgeKind.EXACT));
        assertTrue(toDifference.stream().noneMatch(e -> e.kind() == NodeSemanticEdgeKind.TYPE
                        && "output_geometry".equalsIgnoreCase(e.sourcePortId())
                        && "input_base".equalsIgnoreCase(e.targetPortId())),
                "TYPE duplicate of EXACT wall→difference should be deduped; got=" + summarize(toDifference));
    }

    @Test
    void categoryEdgeOverridesTypeDuplicate() {
        // Sphere has no EXACT sourceNodes rule; CATEGORY+TYPE both recommend transform_geometry.
        List<NodeSemanticEdge> edges = catalog.effectiveDownstream(
                "geometry.primitives.sphere", "output_geometry", NodeDataType.GEOMETRY);
        List<NodeSemanticEdge> toTransform = edges.stream()
                .filter(e -> e.targetNodeId() != null
                        && e.targetNodeId().toLowerCase(Locale.ROOT).contains("transform_geometry"))
                .toList();
        assertFalse(toTransform.isEmpty(), "got=" + summarize(edges));
        assertTrue(toTransform.stream().anyMatch(e -> e.kind() == NodeSemanticEdgeKind.CATEGORY),
                "expected CATEGORY transform edge; got=" + summarize(toTransform));
        assertTrue(toTransform.stream().noneMatch(e -> e.kind() == NodeSemanticEdgeKind.TYPE),
                "TYPE duplicate should lose to CATEGORY; got=" + summarize(toTransform));
    }

    @Test
    void sameTargetDifferentPortsAreNotDeduplicated() {
        String window = "geometry.architectural_primitives.window_array";
        List<NodeSemanticEdge> all = catalog.effectiveDownstream(window);
        boolean openingsToDiff = all.stream().anyMatch(e ->
                "geometry.boolean.difference".equalsIgnoreCase(e.targetNodeId())
                        && "output_openings".equalsIgnoreCase(e.sourcePortId()));
        boolean framesToPlace = all.stream().anyMatch(e ->
                e.targetNodeId() != null
                        && e.targetNodeId().contains("place_geometry_on_frames")
                        && "output_frames".equalsIgnoreCase(e.sourcePortId()));
        assertTrue(openingsToDiff, "openings→difference missing; got=" + summarize(all));
        assertTrue(framesToPlace, "frames→place missing; got=" + summarize(all));
    }

    @Test
    void portSpecificQueryReturnsOnlyRelevantEdges() {
        String window = "geometry.architectural_primitives.window_array";
        List<NodeSemanticEdge> openings = catalog.effectiveDownstream(
                window, "output_openings", null);
        assertTrue(openings.stream().anyMatch(e ->
                "geometry.boolean.difference".equalsIgnoreCase(e.targetNodeId())));
        assertTrue(openings.stream().noneMatch(e ->
                "output_frames".equalsIgnoreCase(e.sourcePortId())));

        List<NodeSemanticEdge> frames = catalog.effectiveDownstream(window, "output_frames", null);
        assertTrue(frames.stream().noneMatch(e ->
                "geometry.boolean.difference".equalsIgnoreCase(e.targetNodeId())
                        && "output_openings".equalsIgnoreCase(e.sourcePortId())));
    }

    @Test
    void consistencyCanonicalWorkflowEdgesVisible() {
        assertTrue(catalog.exactDownstream("geometry.architectural_primitives.window_array").stream()
                .anyMatch(e -> "geometry.boolean.difference".equalsIgnoreCase(e.targetNodeId())
                        && "input_cutter".equalsIgnoreCase(e.targetPortId())));

        assertTrue(catalog.exactDownstream("geometry.voxel.voxelize_geometry").stream()
                .anyMatch(e -> e.targetNodeId() != null
                        && e.targetNodeId().contains("assign_block_type")));

        List<NodeSemanticEdge> strip = catalog.effectiveDownstream(
                "geometry.solids.sweep", "output_surface_strip", NodeDataType.SURFACE_STRIP);
        assertTrue(strip.stream().anyMatch(e ->
                        e.targetNodeId() != null
                                && (e.targetNodeId().contains("surface_strip")
                                || e.targetNodeId().contains("lattice")
                                || e.targetNodeId().contains("offset"))),
                "SURFACE_STRIP should expose strip workflow neighbors; got=" + summarize(strip));

        List<NodeSemanticEdge> scalar = catalog.effectiveDownstream(
                "math.fields.noise_scalar_field", "output_field", NodeDataType.SCALAR_FIELD);
        assertTrue(scalar.stream().anyMatch(e ->
                        e.targetNodeId() != null
                                && e.targetNodeId().contains("scalar")),
                "SCALAR_FIELD should expose type/category neighbors; got=" + summarize(scalar));

        List<NodeSemanticEdge> tree = catalog.effectiveDownstream(
                "math.data_tree.graft", "output_tree", NodeDataType.DATA_TREE);
        assertTrue(tree.stream().anyMatch(e ->
                        e.targetNodeId() != null
                                && e.targetNodeId().toLowerCase(Locale.ROOT).contains("data_tree")),
                "DATA_TREE should reach tree ops; got=" + summarize(tree));
    }

    private static Set<String> exactTargets(List<NodeSemanticEdge> edges) {
        return edges.stream()
                .filter(e -> e.kind() == NodeSemanticEdgeKind.EXACT)
                .map(NodeSemanticEdge::targetNodeId)
                .filter(id -> id != null && !id.isBlank())
                .map(id -> id.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    private static String summarize(List<NodeSemanticEdge> edges) {
        return edges.stream()
                .map(e -> e.kind() + ":" + e.sourcePortId() + "→" + e.targetNodeId())
                .collect(Collectors.joining(", "));
    }
}

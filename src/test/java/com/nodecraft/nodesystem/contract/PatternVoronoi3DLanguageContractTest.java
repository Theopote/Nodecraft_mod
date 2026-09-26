package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.GenerationLimits;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pattern Voronoi 3D v1 language fence (Graph V45).
 */
class PatternVoronoi3DLanguageContractTest {

    private static final String LLOYD_RELAX_ID = "pattern.voronoi_3d.lloyd_relax";

    private static NodeRegistry registry;

    @BeforeAll
    static void init() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void currentGraphFormatIsV45() {
        assertEquals(45, GraphFormatVersion.V45);
        assertEquals(GraphFormatVersion.V45, GraphFormatVersion.CURRENT);
    }

    @Test
    void exactlyOneCanonicalVoronoi3DNodeRegistered() {
        List<String> ids = registry.getAllNodeIds().stream()
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("pattern.voronoi_3d."))
                .sorted()
                .toList();
        assertEquals(1, ids.size(), "Expected 1 pattern.voronoi_3d node: " + ids);
        assertEquals(Set.of(LLOYD_RELAX_ID), Set.copyOf(ids));
    }

    @Test
    void lloydRelaxUsesTypedSpatialPorts() {
        assertPortType(LLOYD_RELAX_ID, "input_sites", true, NodeDataType.POINT_LIST);
        assertPortType(LLOYD_RELAX_ID, "input_corner_a", true, NodeDataType.POINT);
        assertPortType(LLOYD_RELAX_ID, "input_corner_b", true, NodeDataType.POINT);
        assertPortType(LLOYD_RELAX_ID, "input_cells", true, NodeDataType.INTEGER);
        assertPortType(LLOYD_RELAX_ID, "input_iterations", true, NodeDataType.INTEGER);
        assertPortType(LLOYD_RELAX_ID, "output_sites", false, NodeDataType.POINT_LIST);
        assertPortType(LLOYD_RELAX_ID, "output_count", false, NodeDataType.INTEGER);
        assertPortType(LLOYD_RELAX_ID, "output_valid", false, NodeDataType.BOOLEAN);
        assertFalse(hasPort(LLOYD_RELAX_ID, "input_min", true));
        assertFalse(hasPort(LLOYD_RELAX_ID, "input_max", true));
    }

    @Test
    void zeroIterationsPassthroughPreservesSites() {
        BaseNode node = createLloydRelax();
        List<PointData> sites = defaultSites();
        node.setInput("input_sites", sites);
        node.setInput("input_corner_a", new PointData(0, 0, 0));
        node.setInput("input_corner_b", new PointData(10, 10, 10));
        node.setInput("input_iterations", 0);
        node.processNode(null);

        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));
        assertEquals(3, node.getOutput("output_count"));
        @SuppressWarnings("unchecked")
        List<PointData> out = assertInstanceOf(List.class, node.getOutput("output_sites"));
        assertEquals(3, out.size());
        for (int i = 0; i < sites.size(); i++) {
            assertPointEquals(sites.get(i), out.get(i));
        }
    }

    @Test
    void outputCountMatchesInputSiteCount() {
        BaseNode node = createLloydRelax();
        node.setInput("input_sites", defaultSites());
        node.setInput("input_corner_a", new PointData(0, 0, 0));
        node.setInput("input_corner_b", new PointData(10, 10, 10));
        node.setInput("input_iterations", 1);
        node.setInput("input_cells", 4);
        node.processNode(null);

        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));
        assertEquals(3, node.getOutput("output_count"));
        @SuppressWarnings("unchecked")
        List<PointData> out = assertInstanceOf(List.class, node.getOutput("output_sites"));
        assertEquals(3, out.size());
    }

    @Test
    void passthroughPreservesIndexIdentityWhenOneSiteMoves() {
        BaseNode node = createLloydRelax();
        List<PointData> sites = List.of(
                new PointData(2, 2, 2),
                new PointData(5, 5, 5),
                new PointData(8, 3, 7)
        );
        node.setInput("input_sites", sites);
        node.setInput("input_corner_a", new PointData(0, 0, 0));
        node.setInput("input_corner_b", new PointData(10, 10, 10));
        node.setInput("input_iterations", 0);
        node.processNode(null);

        @SuppressWarnings("unchecked")
        List<PointData> out = assertInstanceOf(List.class, node.getOutput("output_sites"));
        assertPointEquals(sites.get(0), out.get(0));
        assertPointEquals(sites.get(2), out.get(2));
    }

    @Test
    void reversedCornersMatchOrderedBounds() {
        List<PointData> sites = defaultSites();
        BaseNode ordered = createLloydRelax();
        ordered.setInput("input_sites", sites);
        ordered.setInput("input_corner_a", new PointData(0, 0, 0));
        ordered.setInput("input_corner_b", new PointData(10, 10, 10));
        ordered.setInput("input_iterations", 1);
        ordered.setInput("input_cells", 4);
        ordered.processNode(null);

        BaseNode reversed = createLloydRelax();
        reversed.setInput("input_sites", sites);
        reversed.setInput("input_corner_a", new PointData(10, 10, 10));
        reversed.setInput("input_corner_b", new PointData(0, 0, 0));
        reversed.setInput("input_iterations", 1);
        reversed.setInput("input_cells", 4);
        reversed.processNode(null);

        assertEquals(ordered.getOutput("output_valid"), reversed.getOutput("output_valid"));
        assertEquals(ordered.getOutput("output_count"), reversed.getOutput("output_count"));
        @SuppressWarnings("unchecked")
        List<PointData> orderedOut = assertInstanceOf(List.class, ordered.getOutput("output_sites"));
        @SuppressWarnings("unchecked")
        List<PointData> reversedOut = assertInstanceOf(List.class, reversed.getOutput("output_sites"));
        assertEquals(orderedOut.size(), reversedOut.size());
        for (int i = 0; i < orderedOut.size(); i++) {
            assertPointEquals(orderedOut.get(i), reversedOut.get(i));
        }
    }

    @Test
    void nanCornerFailsClosed() {
        BaseNode node = createLloydRelax();
        node.setInput("input_sites", defaultSites());
        node.setInput("input_corner_a", new PointData(Double.NaN, 0, 0));
        node.setInput("input_corner_b", new PointData(10, 10, 10));
        node.processNode(null);
        assertInvalid(node);
    }

    @Test
    void nanSiteFailsClosed() {
        BaseNode node = createLloydRelax();
        node.setInput("input_sites", List.of(new PointData(2, 2, 2), new PointData(Double.NaN, 5, 5)));
        node.setInput("input_corner_a", new PointData(0, 0, 0));
        node.setInput("input_corner_b", new PointData(10, 10, 10));
        node.processNode(null);
        assertInvalid(node);
    }

    @Test
    void degenerateBoundsFailClosed() {
        BaseNode flatZ = createLloydRelax();
        flatZ.setInput("input_sites", defaultSites());
        flatZ.setInput("input_corner_a", new PointData(0, 0, 0));
        flatZ.setInput("input_corner_b", new PointData(10, 10, 0));
        flatZ.processNode(null);
        assertInvalid(flatZ);
    }

    @Test
    void siteOutsideBoundsFailsClosed() {
        BaseNode node = createLloydRelax();
        node.setInput("input_sites", List.of(new PointData(2, 2, 2), new PointData(11, 5, 5)));
        node.setInput("input_corner_a", new PointData(0, 0, 0));
        node.setInput("input_corner_b", new PointData(10, 10, 10));
        node.processNode(null);
        assertInvalid(node);
    }

    @Test
    void duplicateSitesFailClosed() {
        BaseNode node = createLloydRelax();
        node.setInput("input_sites", List.of(new PointData(2, 2, 2), new PointData(2, 2, 2)));
        node.setInput("input_corner_a", new PointData(0, 0, 0));
        node.setInput("input_corner_b", new PointData(10, 10, 10));
        node.processNode(null);
        assertInvalid(node);
    }

    @Test
    void cellsBelowMinimumFailClosed() {
        BaseNode node = createLloydRelax();
        node.setInput("input_sites", defaultSites());
        node.setInput("input_corner_a", new PointData(0, 0, 0));
        node.setInput("input_corner_b", new PointData(10, 10, 10));
        node.setInput("input_cells", 3);
        node.processNode(null);
        assertInvalid(node);
    }

    @Test
    void nonIntegerCellsUsePropertyFallback() {
        BaseNode node = createLloydRelax();
        node.setInput("input_sites", defaultSites());
        node.setInput("input_corner_a", new PointData(0, 0, 0));
        node.setInput("input_corner_b", new PointData(10, 10, 10));
        node.setInput("input_cells", 3.8d);
        node.setInput("input_iterations", 0);
        node.processNode(null);

        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));
        assertEquals(3, node.getOutput("output_count"));
    }

    @Test
    void negativeIterationsFailClosed() {
        BaseNode node = createLloydRelax();
        node.setInput("input_sites", defaultSites());
        node.setInput("input_corner_a", new PointData(0, 0, 0));
        node.setInput("input_corner_b", new PointData(10, 10, 10));
        node.setInput("input_iterations", -1);
        node.processNode(null);
        assertInvalid(node);
    }

    @Test
    void processingIsDeterministic() {
        BaseNode first = createLloydRelax();
        first.setInput("input_sites", defaultSites());
        first.setInput("input_corner_a", new PointData(0, 0, 0));
        first.setInput("input_corner_b", new PointData(10, 10, 10));
        first.setInput("input_iterations", 2);
        first.setInput("input_cells", 6);
        first.processNode(null);

        BaseNode second = createLloydRelax();
        second.setInput("input_sites", defaultSites());
        second.setInput("input_corner_a", new PointData(0, 0, 0));
        second.setInput("input_corner_b", new PointData(10, 10, 10));
        second.setInput("input_iterations", 2);
        second.setInput("input_cells", 6);
        second.processNode(null);

        assertEquals(first.getOutput("output_valid"), second.getOutput("output_valid"));
        assertEquals(first.getOutput("output_count"), second.getOutput("output_count"));
        @SuppressWarnings("unchecked")
        List<PointData> firstOut = assertInstanceOf(List.class, first.getOutput("output_sites"));
        @SuppressWarnings("unchecked")
        List<PointData> secondOut = assertInstanceOf(List.class, second.getOutput("output_sites"));
        assertEquals(firstOut.size(), secondOut.size());
        for (int i = 0; i < firstOut.size(); i++) {
            assertPointEquals(firstOut.get(i), secondOut.get(i));
        }
    }

    @Test
    void siteCountOverCapFailsClosed() {
        List<PointData> sites = new ArrayList<>(GenerationLimits.MAX_VORONOI_LLOYD_SITES + 1);
        for (int i = 0; i <= GenerationLimits.MAX_VORONOI_LLOYD_SITES; i++) {
            sites.add(new PointData(i * 1.0e-6d, 1, 1));
        }
        BaseNode node = createLloydRelax();
        node.setInput("input_sites", sites);
        node.setInput("input_corner_a", new PointData(0, 0, 0));
        node.setInput("input_corner_b", new PointData(10, 10, 10));
        node.setInput("input_iterations", 0);
        node.processNode(null);
        assertInvalid(node);
    }

    @Test
    void workBudgetExceededFailsClosed() {
        assertTrue(GenerationLimits.exceedsLloydWorkBudget(40, 100, 20));

        BaseNode node = createLloydRelax();
        List<PointData> sites = new ArrayList<>(100);
        for (int i = 0; i < 100; i++) {
            sites.add(new PointData(1 + (i % 8), 1 + (i % 8), 1 + (i % 8)));
        }
        node.setInput("input_sites", sites);
        node.setInput("input_corner_a", new PointData(0, 0, 0));
        node.setInput("input_corner_b", new PointData(10, 10, 10));
        node.setInput("input_cells", 40);
        node.setInput("input_iterations", 20);
        node.processNode(null);
        assertInvalid(node);
    }

    private static BaseNode createLloydRelax() {
        return assertInstanceOf(BaseNode.class, registry.createNodeInstance(LLOYD_RELAX_ID));
    }

    private static List<PointData> defaultSites() {
        return List.of(
                new PointData(2, 2, 2),
                new PointData(5, 5, 5),
                new PointData(8, 3, 7)
        );
    }

    private static void assertInvalid(BaseNode node) {
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertEquals(0, node.getOutput("output_count"));
        @SuppressWarnings("unchecked")
        List<?> sites = assertInstanceOf(List.class, node.getOutput("output_sites"));
        assertTrue(sites.isEmpty());
    }

    private static void assertPointEquals(PointData expected, PointData actual) {
        Vector3d e = expected.getPosition();
        Vector3d a = actual.getPosition();
        assertEquals(e.x, a.x, 1.0e-9d);
        assertEquals(e.y, a.y, 1.0e-9d);
        assertEquals(e.z, a.z, 1.0e-9d);
    }

    private static void assertPortType(String typeId, String portId, boolean input, NodeDataType expected) {
        INode node = registry.createNodeInstance(typeId);
        IPort port = (input ? node.getInputPorts() : node.getOutputPorts()).stream()
                .filter(candidate -> candidate.getId().equals(portId))
                .findFirst()
                .orElseThrow(() -> new AssertionError(typeId + " missing port " + portId));
        assertEquals(expected, port.getDataType(), typeId + "." + portId);
    }

    private static boolean hasPort(String typeId, String portId, boolean input) {
        INode node = registry.createNodeInstance(typeId);
        return (input ? node.getInputPorts() : node.getOutputPorts()).stream()
                .anyMatch(port -> port.getId().equals(portId));
    }
}

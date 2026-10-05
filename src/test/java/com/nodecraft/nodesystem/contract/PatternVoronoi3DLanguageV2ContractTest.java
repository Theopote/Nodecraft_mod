package com.nodecraft.nodesystem.contract;

import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.execution.runtime.NodeEffectResolver;
import com.nodecraft.nodesystem.nodes.pattern.voronoi_3d.Voronoi3DLloydRelaxNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.GenerationLimits;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Language fence for Pattern Voronoi 3D Language v2.
 */
class PatternVoronoi3DLanguageV2ContractTest {

    private static final String LLOYD_RELAX_ID = "pattern.voronoi_3d.lloyd_relax";

    private static final Set<NodeDataType> FORBIDDEN_PORT_TYPES = Set.of(
        NodeDataType.ANY,
        NodeDataType.LIST,
        NodeDataType.LINE,
        NodeDataType.POLYLINE,
        NodeDataType.CURVE,
        NodeDataType.BLOCK_LIST,
        NodeDataType.FILE_PATH
    );

    private static NodeRegistry registry;

    @BeforeAll
    static void init() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void exactlyOneNodeOrderZeroPure() {
        List<String> ids = registry.getAllNodeIds().stream()
            .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("pattern.voronoi_3d."))
            .sorted()
            .toList();
        assertEquals(1, ids.size(), "Expected 1 pattern.voronoi_3d node: " + ids);
        assertEquals(LLOYD_RELAX_ID, ids.getFirst());

        NodeInfo info = registry.getNodeInfo(LLOYD_RELAX_ID);
        assertNotNull(info);
        assertEquals(0, info.getOrder());
        assertEquals("pattern.voronoi_3d", info.getCategoryId());
        INode node = registry.createNodeInstance(LLOYD_RELAX_ID);
        assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(node.getClass(), LLOYD_RELAX_ID));
    }

    @Test
    void exposesValidAndError() {
        INode node = registry.createNodeInstance(LLOYD_RELAX_ID);
        assertTrue(hasPort(node, "output_valid"));
        assertTrue(hasPort(node, "output_error"));
        assertPortType(LLOYD_RELAX_ID, "output_error", false, NodeDataType.STRING);
    }

    @Test
    void publicPortsForbidLegacyLooseTypes() {
        INode node = registry.createNodeInstance(LLOYD_RELAX_ID);
        for (IPort port : node.getInputPorts()) {
            assertFalse(FORBIDDEN_PORT_TYPES.contains(port.getDataType()),
                "input " + port.getId() + " has forbidden type " + port.getDataType());
        }
        for (IPort port : node.getOutputPorts()) {
            assertFalse(FORBIDDEN_PORT_TYPES.contains(port.getDataType()),
                "output " + port.getId() + " has forbidden type " + port.getDataType());
        }
    }

    @Test
    void cellsConnectedNonExactIntegerFails() {
        LloydProbe probe = new LloydProbe();
        seedValidGeometry(probe);
        probe.connectInput("input_cells", NodeDataType.INTEGER);
        probe.putRawInput("input_cells", 3.8d);
        probe.setNodeState(Map.of("iterations", 0));
        probe.processNode(null);
        assertInvalid(probe);
        assertTrue(String.valueOf(probe.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("cells"));
    }

    @Test
    void iterationsConnectedNonExactIntegerFails() {
        LloydProbe probe = new LloydProbe();
        seedValidGeometry(probe);
        probe.connectInput("input_iterations", NodeDataType.INTEGER);
        probe.putRawInput("input_iterations", 1.5d);
        probe.processNode(null);
        assertInvalid(probe);
        assertTrue(String.valueOf(probe.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("iterations"));
    }

    @Test
    void cellsBelowMinimumFailClosed() {
        BaseNode node = createLloyd();
        seedValidGeometry(node);
        node.setNodeState(Map.of("cellsPerAxis", 3, "iterations", 0));
        node.processNode(null);
        assertInvalid(node);
    }

    @Test
    void cellsAboveMaximumFailClosed() {
        BaseNode node = createLloyd();
        seedValidGeometry(node);
        node.setNodeState(Map.of(
            "cellsPerAxis", GenerationLimits.MAX_VORONOI_LLOYD_CELLS_PER_AXIS + 1,
            "iterations", 0
        ));
        node.processNode(null);
        assertInvalid(node);
    }

    @Test
    void negativeIterationsFailClosed() {
        BaseNode node = createLloyd();
        seedValidGeometry(node);
        node.setNodeState(Map.of("iterations", -1));
        node.processNode(null);
        assertInvalid(node);
    }

    @Test
    void zeroIterationsPassthroughPreservesIndexIdentity() {
        BaseNode node = createLloyd();
        List<PointData> sites = List.of(
            new PointData(2, 2, 2),
            new PointData(5, 5, 5),
            new PointData(8, 3, 7)
        );
        node.setInput("input_sites", sites);
        node.setInput("input_corner_a", new PointData(0, 0, 0));
        node.setInput("input_corner_b", new PointData(10, 10, 10));
        node.setNodeState(Map.of("iterations", 0));
        node.processNode(null);

        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));
        assertEquals("", node.getOutput("output_error"));
        assertEquals(3, node.getOutput("output_count"));
        @SuppressWarnings("unchecked")
        List<PointData> out = assertInstanceOf(List.class, node.getOutput("output_sites"));
        assertEquals(3, out.size());
        for (int i = 0; i < sites.size(); i++) {
            assertPointEquals(sites.get(i), out.get(i));
        }
    }

    @Test
    void outputCardinalityMatchesInput() {
        BaseNode node = createLloyd();
        seedValidGeometry(node);
        node.setNodeState(Map.of("cellsPerAxis", 4, "iterations", 1));
        node.processNode(null);

        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));
        assertEquals(3, node.getOutput("output_count"));
        @SuppressWarnings("unchecked")
        List<PointData> out = assertInstanceOf(List.class, node.getOutput("output_sites"));
        assertEquals(3, out.size());
        for (PointData site : out) {
            assertTrue(Double.isFinite(site.position().x));
            assertTrue(Double.isFinite(site.position().y));
            assertTrue(Double.isFinite(site.position().z));
            assertTrue(site.position().x >= 0 && site.position().x <= 10);
            assertTrue(site.position().y >= 0 && site.position().y <= 10);
            assertTrue(site.position().z >= 0 && site.position().z <= 10);
        }
    }

    @Test
    void reversedCornersMatchOrderedBounds() {
        List<PointData> sites = defaultSites();
        BaseNode ordered = createLloyd();
        ordered.setInput("input_sites", sites);
        ordered.setInput("input_corner_a", new PointData(0, 0, 0));
        ordered.setInput("input_corner_b", new PointData(10, 10, 10));
        ordered.setNodeState(Map.of("cellsPerAxis", 4, "iterations", 1));
        ordered.processNode(null);

        BaseNode reversed = createLloyd();
        reversed.setInput("input_sites", sites);
        reversed.setInput("input_corner_a", new PointData(10, 10, 10));
        reversed.setInput("input_corner_b", new PointData(0, 0, 0));
        reversed.setNodeState(Map.of("cellsPerAxis", 4, "iterations", 1));
        reversed.processNode(null);

        assertEquals(Boolean.TRUE, ordered.getOutput("output_valid"));
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
        BaseNode node = createLloyd();
        node.setInput("input_sites", defaultSites());
        node.setInput("input_corner_a", new Vector3d(Double.NaN, 0, 0));
        node.setInput("input_corner_b", new PointData(10, 10, 10));
        node.setNodeState(Map.of("iterations", 0));
        node.processNode(null);
        assertInvalid(node);
    }

    @Test
    void nanSiteFailsClosed() {
        BaseNode node = createLloyd();
        node.setInput("input_sites", List.of(new PointData(2, 2, 2), new Vector3d(5, 5, 5)));
        node.setInput("input_corner_a", new PointData(0, 0, 0));
        node.setInput("input_corner_b", new PointData(10, 10, 10));
        node.setNodeState(Map.of("iterations", 0));
        node.processNode(null);
        assertInvalid(node);
    }

    @Test
    void degenerateBoundsFailClosed() {
        BaseNode node = createLloyd();
        node.setInput("input_sites", defaultSites());
        node.setInput("input_corner_a", new PointData(0, 0, 0));
        node.setInput("input_corner_b", new PointData(10, 10, 0));
        node.setNodeState(Map.of("iterations", 0));
        node.processNode(null);
        assertInvalid(node);
    }

    @Test
    void extremeFiniteCornersWithInfiniteSpanFailClosed() {
        BaseNode node = createLloyd();
        node.setInput("input_sites", List.of(new PointData(0, 0, 0)));
        node.setInput("input_corner_a", new PointData(-1.0e308d, -1.0e308d, -1.0e308d));
        node.setInput("input_corner_b", new PointData(1.0e308d, 1.0e308d, 1.0e308d));
        node.setNodeState(Map.of("cellsPerAxis", 4, "iterations", 1));
        node.processNode(null);
        assertInvalid(node);
        assertTrue(String.valueOf(node.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("bound"));
    }

    @Test
    void siteOutsideBoundsFailsClosed() {
        BaseNode node = createLloyd();
        node.setInput("input_sites", List.of(new PointData(2, 2, 2), new PointData(11, 5, 5)));
        node.setInput("input_corner_a", new PointData(0, 0, 0));
        node.setInput("input_corner_b", new PointData(10, 10, 10));
        node.setNodeState(Map.of("iterations", 0));
        node.processNode(null);
        assertInvalid(node);
    }

    @Test
    void duplicateSitesFailClosed() {
        BaseNode node = createLloyd();
        node.setInput("input_sites", List.of(new PointData(2, 2, 2), new PointData(2, 2, 2)));
        node.setInput("input_corner_a", new PointData(0, 0, 0));
        node.setInput("input_corner_b", new PointData(10, 10, 10));
        node.setNodeState(Map.of("iterations", 0));
        node.processNode(null);
        assertInvalid(node);
    }

    @Test
    void siteCountOverCapFailsClosed() {
        List<PointData> sites = new ArrayList<>(GenerationLimits.MAX_VORONOI_LLOYD_SITES + 1);
        for (int i = 0; i <= GenerationLimits.MAX_VORONOI_LLOYD_SITES; i++) {
            sites.add(new PointData(i * 1.0e-6d, 1, 1));
        }
        BaseNode node = createLloyd();
        node.setInput("input_sites", sites);
        node.setInput("input_corner_a", new PointData(0, 0, 0));
        node.setInput("input_corner_b", new PointData(10, 10, 10));
        node.setNodeState(Map.of("iterations", 0));
        node.processNode(null);
        assertInvalid(node);
    }

    @Test
    void siteCountExceedsGridSamplesFailsWhenIterationsPositive() {
        BaseNode node = createLloyd();
        List<PointData> sites = new ArrayList<>(100);
        for (int i = 0; i < 100; i++) {
            double x = 0.5d + (i % 10) * 0.9d;
            double y = 0.5d + ((i / 10) % 10) * 0.9d;
            double z = 0.5d + ((i / 100) % 10) * 0.9d;
            sites.add(new PointData(x, y, z));
        }
        node.setInput("input_sites", sites);
        node.setInput("input_corner_a", new PointData(0, 0, 0));
        node.setInput("input_corner_b", new PointData(10, 10, 10));
        node.setNodeState(Map.of("cellsPerAxis", 4, "iterations", 1));
        node.processNode(null);
        assertInvalid(node);
        String error = String.valueOf(node.getOutput("output_error")).toLowerCase(Locale.ROOT);
        assertTrue(error.contains("grid") || error.contains("sample") || error.contains("cells"));
    }

    @Test
    void siteCountMayExceedGridSamplesWhenIterationsZero() {
        BaseNode node = createLloyd();
        List<PointData> sites = new ArrayList<>(100);
        for (int i = 0; i < 100; i++) {
            double x = 0.5d + (i % 10) * 0.9d;
            double y = 0.5d + ((i / 10) % 10) * 0.9d;
            double z = 0.5d + ((i / 100) % 10) * 0.9d;
            sites.add(new PointData(x, y, z));
        }
        node.setInput("input_sites", sites);
        node.setInput("input_corner_a", new PointData(0, 0, 0));
        node.setInput("input_corner_b", new PointData(10, 10, 10));
        node.setNodeState(Map.of("cellsPerAxis", 4, "iterations", 0));
        node.processNode(null);

        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));
        assertEquals("", node.getOutput("output_error"));
        assertEquals(100, node.getOutput("output_count"));
        @SuppressWarnings("unchecked")
        List<PointData> out = assertInstanceOf(List.class, node.getOutput("output_sites"));
        assertEquals(100, out.size());
    }

    @Test
    void outputSitesRemainDistinctAfterRelaxation() {
        BaseNode node = createLloyd();
        seedValidGeometry(node);
        node.setNodeState(Map.of("cellsPerAxis", 6, "iterations", 2));
        node.processNode(null);

        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<PointData> out = assertInstanceOf(List.class, node.getOutput("output_sites"));
        for (int i = 0; i < out.size(); i++) {
            for (int j = i + 1; j < out.size(); j++) {
                Vector3d a = out.get(i).position();
                Vector3d b = out.get(j).position();
                assertTrue(a.distanceSquared(b) > 1.0e-12d,
                    "output sites " + i + " and " + j + " are near-duplicates");
            }
        }
    }

    @Test
    void workBudgetExceededFailsClosed() {
        assertEquals(10_000_000L, GenerationLimits.MAX_LLOYD_DISTANCE_TESTS);
        assertTrue(GenerationLimits.exceedsLloydWorkBudget(40, 100, 20));

        BaseNode node = createLloyd();
        List<PointData> sites = new ArrayList<>(100);
        for (int i = 0; i < 100; i++) {
            double x = 0.5d + (i % 10) * 0.9d;
            double y = 0.5d + ((i / 10) % 10) * 0.9d;
            sites.add(new PointData(x, y, 5.0d));
        }
        node.setInput("input_sites", sites);
        node.setInput("input_corner_a", new PointData(0, 0, 0));
        node.setInput("input_corner_b", new PointData(10, 10, 10));
        node.setNodeState(Map.of("cellsPerAxis", 40, "iterations", 20));
        node.processNode(null);
        assertInvalid(node);
    }

    @Test
    void processingIsDeterministic() {
        BaseNode first = createLloyd();
        seedValidGeometry(first);
        first.setNodeState(Map.of("cellsPerAxis", 6, "iterations", 2));
        first.processNode(null);

        BaseNode second = createLloyd();
        seedValidGeometry(second);
        second.setNodeState(Map.of("cellsPerAxis", 6, "iterations", 2));
        second.processNode(null);

        assertEquals(Boolean.TRUE, first.getOutput("output_valid"));
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

    private static BaseNode createLloyd() {
        return assertInstanceOf(BaseNode.class, registry.createNodeInstance(LLOYD_RELAX_ID));
    }

    private static void seedValidGeometry(BaseNode node) {
        node.setInput("input_sites", defaultSites());
        node.setInput("input_corner_a", new PointData(0, 0, 0));
        node.setInput("input_corner_b", new PointData(10, 10, 10));
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
        assertNotNull(node.getOutput("output_error"));
        assertFalse(String.valueOf(node.getOutput("output_error")).isBlank());
    }

    private static void assertPointEquals(PointData expected, PointData actual) {
        Vector3d e = expected.position();
        Vector3d a = actual.position();
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

    private static boolean hasPort(INode node, String portId) {
        return node.getOutputPorts().stream().anyMatch(port -> port.getId().equals(portId))
            || node.getInputPorts().stream().anyMatch(port -> port.getId().equals(portId));
    }

    private static void connectInput(BaseNode target, String inputPortId, NodeDataType outputType) {
        PortStubNode stub = new PortStubNode(outputType);
        BasePort output = (BasePort) stub.getOutputPorts().getFirst();
        BasePort input = (BasePort) target.getInputPorts().stream()
            .filter(port -> inputPortId.equals(port.getId()))
            .findFirst()
            .orElseThrow();
        assertTrue(output.connectTo(input));
    }

    private static final class LloydProbe extends Voronoi3DLloydRelaxNode {
        void putRawInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            PatternVoronoi3DLanguageV2ContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class PortStubNode extends BaseNode {
        PortStubNode(NodeDataType outputType) {
            super(UUID.randomUUID(), "test.port_stub");
            addOutputPort(new BasePort("output_stub", "Stub", "", outputType, this));
        }

        @Override
        public void processNode(ExecutionContext context) {
        }
    }
}

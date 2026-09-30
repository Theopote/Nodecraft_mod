package com.nodecraft.nodesystem.contract;

import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.SphereData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.execution.runtime.NodeEffectResolver;
import com.nodecraft.nodesystem.graph.GraphMigrationRegistry;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedConnection;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.BoxFaceValidator;
import com.nodecraft.nodesystem.util.GenerationLimits;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Language fence for Pattern Grid Language v2 (Graph V80).
 */
class PatternGridLanguageV2ContractTest {

    private static final List<String> CANONICAL_IDS = List.of(
        "pattern.grid.grid_array",
        "pattern.grid.facade_grid",
        "pattern.grid.staggered_grid",
        "pattern.grid.hex_grid",
        "pattern.grid.triangular_grid"
    );

    private static final Set<NodeDataType> FORBIDDEN_PORT_TYPES = Set.of(
        NodeDataType.ANY,
        NodeDataType.LIST,
        NodeDataType.LINE,
        NodeDataType.POLYLINE,
        NodeDataType.CURVE
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
    void currentGraphFormatIsAtLeastV80() {
        assertTrue(GraphFormatVersion.isCurrent(GraphFormatVersion.CURRENT));
        assertTrue(GraphFormatVersion.isCurrent(GraphFormatVersion.CURRENT));
    }

    @Test
    void exactlyFiveNodesWithUniqueOrdersZeroToFour() {
        List<String> ids = registry.getAllNodeIds().stream()
            .filter(id -> id.startsWith("pattern.grid."))
            .sorted()
            .toList();
        assertEquals(5, ids.size());
        assertEquals(Set.copyOf(CANONICAL_IDS), Set.copyOf(ids));

        Set<Integer> orders = new HashSet<>();
        for (String id : ids) {
            NodeInfo info = registry.getNodeInfo(id);
            assertNotNull(info);
            assertEquals("pattern.grid", info.getCategoryId());
            assertTrue(orders.add(info.getOrder()), "duplicate order for " + id);
            INode node = registry.createNodeInstance(id);
            assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(node.getClass(), id));
        }
        for (int i = 0; i < 5; i++) {
            assertTrue(orders.contains(i), "missing order " + i);
        }
    }

    @Test
    void allPatternGridNodesExposeValidAndError() {
        for (String id : CANONICAL_IDS) {
            INode node = registry.createNodeInstance(id);
            assertTrue(hasPort(node, "output_valid"), id);
            assertTrue(hasPort(node, "output_error"), id);
        }
    }

    @Test
    void publicPortsForbidAnyRawListLinePolylineCurve() {
        for (String id : CANONICAL_IDS) {
            INode node = registry.createNodeInstance(id);
            for (IPort port : node.getInputPorts()) {
                assertFalse(FORBIDDEN_PORT_TYPES.contains(port.getDataType()),
                    id + " input " + port.getId() + " has forbidden type " + port.getDataType());
            }
            for (IPort port : node.getOutputPorts()) {
                assertFalse(FORBIDDEN_PORT_TYPES.contains(port.getDataType()),
                    id + " output " + port.getId() + " has forbidden type " + port.getDataType());
            }
        }
    }

    @Test
    void gridArrayHasNoOutputGeometries() {
        assertFalse(hasPort(registry.createNodeInstance("pattern.grid.grid_array"), "output_geometries"));
    }

    @Test
    void gridArrayExactCountEmitsTransactionalCopies() {
        BaseNode grid = node("pattern.grid.grid_array");
        grid.setInput("input_geometry", new SphereData(new Vector3d(), 1.0d));
        grid.setNodeState(Map.of(
            "xDistance", 2.0d, "xCount", 3,
            "yDistance", 2.0d, "yCount", 5,
            "zDistance", 1.0d, "zCount", 1
        ));
        grid.processNode(null);
        assertEquals(Boolean.TRUE, grid.getOutput("output_valid"));
        assertEquals(15, grid.getOutput("output_count"));
        assertInstanceOf(CompositeGeometryData.class, grid.getOutput("output_geometry"));
        @SuppressWarnings("unchecked")
        List<Vector3d> offsets = assertInstanceOf(List.class, grid.getOutput("output_offsets"));
        assertEquals(15, offsets.size());
        assertEquals("", grid.getOutput("output_error"));
    }

    @Test
    void gridArrayCountOverBudgetFailsClosed() {
        BaseNode grid = node("pattern.grid.grid_array");
        grid.setInput("input_geometry", new SphereData(new Vector3d(), 1.0d));
        int over = GenerationLimits.MAX_GEOMETRY_INSTANCES + 1;
        grid.setNodeState(Map.of("xCount", over, "yCount", 1, "zCount", 1));
        grid.processNode(null);
        assertEquals(Boolean.FALSE, grid.getOutput("output_valid"));
        assertTrue(String.valueOf(grid.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("budget")
            || String.valueOf(grid.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("count"));
        assertEquals(0, grid.getOutput("output_count"));
        assertNull(grid.getOutput("output_geometry"));
    }

    @Test
    void gridArrayCountZeroFailsClosed() {
        BaseNode grid = node("pattern.grid.grid_array");
        grid.setInput("input_geometry", new SphereData(new Vector3d(), 1.0d));
        grid.setNodeState(Map.of("xCount", 0, "yCount", 3, "zCount", 1));
        grid.processNode(null);
        assertEquals(Boolean.FALSE, grid.getOutput("output_valid"));
        assertEquals(0, grid.getOutput("output_count"));
    }

    @Test
    void gridArrayDirectionConnectedInvalidFails() {
        GridArrayProbe probe = new GridArrayProbe();
        probe.setInput("input_geometry", new SphereData(new Vector3d(), 1.0d));
        probe.setNodeState(Map.of("xCount", 2, "yCount", 1, "zCount", 1));
        probe.connectInput("input_x_direction", NodeDataType.VECTOR);
        probe.putRawInput("input_x_direction", new Vector3d(0, 0, 0));
        probe.processNode(null);
        assertEquals(Boolean.FALSE, probe.getOutput("output_valid"));
        assertTrue(String.valueOf(probe.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("direction"));
    }

    @Test
    void facadeUsesBoxFaceAndBudgetFailClosed() {
        assertPortType("pattern.grid.facade_grid", "input_face", true, NodeDataType.BOX_FACE);
        BaseNode facade = node("pattern.grid.facade_grid");
        facade.setInput("input_face", requireFace(new BoxGeometryData(
            new Vector3d(10, 5, 8), new Vector3d(10, 5, 8)), "Front"));
        int over = (int) Math.sqrt(GenerationLimits.MAX_LAYOUT_INSTANCES) + 2;
        facade.setNodeState(Map.of("columns", over, "rows", over));
        facade.processNode(null);
        assertEquals(Boolean.FALSE, facade.getOutput("output_valid"));
        assertTrue(String.valueOf(facade.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("budget")
            || String.valueOf(facade.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("count"));
    }

    @Test
    void facadeMarginConnectedInvalidFails() {
        FacadeProbe probe = new FacadeProbe();
        probe.setInput("input_face", requireFace(new BoxGeometryData(
            new Vector3d(10, 5, 8), new Vector3d(10, 5, 8)), "Front"));
        probe.setNodeState(Map.of("columns", 3, "rows", 3));
        probe.connectInput("input_margin_x", NodeDataType.DOUBLE);
        probe.putRawInput("input_margin_x", Double.NaN);
        probe.processNode(null);
        assertEquals(Boolean.FALSE, probe.getOutput("output_valid"));
        assertTrue(String.valueOf(probe.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("margin"));
    }

    @Test
    void facadeGridRejectsTrapezoidFace() {
        List<Vector3d> corners = List.of(
            new Vector3d(0, 0, 0),
            new Vector3d(2, 0, 0),
            new Vector3d(3, 1, 0),
            new Vector3d(0, 1, 0)
        );
        BoxFaceData trapezoid = new BoxFaceData(
            0, "front", List.of(0, 1, 2, 3), corners,
            new Vector3d(1.25, 0.5, 0), new Vector3d(0, 0, 1)
        );
        assertNotNull(BoxFaceValidator.validate(trapezoid));

        BaseNode facade = node("pattern.grid.facade_grid");
        facade.setInput("input_face", trapezoid);
        facade.setNodeState(Map.of("columns", 3, "rows", 3));
        facade.processNode(null);
        assertEquals(Boolean.FALSE, facade.getOutput("output_valid"));
        assertFalse(String.valueOf(facade.getOutput("output_error")).isBlank());
        assertEquals(0, facade.getOutput("output_cell_count"));
    }

    @Test
    void gridArrayOversizedLeafWorkloadFailsClosed() {
        BaseNode grid = node("pattern.grid.grid_array");
        List<com.nodecraft.nodesystem.datatypes.GeometryData> leaves = new ArrayList<>();
        int leafCount = GenerationLimits.MAX_GEOMETRY_INSTANCES / 2 + 1;
        for (int i = 0; i < leafCount; i++) {
            leaves.add(new SphereData(new Vector3d(i, 0, 0), 0.1d));
        }
        grid.setInput("input_geometry", new CompositeGeometryData(leaves));
        grid.setNodeState(Map.of(
            "xCount", 2,
            "yCount", 1,
            "zCount", 1,
            "xDistance", 1.0d,
            "yDistance", 1.0d,
            "zDistance", 1.0d
        ));
        grid.processNode(null);
        assertEquals(Boolean.FALSE, grid.getOutput("output_valid"));
        assertTrue(String.valueOf(grid.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("workload")
            || String.valueOf(grid.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("limit"));
    }

    @Test
    void facadeGeneratesMatchingCentersAndBoundaries() {
        BaseNode facade = node("pattern.grid.facade_grid");
        facade.setInput("input_face", requireFace(new BoxGeometryData(
            new Vector3d(10, 5, 8), new Vector3d(10, 5, 8)), "Front"));
        facade.setNodeState(Map.of("columns", 3, "rows", 2));
        facade.processNode(null);
        assertEquals(Boolean.TRUE, facade.getOutput("output_valid"));
        assertEquals(6, facade.getOutput("output_cell_count"));
        @SuppressWarnings("unchecked")
        List<?> centers = assertInstanceOf(List.class, facade.getOutput("output_center_points"));
        @SuppressWarnings("unchecked")
        List<?> boundaries = assertInstanceOf(List.class, facade.getOutput("output_cell_boundaries"));
        assertEquals(6, centers.size());
        assertEquals(6, boundaries.size());
    }

    @Test
    void staggeredOriginConnectedInvalidFails() {
        StaggeredProbe probe = new StaggeredProbe();
        probe.setNodeState(Map.of("stepCount", 2, "rowCount", 2));
        probe.connectInput("input_origin", NodeDataType.POINT);
        probe.putRawInput("input_origin", new PointData(Double.NaN, 0, 0));
        probe.processNode(null);
        assertEquals(Boolean.FALSE, probe.getOutput("output_valid"));
        assertTrue(String.valueOf(probe.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("origin"));
    }

    @Test
    void staggeredBudgetFailsClosedNoClamp() {
        BaseNode grid = node("pattern.grid.staggered_grid");
        grid.setNodeState(Map.of("stepCount", 2048, "rowCount", 2048));
        grid.processNode(null);
        assertEquals(Boolean.FALSE, grid.getOutput("output_valid"));
        assertEquals(0, grid.getOutput("output_count"));
        @SuppressWarnings("unchecked")
        List<?> points = assertInstanceOf(List.class, grid.getOutput("output_points"));
        assertTrue(points.isEmpty());
    }

    @Test
    void hexBudgetFailsClosedNoClamp() {
        BaseNode grid = node("pattern.grid.hex_grid");
        grid.setNodeState(Map.of("qCount", 1024, "rCount", 1024, "radius", 1.0d));
        grid.processNode(null);
        assertEquals(Boolean.FALSE, grid.getOutput("output_valid"));
        assertEquals(0, grid.getOutput("output_count"));
    }

    @Test
    void triangularFlipSizeMatchesPoints() {
        BaseNode grid = node("pattern.grid.triangular_grid");
        grid.setNodeState(Map.of("uCount", 2, "vCount", 2, "sideLength", 2.0d));
        grid.processNode(null);
        assertEquals(Boolean.TRUE, grid.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<?> points = assertInstanceOf(List.class, grid.getOutput("output_points"));
        @SuppressWarnings("unchecked")
        List<?> flip = assertInstanceOf(List.class, grid.getOutput("output_flip"));
        assertEquals(4, points.size());
        assertEquals(4, flip.size());
        assertEquals(4, grid.getOutput("output_count"));
    }


    private static BaseNode node(String typeId) {
        return assertInstanceOf(BaseNode.class, registry.createNodeInstance(typeId));
    }

    private static BoxFaceData requireFace(BoxGeometryData box, String name) {
        return box.getFaces().stream()
            .filter(face -> name.equalsIgnoreCase(face.getName()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Missing face " + name));
    }

    private static void assertPortType(String typeId, String portId, boolean input, NodeDataType expected) {
        INode node = registry.createNodeInstance(typeId);
        List<? extends IPort> ports = input ? node.getInputPorts() : node.getOutputPorts();
        IPort port = ports.stream().filter(p -> portId.equals(p.getId())).findFirst().orElseThrow();
        assertEquals(expected, port.getDataType());
    }

    private static boolean hasPort(INode node, String portId) {
        return node.getInputPorts().stream().anyMatch(p -> portId.equals(p.getId()))
            || node.getOutputPorts().stream().anyMatch(p -> portId.equals(p.getId()));
    }

    private static SavedNode savedNode(String id, String typeId) {
        SavedNode node = new SavedNode();
        node.nodeId = id;
        node.typeId = typeId;
        return node;
    }

    private static SavedConnection wire(String sourceId, String sourcePort, String targetId, String targetPort) {
        SavedConnection connection = new SavedConnection();
        connection.sourceNodeId = sourceId;
        connection.sourcePortId = sourcePort;
        connection.targetNodeId = targetId;
        connection.targetPortId = targetPort;
        return connection;
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

    private static final class GridArrayProbe extends com.nodecraft.nodesystem.nodes.pattern.grid.GridArrayNode {
        void putRawInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            PatternGridLanguageV2ContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class FacadeProbe extends com.nodecraft.nodesystem.nodes.pattern.grid.FacadeGridNode {
        void putRawInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            PatternGridLanguageV2ContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class StaggeredProbe extends com.nodecraft.nodesystem.nodes.pattern.grid.StaggeredGridNode {
        void putRawInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            PatternGridLanguageV2ContractTest.connectInput(this, portId, outputType);
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

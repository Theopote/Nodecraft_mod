package com.nodecraft.nodesystem.contract;

import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.datatypes.SignedDistanceFieldData;
import com.nodecraft.nodesystem.datatypes.VectorData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.execution.runtime.NodeEffectResolver;
import com.nodecraft.nodesystem.graph.GraphMigrationRegistry;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedConnection;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import com.nodecraft.nodesystem.nodes.transform.deformations.TwistSdfNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.GenerationLimits;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.HashMap;
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
 * Language fence for Deformations Language v2 (Graph V78).
 */
class DeformationsLanguageV2ContractTest {

    private static final List<String> CANONICAL_IDS = List.of(
        "transform.deformations.twist",
        "transform.deformations.bend",
        "transform.deformations.taper",
        "transform.deformations.shear_point_list",
        "transform.deformations.noise_displace",
        "transform.deformations.spherical_displace",
        "transform.deformations.curve_attract",
        "transform.deformations.relax_points",
        "transform.deformations.lattice_deform",
        "transform.deformations.twist_sdf",
        "transform.deformations.bend_sdf"
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
    void currentGraphFormatIsAtLeastV78() {
        assertEquals(78, GraphFormatVersion.V78);
        assertTrue(GraphFormatVersion.CURRENT >= GraphFormatVersion.V78);
    }

    @Test
    void exactlyElevenNodesWithUniqueOrdersZeroToTen() {
        List<String> ids = registry.getAllNodeIds().stream()
            .filter(id -> id.startsWith("transform.deformations."))
            .sorted()
            .toList();
        assertEquals(11, ids.size());
        assertEquals(Set.copyOf(CANONICAL_IDS), Set.copyOf(ids));
        assertFalse(ids.contains("transform.deformations.twist_geometry"));
        assertFalse(ids.contains("transform.deformations.bend_geometry"));

        Set<Integer> orders = new HashSet<>();
        for (String id : ids) {
            NodeInfo info = registry.getNodeInfo(id);
            assertNotNull(info);
            assertEquals("transform.deformations", info.getCategoryId());
            assertTrue(orders.add(info.getOrder()), "duplicate order for " + id);
            INode node = registry.createNodeInstance(id);
            assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(node.getClass(), id));
        }
        for (int i = 0; i < 11; i++) {
            assertTrue(orders.contains(i), "missing order " + i);
        }
    }

    @Test
    void allDeformationNodesExposeValidAndError() {
        for (String id : CANONICAL_IDS) {
            INode node = registry.createNodeInstance(id);
            assertTrue(hasPort(node, "output_valid"), id);
            assertTrue(hasPort(node, "output_error"), id);
        }
    }

    @Test
    void twistSdfHasNoGeometryPorts() {
        INode node = registry.createNodeInstance("transform.deformations.twist_sdf");
        assertFalse(hasPort(node, "input_geometry"));
        assertFalse(hasPort(node, "output_geometry"));
        assertFalse(hasPort(node, "input_iso"));
        assertFalse(hasPort(node, "output_approximate"));
        assertFalse(hasPort(node, "output_source_voxels"));
        assertPortType("transform.deformations.twist_sdf", "input_sdf", true, NodeDataType.SDF);
        assertPortType("transform.deformations.twist_sdf", "output_sdf", false, NodeDataType.SDF);
    }

    @Test
    void twistPointListOversizedInputFailsWithError() {
        BaseNode twist = node("transform.deformations.twist");
        twist.setInput("input_points", oversizedPointList());
        twist.setInput("input_axis_origin", new PointData(0, 0, 0));
        twist.setInput("input_axis_direction", new Vector3d(0, 1, 0));
        twist.processNode(null);
        assertEquals(Boolean.FALSE, twist.getOutput("output_valid"));
        assertFalse(String.valueOf(twist.getOutput("output_error")).isBlank());
        assertEquals(0, twist.getOutput("output_count"));
    }

    @Test
    void twistSdfRequiresSdfInput() {
        TwistSdfProbe probe = new TwistSdfProbe();
        probe.connectInput("input_sdf", NodeDataType.SDF);
        probe.putRawInput("input_sdf", null);
        probe.setInput("input_axis_origin", new PointData(0, 0, 0));
        probe.setInput("input_axis_direction", new Vector3d(0, 1, 0));
        probe.processNode(null);
        assertEquals(Boolean.FALSE, probe.getOutput("output_valid"));
        assertTrue(String.valueOf(probe.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("sdf"));
        assertNull(probe.getOutput("output_sdf"));
    }

    @Test
    void twistSdfHalfConnectedBoundsFailsClosed() {
        SignedDistanceFieldData sdf = point -> point.length() - 2.0d;

        TwistSdfProbe minOnly = new TwistSdfProbe();
        minOnly.setInput("input_sdf", sdf);
        minOnly.setInput("input_axis_origin", new PointData(0, 0, 0));
        minOnly.setInput("input_axis_direction", new Vector3d(0, 1, 0));
        minOnly.connectInput("input_bounds_min", NodeDataType.POINT);
        minOnly.putRawInput("input_bounds_min", new PointData(0, 0, 0));
        minOnly.processNode(null);
        assertEquals(Boolean.FALSE, minOnly.getOutput("output_valid"));
        assertTrue(String.valueOf(minOnly.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("bounds"));
        assertNull(minOnly.getOutput("output_sdf"));
    }

    @Test
    void pathAttractWorkloadBudgetExceededFailsClosed() {
        BaseNode attract = node("transform.deformations.curve_attract");
        int points = (int) Math.min(GenerationLimits.MAX_LIST_ELEMENTS, 2048);
        int pathVerts = (int) (GenerationLimits.MAX_DEFORMATION_PATH_WORK / points + 2);
        List<PointData> pts = new ArrayList<>();
        for (int i = 0; i < points; i++) {
            pts.add(new PointData(i, 0, 0));
        }
        List<Vec3d> path = new ArrayList<>();
        for (int i = 0; i < pathVerts; i++) {
            path.add(new Vec3d(i, 0, 0));
        }
        attract.setInput("input_points", pts);
        attract.setInput("input_path", PathData.fromPolyline(new PolylineData(path)));
        attract.setNodeState(Map.of("strength", 0.5d, "radius", 100.0d));
        attract.processNode(null);
        assertEquals(Boolean.FALSE, attract.getOutput("output_valid"));
        assertTrue(String.valueOf(attract.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("budget"));
    }

    @Test
    void pathAttractTangentialWithZeroTangentFailsClosed() {
        BaseNode attract = node("transform.deformations.curve_attract");
        attract.setInput("input_points", List.of(new PointData(1, 0, 0)));
        attract.setInput("input_path", PathData.fromPolyline(new PolylineData(List.of(
            new Vec3d(0, 0, 0),
            new Vec3d(0, 0, 0)
        ))));
        attract.setNodeState(Map.of("displacementMode", "TANGENTIAL", "strength", 1.0d, "radius", 10.0d));
        attract.processNode(null);
        assertEquals(Boolean.FALSE, attract.getOutput("output_valid"));
        assertFalse(String.valueOf(attract.getOutput("output_error")).isBlank());
    }

    @Test
    void migrateV77ToV78RenamesSdfNodesAndDropsGeometryWires() {
        SavedGraph graph = new SavedGraph();
        graph.formatVersion = GraphFormatVersion.V77;
        graph.nodes = new ArrayList<>();
        graph.connections = new ArrayList<>();

        SavedNode twist = savedNode("n1", "transform.deformations.twist_geometry");
        Map<String, Object> twistState = new HashMap<>();
        twistState.put("fillSourceGeometry", true);
        twist.state = twistState;

        SavedNode sinkSdf = savedNode("n2", "reference.vectors.vector");
        SavedNode sinkGeo = savedNode("n3", "reference.vectors.vector");
        graph.nodes.addAll(List.of(twist, sinkSdf, sinkGeo));

        graph.connections.add(wire("n1", "input_sdf", "n2", "output_x"));
        graph.connections.add(wire("n1", "input_geometry", "n3", "output_x"));
        graph.connections.add(wire("n1", "output_geometry", "n3", "input_x"));
        graph.connections.add(wire("n1", "output_sdf", "n2", "input_x"));

        SavedGraph migrated = GraphMigrationRegistry.migrateToCurrent(graph);
        assertEquals(GraphFormatVersion.CURRENT, migrated.formatVersion);
        assertEquals("transform.deformations.twist_sdf", typeOf(migrated, "n1"));

        assertTrue(migrated.connections.stream().anyMatch(c ->
            "n1".equals(c.sourceNodeId) && "output_sdf".equals(c.sourcePortId)));
        assertFalse(migrated.connections.stream().anyMatch(c ->
            "input_geometry".equals(c.targetPortId) || "output_geometry".equals(c.sourcePortId)));

        Object migratedTwistState = nodeOf(migrated, "n1").state;
        if (migratedTwistState instanceof Map<?, ?> cleaned) {
            assertFalse(cleaned.containsKey("fillSourceGeometry"));
        } else {
            assertNull(migratedTwistState);
        }
    }

    private static List<PointData> oversizedPointList() {
        return new AbstractList<>() {
            @Override
            public PointData get(int index) {
                return new PointData(index, 0, 0);
            }

            @Override
            public int size() {
                return GenerationLimits.MAX_LIST_ELEMENTS + 1;
            }
        };
    }

    private static BaseNode node(String typeId) {
        return assertInstanceOf(BaseNode.class, registry.createNodeInstance(typeId));
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

    private static String typeOf(SavedGraph graph, String nodeId) {
        return nodeOf(graph, nodeId).typeId;
    }

    private static SavedNode nodeOf(SavedGraph graph, String nodeId) {
        return graph.nodes.stream()
            .filter(n -> nodeId.equals(n.nodeId))
            .findFirst()
            .orElseThrow();
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

    private static final class TwistSdfProbe extends TwistSdfNode {
        void putRawInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            DeformationsLanguageV2ContractTest.connectInput(this, portId, outputType);
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

package com.nodecraft.nodesystem.contract;

import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.runtime.NodeEffectResolver;
import com.nodecraft.nodesystem.graph.GraphMigrationRegistry;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedConnection;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import com.nodecraft.nodesystem.nodes.geometry.primitives.CapsuleByAxisRadiusNode;
import com.nodecraft.nodesystem.nodes.geometry.primitives.ConeByBaseApexRadiusNode;
import com.nodecraft.nodesystem.nodes.geometry.primitives.CylinderByAxisRadiusNode;
import com.nodecraft.nodesystem.nodes.geometry.primitives.FrustumByTwoCentersRadiiNode;
import com.nodecraft.nodesystem.nodes.geometry.primitives.SphereByCenterRadiusNode;
import com.nodecraft.nodesystem.nodes.geometry.primitives.SphereByDiameterNode;
import com.nodecraft.nodesystem.nodes.geometry.primitives.TorusByCenterAxisRadiiNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.PrimitiveGeometryValidator;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Language fence for Primitive Geometry Language v1 (Graph V74).
 */
class GeometryPrimitivesLanguageContractTest {

    private static NodeRegistry registry;

    @BeforeAll
    static void init() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void currentGraphFormatIsAtLeastV74() {
        assertEquals(74, GraphFormatVersion.V74);
        assertTrue(GraphFormatVersion.CURRENT >= GraphFormatVersion.V74);
    }

    @Test
    void v74CanonicalTwentyNineNodesRemainAtOrdersZeroToTwentyEight() {
        // Graph V90 adds deconstruct_torus (29) and deconstruct_capsule (30); V74 fence covers orders 0–28.
        List<String> ids = registry.getAllNodeIds().stream()
            .filter(id -> id.startsWith("geometry.primitives."))
            .sorted()
            .toList();
        assertTrue(ids.size() >= 29);

        Set<Integer> orders = new HashSet<>();
        for (String id : ids) {
            NodeInfo info = registry.getNodeInfo(id);
            assertNotNull(info);
            assertEquals("geometry.primitives", info.getCategoryId());
            assertTrue(orders.add(info.getOrder()), "duplicate order for " + id);
            INode node = registry.createNodeInstance(id);
            assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(node.getClass(), id));
        }
        for (int i = 0; i < 29; i++) {
            assertTrue(orders.contains(i), "missing order " + i);
        }
    }

    @Test
    void constructorAndDeconstructOrdersMatchLanguageTable() {
        assertOrder("geometry.primitives.box", 0);
        assertOrder("geometry.primitives.box_from_corner_size", 1);
        assertOrder("geometry.primitives.box_from_corners", 2);
        assertOrder("geometry.primitives.sphere", 3);
        assertOrder("geometry.primitives.sphere_from_diameter", 4);
        assertOrder("geometry.primitives.cylinder", 5);
        assertOrder("geometry.primitives.cone", 6);
        assertOrder("geometry.primitives.frustum_cone", 7);
        assertOrder("geometry.primitives.capsule", 8);
        assertOrder("geometry.primitives.hemisphere", 9);
        assertOrder("geometry.primitives.torus", 10);
        assertOrder("geometry.primitives.ellipsoid", 11);
        assertOrder("geometry.primitives.square_pyramid", 12);
        assertOrder("geometry.primitives.tetrahedron", 13);
        assertOrder("geometry.primitives.octahedron", 14);
        assertOrder("geometry.primitives.icosahedron", 15);
        assertOrder("geometry.primitives.dodecahedron", 16);
        assertOrder("geometry.primitives.deconstruct_box", 17);
        assertOrder("geometry.primitives.deconstruct_sphere", 18);
        assertOrder("geometry.primitives.deconstruct_cylinder", 19);
        assertOrder("geometry.primitives.deconstruct_cone", 20);
        assertOrder("geometry.primitives.deconstruct_frustum_cone", 21);
        assertOrder("geometry.primitives.deconstruct_hemisphere", 22);
        assertOrder("geometry.primitives.deconstruct_ellipsoid", 23);
        assertOrder("geometry.primitives.deconstruct_prism", 24);
        assertOrder("geometry.primitives.deconstruct_tetrahedron", 25);
        assertOrder("geometry.primitives.deconstruct_octahedron", 26);
        assertOrder("geometry.primitives.deconstruct_icosahedron", 27);
        assertOrder("geometry.primitives.deconstruct_dodecahedron", 28);
    }

    @Test
    void allPrimitiveNodesHaveValidAndErrorWithoutBannedPortTypes() {
        List<String> errors = new ArrayList<>();
        for (String id : registry.getAllNodeIds().stream()
            .filter(s -> s.startsWith("geometry.primitives."))
            .toList()) {
            INode node = registry.createNodeInstance(id);
            if (!hasPort(node, "output_valid")) {
                errors.add(id + " missing output_valid");
            }
            if (!hasPort(node, "output_error")) {
                errors.add(id + " missing output_error");
            }
            for (IPort port : node.getInputPorts()) {
                banPortType(errors, id, port);
            }
            for (IPort port : node.getOutputPorts()) {
                banPortType(errors, id, port);
            }
        }
        assertTrue(errors.isEmpty(), String.join(System.lineSeparator(), errors));
    }

    @Test
    void boxFaceListExistsAndBoxPortsAreContinuousOnly() {
        assertEquals(NodeDataType.BOX_FACE_LIST, NodeDataType.forListElementKind(
            com.nodecraft.nodesystem.api.ListElementKind.BOX_FACE));
        assertPortType("geometry.primitives.box", "output_faces", false, NodeDataType.BOX_FACE_LIST);
        assertPortType("geometry.primitives.deconstruct_box", "output_faces", false, NodeDataType.BOX_FACE_LIST);
        assertPortType("geometry.primitives.deconstruct_box", "output_corner_names", false, NodeDataType.STRING_LIST);
        assertPortType("geometry.primitives.deconstruct_box", "output_face_names", false, NodeDataType.STRING_LIST);

        INode box = registry.createNodeInstance("geometry.primitives.box");
        assertFalse(hasPort(box, "output_box_blocks"));
        assertFalse(hasPort(box, "output_region"));
        assertFalse(hasPort(box, "output_min_corner"));
        assertFalse(hasPort(box, "output_max_corner"));
        assertFalse(hasPort(box, "output_count"));
    }

    @Test
    void axisAndDiameterOutputsArePath() {
        assertPortType("geometry.primitives.cylinder", "output_axis_path", false, NodeDataType.PATH);
        assertPortType("geometry.primitives.cone", "output_axis_path", false, NodeDataType.PATH);
        assertPortType("geometry.primitives.frustum_cone", "output_axis_path", false, NodeDataType.PATH);
        assertPortType("geometry.primitives.capsule", "output_axis_path", false, NodeDataType.PATH);
        assertPortType("geometry.primitives.sphere_from_diameter", "output_diameter_path", false, NodeDataType.PATH);
        assertFalse(hasPort(registry.createNodeInstance("geometry.primitives.cylinder"), "output_axis_line"));
        assertFalse(hasPort(registry.createNodeInstance("geometry.primitives.sphere_from_diameter"), "output_diameter_line"));
    }

    @Test
    void capsuleZeroAxisFailsClosed() {
        CapsuleByAxisRadiusNode capsule = new CapsuleByAxisRadiusNode();
        connectInput(capsule, "input_start", NodeDataType.POINT);
        connectInput(capsule, "input_end", NodeDataType.POINT);
        connectInput(capsule, "input_radius", NodeDataType.DOUBLE);
        capsule.setInput("input_start", new PointData(1, 2, 3));
        capsule.setInput("input_end", new PointData(1, 2, 3));
        capsule.setInput("input_radius", 2.0d);
        capsule.processNode(null);
        assertEquals(Boolean.FALSE, capsule.getOutput("output_valid"));
        assertTrue(String.valueOf(capsule.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("axis"));
        assertNull(capsule.getOutput("output_geometry"));
    }

    @Test
    void sphereCoincidentDiameterFailsClosed() {
        SphereByDiameterNode sphere = new SphereByDiameterNode();
        connectInput(sphere, "input_start", NodeDataType.POINT);
        connectInput(sphere, "input_end", NodeDataType.POINT);
        sphere.setInput("input_start", new PointData(0, 0, 0));
        sphere.setInput("input_end", new PointData(0, 0, 0));
        sphere.processNode(null);
        assertEquals(Boolean.FALSE, sphere.getOutput("output_valid"));
        assertNotNull(sphere.getOutput("output_error"));
    }

    @Test
    void cylinderConeFrustumZeroHeightFailsClosed() {
        CylinderByAxisRadiusNode cylinder = new CylinderByAxisRadiusNode();
        connectInput(cylinder, "input_start", NodeDataType.POINT);
        connectInput(cylinder, "input_end", NodeDataType.POINT);
        cylinder.setInput("input_start", new PointData(0, 0, 0));
        cylinder.setInput("input_end", new PointData(0, 0, 0));
        cylinder.processNode(null);
        assertEquals(Boolean.FALSE, cylinder.getOutput("output_valid"));

        ConeByBaseApexRadiusNode cone = new ConeByBaseApexRadiusNode();
        connectInput(cone, "input_base_center", NodeDataType.POINT);
        connectInput(cone, "input_apex", NodeDataType.POINT);
        cone.setInput("input_base_center", new PointData(0, 0, 0));
        cone.setInput("input_apex", new PointData(0, 0, 0));
        cone.processNode(null);
        assertEquals(Boolean.FALSE, cone.getOutput("output_valid"));

        FrustumByTwoCentersRadiiNode frustum = new FrustumByTwoCentersRadiiNode();
        connectInput(frustum, "input_base_center", NodeDataType.POINT);
        connectInput(frustum, "input_top_center", NodeDataType.POINT);
        frustum.setInput("input_base_center", new PointData(0, 0, 0));
        frustum.setInput("input_top_center", new PointData(0, 0, 0));
        frustum.processNode(null);
        assertEquals(Boolean.FALSE, frustum.getOutput("output_valid"));
    }

    @Test
    void torusMinorNotLessThanMajorFailsClosed() {
        assertNotNull(PrimitiveGeometryValidator.validateRingTorus(
            new Vector3d(0, 0, 0), new Vector3d(0, 1, 0), 2.0d, 2.0d));
        assertNotNull(PrimitiveGeometryValidator.validateRingTorus(
            new Vector3d(0, 0, 0), new Vector3d(0, 1, 0), 2.0d, 3.0d));

        TorusByCenterAxisRadiiNode torus = new TorusByCenterAxisRadiiNode();
        connectInput(torus, "input_major_radius", NodeDataType.DOUBLE);
        connectInput(torus, "input_minor_radius", NodeDataType.DOUBLE);
        torus.setInput("input_major_radius", 2.0d);
        torus.setInput("input_minor_radius", 2.0d);
        torus.processNode(null);
        assertEquals(Boolean.FALSE, torus.getOutput("output_valid"));
        assertTrue(String.valueOf(torus.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("minor"));
    }

    @Test
    void connectedInvalidCenterFailsClosedWithoutPropertyWashout() {
        SphereByCenterRadiusNode sphere = new SphereByCenterRadiusNode();
        connectInput(sphere, "input_center", NodeDataType.POINT);
        sphere.setInput("input_center", "not-a-point");
        sphere.processNode(null);
        assertEquals(Boolean.FALSE, sphere.getOutput("output_valid"));
        assertTrue(String.valueOf(sphere.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("center"));
        assertNull(sphere.getOutput("output_sphere"));
    }

    @Test
    void pointPortsRejectLineDataImplicitly() {
        CapsuleByAxisRadiusNode capsule = new CapsuleByAxisRadiusNode();
        connectInput(capsule, "input_start", NodeDataType.POINT);
        connectInput(capsule, "input_end", NodeDataType.POINT);
        LineData line = new LineData(new Vec3d(0, 0, 0), new Vec3d(0, 5, 0));
        capsule.setInput("input_start", line);
        capsule.setInput("input_end", line);
        capsule.processNode(null);
        assertEquals(Boolean.FALSE, capsule.getOutput("output_valid"));
        assertNull(capsule.getOutput("output_geometry"));
    }

    @Test
    void migrateV73ToV74DropsBoxVoxelWiresAndRemapsPathPorts() {
        SavedGraph graph = new SavedGraph();
        graph.formatVersion = GraphFormatVersion.V73;

        SavedNode box = new SavedNode();
        box.nodeId = "box";
        box.typeId = "geometry.primitives.box";
        Map<String, Object> state = new HashMap<>();
        state.put("fillBox", false);
        state.put("outputAsRegion", true);
        state.put("sizeX", 3.0d);
        box.state = state;

        SavedNode cylinder = new SavedNode();
        cylinder.nodeId = "cyl";
        cylinder.typeId = "geometry.primitives.cylinder";

        SavedNode sinkBlocks = new SavedNode();
        sinkBlocks.nodeId = "sink_blocks";
        sinkBlocks.typeId = "utilities.passthrough.any";

        SavedNode sinkPath = new SavedNode();
        sinkPath.nodeId = "sink_path";
        sinkPath.typeId = "utilities.passthrough.any";

        graph.nodes = new ArrayList<>(List.of(box, cylinder, sinkBlocks, sinkPath));

        SavedConnection drop = new SavedConnection();
        drop.sourceNodeId = "box";
        drop.sourcePortId = "output_box_blocks";
        drop.targetNodeId = "sink_blocks";
        drop.targetPortId = "input";

        SavedConnection remap = new SavedConnection();
        remap.sourceNodeId = "cyl";
        remap.sourcePortId = "output_axis_line";
        remap.targetNodeId = "sink_path";
        remap.targetPortId = "input";

        SavedConnection keep = new SavedConnection();
        keep.sourceNodeId = "box";
        keep.sourcePortId = "output_geometry";
        keep.targetNodeId = "sink_path";
        keep.targetPortId = "input2";

        graph.connections = new ArrayList<>(List.of(drop, remap, keep));

        SavedGraph migrated = GraphMigrationRegistry.migrateToCurrent(graph);
        assertEquals(GraphFormatVersion.CURRENT, migrated.formatVersion);
        assertTrue(migrated.formatVersion >= GraphFormatVersion.V74);
        assertEquals(2, migrated.connections.size());

        boolean foundRemap = false;
        boolean foundKeep = false;
        for (SavedConnection c : migrated.connections) {
            assertFalse("output_box_blocks".equals(c.sourcePortId));
            if ("cyl".equals(c.sourceNodeId)) {
                assertEquals("output_axis_path", c.sourcePortId);
                foundRemap = true;
            }
            if ("box".equals(c.sourceNodeId) && "output_geometry".equals(c.sourcePortId)) {
                foundKeep = true;
            }
        }
        assertTrue(foundRemap);
        assertTrue(foundKeep);

        @SuppressWarnings("unchecked")
        Map<String, Object> migratedState = (Map<String, Object>) migrated.nodes.stream()
            .filter(n -> "box".equals(n.nodeId))
            .findFirst()
            .orElseThrow()
            .state;
        assertFalse(migratedState.containsKey("fillBox"));
        assertFalse(migratedState.containsKey("outputAsRegion"));
        assertEquals(3.0d, ((Number) migratedState.get("sizeX")).doubleValue(), 1e-9);
    }

    private static void assertOrder(String typeId, int expected) {
        NodeInfo info = registry.getNodeInfo(typeId);
        assertNotNull(info, typeId);
        assertEquals(expected, info.getOrder(), typeId);
    }

    private static void banPortType(List<String> errors, String id, IPort port) {
        if (port.getDataType() == NodeDataType.ANY) {
            errors.add(id + "." + port.getId() + " is ANY");
        }
        if (port.getDataType() == NodeDataType.LIST) {
            errors.add(id + "." + port.getId() + " uses raw LIST");
        }
        if (port.getDataType() == NodeDataType.LINE) {
            errors.add(id + "." + port.getId() + " uses graph LINE");
        }
        if (port.getDataType() == NodeDataType.POLYLINE) {
            errors.add(id + "." + port.getId() + " uses graph POLYLINE");
        }
    }

    private static void assertPortType(String typeId, String portId, boolean input, NodeDataType expected) {
        INode node = registry.createNodeInstance(typeId);
        assertNotNull(node, typeId);
        List<? extends IPort> ports = input ? node.getInputPorts() : node.getOutputPorts();
        IPort port = ports.stream().filter(p -> portId.equals(p.getId())).findFirst().orElse(null);
        assertNotNull(port, typeId + " missing port " + portId);
        assertEquals(expected, port.getDataType(), typeId + "." + portId);
    }

    private static boolean hasPort(INode node, String portId) {
        return node.getInputPorts().stream().anyMatch(p -> portId.equals(p.getId()))
            || node.getOutputPorts().stream().anyMatch(p -> portId.equals(p.getId()));
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

    private static final class PortStubNode extends BaseNode {
        PortStubNode(NodeDataType outputType) {
            super(UUID.randomUUID(), "test.port_stub");
            addOutputPort(new BasePort("output_stub", "Stub", "", outputType, this));
        }

        @Override
        public void processNode(com.nodecraft.nodesystem.execution.ExecutionContext context) {
        }
    }
}

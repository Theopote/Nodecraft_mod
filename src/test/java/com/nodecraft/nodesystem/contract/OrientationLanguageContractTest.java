package com.nodecraft.nodesystem.contract;

import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.datatypes.VectorData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.execution.runtime.NodeEffectResolver;
import com.nodecraft.nodesystem.graph.GraphMigrationRegistry;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedConnection;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
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
 * Language fence for Orientation Language v1 / Frame Handedness & Projected Path Validity v2 (Graph V99).
 */
class OrientationLanguageContractTest {

    private static final List<String> CANONICAL_IDS = List.of(
        "transform.orientation.project_to_plane",
        "transform.orientation.rotate_vector",
        "transform.orientation.align_to_surface",
        "transform.orientation.project_points_to_plane",
        "transform.orientation.project_path_to_plane",
        "transform.orientation.project_profile_to_plane"
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
    void currentGraphFormatIsAtLeastV77() {
        assertEquals(77, GraphFormatVersion.V77);
        assertEquals(99, GraphFormatVersion.V99);
        assertTrue(GraphFormatVersion.CURRENT >= GraphFormatVersion.V77);
        assertTrue(GraphFormatVersion.CURRENT >= GraphFormatVersion.V99);
    }

    @Test
    void exactlySixNodesWithUniqueOrdersZeroToFive() {
        List<String> ids = registry.getAllNodeIds().stream()
            .filter(id -> id.startsWith("transform.orientation."))
            .sorted()
            .toList();
        assertEquals(6, ids.size());
        assertEquals(Set.copyOf(CANONICAL_IDS), Set.copyOf(ids));
        assertFalse(ids.contains("transform.orientation.project_curve_to_plane"));

        Set<Integer> orders = new HashSet<>();
        for (String id : ids) {
            NodeInfo info = registry.getNodeInfo(id);
            assertNotNull(info);
            assertEquals("transform.orientation", info.getCategoryId());
            assertTrue(orders.add(info.getOrder()), "duplicate order for " + id);
            INode node = registry.createNodeInstance(id);
            assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(node.getClass(), id));
        }
        for (int i = 0; i < 6; i++) {
            assertTrue(orders.contains(i), "missing order " + i);
        }
    }

    @Test
    void allNodesHaveValidAndErrorWithoutBannedPortTypes() {
        List<String> errors = new ArrayList<>();
        for (String id : CANONICAL_IDS) {
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
    void projectPathUsesPathPortsOnly() {
        assertPortType("transform.orientation.project_path_to_plane", "input_path", true, NodeDataType.PATH);
        assertPortType("transform.orientation.project_path_to_plane", "output_path", false, NodeDataType.PATH);
        assertFalse(hasPort(registry.createNodeInstance("transform.orientation.project_path_to_plane"), "output_curve"));
        assertFalse(hasPort(registry.createNodeInstance("transform.orientation.project_path_to_plane"), "output_polyline"));
    }

    @Test
    void profileBoundaryIsPathAndDistancesAreDoubleList() {
        assertPortType("transform.orientation.project_profile_to_plane", "output_boundary", false, NodeDataType.PATH);
        assertPortType("transform.orientation.project_profile_to_plane", "output_distances", false, NodeDataType.DOUBLE_LIST);
        assertPortType("transform.orientation.project_points_to_plane", "output_distances", false, NodeDataType.DOUBLE_LIST);
        assertPortType("transform.orientation.project_points_to_plane", "output_signed_distances", false, NodeDataType.DOUBLE_LIST);
        assertPortType("transform.orientation.project_path_to_plane", "output_distances", false, NodeDataType.DOUBLE_LIST);
    }

    @Test
    void projectPointsStrictListRejectsInvalidEntry() {
        BaseNode node = node("transform.orientation.project_points_to_plane");
        node.setInput("input_plane", PlaneData.XZ_PLANE);
        List<Object> mixed = new ArrayList<>();
        mixed.add(new PointData(0, 0, 0));
        mixed.add("bad");
        node.setInput("input_points", mixed);
        node.processNode(null);
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertTrue(String.valueOf(node.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("invalid"));
        assertEquals(0, node.getOutput("output_count"));
    }

    @Test
    void projectPointsDoesNotSkipInvalidEntries() {
        BaseNode node = node("transform.orientation.project_points_to_plane");
        node.setInput("input_plane", PlaneData.XZ_PLANE);
        List<PointData> points = List.of(
            new PointData(0, 0, 0),
            new PointData(0, 5, 0),
            new PointData(0, 0, 0)
        );
        node.setInput("input_points", points);
        node.processNode(null);
        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));
        assertEquals(3, node.getOutput("output_count"));
        @SuppressWarnings("unchecked")
        List<Double> distances = (List<Double>) node.getOutput("output_distances");
        assertEquals(3, distances.size());
    }

    @Test
    void alignRequiresEqualLengthPointsAndNormals() {
        AlignProbe align = new AlignProbe();
        align.setInput("input_points", List.of(new PointData(0, 0, 0), new PointData(1, 0, 0)));
        align.setInput("input_normals", List.of(new VectorData(0, 1, 0)));
        align.processNode(null);
        assertEquals(Boolean.FALSE, align.getOutput("output_valid"));
        assertTrue(String.valueOf(align.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("equal length"));
    }

    @Test
    void alignForwardHintConnectedInvalidFailsClosed() {
        AlignProbe align = new AlignProbe();
        align.setInput("input_points", List.of(new PointData(0, 0, 0)));
        align.setInput("input_normals", List.of(new VectorData(0, 1, 0)));
        align.connectInput("input_forward_hint", NodeDataType.VECTOR);
        align.setInput("input_forward_hint", null);
        align.processNode(null);
        assertEquals(Boolean.FALSE, align.getOutput("output_valid"));
        assertTrue(String.valueOf(align.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("forward hint"));
    }

    @Test
    void alignForwardHintParallelToNormalFailsClosed() {
        AlignProbe align = new AlignProbe();
        align.setInput("input_points", List.of(new PointData(0, 0, 0)));
        align.setInput("input_normals", List.of(new VectorData(0, 1, 0)));
        align.connectInput("input_forward_hint", NodeDataType.VECTOR);
        align.setInput("input_forward_hint", new VectorData(0, 1, 0));
        align.processNode(null);
        assertEquals(Boolean.FALSE, align.getOutput("output_valid"));
        assertTrue(String.valueOf(align.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("tangent"));
    }

    @Test
    void alignYUpActuallyPointsAlongNormal() {
        AlignProbe align = new AlignProbe();
        align.setNodeState(Map.of("localUpAxis", "Y"));
        Vector3d normal = new Vector3d(0, 0, 1);
        runAlignWithHint(align, normal, new Vector3d(1, 0, 0));
        assertEquals(Boolean.TRUE, align.getOutput("output_valid"));
        FrameData frame = firstFrame(align);
        assertTrue(frame.getYAxis().dot(normal) > 0.999999d);
        assertRightHanded(frame);
        PlaneData plane = firstPlane(align);
        assertTrue(plane.getNormal().dot(frame.getYAxis()) > 0.999999d);
    }

    @Test
    void alignXUpActuallyPointsAlongNormal() {
        AlignProbe align = new AlignProbe();
        align.setNodeState(Map.of("localUpAxis", "X"));
        Vector3d normal = new Vector3d(0, 0, 1);
        runAlignWithHint(align, normal, new Vector3d(1, 0, 0));
        assertEquals(Boolean.TRUE, align.getOutput("output_valid"));
        FrameData frame = firstFrame(align);
        assertTrue(frame.getXAxis().dot(normal) > 0.999999d);
        assertRightHanded(frame);
    }

    @Test
    void alignZUpActuallyPointsAlongNormal() {
        AlignProbe align = new AlignProbe();
        align.setNodeState(Map.of("localUpAxis", "Z"));
        Vector3d normal = new Vector3d(0, 1, 0);
        runAlignWithHint(align, normal, new Vector3d(1, 0, 0));
        assertEquals(Boolean.TRUE, align.getOutput("output_valid"));
        FrameData frame = firstFrame(align);
        assertTrue(frame.getZAxis().dot(normal) > 0.999999d);
        assertRightHanded(frame);
    }

    @Test
    void projectPathZeroLengthAfterProjectionFailsClosed() {
        BaseNode project = node("transform.orientation.project_path_to_plane");
        project.setInput("input_path", PathData.fromLine(new LineData(new Vec3d(0, 0, 0), new Vec3d(0, 5, 0))));
        project.setInput("input_plane", PlaneData.XZ_PLANE);
        project.processNode(null);
        assertEquals(Boolean.FALSE, project.getOutput("output_valid"));
        assertNull(project.getOutput("output_path"));
    }

    @Test
    void projectPathLineInputEmitsLineWhenTwoPointsRemain() {
        BaseNode project = node("transform.orientation.project_path_to_plane");
        project.setInput("input_path", PathData.fromLine(new LineData(new Vec3d(0, 5, 0), new Vec3d(10, 5, 0))));
        project.setInput("input_plane", PlaneData.XZ_PLANE);
        project.processNode(null);
        assertEquals(Boolean.TRUE, project.getOutput("output_valid"));
        PathData path = assertInstanceOf(PathData.class, project.getOutput("output_path"));
        assertEquals(PathData.Kind.LINE, path.getKind());
        assertNotNull(path.getLine());
    }

    @Test
    void rotateVectorEmitsVectorData() {
        RotateProbe rotate = new RotateProbe();
        rotate.setInput("input_vector", new VectorData(1, 0, 0));
        rotate.setInput("input_axis", new VectorData(0, 1, 0));
        rotate.setInput("input_angle", 90.0d);
        rotate.processNode(null);
        assertEquals(Boolean.TRUE, rotate.getOutput("output_valid"));
        assertInstanceOf(VectorData.class, rotate.getOutput("output_rotated_vector"));
    }

    @Test
    void rotateAxisConnectedInvalidFailsClosed() {
        RotateProbe rotate = new RotateProbe();
        rotate.setInput("input_vector", new VectorData(1, 0, 0));
        rotate.connectInput("input_axis", NodeDataType.VECTOR);
        rotate.setInput("input_axis", "not-a-vector");
        rotate.processNode(null);
        assertEquals(Boolean.FALSE, rotate.getOutput("output_valid"));
        assertTrue(String.valueOf(rotate.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("axis"));
    }

    @Test
    void rotateAngleConnectedInvalidFailsClosed() {
        RotateProbe rotate = new RotateProbe();
        rotate.setInput("input_vector", new VectorData(1, 0, 0));
        rotate.connectInput("input_angle", NodeDataType.DOUBLE);
        rotate.setInput("input_angle", Double.NaN);
        rotate.processNode(null);
        assertEquals(Boolean.FALSE, rotate.getOutput("output_valid"));
        assertTrue(String.valueOf(rotate.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("angle"));
    }

    @Test
    void rotateVectorDescriptionUsesDegrees() {
        BaseNode rotate = node("transform.orientation.rotate_vector");
        String desc = rotate.getDescription().toLowerCase(Locale.ROOT);
        assertTrue(desc.contains("degrees"));
        assertFalse(desc.contains("radian"));
    }

    @Test
    void projectPathEmitsPathOutput() {
        BaseNode project = node("transform.orientation.project_path_to_plane");
        project.setInput("input_path", PathData.fromLine(new LineData(new Vec3d(0, 5, 0), new Vec3d(10, 5, 0))));
        project.setInput("input_plane", PlaneData.XZ_PLANE);
        project.processNode(null);
        assertEquals(Boolean.TRUE, project.getOutput("output_valid"));
        assertInstanceOf(PathData.class, project.getOutput("output_path"));
    }

    @Test
    void projectProfileDegeneracyFailsClosed() {
        BaseNode project = node("transform.orientation.project_profile_to_plane");
        project.setInput("input_profile", verticalSquareProfile());
        project.setInput("input_plane", PlaneData.XZ_PLANE);
        project.processNode(null);
        assertEquals(Boolean.FALSE, project.getOutput("output_valid"));
        assertNull(project.getOutput("output_profile"));
    }

    @Test
    void projectPointsOversizedListFailsClosed() {
        BaseNode node = node("transform.orientation.project_points_to_plane");
        node.setInput("input_plane", PlaneData.XZ_PLANE);
        node.setInput("input_points", oversizedPointList());
        node.processNode(null);
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertTrue(String.valueOf(node.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("max_list_elements"));
    }

    @Test
    void migrateV76ToV77RemapsProjectCurveNodeAndPortsOnly() {
        SavedGraph graph = new SavedGraph();
        graph.formatVersion = GraphFormatVersion.V76;
        graph.nodes = new ArrayList<>();
        graph.connections = new ArrayList<>();

        SavedNode curve = savedNode("n1", "transform.orientation.project_curve_to_plane");
        Map<String, Object> curveState = new HashMap<>();
        curveState.put("sampleCurve", false);
        curve.state = curveState;

        SavedNode align = savedNode("n2", "transform.orientation.align_to_surface");
        Map<String, Object> alignState = new HashMap<>();
        alignState.put("useShortestList", true);
        align.state = alignState;

        SavedNode material = savedNode("n3", "material.basic_assignment.assign_block_type");
        SavedNode sink = savedNode("n4", "reference.vectors.vector");
        graph.nodes.addAll(List.of(curve, align, material, sink));

        graph.connections.add(wire("n1", "output_curve", "n4", "input_x"));
        graph.connections.add(wire("n1", "output_polyline", "n4", "input_x"));
        graph.connections.add(wire("n4", "output_z", "n3", "input_coordinates"));

        SavedGraph migrated = GraphMigrationRegistry.migrateToCurrent(graph);
        assertEquals(GraphFormatVersion.CURRENT, migrated.formatVersion);
        assertEquals("transform.orientation.project_path_to_plane", typeOf(migrated, "n1"));

        long pathOutputs = migrated.connections.stream()
            .filter(c -> "n1".equals(c.sourceNodeId))
            .filter(c -> "output_path".equals(c.sourcePortId))
            .count();
        assertEquals(1, pathOutputs);

        assertEquals("input_coordinates", migrated.connections.stream()
            .filter(c -> "n3".equals(c.targetNodeId)).findFirst().orElseThrow().targetPortId);

        @SuppressWarnings("unchecked")
        Map<String, Object> cleanedCurve = (Map<String, Object>) nodeOf(migrated, "n1").state;
        assertFalse(cleanedCurve.containsKey("sampleCurve"));

        @SuppressWarnings("unchecked")
        Map<String, Object> cleanedAlign = (Map<String, Object>) nodeOf(migrated, "n2").state;
        assertFalse(cleanedAlign.containsKey("useShortestList"));
    }

    private static PolygonProfileData verticalSquareProfile() {
        List<Vector3d> closed = List.of(
            new Vector3d(0, 0, 0),
            new Vector3d(1, 0, 0),
            new Vector3d(1, 1, 0),
            new Vector3d(0, 1, 0),
            new Vector3d(0, 0, 0)
        );
        return new PolygonProfileData(closed, PlaneData.XY_PLANE);
    }

    private static void runAlignWithHint(AlignProbe align, Vector3d normal, Vector3d forwardHint) {
        align.setInput("input_points", List.of(new PointData(0, 0, 0)));
        align.setInput("input_normals", List.of(new VectorData(normal.x, normal.y, normal.z)));
        align.connectInput("input_forward_hint", NodeDataType.VECTOR);
        align.setInput("input_forward_hint", new VectorData(forwardHint.x, forwardHint.y, forwardHint.z));
        align.processNode(null);
    }

    @SuppressWarnings("unchecked")
    private static FrameData firstFrame(AlignProbe align) {
        List<FrameData> frames = (List<FrameData>) align.getOutput("output_frames");
        assertNotNull(frames);
        assertFalse(frames.isEmpty());
        return frames.getFirst();
    }

    @SuppressWarnings("unchecked")
    private static PlaneData firstPlane(AlignProbe align) {
        List<PlaneData> planes = (List<PlaneData>) align.getOutput("output_planes");
        assertNotNull(planes);
        assertFalse(planes.isEmpty());
        return planes.getFirst();
    }

    private static void assertRightHanded(FrameData frame) {
        Vector3d cross = new Vector3d(frame.getXAxis()).cross(frame.getYAxis());
        assertEquals(1.0d, cross.dot(frame.getZAxis()), 1.0e-6d);
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

    private static void banPortType(List<String> errors, String nodeId, IPort port) {
        NodeDataType type = port.getDataType();
        if (type == NodeDataType.ANY
            || type == NodeDataType.LIST
            || type == NodeDataType.LINE
            || type == NodeDataType.POLYLINE
            || type == NodeDataType.CURVE) {
            errors.add(nodeId + " port " + port.getId() + " uses banned type " + type);
        }
    }

    private static SavedNode savedNode(String id, String typeId) {
        SavedNode node = new SavedNode();
        node.nodeId = id;
        node.typeId = typeId;
        return node;
    }

    private static SavedConnection wire(String src, String srcPort, String dst, String dstPort) {
        SavedConnection c = new SavedConnection();
        c.sourceNodeId = src;
        c.sourcePortId = srcPort;
        c.targetNodeId = dst;
        c.targetPortId = dstPort;
        return c;
    }

    private static String typeOf(SavedGraph graph, String nodeId) {
        return nodeOf(graph, nodeId).typeId;
    }

    private static SavedNode nodeOf(SavedGraph graph, String nodeId) {
        return graph.nodes.stream()
            .filter(node -> nodeId.equals(node.nodeId))
            .findFirst()
            .orElseThrow();
    }

    private static void connectInput(BaseNode target, String inputPortId, NodeDataType outputType) {
        PortStubNode stub = new PortStubNode(outputType);
        BasePort output = (BasePort) stub.getOutputPorts().getFirst();
        BasePort input = (BasePort) target.getInputPorts().stream()
            .filter(port -> inputPortId.equals(port.getId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError(target.getTypeId() + " missing port " + inputPortId));
        assertTrue(output.connectTo(input), inputPortId + " connect failed");
    }

    private static final class AlignProbe
            extends com.nodecraft.nodesystem.nodes.transform.orientation.AlignPointsToSurfaceNormalsNode {
        void connectInput(String portId, NodeDataType outputType) {
            OrientationLanguageContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class RotateProbe
            extends com.nodecraft.nodesystem.nodes.transform.orientation.RotateVectorNode {
        void connectInput(String portId, NodeDataType outputType) {
            OrientationLanguageContractTest.connectInput(this, portId, outputType);
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

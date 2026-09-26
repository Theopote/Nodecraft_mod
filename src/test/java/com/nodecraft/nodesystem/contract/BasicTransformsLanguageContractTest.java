package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.SphereData;
import com.nodecraft.nodesystem.datatypes.VectorData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.graph.GraphMigrationRegistry;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedConnection;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import com.nodecraft.nodesystem.nodes.transform.basic_transforms.MoveGeometryNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.GeometryTransform;
import com.nodecraft.nodesystem.util.VectorUtils;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Basic Transforms v1 language fence (Graph V52).
 */
class BasicTransformsLanguageContractTest {

    private static final Set<String> CANONICAL_IDS = Set.of(
            "transform.basic_transforms.move_geometry",
            "transform.basic_transforms.rotate_geometry_axis",
            "transform.basic_transforms.scale_geometry_point",
            "transform.basic_transforms.transform_geometry",
            "transform.basic_transforms.mirror_geometry_plane",
            "transform.basic_transforms.mirror_point_list_plane",
            "transform.basic_transforms.transform_by_frames",
            "transform.basic_transforms.offset_face",
            "transform.basic_transforms.inset_face"
    );

    private static final Set<String> PLACEMENT_BLOCK_IDS = Set.of(
            "transform.placement.offset_coordinate",
            "transform.placement.offset_coordinates",
            "transform.placement.rotate_coordinates",
            "transform.placement.scale_coordinates",
            "transform.placement.mirror_coordinates"
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
    void basicTransformsFreezeVersionIsV52() {
        assertEquals(52, GraphFormatVersion.V52);
    }

    @Test
    void exactlyNineCanonicalBasicTransformNodesRegistered() {
        List<String> ids = registry.getAllNodeIds().stream()
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("transform.basic_transforms."))
                .sorted()
                .toList();
        assertEquals(9, ids.size(), "Expected 9 basic_transforms nodes: " + ids);
        assertEquals(CANONICAL_IDS, Set.copyOf(ids));
        assertFalse(registry.getAllNodeIds().contains("transform.basic_transforms.mirror_vector_list_plane"));
        assertFalse(registry.getAllNodeIds().contains("transform.basic_transforms.shear"));
        assertFalse(registry.getAllNodeIds().contains("transform.basic_transforms.offset_coordinate"));
    }

    @Test
    void basicTransformNodesHaveUniqueOrderZeroThroughEight() {
        int[] orders = CANONICAL_IDS.stream()
                .mapToInt(typeId -> {
                    INode node = registry.createNodeInstance(typeId);
                    assertNotNull(node, typeId);
                    NodeInfo info = node.getClass().getAnnotation(NodeInfo.class);
                    assertNotNull(info, typeId);
                    return info.order();
                })
                .sorted()
                .toArray();
        assertEquals(9, orders.length);
        for (int i = 0; i < orders.length; i++) {
            assertEquals(i, orders[i], "Expected unique order " + i);
        }
    }

    @Test
    void blockGridNodesLiveInPlacementFamily() {
        for (String typeId : PLACEMENT_BLOCK_IDS) {
            assertNotNull(registry.createNodeInstance(typeId), typeId);
            assertFalse(typeId.startsWith("transform.basic_transforms."));
        }
        assertNotNull(registry.createNodeInstance("transform.deformations.shear_point_list"));
    }

    @Test
    void compositeTransformFailsClosedWhenAnyChildFails() {
        BoxGeometryData a = new BoxGeometryData(new Vector3d(2, 2, 2), new Vector3d(1, 1, 1));
        GeometryData unsupported = new GeometryData() {};
        CompositeGeometryData composite = new CompositeGeometryData(List.of(a, unsupported));
        assertNull(GeometryTransform.transform(composite, new Vector3d(1, 0, 0), 0, 0, 0, 1));
    }

    @Test
    void moveGeometryConnectedInvalidTranslationFailsClosed() {
        MoveGeometryProbe move = new MoveGeometryProbe();
        move.setX(5.0d);
        move.setY(0.0d);
        move.setZ(0.0d);
        move.setInput("input_geometry", new SphereData(new Vector3d(), 1.0d));
        move.connectInput("input_translation", NodeDataType.VECTOR);
        move.putRawInput("input_translation", null);
        move.processNode(null);
        assertEquals(Boolean.FALSE, move.getOutput("output_valid"));
        assertNull(move.getOutput("output_geometry"));
        assertNull(move.getOutput("output_effective_translation"));
    }

    @Test
    void moveGeometryUnconnectedUsesPropertyFallback() {
        MoveGeometryProbe move = new MoveGeometryProbe();
        move.setX(3.0d);
        move.setY(0.0d);
        move.setZ(0.0d);
        move.setInput("input_geometry", new SphereData(new Vector3d(), 1.0d));
        move.processNode(null);
        assertEquals(Boolean.TRUE, move.getOutput("output_valid"));
        assertNotNull(move.getOutput("output_geometry"));
        Vector3d effective = VectorUtils.toVector(move.getOutput("output_effective_translation"));
        assertNotNull(effective);
        assertEquals(3.0d, effective.x, 1.0e-9d);
        assertInstanceOf(VectorData.class, move.getOutput("output_effective_translation"));
    }

    @Test
    void scaleRejectsNonPositive() {
        ScaleGeometryProbe scale = new ScaleGeometryProbe();
        scale.setInput("input_geometry", new SphereData(new Vector3d(10, 0, 0), 2.0d));
        scale.setInput("input_center", new PointData(new Vector3d(10, 0, 0)));
        scale.connectInput("input_scale", NodeDataType.DOUBLE);
        scale.putRawInput("input_scale", 0.0d);
        scale.processNode(null);
        assertEquals(Boolean.FALSE, scale.getOutput("output_valid"));
        assertNull(scale.getOutput("output_geometry"));
    }

    @Test
    void mirrorPointListStrictRejectsMixedList() {
        BaseNode mirror = node("transform.basic_transforms.mirror_point_list_plane");
        mirror.setInput("input_points", List.of(new PointData(1, 0, 0), "not-a-point"));
        mirror.setInput("input_plane", new PlaneData(new Vector3d(), new Vector3d(1, 0, 0)));
        mirror.processNode(null);
        assertEquals(Boolean.FALSE, mirror.getOutput("output_valid"));
        assertEquals(0, mirror.getOutput("output_count"));
        assertFalse(hasOutputPort("transform.basic_transforms.mirror_point_list_plane", "output_skipped_count"));
    }

    @Test
    void faceNodesAreSlimFacePlusValid() {
        assertPortType("transform.basic_transforms.offset_face", "output_face", false, NodeDataType.BOX_FACE);
        assertPortType("transform.basic_transforms.offset_face", "output_valid", false, NodeDataType.BOOLEAN);
        assertFalse(hasOutputPort("transform.basic_transforms.offset_face", "output_center"));
        assertFalse(hasOutputPort("transform.basic_transforms.offset_face", "output_normal"));
        assertFalse(hasOutputPort("transform.basic_transforms.offset_face", "output_polyline"));

        assertPortType("transform.basic_transforms.inset_face", "output_face", false, NodeDataType.BOX_FACE);
        assertPortType("transform.basic_transforms.inset_face", "output_valid", false, NodeDataType.BOOLEAN);
        assertFalse(hasOutputPort("transform.basic_transforms.inset_face", "output_effective_distance"));
        assertFalse(hasOutputPort("transform.basic_transforms.inset_face", "output_edges"));
    }

    @Test
    void offsetFaceRequiresFiniteDistance() {
        BaseNode offset = node("transform.basic_transforms.offset_face");
        offset.setInput("input_face", unitFace());
        offset.processNode(null);
        assertEquals(Boolean.FALSE, offset.getOutput("output_valid"));
        assertNull(offset.getOutput("output_face"));
    }

    @Test
    void insetFaceRejectsTooLargePositiveInset() {
        BaseNode inset = node("transform.basic_transforms.inset_face");
        inset.setInput("input_face", unitFace());
        inset.setInput("input_distance", 10.0d);
        inset.processNode(null);
        assertEquals(Boolean.FALSE, inset.getOutput("output_valid"));
        assertNull(inset.getOutput("output_face"));
    }

    @Test
    void insetFaceRejectsNonFiniteCornersWithoutThrowing() {
        List<Vector3d> corners = List.of(
                new Vector3d(0, 0, 0),
                new Vector3d(Double.NaN, 0, 0),
                new Vector3d(2, 2, 0),
                new Vector3d(0, 2, 0)
        );
        BoxFaceData malformed = new BoxFaceData(
                0, "front", List.of(0, 1, 2, 3), corners, new Vector3d(1, 1, 0), new Vector3d(0, 0, 1));

        BaseNode inset = node("transform.basic_transforms.inset_face");
        inset.setInput("input_face", malformed);
        inset.setInput("input_distance", 0.1d);
        inset.processNode(null);
        assertEquals(Boolean.FALSE, inset.getOutput("output_valid"));
        assertNull(inset.getOutput("output_face"));
    }

    @Test
    void transformByFramesIsCartesianFrameMajor() {
        FrameData frame0 = new FrameData(
                new Vector3d(0, 0, 0),
                new Vector3d(1, 0, 0),
                new Vector3d(0, 1, 0),
                new Vector3d(0, 0, 1)
        );
        FrameData frame1 = new FrameData(
                new Vector3d(10, 0, 0),
                new Vector3d(1, 0, 0),
                new Vector3d(0, 1, 0),
                new Vector3d(0, 0, 1)
        );
        BaseNode transform = node("transform.basic_transforms.transform_by_frames");
        transform.setInput("input_local_points", List.of(
                new PointData(1, 0, 0),
                new PointData(0, 1, 0)
        ));
        transform.setInput("input_frames", List.of(frame0, frame1));
        transform.processNode(null);

        assertEquals(Boolean.TRUE, transform.getOutput("output_valid"));
        assertEquals(4, transform.getOutput("output_count"));
        List<?> points = (List<?>) transform.getOutput("output_points");
        assertEquals(4, points.size());
        assertPointEquals(new Vector3d(1, 0, 0), assertInstanceOf(PointData.class, points.get(0)));
        assertPointEquals(new Vector3d(0, 1, 0), assertInstanceOf(PointData.class, points.get(1)));
        assertPointEquals(new Vector3d(11, 0, 0), assertInstanceOf(PointData.class, points.get(2)));
        assertPointEquals(new Vector3d(10, 1, 0), assertInstanceOf(PointData.class, points.get(3)));
    }

    @Test
    void migrateV51ToV52RemapsTypesAndDropsLegacyPorts() {
        SavedGraph graph = new SavedGraph();
        graph.formatVersion = GraphFormatVersion.V51;
        graph.nodes = new ArrayList<>();
        graph.connections = new ArrayList<>();

        SavedNode offsetCoord = savedNode("n1", "transform.basic_transforms.offset_coordinate");
        SavedNode shear = savedNode("n2", "transform.basic_transforms.shear");
        SavedNode mirror = savedNode("n3", "transform.basic_transforms.mirror_vector_list_plane");
        SavedNode face = savedNode("n4", "transform.basic_transforms.offset_face");
        SavedNode sink = savedNode("n5", "reference.vectors.vector");
        graph.nodes.addAll(List.of(offsetCoord, shear, mirror, face, sink));

        graph.connections.add(wire("n1", "output_x", "n5", "input_x"));
        graph.connections.add(wire("n2", "output_skipped_count", "n5", "input_y"));
        graph.connections.add(wire("n3", "output_skipped_count", "n5", "input_z"));
        graph.connections.add(wire("n4", "output_center", "n5", "input_x"));
        graph.connections.add(wire("n4", "output_valid", "n5", "input_y"));

        SavedGraph migrated = GraphMigrationRegistry.migrateToCurrent(graph);
        assertEquals(GraphFormatVersion.CURRENT, migrated.formatVersion);

        assertEquals("transform.placement.offset_coordinate", typeOf(migrated, "n1"));
        assertEquals("transform.deformations.shear_point_list", typeOf(migrated, "n2"));
        assertEquals("transform.basic_transforms.mirror_point_list_plane", typeOf(migrated, "n3"));
        assertEquals("transform.basic_transforms.offset_face", typeOf(migrated, "n4"));

        assertEquals(1, migrated.connections.size());
        SavedConnection kept = migrated.connections.getFirst();
        assertEquals("n4", kept.sourceNodeId);
        assertEquals("output_valid", kept.sourcePortId);
    }

    private static String typeOf(SavedGraph graph, String nodeId) {
        return graph.nodes.stream()
                .filter(node -> nodeId.equals(node.nodeId))
                .map(node -> node.typeId)
                .findFirst()
                .orElseThrow();
    }

    private static SavedNode savedNode(String id, String typeId) {
        SavedNode node = new SavedNode();
        node.nodeId = id;
        node.typeId = typeId;
        return node;
    }

    private static SavedConnection wire(String sourceNode, String sourcePort, String targetNode, String targetPort) {
        SavedConnection connection = new SavedConnection();
        connection.sourceNodeId = sourceNode;
        connection.sourcePortId = sourcePort;
        connection.targetNodeId = targetNode;
        connection.targetPortId = targetPort;
        return connection;
    }

    private static BoxFaceData unitFace() {
        List<Vector3d> corners = List.of(
                new Vector3d(0, 0, 0),
                new Vector3d(2, 0, 0),
                new Vector3d(2, 2, 0),
                new Vector3d(0, 2, 0)
        );
        return new BoxFaceData(0, "front", List.of(0, 1, 2, 3), corners, new Vector3d(1, 1, 0), new Vector3d(0, 0, 1));
    }

    private static void assertPointEquals(Vector3d expected, PointData actual) {
        assertEquals(expected.x, actual.position().x, 1.0e-9d);
        assertEquals(expected.y, actual.position().y, 1.0e-9d);
        assertEquals(expected.z, actual.position().z, 1.0e-9d);
    }

    private static BaseNode node(String typeId) {
        BaseNode created = (BaseNode) registry.createNodeInstance(typeId);
        assertNotNull(created, typeId);
        return created;
    }

    private static void assertPortType(String typeId, String portId, boolean input, NodeDataType expected) {
        INode created = registry.createNodeInstance(typeId);
        assertNotNull(created);
        IPort port = (input ? created.getInputPorts() : created.getOutputPorts()).stream()
                .filter(candidate -> candidate.getId().equals(portId))
                .findFirst()
                .orElseThrow(() -> new AssertionError(typeId + " missing port " + portId));
        assertEquals(expected, port.getDataType(), typeId + "." + portId);
    }

    private static boolean hasOutputPort(String typeId, String portId) {
        INode created = registry.createNodeInstance(typeId);
        return created.getOutputPorts().stream().anyMatch(port -> port.getId().equals(portId));
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

    private static final class MoveGeometryProbe extends MoveGeometryNode {
        void putRawInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            BasicTransformsLanguageContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class ScaleGeometryProbe
            extends com.nodecraft.nodesystem.nodes.transform.basic_transforms.ScaleGeometryAroundPointNode {
        void putRawInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            BasicTransformsLanguageContractTest.connectInput(this, portId, outputType);
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

package com.nodecraft.nodesystem.contract;

import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.VectorData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.execution.runtime.NodeEffectResolver;
import com.nodecraft.nodesystem.nodes.reference.planes.OffsetPlaneNode;
import com.nodecraft.nodesystem.nodes.reference.planes.PlaneSelectorNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.BoxFaceValidator;
import com.nodecraft.nodesystem.util.PlaneUtils;
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
 * Language fence for Reference Planes Language v2 (Graph V86).
 */
class ReferencePlanesLanguageV2ContractTest {

    private static final List<String> CANONICAL_IDS = List.of(
        "reference.planes.world_plane",
        "reference.planes.construct_plane",
        "reference.planes.plane_from_points",
        "reference.planes.box_face_plane",
        "reference.planes.offset_plane",
        "reference.planes.distance_point_to_plane",
        "reference.planes.deconstruct_plane"
    );

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
    void exactlySevenNodesWithUniqueOrdersZeroToSix() {
        List<String> ids = registry.getAllNodeIds().stream()
            .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("reference.planes."))
            .sorted()
            .toList();
        assertEquals(7, ids.size(), "Expected 7 reference.planes nodes: " + ids);
        assertEquals(Set.copyOf(CANONICAL_IDS), Set.copyOf(ids));

        Set<Integer> orders = new HashSet<>();
        for (String id : ids) {
            NodeInfo info = registry.getNodeInfo(id);
            assertNotNull(info);
            assertEquals("reference.planes", info.getCategoryId());
            assertTrue(orders.add(info.getOrder()), "duplicate order for " + id);
            INode node = registry.createNodeInstance(id);
            assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(node.getClass(), id));
        }
        for (int i = 0; i < 7; i++) {
            assertTrue(orders.contains(i), "missing order " + i);
        }
    }

    @Test
    void allNodesExposeValidAndError() {
        for (String id : CANONICAL_IDS) {
            INode node = registry.createNodeInstance(id);
            assertTrue(hasPort(node, "output_valid"), id + " missing output_valid");
            assertTrue(hasPort(node, "output_error"), id + " missing output_error");
            assertPortType(id, "output_error", false, NodeDataType.STRING);
        }
    }

    @Test
    void publicPortsForbidLegacyLooseTypes() {
        List<String> errors = new ArrayList<>();
        for (String id : CANONICAL_IDS) {
            INode node = registry.createNodeInstance(id);
            for (IPort port : node.getInputPorts()) {
                if (FORBIDDEN_PORT_TYPES.contains(port.getDataType())) {
                    errors.add(id + "." + port.getId() + " input is " + port.getDataType());
                }
            }
            for (IPort port : node.getOutputPorts()) {
                if (FORBIDDEN_PORT_TYPES.contains(port.getDataType())) {
                    errors.add(id + "." + port.getId() + " output is " + port.getDataType());
                }
            }
        }
        assertTrue(errors.isEmpty(), String.join(System.lineSeparator(), errors));
    }

    @Test
    void constructDeconstructRoundTripPreservesConstructionOrigin() {
        BaseNode construct = node("reference.planes.construct_plane");
        construct.setInput("input_origin", new PointData(1, 2, 3));
        construct.setInput("input_normal", new Vector3d(0, 1, 0));
        construct.processNode(null);
        assertValid(construct);

        PlaneData plane = assertInstanceOf(PlaneData.class, construct.getOutput("output_plane"));
        assertEquals(1.0d, plane.getNormal().length(), 1.0e-9d);

        BaseNode deconstruct = node("reference.planes.deconstruct_plane");
        deconstruct.setInput("input_plane", plane);
        deconstruct.processNode(null);
        assertValid(deconstruct);

        PointData origin = assertInstanceOf(PointData.class, deconstruct.getOutput("output_origin"));
        assertVectorEquals(new Vector3d(1, 2, 3), origin.position(), 1.0e-9d);
        VectorData normal = assertInstanceOf(VectorData.class, deconstruct.getOutput("output_normal"));
        assertEquals(1.0d, normal.components().length(), 1.0e-9d);
    }

    @Test
    void worldPlaneUnconnectedOriginUsesProperty() {
        BaseNode world = node("reference.planes.world_plane");
        world.setNodeState(Map.of("originX", 3.0d, "originY", 4.0d, "originZ", 5.0d));
        world.processNode(null);
        assertValid(world);

        PlaneData plane = assertInstanceOf(PlaneData.class, world.getOutput("output_plane"));
        assertVectorEquals(new Vector3d(3, 4, 5), plane.getPoint(), 1.0e-9d);
    }

    @Test
    void worldPlaneConnectedInvalidOriginFailsClosed() {
        WorldPlaneProbe probe = new WorldPlaneProbe();
        probe.connectInput("input_origin", NodeDataType.POINT);
        probe.putRawInput("input_origin", new PointData(Double.NaN, 0, 0));
        probe.processNode(null);
        assertInvalid(probe);
    }

    @Test
    void worldPlanePresetNormalsAreDocumentedOrientations() {
        assertWorldPlaneNormal("XY", new Vector3d(0, 0, 1));
        assertWorldPlaneNormal("YZ", new Vector3d(1, 0, 0));
        assertWorldPlaneNormal("XZ", new Vector3d(0, 1, 0));
    }

    private static void assertWorldPlaneNormal(String preset, Vector3d expectedNormal) {
        BaseNode world = node("reference.planes.world_plane");
        world.setNodeState(Map.of(
            "planePreset", preset,
            "originX", 0.0d,
            "originY", 0.0d,
            "originZ", 0.0d
        ));
        world.processNode(null);
        assertValid(world);
        PlaneData plane = assertInstanceOf(PlaneData.class, world.getOutput("output_plane"));
        assertEquals(1.0d, plane.getNormal().length(), 1.0e-9d);
        assertVectorEquals(expectedNormal, plane.getNormal(), 1.0e-9d);
    }

    @Test
    void constructPlaneAcceptsCanonicalVectorData() {
        BaseNode construct = node("reference.planes.construct_plane");
        construct.setInput("input_origin", new PointData(1, 2, 3));
        construct.setInput("input_normal", VectorData.canonical(new Vector3d(0, 1, 0)));
        construct.processNode(null);
        assertValid(construct);
        PlaneData plane = assertInstanceOf(PlaneData.class, construct.getOutput("output_plane"));
        assertTrue(plane.isCanonical());
        assertVectorEquals(new Vector3d(0, 1, 0), plane.getNormal(), 1.0e-9d);
    }

    @Test
    void constructVectorFeedsConstructPlaneNormal() {
        BaseNode vector = node("reference.vectors.construct_vector");
        vector.setInput("input_x", 0.0d);
        vector.setInput("input_y", 1.0d);
        vector.setInput("input_z", 0.0d);
        vector.processNode(null);
        assertValid(vector);

        BaseNode construct = node("reference.planes.construct_plane");
        construct.setInput("input_origin", new PointData(0, 0, 0));
        construct.setInput("input_normal", vector.getOutput("output_vector"));
        construct.processNode(null);
        assertValid(construct);
        PlaneData plane = assertInstanceOf(PlaneData.class, construct.getOutput("output_plane"));
        assertVectorEquals(new Vector3d(0, 1, 0), plane.getNormal(), 1.0e-9d);
    }

    @Test
    void constructPlaneZeroNormalFailsClosed() {
        BaseNode construct = node("reference.planes.construct_plane");
        construct.setInput("input_origin", new PointData(0, 0, 0));
        construct.setInput("input_normal", new Vector3d(0, 0, 0));
        construct.processNode(null);
        assertInvalid(construct);
        assertTrue(String.valueOf(construct.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("normal"));
    }

    @Test
    void planeFromPointsCollinearFailsClosed() {
        BaseNode fromPoints = node("reference.planes.plane_from_points");
        fromPoints.setInput("input_point_a", new PointData(0, 0, 0));
        fromPoints.setInput("input_point_b", new PointData(1, 0, 0));
        fromPoints.setInput("input_point_c", new PointData(2, 0, 0));
        fromPoints.processNode(null);
        assertInvalid(fromPoints);
        assertTrue(String.valueOf(fromPoints.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("collinear"));
    }

    @Test
    void planeFromPointsSwappedBcFlipsNormal() {
        Vector3d a = new Vector3d(0, 0, 0);
        Vector3d b = new Vector3d(1, 0, 0);
        Vector3d c = new Vector3d(0, 1, 0);

        PlaneData abc = PlaneUtils.fromThreePoints(a, b, c);
        PlaneData acb = PlaneUtils.fromThreePoints(a, c, b);
        assertNotNull(abc);
        assertNotNull(acb);
        assertTrue(abc.getNormal().dot(acb.getNormal()) < 0.0d);
    }

    @Test
    void planeFromPointsScaleRelativeCollinearity() {
        double scale = 1000.0d;
        PlaneData unitCollinear = PlaneUtils.fromThreePoints(
            new Vector3d(0, 0, 0),
            new Vector3d(1, 0, 0),
            new Vector3d(2, 0, 0)
        );
        PlaneData scaledCollinear = PlaneUtils.fromThreePoints(
            new Vector3d(0, 0, 0),
            new Vector3d(scale, 0, 0),
            new Vector3d(2 * scale, 0, 0)
        );
        assertEquals(null, unitCollinear);
        assertEquals(null, scaledCollinear);

        PlaneData unitValid = PlaneUtils.fromThreePoints(
            new Vector3d(0, 0, 0),
            new Vector3d(1, 0, 0),
            new Vector3d(0, 1, 0)
        );
        PlaneData scaledValid = PlaneUtils.fromThreePoints(
            new Vector3d(0, 0, 0),
            new Vector3d(scale, 0, 0),
            new Vector3d(0, scale, 0)
        );
        assertNotNull(unitValid);
        assertNotNull(scaledValid);
        assertVectorEquals(unitValid.getNormal(), scaledValid.getNormal(), 1.0e-9d);
    }

    @Test
    void planeFromPointsOverflowingSubtractFailsClosed() {
        assertNull(PlaneUtils.fromThreePoints(
            new Vector3d(-1.0e308d, 0, 0),
            new Vector3d(1.0e308d, 0, 0),
            new Vector3d(0, 1, 0)
        ));
    }

    @Test
    void boxFaceToPlaneRejectsNonCoplanarFace() {
        List<Vector3d> corners = List.of(
            new Vector3d(0, 0, 0),
            new Vector3d(2, 0, 0),
            new Vector3d(2, 2, 0),
            new Vector3d(0, 2, 1)
        );
        BoxFaceData malformed = new BoxFaceData(
            0, "front", List.of(0, 1, 2, 3), corners,
            new Vector3d(1, 1, 0.25), new Vector3d(0, 0, 1)
        );
        assertNotNull(BoxFaceValidator.validate(malformed));

        BaseNode boxFace = node("reference.planes.box_face_plane");
        boxFace.setInput("input_face", malformed);
        boxFace.processNode(null);
        assertInvalid(boxFace);
    }

    @Test
    void offsetPlaneUnconnectedDistanceDefaultsToZero() {
        PlaneData plane = PlaneData.canonical(new Vector3d(1, 2, 3), new Vector3d(0, 1, 0));
        assertNotNull(plane);

        BaseNode offset = node("reference.planes.offset_plane");
        offset.setInput("input_plane", plane);
        offset.processNode(null);
        assertValid(offset);

        PlaneData out = assertInstanceOf(PlaneData.class, offset.getOutput("output_plane"));
        assertVectorEquals(plane.getPoint(), out.getPoint(), 1.0e-9d);
    }

    @Test
    void offsetPlaneConnectedInvalidDistanceFailsClosed() {
        PlaneData plane = PlaneData.canonical(new Vector3d(0, 0, 0), new Vector3d(0, 1, 0));
        assertNotNull(plane);

        OffsetProbe probe = new OffsetProbe();
        probe.setInput("input_plane", plane);
        probe.connectInput("input_distance", NodeDataType.DOUBLE);
        probe.putRawInput("input_distance", Double.NaN);
        probe.processNode(null);
        assertInvalid(probe);
    }

    @Test
    void offsetPlanePositiveAndNegativeFollowNormal() {
        PlaneData plane = PlaneData.canonical(new Vector3d(0, 0, 0), new Vector3d(0, 1, 0));
        assertNotNull(plane);

        OffsetProbe positive = new OffsetProbe();
        positive.setInput("input_plane", plane);
        positive.connectInput("input_distance", NodeDataType.DOUBLE);
        positive.putRawInput("input_distance", 2.0d);
        positive.processNode(null);
        assertValid(positive);
        PlaneData posOut = assertInstanceOf(PlaneData.class, positive.getOutput("output_plane"));
        assertVectorEquals(new Vector3d(0, 2, 0), posOut.getPoint(), 1.0e-9d);

        OffsetProbe negative = new OffsetProbe();
        negative.setInput("input_plane", plane);
        negative.connectInput("input_distance", NodeDataType.DOUBLE);
        negative.putRawInput("input_distance", -2.0d);
        negative.processNode(null);
        assertValid(negative);
        PlaneData negOut = assertInstanceOf(PlaneData.class, negative.getOutput("output_plane"));
        assertVectorEquals(new Vector3d(0, -2, 0), negOut.getPoint(), 1.0e-9d);
    }

    @Test
    void offsetPlaneHugeDistanceOverflowFailsClosed() {
        PlaneData plane = PlaneData.canonical(new Vector3d(0, 0.9e308d, 0), new Vector3d(0, 1, 0));
        assertNotNull(plane);

        OffsetProbe offset = new OffsetProbe();
        offset.setInput("input_plane", plane);
        offset.connectInput("input_distance", NodeDataType.DOUBLE);
        offset.putRawInput("input_distance", 0.9e308d);
        offset.processNode(null);
        assertInvalid(offset);
    }

    @Test
    void distancePointToPlaneInvalidOutputsZeroWithError() {
        BaseNode distance = node("reference.planes.distance_point_to_plane");
        distance.setInput("input_point", new PointData(1, 0, 0));
        distance.processNode(null);
        assertInvalid(distance);
        assertEquals(0.0d, distance.getOutput("output_distance"));
        assertEquals(0.0d, distance.getOutput("output_signed_distance"));
    }

    @Test
    void distancePointToPlaneSignedDistanceFollowsNormalDirection() {
        PlaneData plane = PlaneData.canonical(new Vector3d(5, 0, 0), new Vector3d(1, 0, 0));
        assertNotNull(plane);

        BaseNode distance = node("reference.planes.distance_point_to_plane");
        distance.setInput("input_plane", plane);
        distance.setInput("input_point", new PointData(6, 0, 0));
        distance.processNode(null);
        assertValid(distance);
        assertEquals(1.0d, distance.getOutput("output_signed_distance"));
        assertEquals(1.0d, distance.getOutput("output_distance"));

        distance.setInput("input_point", new PointData(4, 0, 0));
        distance.processNode(null);
        assertValid(distance);
        assertEquals(-1.0d, distance.getOutput("output_signed_distance"));
    }

    @Test
    void deconstructPlaneInvalidFailsCleanly() {
        BaseNode deconstruct = node("reference.planes.deconstruct_plane");
        deconstruct.processNode(null);
        assertInvalid(deconstruct);
        assertEquals(null, deconstruct.getOutput("output_origin"));
        assertEquals(null, deconstruct.getOutput("output_normal"));
    }

    @Test
    void frameFromPlaneUsesPlaneConstructionOrigin() {
        BaseNode construct = node("reference.planes.construct_plane");
        construct.setInput("input_origin", new PointData(7, 8, 9));
        construct.setInput("input_normal", new Vector3d(0, 0, 1));
        construct.processNode(null);
        assertValid(construct);
        PlaneData plane = assertInstanceOf(PlaneData.class, construct.getOutput("output_plane"));

        BaseNode fromPlane = node("reference.frames.frame_from_plane");
        fromPlane.setInput("input_plane", plane);
        fromPlane.processNode(null);
        assertEquals(Boolean.TRUE, fromPlane.getOutput("output_valid"));

        FrameData frame = assertInstanceOf(FrameData.class, fromPlane.getOutput("output_frame"));
        assertVectorEquals(new Vector3d(7, 8, 9), frame.getOrigin(), 1.0e-9d);
    }

    private static BaseNode node(String typeId) {
        return assertInstanceOf(BaseNode.class, registry.createNodeInstance(typeId));
    }

    private static void assertValid(BaseNode node) {
        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));
        assertEquals("", node.getOutput("output_error"));
    }

    private static void assertInvalid(BaseNode node) {
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertNotNull(node.getOutput("output_error"));
        assertFalse(String.valueOf(node.getOutput("output_error")).isBlank());
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
        return node.getInputPorts().stream().anyMatch(p -> portId.equals(p.getId()))
            || node.getOutputPorts().stream().anyMatch(p -> portId.equals(p.getId()));
    }

    private static void assertVectorEquals(Vector3d expected, Vector3d actual, double epsilon) {
        assertEquals(expected.x, actual.x, epsilon);
        assertEquals(expected.y, actual.y, epsilon);
        assertEquals(expected.z, actual.z, epsilon);
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

    private static final class WorldPlaneProbe extends PlaneSelectorNode {
        void putRawInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            ReferencePlanesLanguageV2ContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class OffsetProbe extends OffsetPlaneNode {
        void putRawInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            ReferencePlanesLanguageV2ContractTest.connectInput(this, portId, outputType);
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

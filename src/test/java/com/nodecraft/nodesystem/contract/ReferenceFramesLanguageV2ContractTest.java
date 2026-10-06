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
import com.nodecraft.nodesystem.datatypes.FrameDataTestAccess;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.SphereData;
import com.nodecraft.nodesystem.datatypes.VectorData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.execution.runtime.NodeEffectResolver;
import com.nodecraft.nodesystem.nodes.reference.frames.ConstructFrameNode;
import com.nodecraft.nodesystem.nodes.reference.frames.FrameFromPlaneNode;
import com.nodecraft.nodesystem.nodes.reference.frames.SphereSurfaceFrameNode;
import com.nodecraft.nodesystem.nodes.reference.frames.TransformFrameNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.BoxFaceValidator;
import com.nodecraft.nodesystem.util.FrameUtils;
import com.nodecraft.nodesystem.util.GenerationLimits;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.AbstractList;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Language fence for Reference Frames Language v2 (Graph V85).
 */
class ReferenceFramesLanguageV2ContractTest {

    private static final List<String> CANONICAL_IDS = List.of(
        "reference.frames.frame_from_face",
        "reference.frames.sphere_surface_frame",
        "reference.frames.world_frame",
        "reference.frames.construct_frame",
        "reference.frames.frame_from_plane",
        "reference.frames.transform_frame",
        "reference.frames.deconstruct_frame",
        "reference.frames.deconstruct_frames"
    );

    private static final List<String> FALLIBLE_IDS = List.of(
        "reference.frames.frame_from_face",
        "reference.frames.sphere_surface_frame",
        "reference.frames.construct_frame",
        "reference.frames.frame_from_plane",
        "reference.frames.transform_frame",
        "reference.frames.deconstruct_frame",
        "reference.frames.deconstruct_frames"
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
    void exactlyEightNodesWithUniqueOrdersZeroToSeven() {
        List<String> ids = registry.getAllNodeIds().stream()
            .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("reference.frames."))
            .sorted()
            .toList();
        assertEquals(8, ids.size(), "Expected 8 reference.frames nodes: " + ids);
        assertEquals(Set.copyOf(CANONICAL_IDS), Set.copyOf(ids));

        Set<Integer> orders = new HashSet<>();
        for (String id : ids) {
            NodeInfo info = registry.getNodeInfo(id);
            assertNotNull(info);
            assertEquals("reference.frames", info.getCategoryId());
            assertTrue(orders.add(info.getOrder()), "duplicate order for " + id);
            INode node = registry.createNodeInstance(id);
            assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(node.getClass(), id));
        }
        for (int i = 0; i < 8; i++) {
            assertTrue(orders.contains(i), "missing order " + i);
        }
    }

    @Test
    void fallibleNodesExposeValidAndError() {
        for (String id : FALLIBLE_IDS) {
            INode node = registry.createNodeInstance(id);
            assertTrue(hasPort(node, "output_valid"), id + " missing output_valid");
            assertTrue(hasPort(node, "output_error"), id + " missing output_error");
            assertPortType(id, "output_error", false, NodeDataType.STRING);
        }
    }

    @Test
    void worldFrameHasNoValidOrError() {
        INode node = registry.createNodeInstance("reference.frames.world_frame");
        assertFalse(hasPort(node, "output_valid"));
        assertFalse(hasPort(node, "output_error"));
        assertPortType("reference.frames.world_frame", "output_frame", false, NodeDataType.FRAME);
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
    void constructUsesDocumentedDefaultsWhenUnconnected() {
        BaseNode construct = node("reference.frames.construct_frame");
        construct.processNode(null);

        assertValid(construct);
        FrameData frame = assertInstanceOf(FrameData.class, construct.getOutput("output_frame"));
        assertVectorEquals(new Vector3d(0, 0, 0), frame.getOrigin(), 1.0e-9d);
        assertVectorEquals(new Vector3d(1, 0, 0), frame.getXAxis(), 1.0e-9d);
        assertVectorEquals(new Vector3d(0, 1, 0), frame.getYAxis(), 1.0e-9d);
        assertVectorEquals(new Vector3d(0, 0, 1), frame.getZAxis(), 1.0e-9d);
    }

    @Test
    void constructConnectedParallelAxesFailClosed() {
        ConstructProbe probe = new ConstructProbe();
        probe.connectInput("input_x_axis", NodeDataType.VECTOR);
        probe.connectInput("input_y_axis", NodeDataType.VECTOR);
        probe.putRawInput("input_x_axis", new VectorData(1, 0, 0));
        probe.putRawInput("input_y_axis", new VectorData(2, 0, 0));
        probe.processNode(null);

        assertInvalid(probe);
        assertTrue(String.valueOf(probe.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("parallel"));
    }

    @Test
    void constructConnectedInvalidAxisFailsClosed() {
        ConstructProbe probe = new ConstructProbe();
        probe.connectInput("input_x_axis", NodeDataType.VECTOR);
        probe.putRawInput("input_x_axis", new Vector3d(Double.NaN, 0, 0));
        probe.processNode(null);
        assertInvalid(probe);
    }

    @Test
    void frameFromPlaneUnconnectedUsesDeterministicFallback() {
        PlaneData plane = new PlaneData(new Vector3d(1, 2, 3), new Vector3d(0, 1, 0));
        BaseNode fromPlane = node("reference.frames.frame_from_plane");
        fromPlane.setInput("input_plane", plane);
        fromPlane.processNode(null);

        assertValid(fromPlane);
        FrameData frame = assertInstanceOf(FrameData.class, fromPlane.getOutput("output_frame"));
        FrameData helper = FrameUtils.fromPlane(plane, null);
        assertNotNull(helper);
        assertVectorEquals(helper.getXAxis(), frame.getXAxis(), 1.0e-9d);
        assertVectorEquals(new Vector3d(1, 2, 3), frame.getOrigin(), 1.0e-9d);
    }

    @Test
    void frameFromPlaneConnectedParallelHintFailsClosed() {
        PlaneData plane = new PlaneData(new Vector3d(1, 2, 3), new Vector3d(0, 1, 0));
        FrameFromPlaneProbe probe = new FrameFromPlaneProbe();
        probe.setInput("input_plane", plane);
        probe.connectInput("input_x_hint", NodeDataType.VECTOR);
        probe.putRawInput("input_x_hint", new com.nodecraft.nodesystem.datatypes.VectorData(0, 1, 0));
        probe.processNode(null);
        assertInvalid(probe);
    }

    @Test
    void frameFromPlaneConnectedValidHintSucceeds() {
        PlaneData plane = new PlaneData(new Vector3d(1, 2, 3), new Vector3d(0, 1, 0));
        FrameFromPlaneProbe probe = new FrameFromPlaneProbe();
        probe.setInput("input_plane", plane);
        probe.connectInput("input_x_hint", NodeDataType.VECTOR);
        probe.putRawInput("input_x_hint", new com.nodecraft.nodesystem.datatypes.VectorData(1, 0, 0));
        probe.processNode(null);

        assertValid(probe);
        FrameData frame = assertInstanceOf(FrameData.class, probe.getOutput("output_frame"));
        assertVectorEquals(new Vector3d(1, 0, 0), frame.getXAxis(), 1.0e-9d);
    }

    @Test
    void sphereSurfaceFrameUnconnectedHintUsesFallback() {
        SphereData sphere = new SphereData(new Vector3d(0, 0, 0), 2.0d);
        BaseNode surface = node("reference.frames.sphere_surface_frame");
        surface.setInput("input_sphere", sphere);
        surface.setInput("input_point", new PointData(2, 0, 0));
        surface.processNode(null);

        assertValid(surface);
        FrameData frame = assertInstanceOf(FrameData.class, surface.getOutput("output_frame"));
        FrameData helper = FrameUtils.fromNormal(new Vector3d(2, 0, 0), new Vector3d(1, 0, 0), null);
        assertNotNull(helper);
        assertVectorEquals(helper.getXAxis(), frame.getXAxis(), 1.0e-9d);
    }

    @Test
    void sphereSurfaceFrameConnectedParallelHintFailsClosed() {
        SphereData sphere = new SphereData(new Vector3d(0, 0, 0), 2.0d);
        SphereSurfaceProbe probe = new SphereSurfaceProbe();
        probe.setInput("input_sphere", sphere);
        probe.setInput("input_point", new PointData(2, 0, 0));
        probe.connectInput("input_x_hint", NodeDataType.VECTOR);
        probe.putRawInput("input_x_hint", new com.nodecraft.nodesystem.datatypes.VectorData(1, 0, 0));
        probe.processNode(null);
        assertInvalid(probe);
    }

    @Test
    void transformRejectsNonCanonicalInput() {
        FrameData scaled = FrameDataTestAccess.unchecked(
            new Vector3d(0, 0, 0),
            new Vector3d(2, 0, 0),
            new Vector3d(0, 3, 0),
            new Vector3d(0, 0, 4)
        );
        BaseNode transform = node("reference.frames.transform_frame");
        transform.setInput("input_frame", scaled);
        transform.processNode(null);

        assertInvalid(transform);
    }

    @Test
    void transformRejectsNonFiniteRadians() {
        FrameData input = FrameDataTestAccess.unchecked(
            new Vector3d(0, 0, 0),
            new Vector3d(1, 0, 0),
            new Vector3d(0, 1, 0),
            new Vector3d(0, 0, 1)
        );
        TransformProbe probe = new TransformProbe();
        probe.setInput("input_frame", input);
        probe.connectInput("input_rotation_x", NodeDataType.DOUBLE);
        probe.putRawInput("input_rotation_x", Double.POSITIVE_INFINITY);
        probe.processNode(null);
        assertInvalid(probe);
    }

    @Test
    void transformUsesPropertyRotationWhenUnconnected() {
        FrameData input = FrameDataTestAccess.unchecked(
            new Vector3d(0, 0, 0),
            new Vector3d(1, 0, 0),
            new Vector3d(0, 1, 0),
            new Vector3d(0, 0, 1)
        );
        BaseNode transform = node("reference.frames.transform_frame");
        transform.setInput("input_frame", input);
        transform.setNodeState(Map.of("rotationZ", 90.0d));
        transform.processNode(null);

        assertValid(transform);
        FrameData out = assertInstanceOf(FrameData.class, transform.getOutput("output_frame"));
        assertEquals(1.0d, out.getXAxis().length(), 1.0e-9d);
        assertEquals(0.0d, out.getXAxis().x, 1.0e-6d);
        assertEquals(1.0d, Math.abs(out.getXAxis().y), 1.0e-6d);
    }

    @Test
    void transformRotationUsesWorldAxisSemantics() {
        // Pre-rotated frame: local X = world +Z (not identity).
        FrameData preRotated = FrameDataTestAccess.unchecked(
            new Vector3d(0, 0, 0),
            new Vector3d(0, 0, 1),
            new Vector3d(0, 1, 0),
            new Vector3d(-1, 0, 0)
        );
        BaseNode transform = node("reference.frames.transform_frame");
        transform.setInput("input_frame", preRotated);
        transform.setNodeState(Map.of("rotationX", 90.0d));
        transform.processNode(null);

        assertValid(transform);
        FrameData out = assertInstanceOf(FrameData.class, transform.getOutput("output_frame"));
        // World-axis Rx(90) · (0,0,1) = (0,-1,0). Local Rx would leave outX ≈ (0,0,1).
        assertVectorEquals(new Vector3d(0, -1, 0), out.getXAxis(), 1.0e-6d);
    }

    @Test
    void transformSavedStateNonFiniteRotationFailsAtProcess() {
        FrameData identity = FrameDataTestAccess.unchecked(
            new Vector3d(0, 0, 0),
            new Vector3d(1, 0, 0),
            new Vector3d(0, 1, 0),
            new Vector3d(0, 0, 1)
        );
        BaseNode transform = node("reference.frames.transform_frame");
        transform.setInput("input_frame", identity);
        transform.setNodeState(Map.of("rotationZ", 45.0d));
        transform.setNodeState(Map.of("rotationZ", Double.NaN));
        transform.processNode(null);
        assertInvalid(transform);
    }

    @Test
    void transformWorldRotationPortLabels() {
        INode node = registry.createNodeInstance("reference.frames.transform_frame");
        for (String portId : List.of("input_rotation_x", "input_rotation_y", "input_rotation_z")) {
            IPort port = node.getInputPorts().stream()
                .filter(candidate -> portId.equals(candidate.getId()))
                .findFirst()
                .orElseThrow();
            assertTrue(port.getDisplayName().contains("World Rotation"),
                portId + " display name should contain World Rotation, was: " + port.getDisplayName());
        }
    }

    @Test
    void transformConnectedInvalidRotationFailsClosed() {
        FrameData input = FrameDataTestAccess.unchecked(
            new Vector3d(0, 0, 0),
            new Vector3d(1, 0, 0),
            new Vector3d(0, 1, 0),
            new Vector3d(0, 0, 1)
        );
        TransformProbe probe = new TransformProbe();
        probe.setInput("input_frame", input);
        probe.connectInput("input_rotation_z", NodeDataType.DOUBLE);
        probe.putRawInput("input_rotation_z", Double.NaN);
        probe.processNode(null);
        assertInvalid(probe);
    }

    @Test
    void faceCenterFrameRejectsMalformedFace() {
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

        BaseNode face = node("reference.frames.frame_from_face");
        face.setInput("input_face", malformed);
        face.processNode(null);
        assertInvalid(face);
    }

    @Test
    void deconstructFramesOverCapFailsClosed() {
        FrameData valid = FrameDataTestAccess.unchecked(
            new Vector3d(0, 0, 0),
            new Vector3d(1, 0, 0),
            new Vector3d(0, 1, 0),
            new Vector3d(0, 0, 1)
        );
        BaseNode deconstruct = node("reference.frames.deconstruct_frames");
        deconstruct.setInput("input_frames", oversizedFrameList(valid));
        deconstruct.processNode(null);
        assertInvalid(deconstruct);
        assertTrue(String.valueOf(deconstruct.getOutput("output_error")).contains("MAX_FRAME_LIST_ELEMENTS"));
    }

    @Test
    void deconstructFrameEmptyInputFailsClosedWithError() {
        BaseNode deconstruct = node("reference.frames.deconstruct_frame");
        deconstruct.processNode(null);
        assertInvalid(deconstruct);
        assertFalse(String.valueOf(deconstruct.getOutput("output_error")).isBlank());
    }

    private static BaseNode node(String typeId) {
        return assertInstanceOf(BaseNode.class, registry.createNodeInstance(typeId));
    }

    private static List<FrameData> oversizedFrameList(FrameData template) {
        return new AbstractList<>() {
            @Override
            public FrameData get(int index) {
                return template;
            }

            @Override
            public int size() {
                return GenerationLimits.MAX_FRAME_LIST_ELEMENTS + 1;
            }
        };
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

    private static final class ConstructProbe extends ConstructFrameNode {
        void putRawInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            ReferenceFramesLanguageV2ContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class FrameFromPlaneProbe extends FrameFromPlaneNode {
        void putRawInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            ReferenceFramesLanguageV2ContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class SphereSurfaceProbe extends SphereSurfaceFrameNode {
        void putRawInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            ReferenceFramesLanguageV2ContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class TransformProbe extends TransformFrameNode {
        void putRawInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            ReferenceFramesLanguageV2ContractTest.connectInput(this, portId, outputType);
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

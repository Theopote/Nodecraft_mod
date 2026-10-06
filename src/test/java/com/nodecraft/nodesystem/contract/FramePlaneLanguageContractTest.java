package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.datatypes.FrameDataTestAccess;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.VectorData;
import com.nodecraft.nodesystem.graph.GraphMigrationRegistry;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedConnection;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.FrameUtils;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Freezes Frame/Plane Node Language v1 semantics.
 */
class FramePlaneLanguageContractTest {

    private static final Set<String> FRAME_PLANE_NODE_IDS = Set.of(
            "reference.frames.construct_frame",
            "reference.frames.transform_frame",
            "reference.frames.deconstruct_frame",
            "reference.frames.frame_from_plane",
            "reference.frames.world_frame",
            "reference.frames.frame_from_face",
            "reference.frames.sphere_surface_frame",
            "reference.planes.construct_plane",
            "reference.planes.plane_from_points",
            "reference.planes.deconstruct_plane",
            "reference.planes.world_plane",
            "reference.planes.offset_plane",
            "reference.planes.box_face_plane",
            "transform.basic_transforms.transform_by_frames",
            "pattern.linear.path_frames"
    );

    @BeforeAll
    static void init() {
        NodeRegistry registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void framePlaneNodesAvoidAnyPorts() {
        List<String> errors = new ArrayList<>();
        for (String typeId : FRAME_PLANE_NODE_IDS) {
            INode node = NodeRegistry.getInstance().createNodeInstance(typeId);
            if (node == null) {
                errors.add("missing instance: " + typeId);
                continue;
            }
            for (IPort port : node.getInputPorts()) {
                if (port.getDataType() == NodeDataType.ANY) {
                    errors.add(typeId + "." + port.getId() + " input is ANY");
                }
            }
            for (IPort port : node.getOutputPorts()) {
                if (port.getDataType() == NodeDataType.ANY) {
                    errors.add(typeId + "." + port.getId() + " output is ANY");
                }
            }
        }
        assertTrue(errors.isEmpty(), String.join(System.lineSeparator(), errors));
    }

    @Test
    void constructFrameOrthonormalizesScaledAxes() {
        BaseNode construct = node("reference.frames.construct_frame");
        connectInput(construct, "input_origin", NodeDataType.POINT);
        connectInput(construct, "input_x_axis", NodeDataType.VECTOR);
        connectInput(construct, "input_y_axis", NodeDataType.VECTOR);
        construct.setInput("input_origin", new PointData(0, 0, 0));
        construct.setInput("input_x_axis", new VectorData(2, 0, 0));
        construct.setInput("input_y_axis", new VectorData(0, 3, 0));
        construct.processNode(null);

        assertEquals(Boolean.TRUE, construct.getOutput("output_valid"));
        FrameData frame = assertInstanceOf(FrameData.class, construct.getOutput("output_frame"));
        assertEquals(1.0d, frame.getXAxis().length(), 1.0e-9d);
        assertEquals(1.0d, frame.getYAxis().length(), 1.0e-9d);
        assertEquals(1.0d, frame.getZAxis().length(), 1.0e-9d);
        assertEquals(0.0d, frame.getXAxis().dot(frame.getYAxis()), 1.0e-9d);
        Vector3d cross = new Vector3d(frame.getXAxis()).cross(frame.getYAxis());
        assertVectorEquals(frame.getZAxis(), cross, 1.0e-9d);
    }

    @Test
    void transformFrameHasNoScaleInputAndProducesUnitAxes() {
        assertFalse(hasInputPort("reference.frames.transform_frame", "input_scale"));
        assertFalse(hasInputPort("reference.frames.transform_frame", "input_origin"));
        assertFalse(hasOutputPort("reference.frames.transform_frame", "output_plane"));

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

        assertEquals(Boolean.TRUE, transform.getOutput("output_valid"));
        FrameData out = assertInstanceOf(FrameData.class, transform.getOutput("output_frame"));
        assertEquals(1.0d, out.getXAxis().length(), 1.0e-9d);
        assertEquals(1.0d, out.getYAxis().length(), 1.0e-9d);
        assertEquals(1.0d, out.getZAxis().length(), 1.0e-9d);
    }

    @Test
    void frameFromPlaneUsesDeterministicFallbackWhenUnconnected() {
        PlaneData plane = new PlaneData(new Vector3d(1, 2, 3), new Vector3d(0, 1, 0));

        BaseNode fromPlane = node("reference.frames.frame_from_plane");
        fromPlane.setInput("input_plane", plane);
        fromPlane.processNode(null);
        assertEquals(Boolean.TRUE, fromPlane.getOutput("output_valid"));

        FrameData frame = assertInstanceOf(FrameData.class, fromPlane.getOutput("output_frame"));
        assertVectorEquals(new Vector3d(1, 2, 3), frame.getOrigin(), 1.0e-9d);
        assertVectorEquals(new Vector3d(0, 1, 0), frame.getZAxis(), 1.0e-9d);
        assertEquals(1.0d, frame.getXAxis().length(), 1.0e-9d);

        FrameData helper = FrameUtils.fromPlane(plane, null);
        assertNotNull(helper);
        assertVectorEquals(helper.getXAxis(), frame.getXAxis(), 1.0e-9d);
    }

    @Test
    void deconstructPlaneOutputsStrictTypes() {
        assertPortType("reference.planes.deconstruct_plane", "output_origin", false, NodeDataType.POINT);
        assertPortType("reference.planes.deconstruct_plane", "output_normal", false, NodeDataType.VECTOR);

        BaseNode deconstruct = node("reference.planes.deconstruct_plane");
        PlaneData plane = new PlaneData(new Vector3d(2, 3, 4), new Vector3d(0, 0, 1));
        deconstruct.setInput("input_plane", plane);
        deconstruct.processNode(null);
        assertEquals(Boolean.TRUE, deconstruct.getOutput("output_valid"));
        assertInstanceOf(PointData.class, deconstruct.getOutput("output_origin"));
        assertInstanceOf(VectorData.class, deconstruct.getOutput("output_normal"));
    }

    @Test
    void transformPointsByFramesUsesFrameList() {
        assertPortType("transform.basic_transforms.transform_by_frames", "input_frames", true, NodeDataType.FRAME_LIST);
        assertFalse(hasInputPort("transform.basic_transforms.transform_by_frames", "input_origins"));
        assertFalse(hasInputPort("transform.basic_transforms.transform_by_frames", "input_x_axes"));
        assertFalse(hasOutputPort("transform.basic_transforms.transform_by_frames", "output_frame_count"));

        FrameData frame = FrameDataTestAccess.unchecked(
            new Vector3d(10, 0, 0),
            new Vector3d(1, 0, 0),
            new Vector3d(0, 1, 0),
            new Vector3d(0, 0, 1)
        );
        BaseNode transform = node("transform.basic_transforms.transform_by_frames");
        transform.setInput("input_local_points", List.of(new PointData(1, 2, 3)));
        transform.setInput("input_frames", List.of(frame));
        transform.processNode(null);

        assertEquals(Boolean.TRUE, transform.getOutput("output_valid"));
        assertEquals(1, transform.getOutput("output_count"));
        List<?> points = (List<?>) transform.getOutput("output_points");
        assertEquals(1, points.size());
        PointData world = assertInstanceOf(PointData.class, points.getFirst());
        assertEquals(11.0d, world.position().x, 1.0e-6d);
        assertEquals(2.0d, world.position().y, 1.0e-6d);
        assertEquals(3.0d, world.position().z, 1.0e-6d);
    }

    @Test
    void transformPointsByFramesRejectsNonCanonicalAxes() {
        FrameData scaledAxes = FrameDataTestAccess.unchecked(
            new Vector3d(0, 0, 0),
            new Vector3d(2, 0, 0),
            new Vector3d(0, 3, 0),
            new Vector3d(0, 0, 4)
        );

        BaseNode transform = node("transform.basic_transforms.transform_by_frames");
        transform.setInput("input_local_points", List.of(new PointData(1, 1, 1)));
        transform.setInput("input_frames", List.of(scaledAxes));
        transform.processNode(null);

        assertEquals(Boolean.FALSE, transform.getOutput("output_valid"));
    }

    @Test
    void worldFrameOutputsFrameOnly() {
        assertPortType("reference.frames.world_frame", "output_frame", false, NodeDataType.FRAME);
        assertFalse(hasOutputPort("reference.frames.world_frame", "output_origin"));
        assertFalse(hasOutputPort("reference.frames.world_frame", "output_valid"));

        BaseNode world = node("reference.frames.world_frame");
        world.processNode(null);
        FrameData frame = assertInstanceOf(FrameData.class, world.getOutput("output_frame"));
        assertVectorEquals(new Vector3d(0, 0, 0), frame.getOrigin(), 1.0e-9d);
        assertVectorEquals(new Vector3d(1, 0, 0), frame.getXAxis(), 1.0e-9d);
        assertVectorEquals(new Vector3d(0, 1, 0), frame.getYAxis(), 1.0e-9d);
        assertVectorEquals(new Vector3d(0, 0, 1), frame.getZAxis(), 1.0e-9d);
    }

    @Test
    void worldPlaneHasNoBlockPosOutput() {
        assertPortType("reference.planes.world_plane", "input_origin", true, NodeDataType.POINT);
        assertPortType("reference.planes.world_plane", "output_plane", false, NodeDataType.PLANE);
        assertPortType("reference.planes.world_plane", "output_valid", false, NodeDataType.BOOLEAN);
        assertFalse(hasOutputPort("reference.planes.world_plane", "output_origin"));
        assertFalse(hasOutputPort("reference.planes.world_plane", "output_normal"));
    }

    @Test
    void offsetPlaneCompactOutputs() {
        assertPortType("reference.planes.offset_plane", "output_plane", false, NodeDataType.PLANE);
        assertPortType("reference.planes.offset_plane", "output_valid", false, NodeDataType.BOOLEAN);
        assertFalse(hasOutputPort("reference.planes.offset_plane", "output_origin"));
        assertFalse(hasOutputPort("reference.planes.offset_plane", "output_normal"));
    }

    @Test
    void producerCompactnessFrozen() {
        assertFalse(hasOutputPort("reference.frames.frame_from_face", "output_plane"));
        assertFalse(hasOutputPort("reference.frames.frame_from_face", "output_x_axis"));
        assertFalse(hasOutputPort("reference.frames.frame_from_face", "output_corner_indices"));

        assertFalse(hasOutputPort("reference.frames.sphere_surface_frame", "output_center"));
        assertFalse(hasOutputPort("reference.frames.sphere_surface_frame", "output_x_axis"));
        assertFalse(hasOutputPort("reference.frames.sphere_surface_frame", "output_plane"));

        assertFalse(hasOutputPort("reference.frames.world_frame", "output_origin_pos"));
        assertFalse(hasOutputPort("reference.planes.box_face_plane", "output_center"));
        assertFalse(hasOutputPort("reference.planes.construct_plane", "output_normalized_normal"));
    }

    @Test
    void pathFramesUnchanged() {
        assertPortType("pattern.linear.path_frames", "output_frames", false, NodeDataType.FRAME_LIST);
        assertPortType("pattern.linear.path_frames", "output_points", false, NodeDataType.POINT_LIST);
        assertPortType("pattern.linear.path_frames", "output_tangents", false, NodeDataType.VECTOR_LIST);
        assertPortType("pattern.linear.path_frames", "output_length", false, NodeDataType.DOUBLE);
    }


    private static BaseNode node(String typeId) {
        BaseNode node = (BaseNode) NodeRegistry.getInstance().createNodeInstance(typeId);
        assertNotNull(node, typeId);
        return node;
    }

    private static void assertPortType(String typeId, String portId, boolean input, NodeDataType expected) {
        INode node = NodeRegistry.getInstance().createNodeInstance(typeId);
        assertNotNull(node);
        IPort port = (input ? node.getInputPorts() : node.getOutputPorts()).stream()
            .filter(candidate -> candidate.getId().equals(portId))
            .findFirst()
            .orElseThrow(() -> new AssertionError(typeId + " missing port " + portId));
        assertEquals(expected, port.getDataType(), typeId + "." + portId);
    }

    private static boolean hasInputPort(String typeId, String portId) {
        INode node = NodeRegistry.getInstance().createNodeInstance(typeId);
        return node != null && node.getInputPorts().stream().anyMatch(port -> port.getId().equals(portId));
    }

    private static boolean hasOutputPort(String typeId, String portId) {
        INode node = NodeRegistry.getInstance().createNodeInstance(typeId);
        return node != null && node.getOutputPorts().stream().anyMatch(port -> port.getId().equals(portId));
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

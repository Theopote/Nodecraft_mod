package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.SphereData;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.FrameUtils;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reference Frames v1 language fence (Graph V47).
 */
class ReferenceFramesLanguageContractTest {

    private static final Set<String> CANONICAL_IDS = Set.of(
            "reference.frames.frame_from_face",
            "reference.frames.sphere_surface_frame",
            "reference.frames.world_frame",
            "reference.frames.construct_frame",
            "reference.frames.frame_from_plane",
            "reference.frames.transform_frame",
            "reference.frames.deconstruct_frame",
            "reference.frames.deconstruct_frames"
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
    void referenceFramesFreezeVersionIsV47() {
        assertEquals(47, GraphFormatVersion.V47);
    }

    @Test
    void exactlyEightCanonicalReferenceFrameNodesRegistered() {
        List<String> ids = registry.getAllNodeIds().stream()
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("reference.frames."))
                .sorted()
                .toList();
        assertEquals(8, ids.size(), "Expected 8 reference.frames nodes: " + ids);
        assertEquals(CANONICAL_IDS, Set.copyOf(ids));
        assertFalse(ids.contains("reference.frames.frame_along_surface"));
    }

    @Test
    void referenceFrameNodesHaveUniqueOrderZeroThroughSeven() {
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
        assertEquals(8, orders.length);
        for (int i = 0; i < orders.length; i++) {
            assertEquals(i, orders[i], "Expected unique order " + i);
        }
    }

    @Test
    void deconstructFramesPortsAreTypedLists() {
        assertPortType("reference.frames.deconstruct_frames", "input_frames", true, NodeDataType.FRAME_LIST);
        assertPortType("reference.frames.deconstruct_frames", "output_origins", false, NodeDataType.POINT_LIST);
        assertPortType("reference.frames.deconstruct_frames", "output_x_axes", false, NodeDataType.VECTOR_LIST);
        assertPortType("reference.frames.deconstruct_frames", "output_y_axes", false, NodeDataType.VECTOR_LIST);
        assertPortType("reference.frames.deconstruct_frames", "output_z_axes", false, NodeDataType.VECTOR_LIST);
        assertPortType("reference.frames.deconstruct_frames", "output_planes", false, NodeDataType.PLANE_LIST);
        assertPortType("reference.frames.deconstruct_frames", "output_count", false, NodeDataType.INTEGER);
        assertPortType("reference.frames.deconstruct_frames", "output_valid", false, NodeDataType.BOOLEAN);
    }

    @Test
    void constructFrameUsesDocumentedDefaultsWhenUnconnected() {
        BaseNode construct = node("reference.frames.construct_frame");
        construct.processNode(null);

        assertEquals(Boolean.TRUE, construct.getOutput("output_valid"));
        FrameData frame = assertInstanceOf(FrameData.class, construct.getOutput("output_frame"));
        assertVectorEquals(new Vector3d(0, 0, 0), frame.getOrigin(), 1.0e-9d);
        assertVectorEquals(new Vector3d(1, 0, 0), frame.getXAxis(), 1.0e-9d);
        assertVectorEquals(new Vector3d(0, 1, 0), frame.getYAxis(), 1.0e-9d);
        assertVectorEquals(new Vector3d(0, 0, 1), frame.getZAxis(), 1.0e-9d);
    }

    @Test
    void constructFrameParallelWiredAxesFailClosed() {
        BaseNode construct = node("reference.frames.construct_frame");
        construct.setInput("input_x_axis", new Vector3d(1, 0, 0));
        construct.setInput("input_y_axis", new Vector3d(2, 0, 0));
        construct.processNode(null);

        assertEquals(Boolean.FALSE, construct.getOutput("output_valid"));
        assertEquals(null, construct.getOutput("output_frame"));
    }

    @Test
    void sphereSurfaceFrameRequiresSphereInputAndSupportsXHint() {
        assertPortType("reference.frames.sphere_surface_frame", "input_sphere", true, NodeDataType.SPHERE);
        assertPortType("reference.frames.sphere_surface_frame", "input_point", true, NodeDataType.POINT);
        assertPortType("reference.frames.sphere_surface_frame", "input_x_hint", true, NodeDataType.VECTOR);

        SphereData sphere = new SphereData(new Vector3d(0, 0, 0), 2.0d);
        BaseNode surface = node("reference.frames.sphere_surface_frame");
        surface.setInput("input_sphere", sphere);
        surface.setInput("input_point", new PointData(2, 0, 0));
        surface.setInput("input_x_hint", new Vector3d(0, 1, 0));
        surface.processNode(null);

        assertEquals(Boolean.TRUE, surface.getOutput("output_valid"));
        FrameData frame = assertInstanceOf(FrameData.class, surface.getOutput("output_frame"));
        assertEquals(1.0d, frame.getXAxis().length(), 1.0e-9d);
        assertEquals(1.0d, frame.getZAxis().length(), 1.0e-9d);
        assertVectorEquals(new Vector3d(1, 0, 0), frame.getZAxis(), 1.0e-9d);
        assertVectorEquals(new Vector3d(0, 1, 0), frame.getXAxis(), 1.0e-9d);

        surface.setInput("input_x_hint", null);
        surface.processNode(null);
        assertEquals(Boolean.TRUE, surface.getOutput("output_valid"));
        FrameData fallback = assertInstanceOf(FrameData.class, surface.getOutput("output_frame"));
        FrameData helper = FrameUtils.fromNormal(new Vector3d(2, 0, 0), new Vector3d(1, 0, 0), null);
        assertNotNull(helper);
        assertVectorEquals(fallback.getXAxis(), helper.getXAxis(), 1.0e-9d);
    }

    @Test
    void sphereSurfaceFrameFailsClosedWithoutSphere() {
        BaseNode surface = node("reference.frames.sphere_surface_frame");
        surface.setInput("input_point", new PointData(1, 0, 0));
        surface.processNode(null);
        assertEquals(Boolean.FALSE, surface.getOutput("output_valid"));
    }

    @Test
    void deconstructFrameEmptyInputFailsClosed() {
        BaseNode deconstruct = node("reference.frames.deconstruct_frame");
        deconstruct.processNode(null);
        assertEquals(Boolean.FALSE, deconstruct.getOutput("output_valid"));
    }

    @Test
    void deconstructFrameDegenerateFrameFailsClosed() {
        FrameData degenerate = new FrameData(
                new Vector3d(0, 0, 0),
                new Vector3d(Double.NaN, 0, 0),
                new Vector3d(0, 1, 0),
                new Vector3d(0, 0, 1)
        );
        BaseNode deconstruct = node("reference.frames.deconstruct_frame");
        deconstruct.setInput("input_frame", degenerate);
        deconstruct.processNode(null);
        assertEquals(Boolean.FALSE, deconstruct.getOutput("output_valid"));
    }

    @Test
    void deconstructFrameCanonicalizesNonOrthonormalAxes() {
        FrameData scaled = new FrameData(
                new Vector3d(1, 2, 3),
                new Vector3d(2, 0, 0),
                new Vector3d(0, 3, 0),
                new Vector3d(0, 0, 4)
        );
        BaseNode deconstruct = node("reference.frames.deconstruct_frame");
        deconstruct.setInput("input_frame", scaled);
        deconstruct.processNode(null);

        assertEquals(Boolean.TRUE, deconstruct.getOutput("output_valid"));
        Vector3d x = assertInstanceOf(Vector3d.class, deconstruct.getOutput("output_x_axis"));
        Vector3d y = assertInstanceOf(Vector3d.class, deconstruct.getOutput("output_y_axis"));
        Vector3d z = assertInstanceOf(Vector3d.class, deconstruct.getOutput("output_z_axis"));
        assertEquals(1.0d, x.length(), 1.0e-9d);
        assertEquals(1.0d, y.length(), 1.0e-9d);
        assertEquals(1.0d, z.length(), 1.0e-9d);
        assertEquals(0.0d, x.dot(y), 1.0e-9d);
    }

    @Test
    void deconstructFramesEmptyOrMixedInvalidEntriesFailClosed() {
        BaseNode deconstruct = node("reference.frames.deconstruct_frames");
        deconstruct.processNode(null);
        assertEquals(Boolean.FALSE, deconstruct.getOutput("output_valid"));
        assertEquals(0, deconstruct.getOutput("output_count"));

        FrameData valid = new FrameData(
                new Vector3d(0, 0, 0),
                new Vector3d(1, 0, 0),
                new Vector3d(0, 1, 0),
                new Vector3d(0, 0, 1)
        );
        deconstruct.setInput("input_frames", List.of(valid, "not-a-frame"));
        deconstruct.processNode(null);
        assertEquals(Boolean.FALSE, deconstruct.getOutput("output_valid"));
        assertEquals(0, deconstruct.getOutput("output_count"));
    }

    @Test
    void deconstructFramesRejectsDegenerateEntry() {
        FrameData valid = new FrameData(
                new Vector3d(0, 0, 0),
                new Vector3d(1, 0, 0),
                new Vector3d(0, 1, 0),
                new Vector3d(0, 0, 1)
        );
        FrameData degenerate = new FrameData(
                new Vector3d(0, 0, 0),
                new Vector3d(0, 0, 0),
                new Vector3d(0, 1, 0),
                new Vector3d(0, 0, 1)
        );
        BaseNode deconstruct = node("reference.frames.deconstruct_frames");
        deconstruct.setInput("input_frames", List.of(valid, degenerate));
        deconstruct.processNode(null);
        assertEquals(Boolean.FALSE, deconstruct.getOutput("output_valid"));
        assertEquals(0, deconstruct.getOutput("output_count"));
    }

    @Test
    void transformFrameHasNoScaleInputAndProducesUnitAxes() {
        assertFalse(hasInputPort("reference.frames.transform_frame", "input_scale"));

        FrameData input = new FrameData(
                new Vector3d(0, 0, 0),
                new Vector3d(1, 0, 0),
                new Vector3d(0, 1, 0),
                new Vector3d(0, 0, 1)
        );
        BaseNode transform = node("reference.frames.transform_frame");
        transform.setInput("input_frame", input);
        transform.setInput("input_rotation_z", 90.0d);
        transform.processNode(null);

        assertEquals(Boolean.TRUE, transform.getOutput("output_valid"));
        FrameData out = assertInstanceOf(FrameData.class, transform.getOutput("output_frame"));
        assertEquals(1.0d, out.getXAxis().length(), 1.0e-9d);
        assertEquals(1.0d, out.getYAxis().length(), 1.0e-9d);
        assertEquals(1.0d, out.getZAxis().length(), 1.0e-9d);
    }

    private static BaseNode node(String typeId) {
        BaseNode node = (BaseNode) registry.createNodeInstance(typeId);
        assertNotNull(node, typeId);
        return node;
    }

    private static void assertPortType(String typeId, String portId, boolean input, NodeDataType expected) {
        INode node = registry.createNodeInstance(typeId);
        assertNotNull(node);
        IPort port = (input ? node.getInputPorts() : node.getOutputPorts()).stream()
                .filter(candidate -> candidate.getId().equals(portId))
                .findFirst()
                .orElseThrow(() -> new AssertionError(typeId + " missing port " + portId));
        assertEquals(expected, port.getDataType(), typeId + "." + portId);
    }

    private static boolean hasInputPort(String typeId, String portId) {
        INode node = registry.createNodeInstance(typeId);
        return node != null && node.getInputPorts().stream().anyMatch(port -> port.getId().equals(portId));
    }

    private static void assertVectorEquals(Vector3d expected, Vector3d actual, double epsilon) {
        assertEquals(expected.x, actual.x, epsilon);
        assertEquals(expected.y, actual.y, epsilon);
        assertEquals(expected.z, actual.z, epsilon);
    }
}

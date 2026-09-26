package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.PlaneUtils;
import org.joml.Vector3d;
import org.joml.Vector4d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reference Planes v1 language fence (Graph V48).
 */
class ReferencePlanesLanguageContractTest {

    private static final Set<String> CANONICAL_IDS = Set.of(
            "reference.planes.world_plane",
            "reference.planes.construct_plane",
            "reference.planes.plane_from_points",
            "reference.planes.box_face_plane",
            "reference.planes.offset_plane",
            "reference.planes.distance_point_to_plane",
            "reference.planes.deconstruct_plane"
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
    void referencePlanesFreezeVersionIsV48() {
        assertEquals(48, GraphFormatVersion.V48);
    }

    @Test
    void exactlySevenCanonicalReferencePlaneNodesRegistered() {
        List<String> ids = registry.getAllNodeIds().stream()
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("reference.planes."))
                .sorted()
                .toList();
        assertEquals(7, ids.size(), "Expected 7 reference.planes nodes: " + ids);
        assertEquals(CANONICAL_IDS, Set.copyOf(ids));
        assertFalse(ids.contains("reference.planes.block_face_plane"));
    }

    @Test
    void referencePlaneNodesHaveUniqueOrderZeroThroughSix() {
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
        assertEquals(7, orders.length);
        for (int i = 0; i < orders.length; i++) {
            assertEquals(i, orders[i], "Expected unique order " + i);
        }
    }

    @Test
    void distancePointToPlanePortsAreTyped() {
        assertPortType("reference.planes.distance_point_to_plane", "input_point", true, NodeDataType.POINT);
        assertPortType("reference.planes.distance_point_to_plane", "input_plane", true, NodeDataType.PLANE);
        assertPortType("reference.planes.distance_point_to_plane", "output_distance", false, NodeDataType.DOUBLE);
        assertPortType("reference.planes.distance_point_to_plane", "output_signed_distance", false, NodeDataType.DOUBLE);
        assertPortType("reference.planes.distance_point_to_plane", "output_valid", false, NodeDataType.BOOLEAN);
    }

    @Test
    void planeDataCanonicalRejectsZeroNormalAndNaNOrigin() {
        assertNull(PlaneData.canonical(new Vector3d(0, 0, 0), new Vector3d(0, 0, 0)));
        assertNull(PlaneData.canonical(new Vector3d(Double.NaN, 0, 0), new Vector3d(0, 1, 0)));
    }

    @Test
    void planeDataFromEquationNormalizesCoefficients() {
        PlaneData plane = PlaneData.fromEquation(new Vector4d(2.0d, 0.0d, 0.0d, -10.0d));
        assertNotNull(plane);
        assertEquals(1.0d, plane.signedDistanceTo(new Vector3d(6.0d, 0.0d, 0.0d)), 1.0e-9d);
        assertEquals(1.0d, plane.getNormal().length(), 1.0e-9d);
    }

    @Test
    void worldPlaneUsesPropertyFallbackWhenOriginUnconnected() {
        BaseNode world = node("reference.planes.world_plane");
        world.processNode(null);
        assertEquals(Boolean.TRUE, world.getOutput("output_valid"));
        assertInstanceOf(PlaneData.class, world.getOutput("output_plane"));
    }

    @Test
    void worldPlaneConnectedInvalidOriginFailsClosed() {
        BaseNode world = node("reference.planes.world_plane");
        world.setInput("input_origin", new PointData(Double.NaN, 0, 0));
        world.processNode(null);
        assertEquals(Boolean.FALSE, world.getOutput("output_valid"));
        assertNull(world.getOutput("output_plane"));
    }

    @Test
    void constructPlaneRequiresValidOriginAndNormal() {
        BaseNode construct = node("reference.planes.construct_plane");
        construct.setInput("input_origin", new PointData(1, 2, 3));
        construct.setInput("input_normal", new Vector3d(0, 1, 0));
        construct.processNode(null);
        assertEquals(Boolean.TRUE, construct.getOutput("output_valid"));
        assertInstanceOf(PlaneData.class, construct.getOutput("output_plane"));
    }

    @Test
    void constructPlaneZeroNormalFailsClosed() {
        BaseNode construct = node("reference.planes.construct_plane");
        construct.setInput("input_origin", new PointData(0, 0, 0));
        construct.setInput("input_normal", new Vector3d(0, 0, 0));
        construct.processNode(null);
        assertEquals(Boolean.FALSE, construct.getOutput("output_valid"));
    }

    @Test
    void planeFromPointsCollinearInputsFailClosed() {
        BaseNode fromPoints = node("reference.planes.plane_from_points");
        fromPoints.setInput("input_point_a", new PointData(0, 0, 0));
        fromPoints.setInput("input_point_b", new PointData(1, 0, 0));
        fromPoints.setInput("input_point_c", new PointData(2, 0, 0));
        fromPoints.processNode(null);
        assertEquals(Boolean.FALSE, fromPoints.getOutput("output_valid"));
    }

    @Test
    void planeFromPointsOrderDeterminesNormalDirection() {
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
    void deconstructPlaneEmptyInputFailsClosed() {
        BaseNode deconstruct = node("reference.planes.deconstruct_plane");
        deconstruct.processNode(null);
        assertEquals(Boolean.FALSE, deconstruct.getOutput("output_valid"));
    }

    @Test
    void deconstructPlaneOutputsCanonicalUnitNormal() {
        PlaneData plane = PlaneData.canonical(new Vector3d(1, 2, 3), new Vector3d(0, 2, 0));
        assertNotNull(plane);

        BaseNode deconstruct = node("reference.planes.deconstruct_plane");
        deconstruct.setInput("input_plane", plane);
        deconstruct.processNode(null);

        assertEquals(Boolean.TRUE, deconstruct.getOutput("output_valid"));
        Vector3d normal = assertInstanceOf(Vector3d.class, deconstruct.getOutput("output_normal"));
        assertEquals(1.0d, normal.length(), 1.0e-9d);
        assertVectorEquals(new Vector3d(0, 1, 0), normal, 1.0e-9d);
    }

    @Test
    void distancePointToPlaneInvalidPlaneFailsClosedWithNaN() {
        BaseNode distance = node("reference.planes.distance_point_to_plane");
        distance.setInput("input_point", new PointData(1, 0, 0));
        distance.processNode(null);
        assertEquals(Boolean.FALSE, distance.getOutput("output_valid"));
        assertEquals(Double.NaN, distance.getOutput("output_distance"));
        assertEquals(Double.NaN, distance.getOutput("output_signed_distance"));
    }

    @Test
    void distancePointToPlaneSignedDistanceFollowsNormalDirection() {
        PlaneData plane = PlaneData.canonical(new Vector3d(5, 0, 0), new Vector3d(1, 0, 0));
        assertNotNull(plane);

        BaseNode distance = node("reference.planes.distance_point_to_plane");
        distance.setInput("input_plane", plane);
        distance.setInput("input_point", new PointData(6, 0, 0));
        distance.processNode(null);

        assertEquals(Boolean.TRUE, distance.getOutput("output_valid"));
        assertEquals(1.0d, distance.getOutput("output_signed_distance"));
        assertEquals(1.0d, distance.getOutput("output_distance"));

        distance.setInput("input_point", new PointData(4, 0, 0));
        distance.processNode(null);
        assertEquals(-1.0d, distance.getOutput("output_signed_distance"));
    }

    @Test
    void offsetPlaneRequiresCanonicalInputPlane() {
        BaseNode offset = node("reference.planes.offset_plane");
        offset.setInput("input_distance", 1.0d);
        offset.processNode(null);
        assertEquals(Boolean.FALSE, offset.getOutput("output_valid"));
    }

    @Test
    void boxFaceToPlaneProducesCanonicalPlane() {
        List<Vector3d> corners = List.of(
                new Vector3d(0, 0, 0),
                new Vector3d(1, 0, 0),
                new Vector3d(1, 1, 0),
                new Vector3d(0, 1, 0)
        );
        BoxFaceData face = new BoxFaceData(
                0,
                "front",
                List.of(0, 1, 2, 3),
                corners,
                new Vector3d(0.5d, 0.5d, 0.0d),
                new Vector3d(0, 0, 1)
        );

        BaseNode boxFace = node("reference.planes.box_face_plane");
        boxFace.setInput("input_face", face);
        boxFace.processNode(null);

        assertEquals(Boolean.TRUE, boxFace.getOutput("output_valid"));
        PlaneData plane = assertInstanceOf(PlaneData.class, boxFace.getOutput("output_plane"));
        assertEquals(1.0d, plane.getNormal().length(), 1.0e-9d);
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

    private static void assertVectorEquals(Vector3d expected, Vector3d actual, double epsilon) {
        assertEquals(expected.x, actual.x, epsilon);
        assertEquals(expected.y, actual.y, epsilon);
        assertEquals(expected.z, actual.z, epsilon);
    }
}

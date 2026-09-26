package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.SphereData;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.joml.Matrix3d;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Freeze fence for Batch 6 Placement language: FRAME placement + array/path frame ports.
 */
class PlacementFamilyContractTest {

    @BeforeAll
    static void init() {
        NodeRegistry registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void placeAndOrientNodesUseFrameLanguage() {
        assertPortType("transform.placement.place_geometry_on_frames", "input_geometry", true, NodeDataType.GEOMETRY);
        assertPortType("transform.placement.place_geometry_on_frames", "input_pivot", true, NodeDataType.POINT);
        assertPortType("transform.placement.place_geometry_on_frames", "input_frame", true, NodeDataType.FRAME);
        assertPortType("transform.placement.place_geometry_on_frames", "input_frames", true, NodeDataType.FRAME_LIST);

        assertPortType("transform.placement.place_geometry_on_plane", "input_plane", true, NodeDataType.PLANE);
        assertPortType("transform.placement.place_geometry_on_plane", "input_x_hint", true, NodeDataType.VECTOR);
        assertPortType("transform.placement.place_geometry_on_plane", "output_frame", false, NodeDataType.FRAME);

        assertPortType("transform.placement.orient_geometry_to_frame", "input_frame", true, NodeDataType.FRAME);
        assertPortType("transform.placement.orient_geometry_to_frame", "input_pivot", true, NodeDataType.POINT);
    }

    @Test
    void pathAndArrayEmitTypedFrames() {
        assertPortType("pattern.linear.path_frames", "output_frames", false, NodeDataType.FRAME_LIST);
        assertPortType("pattern.linear.path_frames", "output_points", false, NodeDataType.POINT_LIST);
        assertPortType("pattern.linear.curve_array", "input_pivot", true, NodeDataType.POINT);
        assertPortType("pattern.linear.curve_array", "output_frames", false, NodeDataType.FRAME_LIST);
        assertPortType("pattern.radial.polar_array", "input_center", true, NodeDataType.POINT);
    }

    @Test
    void placeGeometryOnFrameMovesSphereCenter() {
        BaseNode place = assertInstanceOf(BaseNode.class,
            NodeRegistry.getInstance().createNodeInstance("transform.placement.place_geometry_on_frames"));
        SphereData sphere = new SphereData(new Vector3d(0, 0, 0), 2.0d);
        FrameData frame = new FrameData(
            new Vector3d(10, 0, 0),
            new Vector3d(1, 0, 0),
            new Vector3d(0, 1, 0),
            new Vector3d(0, 0, 1)
        );
        place.setInput("input_geometry", sphere);
        place.setInput("input_pivot", new PointData(0, 0, 0));
        place.setInput("input_frame", frame);
        place.processNode(null);

        assertEquals(Boolean.TRUE, place.getOutput("output_valid"));
        SphereData out = assertInstanceOf(SphereData.class, place.getOutput("output_geometry"));
        assertEquals(10.0d, out.getCenter().x, 1.0e-6d);
        assertEquals(2.0d, out.getRadius(), 1.0e-9d);
    }

    @Test
    void placeGeometryOnPlaneBuildsFrameAndPlaces() {
        BaseNode place = assertInstanceOf(BaseNode.class,
            NodeRegistry.getInstance().createNodeInstance("transform.placement.place_geometry_on_plane"));
        BoxGeometryData box = new BoxGeometryData(new Vector3d(0, 0, 0), new Vector3d(1, 1, 1));
        PlaneData plane = new PlaneData(new Vector3d(0, 5, 0), new Vector3d(0, 1, 0));

        place.setInput("input_geometry", box);
        place.setInput("input_pivot", new PointData(0, 0, 0));
        place.setInput("input_plane", plane);
        place.setInput("input_x_hint", new Vector3d(1, 0, 0));
        place.processNode(null);

        assertEquals(Boolean.TRUE, place.getOutput("output_valid"));
        assertInstanceOf(FrameData.class, place.getOutput("output_frame"));
        assertNotNull(place.getOutput("output_geometry"));
    }

    @Test
    void placeGeometryOnFramesListProducesComposite() {
        BaseNode place = assertInstanceOf(BaseNode.class,
            NodeRegistry.getInstance().createNodeInstance("transform.placement.place_geometry_on_frames"));
        SphereData sphere = new SphereData(new Vector3d(), 1.0d);
        List<FrameData> frames = List.of(
            new FrameData(new Vector3d(1, 0, 0), new Vector3d(1, 0, 0), new Vector3d(0, 1, 0), new Vector3d(0, 0, 1)),
            new FrameData(new Vector3d(3, 0, 0), new Vector3d(1, 0, 0), new Vector3d(0, 1, 0), new Vector3d(0, 0, 1))
        );
        place.setInput("input_geometry", sphere);
        place.setInput("input_frames", frames);
        place.processNode(null);

        assertEquals(Boolean.TRUE, place.getOutput("output_valid"));
        assertEquals(2, place.getOutput("output_count"));
        assertInstanceOf(com.nodecraft.nodesystem.datatypes.CompositeGeometryData.class, place.getOutput("output_geometry"));
    }

    @Test
    void frameRotationMapsLocalUnitAxesToFrameAxes() {
        FrameData frame = FrameData.orthonormal(
            new Vector3d(5, 0, 0),
            new Vector3d(0, 0, -1),
            new Vector3d(0, 1, 0),
            new Vector3d(1, 0, 0)
        );
        assertNotNull(frame);
        Matrix3d rotation = frame.toRotationMatrix();

        Vector3d mappedX = rotation.transform(new Vector3d(1, 0, 0), new Vector3d());
        Vector3d mappedY = rotation.transform(new Vector3d(0, 1, 0), new Vector3d());
        Vector3d mappedZ = rotation.transform(new Vector3d(0, 0, 1), new Vector3d());

        assertVectorEquals(frame.getXAxis(), mappedX, 1.0e-9d);
        assertVectorEquals(frame.getYAxis(), mappedY, 1.0e-9d);
        assertVectorEquals(frame.getZAxis(), mappedZ, 1.0e-9d);
    }

    @Test
    void constructFrameOrthonormalizesScaledAxes() {
        BaseNode construct = assertInstanceOf(BaseNode.class,
            NodeRegistry.getInstance().createNodeInstance("reference.frames.construct_frame"));
        construct.setInput("input_origin", new PointData(0, 0, 0));
        construct.setInput("input_x_axis", new Vector3d(2, 0, 0));
        construct.setInput("input_y_axis", new Vector3d(0, 3, 0));
        construct.processNode(null);

        assertEquals(Boolean.TRUE, construct.getOutput("output_valid"));
        FrameData frame = assertInstanceOf(FrameData.class, construct.getOutput("output_frame"));
        assertEquals(1.0d, frame.getXAxis().length(), 1.0e-9d);
        assertEquals(1.0d, frame.getYAxis().length(), 1.0e-9d);
        assertEquals(1.0d, frame.getZAxis().length(), 1.0e-9d);
        Vector3d cross = new Vector3d(frame.getXAxis()).cross(frame.getYAxis());
        assertVectorEquals(frame.getZAxis(), cross, 1.0e-9d);
    }

    @Test
    void placePivotMapsExactlyToFrameOrigin() {
        BaseNode place = assertInstanceOf(BaseNode.class,
            NodeRegistry.getInstance().createNodeInstance("transform.placement.place_geometry_on_frames"));
        FrameData frame = FrameData.orthonormal(
            new Vector3d(7, 2, -3),
            new Vector3d(0, 0, -1),
            new Vector3d(0, 1, 0),
            new Vector3d(1, 0, 0)
        );
        assertNotNull(frame);
        SphereData sphere = new SphereData(new Vector3d(1, 0, 0), 1.5d);
        place.setInput("input_geometry", sphere);
        place.setInput("input_pivot", new PointData(1, 0, 0));
        place.setInput("input_frame", frame);
        place.processNode(null);

        assertEquals(Boolean.TRUE, place.getOutput("output_valid"));
        SphereData out = assertInstanceOf(SphereData.class, place.getOutput("output_geometry"));
        assertVectorEquals(frame.getOrigin(), out.getCenter(), 1.0e-6d);
    }

    private static void assertVectorEquals(Vector3d expected, Vector3d actual, double eps) {
        assertEquals(expected.x, actual.x, eps);
        assertEquals(expected.y, actual.y, eps);
        assertEquals(expected.z, actual.z, eps);
    }

    private static void assertPortType(String typeId, String portId, boolean input, NodeDataType expected) {
        INode node = NodeRegistry.getInstance().createNodeInstance(typeId);
        assertInstanceOf(INode.class, node);
        IPort port = (input ? node.getInputPorts() : node.getOutputPorts()).stream()
            .filter(candidate -> candidate.getId().equals(portId))
            .findFirst()
            .orElseThrow(() -> new AssertionError(typeId + " missing port " + portId));
        assertEquals(expected, port.getDataType(), typeId + "." + portId);
    }
}

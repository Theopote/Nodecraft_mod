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
        assertPortType("geometry.curves.frame_along_path", "output_frames", false, NodeDataType.FRAME_LIST);
        assertPortType("geometry.curves.frame_along_path", "output_planes", false, NodeDataType.PLANE_LIST);
        assertPortType("pattern.linear.curve_array_geometry", "input_pivot", true, NodeDataType.POINT);
        assertPortType("pattern.linear.curve_array_geometry", "output_frames", false, NodeDataType.FRAME_LIST);
        assertPortType("pattern.radial.polar_array_geometry", "input_center", true, NodeDataType.POINT);
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

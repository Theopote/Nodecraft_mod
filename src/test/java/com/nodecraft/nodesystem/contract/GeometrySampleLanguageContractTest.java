package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Freeze fence for Batch 2 geometry sample language (8 nodes).
 */
class GeometrySampleLanguageContractTest {

    @BeforeAll
    static void init() {
        NodeRegistry registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void spatialPortsAreTypedWithoutAny() {
        assertPortType("geometry.primitives.sphere", "input_center", true, NodeDataType.POINT);
        assertPortType("geometry.primitives.sphere", "output_center", false, NodeDataType.POINT);

        assertPortType("geometry.primitives.cylinder", "input_start", true, NodeDataType.POINT);
        assertPortType("geometry.primitives.cylinder", "input_end", true, NodeDataType.POINT);

        assertPortType("geometry.primitives.cone", "input_base_center", true, NodeDataType.POINT);
        assertPortType("geometry.primitives.cone", "input_apex", true, NodeDataType.POINT);

        assertPortType("geometry.primitives.torus", "input_center", true, NodeDataType.POINT);
        assertPortType("geometry.primitives.torus", "input_axis", true, NodeDataType.VECTOR);

        assertPortType("geometry.primitives.box", "input_center", true, NodeDataType.POINT);
        assertPortType("geometry.primitives.box", "input_size_x", true, NodeDataType.DOUBLE);
        assertPortType("geometry.primitives.box", "output_corners", false, NodeDataType.POINT_LIST);

        for (String profileId : List.of(
                "geometry.profiles.rectangle_profile",
                "geometry.profiles.circle_profile",
                "geometry.profiles.polygon_profile")) {
            assertPortType(profileId, "input_center", true, NodeDataType.POINT);
            assertPortType(profileId, "output_points", false, NodeDataType.POINT_LIST);
            assertPortType(profileId, "output_center", false, NodeDataType.POINT);
            assertPortType(profileId, "output_plane", false, NodeDataType.PLANE);
        }
    }

    @Test
    void sphereDefaultsProduceValidGeometry() {
        BaseNode sphere = (BaseNode) NodeRegistry.getInstance().createNodeInstance("geometry.primitives.sphere");
        assertNotNull(sphere);
        sphere.processNode(null);
        assertEquals(Boolean.TRUE, sphere.getOutput("output_valid"));
        assertInstanceOf(PointData.class, sphere.getOutput("output_center"));
        assertEquals(5.0d, ((Number) sphere.getOutput("output_radius")).doubleValue(), 1e-9);
    }

    @Test
    void rectangleDefaultsToXzPlaneAndUsesPlaneOrigin() {
        BaseNode rectangle = (BaseNode) NodeRegistry.getInstance().createNodeInstance("geometry.profiles.rectangle_profile");
        assertNotNull(rectangle);
        rectangle.processNode(null);
        assertEquals(Boolean.TRUE, rectangle.getOutput("output_valid"));

        PlaneData plane = assertInstanceOf(PlaneData.class, rectangle.getOutput("output_plane"));
        Vector3d normal = plane.getNormal();
        assertTrue(Math.abs(normal.y) > 0.9d, "default plane should be horizontal XZ");

        PointData center = assertInstanceOf(PointData.class, rectangle.getOutput("output_center"));
        assertEquals(0.0d, center.getX(), 1e-9);
        assertEquals(0.0d, center.getY(), 1e-9);
        assertEquals(0.0d, center.getZ(), 1e-9);
    }

    @Test
    void rectangleCenterFallsBackToPlaneOrigin() {
        BaseNode rectangle = (BaseNode) NodeRegistry.getInstance().createNodeInstance("geometry.profiles.rectangle_profile");
        assertNotNull(rectangle);
        PlaneData plane = new PlaneData(new Vector3d(10.0d, 64.0d, 20.0d), new Vector3d(0.0d, 1.0d, 0.0d));
        rectangle.setInput("input_plane", plane);
        rectangle.processNode(null);

        PointData center = assertInstanceOf(PointData.class, rectangle.getOutput("output_center"));
        assertEquals(10.0d, center.getX(), 1e-9);
        assertEquals(64.0d, center.getY(), 1e-9);
        assertEquals(20.0d, center.getZ(), 1e-9);
    }

    @Test
    void boxKeepsContinuousCenterInGeometry() {
        BaseNode box = (BaseNode) NodeRegistry.getInstance().createNodeInstance("geometry.primitives.box");
        assertNotNull(box);
        box.setInput("input_center", new PointData(10.8d, 64.7d, 20.4d));
        box.setInput("input_size_x", 4.0d);
        box.setInput("input_size_y", 4.0d);
        box.setInput("input_size_z", 4.0d);
        box.processNode(null);

        BoxGeometryData geometry = assertInstanceOf(BoxGeometryData.class, box.getOutput("output_box_geometry"));
        Vector3d center = geometry.getCenter();
        assertEquals(10.8d, center.x, 1e-9);
        assertEquals(64.7d, center.y, 1e-9);
        assertEquals(20.4d, center.z, 1e-9);
    }

    @Test
    void torusAxisRejectsPointLikeValuesAtPortLayer() {
        assertFalse(NodeDataType.isConnectableTo(NodeDataType.POINT, NodeDataType.VECTOR));
        assertTrue(NodeDataType.isConnectableTo(NodeDataType.VECTOR, NodeDataType.VECTOR));
    }

    private static void assertPortType(String typeId, String portId, boolean input, NodeDataType expected) {
        INode node = NodeRegistry.getInstance().createNodeInstance(typeId);
        assertNotNull(node, typeId);
        List<? extends IPort> ports = input ? node.getInputPorts() : node.getOutputPorts();
        IPort port = ports.stream().filter(p -> portId.equals(p.getId())).findFirst().orElse(null);
        assertNotNull(port, typeId + " missing port " + portId);
        assertEquals(expected, port.getDataType(), typeId + "." + portId);
        assertFalse(port.getDataType() == NodeDataType.ANY, typeId + "." + portId + " must not be ANY");
    }
}

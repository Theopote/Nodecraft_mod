package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.datatypes.SphereData;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.GenerationLimits;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Freeze fence for Batch 7 Pattern/Array Count + Frame contracts.
 */
class PatternArrayFamilyContractTest {

    @BeforeAll
    static void init() {
        NodeRegistry registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void countMeansTotalEmittedInstances() {
        BaseNode linear = assertInstanceOf(BaseNode.class,
            NodeRegistry.getInstance().createNodeInstance("pattern.linear.linear_array_geometry"));
        SphereData sphere = new SphereData(new Vector3d(0, 0, 0), 1.0d);
        linear.setInput("input_geometry", sphere);
        linear.setInput("input_direction", new Vector3d(1, 0, 0));
        linear.setInput("input_distance", 2.0d);
        linear.setInput("input_count", 4);
        linear.processNode(null);

        assertEquals(Boolean.TRUE, linear.getOutput("output_valid"));
        assertEquals(4, linear.getOutput("output_count"));
        @SuppressWarnings("unchecked")
        List<Object> copies = assertInstanceOf(List.class, linear.getOutput("output_geometries"));
        assertEquals(4, copies.size());
        assertInstanceOf(CompositeGeometryData.class, linear.getOutput("output_geometry"));
    }

    @Test
    void polarFullCircleDoesNotDuplicateOriginal() {
        BaseNode polar = assertInstanceOf(BaseNode.class,
            NodeRegistry.getInstance().createNodeInstance("pattern.radial.polar_array_geometry"));
        SphereData sphere = new SphereData(new Vector3d(2, 0, 0), 0.5d);
        polar.setInput("input_geometry", sphere);
        polar.setInput("input_center", new PointData(0, 0, 0));
        polar.setInput("input_axis", new Vector3d(0, 1, 0));
        polar.setInput("input_count", 4);
        polar.setInput("input_total_angle", 360.0d);
        polar.processNode(null);

        assertEquals(Boolean.TRUE, polar.getOutput("output_valid"));
        assertEquals(4, polar.getOutput("output_count"));
        @SuppressWarnings("unchecked")
        List<Object> copies = assertInstanceOf(List.class, polar.getOutput("output_geometries"));
        assertEquals(4, copies.size());

        SphereData first = assertInstanceOf(SphereData.class, copies.get(0));
        SphereData last = assertInstanceOf(SphereData.class, copies.get(3));
        assertEquals(2.0d, first.getCenter().x, 1.0e-6d);
        assertEquals(0.0d, first.getCenter().z, 1.0e-6d);
        // 270° for i=3 with Count=4 / 360° — not a duplicate of 0°
        assertEquals(0.0d, last.getCenter().x, 1.0e-6d);
        assertEquals(2.0d, last.getCenter().z, 1.0e-6d);
    }

    @Test
    void curveArrayUsesFrameListAndPlacement() {
        assertPortType("pattern.linear.curve_array_geometry", "input_path", true, NodeDataType.PATH);
        assertPortType("pattern.linear.curve_array_geometry", "output_frames", false, NodeDataType.FRAME_LIST);

        BaseNode curve = assertInstanceOf(BaseNode.class,
            NodeRegistry.getInstance().createNodeInstance("pattern.linear.curve_array_geometry"));
        SphereData sphere = new SphereData(new Vector3d(0, 0, 0), 0.5d);
        PolylineData path = new PolylineData(List.of(
            new Vec3d(0, 0, 0),
            new Vec3d(10, 0, 0)
        ));
        curve.setInput("input_geometry", sphere);
        curve.setInput("input_pivot", new PointData(0, 0, 0));
        curve.setInput("input_path", path);
        curve.setInput("input_count", 5);
        curve.setInput("input_up_vector", new Vector3d(0, 1, 0));
        curve.processNode(null);

        assertEquals(Boolean.TRUE, curve.getOutput("output_valid"));
        assertEquals(5, curve.getOutput("output_count"));
        @SuppressWarnings("unchecked")
        List<FrameData> frames = assertInstanceOf(List.class, curve.getOutput("output_frames"));
        assertEquals(5, frames.size());
        assertEquals(0.0d, frames.getFirst().getOrigin().x, 1.0e-6d);
        assertEquals(10.0d, frames.getLast().getOrigin().x, 1.0e-6d);
    }

    @Test
    void pathFramesStayContinuousWithoutBlockFloor() {
        assertPortType("pattern.linear.path_instances", "output_frames", false, NodeDataType.FRAME_LIST);

        BaseNode pathFrames = assertInstanceOf(BaseNode.class,
            NodeRegistry.getInstance().createNodeInstance("pattern.linear.path_instances"));
        PolylineData path = new PolylineData(List.of(
            new Vec3d(1.8, 2.4, 3.9),
            new Vec3d(4.2, 2.4, 5.1)
        ));
        pathFrames.setInput("input_path", path);
        pathFrames.setInput("input_up_vector", new Vector3d(0, 1, 0));
        pathFrames.processNode(null);

        assertEquals(Boolean.TRUE, pathFrames.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<PointData> origins = assertInstanceOf(List.class, pathFrames.getOutput("output_origins"));
        assertFalse(origins.isEmpty());
        PointData first = origins.getFirst();
        assertEquals(1.8d, first.getPosition().x, 1.0e-9d);
        assertEquals(2.4d, first.getPosition().y, 1.0e-9d);
        assertEquals(3.9d, first.getPosition().z, 1.0e-9d);
    }

    @Test
    void geometryInstanceLimitIsLowerThanListLimit() {
        assertTrue(GenerationLimits.MAX_GEOMETRY_INSTANCES < GenerationLimits.MAX_LIST_ELEMENTS);
        assertEquals(16_384, GenerationLimits.MAX_GEOMETRY_INSTANCES);
        assertEquals(16_384, GenerationLimits.clampGeometryInstanceCount(1_000_000));
        assertEquals(0, GenerationLimits.clampGeometryInstanceCount(0));
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

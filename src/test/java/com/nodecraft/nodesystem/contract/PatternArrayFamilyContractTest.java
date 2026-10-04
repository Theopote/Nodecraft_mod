package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.datatypes.SphereData;
import com.nodecraft.nodesystem.datatypes.VectorData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.GenerationLimits;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

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
            NodeRegistry.getInstance().createNodeInstance("pattern.linear.linear_array"));
        SphereData sphere = new SphereData(new Vector3d(0, 0, 0), 1.0d);
        linear.setInput("input_geometry", sphere);
        linear.setNodeState(java.util.Map.of("distance", 2.0d, "count", 4));
        linear.processNode(null);

        assertEquals(Boolean.TRUE, linear.getOutput("output_valid"));
        assertEquals(4, linear.getOutput("output_count"));
        assertInstanceOf(CompositeGeometryData.class, linear.getOutput("output_geometry"));
    }

    @Test
    void curveArrayUsesFrameListAndPlacement() {
        assertPortType("pattern.linear.curve_array", "input_path", true, NodeDataType.PATH);
        assertPortType("pattern.linear.curve_array", "output_frames", false, NodeDataType.FRAME_LIST);

        CurveArrayCountProbe curve = new CurveArrayCountProbe();
        SphereData sphere = new SphereData(new Vector3d(0, 0, 0), 0.5d);
        PolylineData path = new PolylineData(List.of(
            new Vec3d(0, 0, 0),
            new Vec3d(10, 0, 0)
        ));
        curve.setInput("input_geometry", sphere);
        curve.setInput("input_pivot", new PointData(0, 0, 0));
        curve.setInput("input_path", path);
        curve.connectCount(5);
        curve.setInput("input_up_vector", new VectorData(0, 1, 0));
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
        assertPortType("pattern.linear.path_frames", "output_frames", false, NodeDataType.FRAME_LIST);

        BaseNode pathFrames = assertInstanceOf(BaseNode.class,
            NodeRegistry.getInstance().createNodeInstance("pattern.linear.path_frames"));
        PolylineData path = new PolylineData(List.of(
            new Vec3d(1.8, 2.4, 3.9),
            new Vec3d(4.2, 2.4, 5.1)
        ));
        pathFrames.setInput("input_path", path);
        pathFrames.setInput("input_up_vector", new VectorData(0, 1, 0));
        pathFrames.processNode(null);

        assertEquals(Boolean.TRUE, pathFrames.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<PointData> origins = assertInstanceOf(List.class, pathFrames.getOutput("output_points"));
        assertFalse(origins.isEmpty());
        PointData first = origins.getFirst();
        assertEquals(1.8d, first.position().x, 1.0e-9d);
        assertEquals(2.4d, first.position().y, 1.0e-9d);
        assertEquals(3.9d, first.position().z, 1.0e-9d);
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

    private static final class CurveArrayCountProbe
            extends com.nodecraft.nodesystem.nodes.pattern.linear.CurveArrayNode {
        void connectCount(int count) {
            PortStubNode stub = new PortStubNode(NodeDataType.INTEGER);
            BasePort output = (BasePort) stub.getOutputPorts().getFirst();
            BasePort input = (BasePort) getInputPorts().stream()
                .filter(port -> "input_count".equals(port.getId()))
                .findFirst()
                .orElseThrow();
            assertTrue(output.connectTo(input));
            inputValues.put("input_count", count);
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

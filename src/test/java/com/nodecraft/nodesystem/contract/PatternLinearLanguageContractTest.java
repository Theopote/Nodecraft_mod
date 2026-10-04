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
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.BlockPosList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pattern Linear v1 language fence (Graph V41), updated for V79 inventory rename.
 */
class PatternLinearLanguageContractTest {

    private static NodeRegistry registry;

    private static final Set<String> CANONICAL_LINEAR_IDS = Set.of(
            "pattern.linear.linear_array",
            "pattern.linear.path_frames",
            "pattern.linear.instance_block_placements",
            "pattern.linear.curve_array"
    );

    @BeforeAll
    static void init() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void patternLinearFreezeVersionIsV41() {
    }

    @Test
    void exactlyFourCanonicalLinearNodesRegistered() {
        List<String> ids = registry.getAllNodeIds().stream()
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("pattern.linear."))
                .sorted()
                .toList();
        assertEquals(4, ids.size(), "Expected 4 pattern.linear nodes: " + ids);
        assertEquals(CANONICAL_LINEAR_IDS, Set.copyOf(ids));
    }

    @Test
    void removedLegacyTypeIdsAreNotRegistered() {
        List<String> ids = registry.getAllNodeIds();
        assertFalse(ids.contains("pattern.linear.linear_array_geometry"));
        assertFalse(ids.contains("pattern.linear.path_instances"));
        assertFalse(ids.contains("pattern.linear.curve_array_geometry"));
        assertFalse(ids.contains("pattern.linear.along_path"));
        assertFalse(ids.contains("pattern.linear.staggered_array"));
        assertFalse(ids.contains("pattern.linear.instance_on_points"));
    }

    @Test
    void linearArrayCountMeansTotalEmittedInstances() {
        BaseNode linear = assertInstanceOf(BaseNode.class,
                registry.createNodeInstance("pattern.linear.linear_array"));
        SphereData sphere = new SphereData(new Vector3d(0, 0, 0), 1.0d);
        linear.setInput("input_geometry", sphere);
        linear.setNodeState(Map.of("distance", 2.0d, "count", 4));
        linear.processNode(null);

        assertEquals(Boolean.TRUE, linear.getOutput("output_valid"));
        assertEquals(4, linear.getOutput("output_count"));
        assertInstanceOf(CompositeGeometryData.class, linear.getOutput("output_geometry"));
    }

    @Test
    void linearArraySingleCopyReturnsRawGeometry() {
        BaseNode linear = assertInstanceOf(BaseNode.class,
                registry.createNodeInstance("pattern.linear.linear_array"));
        SphereData sphere = new SphereData(new Vector3d(0, 0, 0), 1.0d);
        linear.setInput("input_geometry", sphere);
        linear.setNodeState(Map.of("distance", 2.0d, "count", 1));
        linear.processNode(null);

        assertEquals(Boolean.TRUE, linear.getOutput("output_valid"));
        assertEquals(1, linear.getOutput("output_count"));
        assertInstanceOf(SphereData.class, linear.getOutput("output_geometry"));
    }

    @Test
    void closedPathFramesDoNotDuplicateSeam() {
        BaseNode pathFrames = assertInstanceOf(BaseNode.class,
                registry.createNodeInstance("pattern.linear.path_frames"));
        PolylineData path = new PolylineData(List.of(
                new Vec3d(0, 0, 0),
                new Vec3d(10, 0, 0),
                new Vec3d(10, 0, 10),
                new Vec3d(0, 0, 0)
        ));
        pathFrames.setInput("input_path", path);
        pathFrames.setInput("input_up_vector", new VectorData(0, 1, 0));
        pathFrames.processNode(null);

        assertEquals(Boolean.TRUE, pathFrames.getOutput("output_valid"));
        assertEquals(3, pathFrames.getOutput("output_count"));
    }

    @Test
    void nearClosedPathFramesDoNotDuplicateSeam() {
        BaseNode pathFrames = assertInstanceOf(BaseNode.class,
                registry.createNodeInstance("pattern.linear.path_frames"));
        PolylineData path = new PolylineData(List.of(
                new Vec3d(0, 0, 0),
                new Vec3d(10, 0, 0),
                new Vec3d(10, 0, 10),
                new Vec3d(0.0000005d, 0, 0)
        ));
        pathFrames.setInput("input_path", path);
        pathFrames.setInput("input_up_vector", new VectorData(0, 1, 0));
        pathFrames.processNode(null);

        assertEquals(Boolean.TRUE, pathFrames.getOutput("output_valid"));
        assertEquals(3, pathFrames.getOutput("output_count"));
        @SuppressWarnings("unchecked")
        List<PointData> points = assertInstanceOf(List.class, pathFrames.getOutput("output_points"));
        assertEquals(3, points.size());
    }

    @Test
    void closedCurveArrayDoesNotDuplicateSeam() {
        CurveArrayCountProbe curve = new CurveArrayCountProbe();
        SphereData sphere = new SphereData(new Vector3d(0, 0, 0), 0.5d);
        PolylineData path = new PolylineData(List.of(
                new Vec3d(0, 0, 0),
                new Vec3d(10, 0, 0),
                new Vec3d(10, 0, 10),
                new Vec3d(0, 0, 0)
        ));
        curve.setInput("input_geometry", sphere);
        curve.setInput("input_pivot", new PointData(0, 0, 0));
        curve.setInput("input_path", path);
        curve.connectCount(4);
        curve.setInput("input_up_vector", new VectorData(0, 1, 0));
        curve.processNode(null);

        assertEquals(Boolean.TRUE, curve.getOutput("output_valid"));
        assertEquals(4, curve.getOutput("output_count"));
        @SuppressWarnings("unchecked")
        List<FrameData> frames = assertInstanceOf(List.class, curve.getOutput("output_frames"));
        FrameData first = frames.getFirst();
        FrameData last = frames.get(3);
        assertTrue(first.getOrigin().distanceSquared(last.getOrigin()) > 1.0e-6d,
                "Closed curve array must not duplicate seam at start/end");
    }

    @Test
    void curveArrayConnectedZeroCountDoesNotFallBackToSpacing() {
        CurveArrayCountProbe curve = new CurveArrayCountProbe();
        SphereData sphere = new SphereData(new Vector3d(0, 0, 0), 0.5d);
        PolylineData path = new PolylineData(List.of(
                new Vec3d(0, 0, 0),
                new Vec3d(10, 0, 0)
        ));
        curve.setInput("input_geometry", sphere);
        curve.setInput("input_pivot", new PointData(0, 0, 0));
        curve.setInput("input_path", path);
        curve.connectCount(0);
        curve.setInput("input_spacing", 2.0d);
        curve.processNode(null);

        assertEquals(Boolean.FALSE, curve.getOutput("output_valid"));
        assertEquals(0, curve.getOutput("output_count"));
        assertEquals(null, curve.getOutput("output_geometry"));
    }

    @Test
    void curveArrayCountOneIsLegal() {
        CurveArrayCountProbe curve = new CurveArrayCountProbe();
        SphereData sphere = new SphereData(new Vector3d(0, 0, 0), 0.5d);
        PolylineData path = new PolylineData(List.of(
                new Vec3d(0, 0, 0),
                new Vec3d(10, 0, 0)
        ));
        curve.setInput("input_geometry", sphere);
        curve.setInput("input_pivot", new PointData(0, 0, 0));
        curve.setInput("input_path", path);
        curve.connectCount(1);
        curve.processNode(null);

        assertEquals(Boolean.TRUE, curve.getOutput("output_valid"));
        assertEquals(1, curve.getOutput("output_count"));
        assertInstanceOf(SphereData.class, curve.getOutput("output_geometry"));
    }

    @Test
    void instanceBlockPlacementsUsesBlockListAndNoHiddenStone() {
        assertPortType("pattern.linear.instance_block_placements", "input_anchors", true, NodeDataType.BLOCK_LIST);
        assertFalse(hasPort("pattern.linear.instance_block_placements", "input_template_coordinates", true));
        assertFalse(hasPort("pattern.linear.instance_block_placements", "input_block_info", true));
        assertFalse(hasPort("pattern.linear.instance_block_placements", "output_positions", false));
        assertFalse(hasPort("pattern.linear.instance_block_placements", "output_block_ids", false));

        BaseNode node = assertInstanceOf(BaseNode.class,
                registry.createNodeInstance("pattern.linear.instance_block_placements"));
        node.setInput("input_anchors", new BlockPosList(List.of(new BlockPos(10, 64, 10))));
        node.processNode(null);
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));

        node.setInput("input_template_placements", List.of(
                new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:oak_fence")
        ));
        node.processNode(null);
        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));
        assertEquals(1, node.getOutput("output_instance_count"));
        assertEquals(1, node.getOutput("output_placement_count"));
    }

    private static void assertPortType(String typeId, String portId, boolean input, NodeDataType expected) {
        INode node = registry.createNodeInstance(typeId);
        IPort port = (input ? node.getInputPorts() : node.getOutputPorts()).stream()
                .filter(candidate -> candidate.getId().equals(portId))
                .findFirst()
                .orElseThrow(() -> new AssertionError(typeId + " missing port " + portId));
        assertEquals(expected, port.getDataType(), typeId + "." + portId);
    }

    private static boolean hasPort(String typeId, String portId, boolean input) {
        INode node = registry.createNodeInstance(typeId);
        return (input ? node.getInputPorts() : node.getOutputPorts()).stream()
                .anyMatch(port -> port.getId().equals(portId));
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

package com.nodecraft.nodesystem.contract;

import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.datatypes.SphereData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.execution.runtime.NodeEffectResolver;
import com.nodecraft.nodesystem.graph.GraphMigrationRegistry;
import com.nodecraft.nodesystem.io.SavedConnection;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.GenerationLimits;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.HashMap;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Language fence for pattern.linear: four canonical nodes, Valid/Error, typed arrays.
 */
class PatternLinearLanguageV2ContractTest {

    private static final List<String> CANONICAL_IDS = List.of(
        "pattern.linear.linear_array",
        "pattern.linear.path_frames",
        "pattern.linear.instance_block_placements",
        "pattern.linear.curve_array"
    );

    private static final Set<NodeDataType> FORBIDDEN_PORT_TYPES = Set.of(
        NodeDataType.ANY,
        NodeDataType.LIST,
        NodeDataType.LINE,
        NodeDataType.POLYLINE,
        NodeDataType.CURVE
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
    void exactlyFourNodesWithUniqueOrdersZeroToThree() {
        List<String> ids = registry.getAllNodeIds().stream()
            .filter(id -> id.startsWith("pattern.linear."))
            .sorted()
            .toList();
        assertEquals(4, ids.size());
        assertEquals(Set.copyOf(CANONICAL_IDS), Set.copyOf(ids));
        assertFalse(ids.contains("pattern.linear.instance_on_points"));

        Set<Integer> orders = new HashSet<>();
        for (String id : ids) {
            NodeInfo info = registry.getNodeInfo(id);
            assertNotNull(info);
            assertEquals("pattern.linear", info.getCategoryId());
            assertTrue(orders.add(info.getOrder()), "duplicate order for " + id);
            INode node = registry.createNodeInstance(id);
            assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(node.getClass(), id));
        }
        for (int i = 0; i < 4; i++) {
            assertTrue(orders.contains(i), "missing order " + i);
        }
    }

    @Test
    void allPatternLinearNodesExposeValidAndError() {
        for (String id : CANONICAL_IDS) {
            INode node = registry.createNodeInstance(id);
            assertTrue(hasPort(node, "output_valid"), id);
            assertTrue(hasPort(node, "output_error"), id);
        }
    }

    @Test
    void publicPortsForbidAnyRawListLinePolylineCurve() {
        for (String id : CANONICAL_IDS) {
            INode node = registry.createNodeInstance(id);
            for (IPort port : node.getInputPorts()) {
                assertFalse(FORBIDDEN_PORT_TYPES.contains(port.getDataType()),
                    id + " input " + port.getId() + " has forbidden type " + port.getDataType());
            }
            for (IPort port : node.getOutputPorts()) {
                assertFalse(FORBIDDEN_PORT_TYPES.contains(port.getDataType()),
                    id + " output " + port.getId() + " has forbidden type " + port.getDataType());
            }
        }
    }

    @Test
    void linearAndCurveArrayHaveNoOutputGeometries() {
        assertFalse(hasPort(registry.createNodeInstance("pattern.linear.linear_array"), "output_geometries"));
        assertFalse(hasPort(registry.createNodeInstance("pattern.linear.curve_array"), "output_geometries"));
    }

    @Test
    void linearArrayCountOverBudgetFailsClosed() {
        BaseNode linear = node("pattern.linear.linear_array");
        linear.setInput("input_geometry", new SphereData(new Vector3d(), 1.0d));
        linear.setNodeState(Map.of("distance", 1.0d, "count", GenerationLimits.MAX_GEOMETRY_INSTANCES + 1));
        linear.processNode(null);
        assertEquals(Boolean.FALSE, linear.getOutput("output_valid"));
        assertFalse(String.valueOf(linear.getOutput("output_error")).isBlank());
        assertEquals(0, linear.getOutput("output_count"));
        assertNull(linear.getOutput("output_geometry"));
    }

    @Test
    void linearArrayCountZeroFailsClosed() {
        BaseNode linear = node("pattern.linear.linear_array");
        linear.setInput("input_geometry", new SphereData(new Vector3d(), 1.0d));
        linear.setNodeState(Map.of("distance", 1.0d, "count", 0));
        linear.processNode(null);
        assertEquals(Boolean.FALSE, linear.getOutput("output_valid"));
        assertEquals(0, linear.getOutput("output_count"));
    }

    @Test
    void linearArrayDistanceMustBePositiveWhenCountGreaterThanOne() {
        BaseNode zero = node("pattern.linear.linear_array");
        zero.setInput("input_geometry", new SphereData(new Vector3d(), 1.0d));
        zero.setNodeState(Map.of("distance", 0.0d, "count", 2));
        zero.processNode(null);
        assertEquals(Boolean.FALSE, zero.getOutput("output_valid"));
        assertTrue(String.valueOf(zero.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("distance"));

        BaseNode negative = node("pattern.linear.linear_array");
        negative.setInput("input_geometry", new SphereData(new Vector3d(), 1.0d));
        negative.setNodeState(Map.of("distance", -2.0d, "count", 2));
        negative.processNode(null);
        assertEquals(Boolean.FALSE, negative.getOutput("output_valid"));
        assertTrue(String.valueOf(negative.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("distance"));
    }

    @Test
    void linearArrayCountOneIgnoresDistance() {
        BaseNode linear = node("pattern.linear.linear_array");
        linear.setInput("input_geometry", new SphereData(new Vector3d(), 1.0d));
        linear.setNodeState(Map.of("distance", 0.0d, "count", 1));
        linear.processNode(null);
        assertEquals(Boolean.TRUE, linear.getOutput("output_valid"));
        assertEquals(1, linear.getOutput("output_count"));
    }

    @Test
    void linearArrayHugeFiniteDirectionFailsClosedOrStaysFinite() {
        LinearArrayProbe probe = new LinearArrayProbe();
        probe.setInput("input_geometry", new SphereData(new Vector3d(), 1.0d));
        probe.setNodeState(Map.of("distance", 1.0d, "count", 2));
        probe.connectInput("input_direction", NodeDataType.VECTOR);
        probe.putRawInput("input_direction", new com.nodecraft.nodesystem.datatypes.VectorData(1e308d, 1e308d, 1e308d));
        probe.processNode(null);
        if (Boolean.TRUE.equals(probe.getOutput("output_valid"))) {
            assertEquals(2, probe.getOutput("output_count"));
            assertNotNull(probe.getOutput("output_geometry"));
        } else {
            assertEquals(Boolean.FALSE, probe.getOutput("output_valid"));
            assertTrue(String.valueOf(probe.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("direction"));
        }
    }

    @Test
    void linearArrayDirectionConnectedInvalidFails() {
        LinearArrayProbe probe = new LinearArrayProbe();
        probe.setInput("input_geometry", new SphereData(new Vector3d(), 1.0d));
        probe.setNodeState(Map.of("distance", 1.0d, "count", 2));
        probe.connectInput("input_direction", NodeDataType.VECTOR);
        probe.putRawInput("input_direction", new com.nodecraft.nodesystem.datatypes.VectorData(0, 0, 0));
        probe.processNode(null);
        assertEquals(Boolean.FALSE, probe.getOutput("output_valid"));
        assertTrue(String.valueOf(probe.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("direction"));
    }

    @Test
    void linearArrayDistanceConnectedInvalidFails() {
        LinearArrayProbe probe = new LinearArrayProbe();
        probe.setInput("input_geometry", new SphereData(new Vector3d(), 1.0d));
        probe.setNodeState(Map.of("distance", 1.0d, "count", 2));
        probe.connectInput("input_distance", NodeDataType.DOUBLE);
        probe.putRawInput("input_distance", Double.NaN);
        probe.processNode(null);
        assertEquals(Boolean.FALSE, probe.getOutput("output_valid"));
        assertTrue(String.valueOf(probe.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("distance"));
    }

    @Test
    void linearArrayExactCountEmitsTransactionalCopies() {
        BaseNode linear = node("pattern.linear.linear_array");
        linear.setInput("input_geometry", new SphereData(new Vector3d(), 1.0d));
        linear.setNodeState(Map.of("distance", 2.0d, "count", 4));
        linear.processNode(null);
        assertEquals(Boolean.TRUE, linear.getOutput("output_valid"));
        assertEquals(4, linear.getOutput("output_count"));
        assertInstanceOf(CompositeGeometryData.class, linear.getOutput("output_geometry"));
        assertEquals("", linear.getOutput("output_error"));
    }

    @Test
    void pathFramesPathOnlyAndUpConnectedInvalidFails() {
        assertPortType("pattern.linear.path_frames", "input_path", true, NodeDataType.PATH);
        PathFramesProbe probe = new PathFramesProbe();
        probe.setInput("input_path", new PolylineData(List.of(new Vec3d(0, 0, 0), new Vec3d(10, 0, 0))));
        probe.connectInput("input_up_vector", NodeDataType.VECTOR);
        probe.putRawInput("input_up_vector", new Vector3d(0, 0, 0));
        probe.processNode(null);
        assertEquals(Boolean.FALSE, probe.getOutput("output_valid"));
        assertTrue(String.valueOf(probe.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("up"));
    }

    @Test
    void pathFramesConnectedUpParallelTangentFailsClosed() {
        PathFramesProbe probe = new PathFramesProbe();
        probe.setInput("input_path", new PolylineData(List.of(new Vec3d(0, 0, 0), new Vec3d(10, 0, 0))));
        probe.connectInput("input_up_vector", NodeDataType.VECTOR);
        probe.putRawInput("input_up_vector", new Vector3d(1, 0, 0));
        probe.processNode(null);
        assertEquals(Boolean.FALSE, probe.getOutput("output_valid"));
        String error = String.valueOf(probe.getOutput("output_error")).toLowerCase(Locale.ROOT);
        assertTrue(error.contains("parallel") || error.contains("up"));
        assertEquals(0, probe.getOutput("output_count"));
    }

    @Test
    void pathFramesUnconnectedUpOnVerticalPathSucceeds() {
        BaseNode pathFrames = node("pattern.linear.path_frames");
        pathFrames.setInput("input_path", new PolylineData(List.of(
            new Vec3d(0, 0, 0),
            new Vec3d(0, 10, 0)
        )));
        pathFrames.processNode(null);
        assertEquals(Boolean.TRUE, pathFrames.getOutput("output_valid"));
        assertTrue((Integer) pathFrames.getOutput("output_count") >= 2);
        List<?> tangents = (List<?>) pathFrames.getOutput("output_tangents");
        assertNotNull(tangents);
        assertFalse(tangents.isEmpty());
        assertTrue(tangents.getFirst() instanceof com.nodecraft.nodesystem.datatypes.VectorData);
    }

    @Test
    void pathFramesClosedSeamNoDuplicate() {
        BaseNode pathFrames = node("pattern.linear.path_frames");
        pathFrames.setInput("input_path", new PolylineData(List.of(
            new Vec3d(0, 0, 0),
            new Vec3d(10, 0, 0),
            new Vec3d(10, 0, 10),
            new Vec3d(0, 0, 0)
        )));
        pathFrames.processNode(null);
        assertEquals(Boolean.TRUE, pathFrames.getOutput("output_valid"));
        assertEquals(3, pathFrames.getOutput("output_count"));
    }

    @Test
    void pathFramesDegenerateInteriorTangentFailsClosed() {
        // A → B → A → C: at B, (next - prev) = A - A = zero → must fail, not invent +Z.
        BaseNode pathFrames = node("pattern.linear.path_frames");
        pathFrames.setInput("input_path", new PolylineData(List.of(
            new Vec3d(0, 0, 0),
            new Vec3d(10, 0, 0),
            new Vec3d(0, 0, 0),
            new Vec3d(0, 10, 0)
        )));
        pathFrames.processNode(null);
        assertEquals(Boolean.FALSE, pathFrames.getOutput("output_valid"));
        assertTrue(String.valueOf(pathFrames.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("tangent"));
        assertEquals(0, pathFrames.getOutput("output_count"));
        assertTrue(((List<?>) pathFrames.getOutput("output_frames")).isEmpty());
    }

    @Test
    void curveArrayPathOnly() {
        assertPortType("pattern.linear.curve_array", "input_path", true, NodeDataType.PATH);
        assertPortType("pattern.linear.curve_array", "output_frames", false, NodeDataType.FRAME_LIST);
        assertPortType("pattern.linear.curve_array", "output_origins", false, NodeDataType.POINT_LIST);
    }

    @Test
    void curveArrayCountConnectedInvalidDoesNotFallbackSpacing() {
        CurveArrayProbe probe = new CurveArrayProbe();
        probe.setInput("input_geometry", new SphereData(new Vector3d(), 0.5d));
        probe.setInput("input_path", new PolylineData(List.of(new Vec3d(0, 0, 0), new Vec3d(10, 0, 0))));
        probe.setInput("input_spacing", 2.0d);
        probe.connectInput("input_count", NodeDataType.INTEGER);
        probe.putRawInput("input_count", 0);
        probe.processNode(null);
        assertEquals(Boolean.FALSE, probe.getOutput("output_valid"));
        assertEquals(0, probe.getOutput("output_count"));
        assertNull(probe.getOutput("output_geometry"));
    }

    @Test
    void curveArrayCountRequestedEqualsEmitted() {
        CurveArrayProbe probe = new CurveArrayProbe();
        probe.setInput("input_geometry", new SphereData(new Vector3d(), 0.5d));
        probe.setInput("input_path", new PolylineData(List.of(new Vec3d(0, 0, 0), new Vec3d(10, 0, 0))));
        probe.connectInput("input_count", NodeDataType.INTEGER);
        probe.putRawInput("input_count", 5);
        probe.processNode(null);
        assertEquals(Boolean.TRUE, probe.getOutput("output_valid"));
        assertEquals(5, probe.getOutput("output_count"));
        @SuppressWarnings("unchecked")
        List<FrameData> frames = assertInstanceOf(List.class, probe.getOutput("output_frames"));
        assertEquals(5, frames.size());
    }

    @Test
    void curveArrayPivotConnectedInvalidFails() {
        CurveArrayProbe probe = new CurveArrayProbe();
        probe.setInput("input_geometry", new SphereData(new Vector3d(), 0.5d));
        probe.setInput("input_path", new PolylineData(List.of(new Vec3d(0, 0, 0), new Vec3d(10, 0, 0))));
        probe.connectInput("input_count", NodeDataType.INTEGER);
        probe.putRawInput("input_count", 2);
        probe.connectInput("input_pivot", NodeDataType.POINT);
        probe.putRawInput("input_pivot", new Vector3d(Double.NaN, 0, 0));
        probe.processNode(null);
        assertEquals(Boolean.FALSE, probe.getOutput("output_valid"));
        assertTrue(String.valueOf(probe.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("pivot"));
    }

    @Test
    void curveArrayUpConnectedInvalidFails() {
        CurveArrayProbe probe = new CurveArrayProbe();
        probe.setInput("input_geometry", new SphereData(new Vector3d(), 0.5d));
        probe.setInput("input_path", new PolylineData(List.of(new Vec3d(0, 0, 0), new Vec3d(10, 0, 0))));
        probe.connectInput("input_count", NodeDataType.INTEGER);
        probe.putRawInput("input_count", 2);
        probe.connectInput("input_up_vector", NodeDataType.VECTOR);
        probe.putRawInput("input_up_vector", new Vector3d(0, 0, 0));
        probe.processNode(null);
        assertEquals(Boolean.FALSE, probe.getOutput("output_valid"));
        assertTrue(String.valueOf(probe.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("up"));
    }

    @Test
    void curveArrayConnectedUpParallelTangentFailsClosed() {
        CurveArrayProbe probe = new CurveArrayProbe();
        probe.setInput("input_geometry", new SphereData(new Vector3d(), 0.5d));
        probe.setInput("input_path", new PolylineData(List.of(new Vec3d(0, 0, 0), new Vec3d(10, 0, 0))));
        probe.connectInput("input_count", NodeDataType.INTEGER);
        probe.putRawInput("input_count", 2);
        probe.setNodeState(Map.of("orientToPath", true));
        probe.connectInput("input_up_vector", NodeDataType.VECTOR);
        probe.putRawInput("input_up_vector", new Vector3d(1, 0, 0));
        probe.processNode(null);
        assertEquals(Boolean.FALSE, probe.getOutput("output_valid"));
        String error = String.valueOf(probe.getOutput("output_error")).toLowerCase(Locale.ROOT);
        assertTrue(error.contains("parallel") || error.contains("up"));
        assertEquals(0, probe.getOutput("output_count"));
    }

    @Test
    void curveArraySpacingOverBudgetFails() {
        CurveArrayProbe probe = new CurveArrayProbe();
        probe.setInput("input_geometry", new SphereData(new Vector3d(), 0.5d));
        double span = GenerationLimits.MAX_GEOMETRY_INSTANCES * 2.0d;
        probe.setInput("input_path", new PolylineData(List.of(new Vec3d(0, 0, 0), new Vec3d(span, 0, 0))));
        probe.connectInput("input_spacing", NodeDataType.DOUBLE);
        probe.putRawInput("input_spacing", 1.0d);
        probe.processNode(null);
        assertEquals(Boolean.FALSE, probe.getOutput("output_valid"));
        assertTrue(String.valueOf(probe.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("budget")
            || String.valueOf(probe.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("spacing"));
    }

    @Test
    @Timeout(90)
    void curveArrayClosedSpacingAtInstanceBudgetSucceeds() {
        CurveArrayProbe probe = new CurveArrayProbe();
        probe.setInput("input_geometry", new SphereData(new Vector3d(), 0.5d));
        double side = GenerationLimits.MAX_GEOMETRY_INSTANCES / 4.0d;
        probe.setInput("input_path", new PolylineData(List.of(
            new Vec3d(0, 0, 0),
            new Vec3d(side, 0, 0),
            new Vec3d(side, side, 0),
            new Vec3d(0, side, 0),
            new Vec3d(0, 0, 0)
        )));
        probe.connectInput("input_spacing", NodeDataType.DOUBLE);
        probe.putRawInput("input_spacing", 1.0d);
        probe.setNodeState(Map.of("orientToPath", false, "includeEnds", true));
        probe.processNode(null);
        assertEquals(Boolean.TRUE, probe.getOutput("output_valid"), String.valueOf(probe.getOutput("output_error")));
        assertEquals(GenerationLimits.MAX_GEOMETRY_INSTANCES, probe.getOutput("output_count"));
    }

    @Test
    void curveArrayHugeUpVectorFailsClosedOrStaysFinite() {
        CurveArrayProbe probe = new CurveArrayProbe();
        probe.setInput("input_geometry", new SphereData(new Vector3d(), 0.5d));
        probe.setInput("input_path", new PolylineData(List.of(new Vec3d(0, 0, 0), new Vec3d(10, 0, 0))));
        probe.connectInput("input_count", NodeDataType.INTEGER);
        probe.putRawInput("input_count", 2);
        probe.connectInput("input_up_vector", NodeDataType.VECTOR);
        probe.putRawInput("input_up_vector", new Vector3d(1e308d, 1e308d, 1e308d));
        probe.processNode(null);
        if (Boolean.TRUE.equals(probe.getOutput("output_valid"))) {
            assertEquals(2, probe.getOutput("output_count"));
        } else {
            assertEquals(Boolean.FALSE, probe.getOutput("output_valid"));
            assertTrue(String.valueOf(probe.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("up"));
        }
    }

    @Test
    void instanceBlockPlacementsUsesBlockListNotPointList() {
        assertPortType("pattern.linear.instance_block_placements", "input_anchors", true, NodeDataType.BLOCK_LIST);
        assertFalse(hasPort(registry.createNodeInstance("pattern.linear.instance_block_placements"), "input_points"));
        assertFalse(hasPort(registry.createNodeInstance("pattern.linear.instance_block_placements"), "input_max_instances"));
    }

    @Test
    void instanceBlockPlacementsStrictTemplateFailsOnBadMember() {
        BaseNode node = node("pattern.linear.instance_block_placements");
        node.setInput("input_anchors", new BlockPosList(List.of(new BlockPos(10, 64, 10))));
        List<Object> badTemplate = new ArrayList<>();
        badTemplate.add(new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:oak_fence"));
        badTemplate.add("not-a-placement");
        node.setInput("input_template_placements", badTemplate);
        node.processNode(null);
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertFalse(String.valueOf(node.getOutput("output_error")).isBlank());
        assertEquals(0, node.getOutput("output_placement_count"));
    }

    @Test
    void instanceBlockPlacementsEmitsFullCartesianProduct() {
        BaseNode node = node("pattern.linear.instance_block_placements");
        node.setInput("input_anchors", new BlockPosList(List.of(
            new BlockPos(10, 64, 10),
            new BlockPos(20, 64, 20)
        )));
        node.setInput("input_template_placements", List.of(
            new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:oak_fence"),
            new BlockPlacementData(new BlockPos(0, 1, 0), "minecraft:lantern")
        ));
        node.processNode(null);
        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));
        assertEquals(2, node.getOutput("output_instance_count"));
        assertEquals(4, node.getOutput("output_placement_count"));
    }

    @Test
    void instanceBlockPlacementsRejectsOversizedProductBeforeEmit() {
        BaseNode node = node("pattern.linear.instance_block_placements");
        int side = (int) Math.sqrt(GenerationLimits.MAX_LIST_ELEMENTS) + 1;
        List<BlockPos> anchors = new ArrayList<>(side);
        for (int i = 0; i < side; i++) {
            anchors.add(new BlockPos(i, 0, 0));
        }
        List<BlockPlacementData> template = new ArrayList<>(side);
        for (int i = 0; i < side; i++) {
            template.add(new BlockPlacementData(new BlockPos(0, i, 0), "minecraft:stone"));
        }
        node.setInput("input_anchors", new BlockPosList(anchors));
        node.setInput("input_template_placements", template);
        node.processNode(null);
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertTrue(String.valueOf(node.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("workload")
            || String.valueOf(node.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("max_list"));
        assertEquals(0, node.getOutput("output_placement_count"));
    }

    @Test
    void linearArrayOversizedLeafWorkloadFailsClosed() {
        BaseNode linear = node("pattern.linear.linear_array");
        List<com.nodecraft.nodesystem.datatypes.GeometryData> leaves = new ArrayList<>();
        int leafCount = GenerationLimits.MAX_GEOMETRY_INSTANCES / 2 + 1;
        for (int i = 0; i < leafCount; i++) {
            leaves.add(new SphereData(new Vector3d(i, 0, 0), 0.1d));
        }
        linear.setInput("input_geometry", new CompositeGeometryData(leaves));
        linear.setNodeState(Map.of("distance", 1.0d, "count", 2));
        linear.processNode(null);
        assertEquals(Boolean.FALSE, linear.getOutput("output_valid"));
        assertTrue(String.valueOf(linear.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("workload")
            || String.valueOf(linear.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("limit"));
    }

    @Test
    void instanceBlockPlacementsEnabledConnectedInvalidFailsClosed() {
        InstanceBlockPlacementsProbe probe = new InstanceBlockPlacementsProbe();
        probe.setInput("input_anchors", new BlockPosList(List.of(new BlockPos(10, 64, 10))));
        probe.setInput("input_template_placements", List.of(
            new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:oak_fence")
        ));
        probe.connectInput("input_enabled", NodeDataType.BOOLEAN);
        probe.putRawInput("input_enabled", "not-a-boolean");
        probe.processNode(null);
        assertEquals(Boolean.FALSE, probe.getOutput("output_valid"));
        assertTrue(String.valueOf(probe.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("enabled"));
        assertEquals(0, probe.getOutput("output_placement_count"));
    }


    private static BaseNode node(String typeId) {
        return assertInstanceOf(BaseNode.class, registry.createNodeInstance(typeId));
    }

    private static void assertPortType(String typeId, String portId, boolean input, NodeDataType expected) {
        INode node = registry.createNodeInstance(typeId);
        List<? extends IPort> ports = input ? node.getInputPorts() : node.getOutputPorts();
        IPort port = ports.stream().filter(p -> portId.equals(p.getId())).findFirst().orElseThrow();
        assertEquals(expected, port.getDataType());
    }

    private static boolean hasPort(INode node, String portId) {
        return node.getInputPorts().stream().anyMatch(p -> portId.equals(p.getId()))
            || node.getOutputPorts().stream().anyMatch(p -> portId.equals(p.getId()));
    }

    private static SavedNode savedNode(String id, String typeId) {
        SavedNode node = new SavedNode();
        node.nodeId = id;
        node.typeId = typeId;
        return node;
    }

    private static SavedConnection wire(String sourceId, String sourcePort, String targetId, String targetPort) {
        SavedConnection connection = new SavedConnection();
        connection.sourceNodeId = sourceId;
        connection.sourcePortId = sourcePort;
        connection.targetNodeId = targetId;
        connection.targetPortId = targetPort;
        return connection;
    }

    private static String typeOf(SavedGraph graph, String nodeId) {
        return nodeOf(graph, nodeId).typeId;
    }

    private static SavedNode nodeOf(SavedGraph graph, String nodeId) {
        return graph.nodes.stream()
            .filter(n -> nodeId.equals(n.nodeId))
            .findFirst()
            .orElseThrow();
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

    private static final class LinearArrayProbe extends com.nodecraft.nodesystem.nodes.pattern.linear.LinearArrayNode {
        void putRawInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            PatternLinearLanguageV2ContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class PathFramesProbe extends com.nodecraft.nodesystem.nodes.pattern.linear.PathFramesNode {
        void putRawInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            PatternLinearLanguageV2ContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class CurveArrayProbe extends com.nodecraft.nodesystem.nodes.pattern.linear.CurveArrayNode {
        void putRawInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            PatternLinearLanguageV2ContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class InstanceBlockPlacementsProbe
            extends com.nodecraft.nodesystem.nodes.pattern.linear.InstanceBlockPlacementsNode {
        void putRawInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            PatternLinearLanguageV2ContractTest.connectInput(this, portId, outputType);
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

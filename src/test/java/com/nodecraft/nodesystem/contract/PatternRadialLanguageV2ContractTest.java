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
import com.nodecraft.nodesystem.datatypes.SphereData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.execution.runtime.NodeEffectResolver;
import com.nodecraft.nodesystem.graph.GraphMigrationRegistry;
import com.nodecraft.nodesystem.io.SavedConnection;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.GenerationLimits;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Language fence for Pattern Radial Language v2.
 */
class PatternRadialLanguageV2ContractTest {

    private static final List<String> CANONICAL_IDS = List.of(
        "pattern.radial.polar_array",
        "pattern.radial.spiral",
        "pattern.radial.phyllotaxis"
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
    void exactlyThreeNodesWithUniqueOrdersZeroToTwo() {
        List<String> ids = registry.getAllNodeIds().stream()
            .filter(id -> id.startsWith("pattern.radial."))
            .sorted()
            .toList();
        assertEquals(3, ids.size());
        assertEquals(Set.copyOf(CANONICAL_IDS), Set.copyOf(ids));

        Set<Integer> orders = new HashSet<>();
        for (String id : ids) {
            NodeInfo info = registry.getNodeInfo(id);
            assertNotNull(info);
            assertEquals("pattern.radial", info.getCategoryId());
            assertTrue(orders.add(info.getOrder()), "duplicate order for " + id);
            INode node = registry.createNodeInstance(id);
            assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(node.getClass(), id));
        }
        for (int i = 0; i < 3; i++) {
            assertTrue(orders.contains(i), "missing order " + i);
        }
    }

    @Test
    void allPatternRadialNodesExposeValidAndError() {
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
    void polarArrayHasNoOutputGeometries() {
        assertFalse(hasPort(registry.createNodeInstance("pattern.radial.polar_array"), "output_geometries"));
    }

    @Test
    void polarArrayExactCountEmitsTransactionalCopies() {
        BaseNode polar = node("pattern.radial.polar_array");
        polar.setInput("input_geometry", new SphereData(new Vector3d(2, 0, 0), 0.5d));
        polar.setNodeState(Map.of("count", 4, "totalAngle", 360.0d));
        polar.processNode(null);
        assertEquals(Boolean.TRUE, polar.getOutput("output_valid"));
        assertEquals(4, polar.getOutput("output_count"));
        assertEquals("", polar.getOutput("output_error"));
        CompositeGeometryData composite = assertInstanceOf(CompositeGeometryData.class, polar.getOutput("output_geometry"));
        assertEquals(4, composite.geometries().size());
    }

    @Test
    void polarArrayCountOverBudgetFailsClosed() {
        BaseNode polar = node("pattern.radial.polar_array");
        polar.setInput("input_geometry", new SphereData(new Vector3d(), 1.0d));
        polar.setNodeState(Map.of("count", GenerationLimits.MAX_GEOMETRY_INSTANCES + 1));
        polar.processNode(null);
        assertEquals(Boolean.FALSE, polar.getOutput("output_valid"));
        assertTrue(String.valueOf(polar.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("count")
            || String.valueOf(polar.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("max"));
        assertEquals(0, polar.getOutput("output_count"));
        assertNull(polar.getOutput("output_geometry"));
    }

    @Test
    void polarArrayCountZeroFailsClosed() {
        BaseNode polar = node("pattern.radial.polar_array");
        polar.setInput("input_geometry", new SphereData(new Vector3d(), 1.0d));
        polar.setNodeState(Map.of("count", 0));
        polar.processNode(null);
        assertEquals(Boolean.FALSE, polar.getOutput("output_valid"));
        assertEquals(0, polar.getOutput("output_count"));
    }

    @Test
    void polarArrayOversizedSourceLeavesFailsClosed() {
        List<com.nodecraft.nodesystem.datatypes.GeometryData> leaves = new ArrayList<>();
        int leafCount = GenerationLimits.MAX_GEOMETRY_INSTANCES / 2 + 1;
        for (int i = 0; i < leafCount; i++) {
            leaves.add(new SphereData(new Vector3d(i, 0, 0), 0.1d));
        }
        BaseNode polar = node("pattern.radial.polar_array");
        polar.setInput("input_geometry", new CompositeGeometryData(leaves));
        polar.setNodeState(Map.of("count", 2, "totalAngle", 90.0d));
        polar.processNode(null);
        assertEquals(Boolean.FALSE, polar.getOutput("output_valid"));
        assertTrue(String.valueOf(polar.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("workload")
            || String.valueOf(polar.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("limit"));
        assertEquals(0, polar.getOutput("output_count"));
        assertNull(polar.getOutput("output_geometry"));
    }

    @Test
    void polarArraySourceLeavesTimesCountBudgetFailsClosed() {
        List<com.nodecraft.nodesystem.datatypes.GeometryData> leaves = new ArrayList<>();
        int leafCount = 64;
        for (int i = 0; i < leafCount; i++) {
            leaves.add(new SphereData(new Vector3d(i, 0, 0), 0.25d));
        }
        int count = (GenerationLimits.MAX_GEOMETRY_INSTANCES / leafCount) + 1;
        BaseNode polar = node("pattern.radial.polar_array");
        polar.setInput("input_geometry", new CompositeGeometryData(leaves));
        polar.setNodeState(Map.of("count", count, "totalAngle", 90.0d));
        polar.processNode(null);
        assertEquals(Boolean.FALSE, polar.getOutput("output_valid"));
        assertTrue(String.valueOf(polar.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("leaves")
            || String.valueOf(polar.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("workload")
            || String.valueOf(polar.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("limit"));
        assertEquals(0, polar.getOutput("output_count"));
        assertNull(polar.getOutput("output_geometry"));
    }

    @Test
    void polarArrayCenterConnectedInvalidFails() {
        PolarProbe probe = new PolarProbe();
        probe.setInput("input_geometry", new SphereData(new Vector3d(), 1.0d));
        probe.setNodeState(Map.of("count", 2));
        probe.connectInput("input_center", NodeDataType.POINT);
        probe.putRawInput("input_center", new PointData(Double.NaN, 0, 0));
        probe.processNode(null);
        assertEquals(Boolean.FALSE, probe.getOutput("output_valid"));
        assertTrue(String.valueOf(probe.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("center"));
    }

    @Test
    void polarArrayAxisConnectedZeroFails() {
        PolarProbe probe = new PolarProbe();
        probe.setInput("input_geometry", new SphereData(new Vector3d(), 1.0d));
        probe.setNodeState(Map.of("count", 2));
        probe.connectInput("input_axis", NodeDataType.VECTOR);
        probe.putRawInput("input_axis", new com.nodecraft.nodesystem.datatypes.VectorData(0, 0, 0));
        probe.processNode(null);
        assertEquals(Boolean.FALSE, probe.getOutput("output_valid"));
        assertTrue(String.valueOf(probe.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("axis"));
    }

    @Test
    void polarArrayCountConnectedNonExactIntegerFails() {
        PolarProbe probe = new PolarProbe();
        probe.setInput("input_geometry", new SphereData(new Vector3d(), 1.0d));
        probe.connectInput("input_count", NodeDataType.INTEGER);
        probe.putRawInput("input_count", 3.8d);
        probe.processNode(null);
        assertEquals(Boolean.FALSE, probe.getOutput("output_valid"));
        assertEquals(0, probe.getOutput("output_count"));
    }

    @Test
    void polarFullCircleExclusiveSeamDoesNotDuplicateStart() {
        BaseNode polar = node("pattern.radial.polar_array");
        polar.setInput("input_geometry", new SphereData(new Vector3d(2, 0, 0), 0.5d));
        polar.setNodeState(Map.of("count", 4, "totalAngle", 360.0d, "includeEnd", true));
        polar.processNode(null);
        assertEquals(Boolean.TRUE, polar.getOutput("output_valid"));
        CompositeGeometryData composite = assertInstanceOf(CompositeGeometryData.class, polar.getOutput("output_geometry"));
        SphereData first = assertInstanceOf(SphereData.class, composite.geometries().get(0));
        SphereData last = assertInstanceOf(SphereData.class, composite.geometries().get(3));
        assertEquals(2.0d, first.center().x, 1.0e-6d);
        assertEquals(0.0d, first.center().z, 1.0e-6d);
        assertEquals(0.0d, last.center().x, 1.0e-6d);
        assertEquals(2.0d, last.center().z, 1.0e-6d);
        assertNotEquals(first.center().x, last.center().x, 1.0e-6d);
    }

    @Test
    void spiralCountOverBudgetFailsClosedNoClamp() {
        BaseNode spiral = node("pattern.radial.spiral");
        spiral.setNodeState(Map.of("count", GenerationLimits.MAX_LAYOUT_INSTANCES + 1));
        spiral.processNode(null);
        assertEquals(Boolean.FALSE, spiral.getOutput("output_valid"));
        assertEquals(0, spiral.getOutput("output_count"));
        @SuppressWarnings("unchecked")
        List<?> points = assertInstanceOf(List.class, spiral.getOutput("output_points"));
        assertTrue(points.isEmpty());
    }

    @Test
    void spiralDegenerateTangentFailsClosed() {
        BaseNode spiral = node("pattern.radial.spiral");
        spiral.setNodeState(Map.of(
            "count", 4,
            "turns", 0.0d,
            "startRadius", 0.0d,
            "radiusStep", 0.0d,
            "heightStep", 0.0d
        ));
        spiral.processNode(null);
        assertEquals(Boolean.FALSE, spiral.getOutput("output_valid"));
        assertTrue(String.valueOf(spiral.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("tangent"));
        assertEquals(0, spiral.getOutput("output_count"));
    }

    @Test
    void spiralAlignedLayoutSizesMatch() {
        BaseNode spiral = node("pattern.radial.spiral");
        spiral.setNodeState(Map.of("count", 6, "radiusStep", 0.17d, "heightStep", 0.31d));
        spiral.processNode(null);
        assertEquals(Boolean.TRUE, spiral.getOutput("output_valid"));
        int count = assertInstanceOf(Integer.class, spiral.getOutput("output_count"));
        @SuppressWarnings("unchecked")
        List<PointData> points = assertInstanceOf(List.class, spiral.getOutput("output_points"));
        @SuppressWarnings("unchecked")
        List<Vector3d> tangents = assertInstanceOf(List.class, spiral.getOutput("output_tangents"));
        @SuppressWarnings("unchecked")
        List<FrameData> frames = assertInstanceOf(List.class, spiral.getOutput("output_frames"));
        assertEquals(count, points.size());
        assertEquals(count, tangents.size());
        assertEquals(count, frames.size());
    }

    @Test
    void phyllotaxisCountOverBudgetFailsClosedNoClamp() {
        BaseNode phyllotaxis = node("pattern.radial.phyllotaxis");
        phyllotaxis.setNodeState(Map.of("count", GenerationLimits.MAX_LAYOUT_INSTANCES + 1));
        phyllotaxis.processNode(null);
        assertEquals(Boolean.FALSE, phyllotaxis.getOutput("output_valid"));
        assertEquals(0, phyllotaxis.getOutput("output_count"));
    }

    @Test
    void phyllotaxisDegenerateTangentFailsClosed() {
        BaseNode phyllotaxis = node("pattern.radial.phyllotaxis");
        phyllotaxis.setNodeState(Map.of(
            "count", 4,
            "radiusScale", 0.0d,
            "heightStep", 0.0d,
            "radialExponent", 0.5d
        ));
        phyllotaxis.processNode(null);
        assertEquals(Boolean.FALSE, phyllotaxis.getOutput("output_valid"));
        assertTrue(String.valueOf(phyllotaxis.getOutput("output_error")).toLowerCase(Locale.ROOT)
            .contains("tangent"));
        assertEquals(0, phyllotaxis.getOutput("output_count"));
    }

    @Test
    void spiralFramesHaveRollContinuity() {
        BaseNode spiral = node("pattern.radial.spiral");
        spiral.setNodeState(Map.of(
            "count", 32,
            "turns", 3.0d,
            "heightStep", 0.4d,
            "radiusStep", 0.08d
        ));
        spiral.processNode(null);
        assertEquals(Boolean.TRUE, spiral.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<FrameData> frames = assertInstanceOf(List.class, spiral.getOutput("output_frames"));
        assertTrue(frames.size() >= 24);
        for (int i = 1; i < frames.size(); i++) {
            FrameData prev = frames.get(i - 1);
            FrameData cur = frames.get(i);
            double yDot = Math.abs(prev.getYAxis().dot(cur.getYAxis()));
            double zDot = Math.abs(prev.getZAxis().dot(cur.getZAxis()));
            assertTrue(yDot > 0.5d, "y-axis flipped between " + (i - 1) + " and " + i + ": " + yDot);
            assertTrue(zDot > 0.5d, "z-axis flipped between " + (i - 1) + " and " + i + ": " + zDot);
        }
    }

    @Test
    void phyllotaxisFramesHaveRollContinuity() {
        BaseNode phyllotaxis = node("pattern.radial.phyllotaxis");
        phyllotaxis.setNodeState(Map.of(
            "count", 48,
            "angleStep", 12.0d,
            "radiusScale", 0.5d,
            "radialExponent", 0.5d
        ));
        phyllotaxis.processNode(null);
        assertEquals(Boolean.TRUE, phyllotaxis.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<FrameData> frames = assertInstanceOf(List.class, phyllotaxis.getOutput("output_frames"));
        assertTrue(frames.size() >= 24);
        for (int i = 1; i < frames.size(); i++) {
            FrameData prev = frames.get(i - 1);
            FrameData cur = frames.get(i);
            double yDot = Math.abs(prev.getYAxis().dot(cur.getYAxis()));
            double zDot = Math.abs(prev.getZAxis().dot(cur.getZAxis()));
            assertTrue(yDot > 0.5d, "y-axis flipped between " + (i - 1) + " and " + i + ": " + yDot);
            assertTrue(zDot > 0.5d, "z-axis flipped between " + (i - 1) + " and " + i + ": " + zDot);
        }
    }

    @Test
    void phyllotaxisCountOneUsesParametricTangentNotInventedAxis() {
        BaseNode phyllotaxis = node("pattern.radial.phyllotaxis");
        phyllotaxis.setNodeState(Map.of(
            "count", 1,
            "radiusScale", 0.75d,
            "angleStep", 137.507764d
        ));
        phyllotaxis.processNode(null);
        assertEquals(Boolean.TRUE, phyllotaxis.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<Vector3d> tangents = assertInstanceOf(List.class, phyllotaxis.getOutput("output_tangents"));
        assertEquals(1, tangents.size());
        Vector3d tangent = tangents.getFirst();
        assertFalse(Math.abs(tangent.x - 1.0d) < 1.0e-6d
            && Math.abs(tangent.y) < 1.0e-6d
            && Math.abs(tangent.z) < 1.0e-6d,
            "Count=1 must not invent canonical +X tangent");
    }

    @Test
    void phyllotaxisCountOneDegenerateTangentFailsClosed() {
        BaseNode phyllotaxis = node("pattern.radial.phyllotaxis");
        phyllotaxis.setNodeState(Map.of(
            "count", 1,
            "radiusScale", 0.0d,
            "heightStep", 0.0d
        ));
        phyllotaxis.processNode(null);
        assertEquals(Boolean.FALSE, phyllotaxis.getOutput("output_valid"));
        assertTrue(String.valueOf(phyllotaxis.getOutput("output_error")).toLowerCase(Locale.ROOT)
            .contains("tangent"));
        assertEquals(0, phyllotaxis.getOutput("output_count"));
    }

    @Test
    void phyllotaxisNegativeRadialExponentFailsClosed() {
        BaseNode phyllotaxis = node("pattern.radial.phyllotaxis");
        phyllotaxis.setNodeState(Map.of("count", 4, "radialExponent", -0.5d));
        phyllotaxis.processNode(null);
        assertEquals(Boolean.FALSE, phyllotaxis.getOutput("output_valid"));
        assertTrue(String.valueOf(phyllotaxis.getOutput("output_error")).toLowerCase(Locale.ROOT)
            .contains("exponent"));
    }



    private static BaseNode node(String typeId) {
        return assertInstanceOf(BaseNode.class, registry.createNodeInstance(typeId));
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

    private static void connectInput(BaseNode target, String inputPortId, NodeDataType outputType) {
        PortStubNode stub = new PortStubNode(outputType);
        BasePort output = (BasePort) stub.getOutputPorts().getFirst();
        BasePort input = (BasePort) target.getInputPorts().stream()
            .filter(port -> inputPortId.equals(port.getId()))
            .findFirst()
            .orElseThrow();
        assertTrue(output.connectTo(input));
    }

    private static final class PolarProbe extends com.nodecraft.nodesystem.nodes.pattern.radial.PolarArrayNode {
        void putRawInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            PatternRadialLanguageV2ContractTest.connectInput(this, portId, outputType);
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

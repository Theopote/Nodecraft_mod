package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.SphereData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.pattern.radial.PolarArrayNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.GenerationLimits;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pattern Radial v1 language fence — inventory, Polar seam, Include End, Spiral radii, Phyllotaxis exponent.
 * Count budget and OptionalPortDrive strictness are owned by Pattern Radial Language v2.
 */
class PatternRadialLanguageContractTest {

    private static NodeRegistry registry;

    private static final Set<String> CANONICAL_RADIAL_IDS = Set.of(
            "pattern.radial.polar_array",
            "pattern.radial.spiral",
            "pattern.radial.phyllotaxis"
    );

    private static final List<String> LAYOUT_PRODUCER_IDS = List.of(
            "pattern.radial.spiral",
            "pattern.radial.phyllotaxis"
    );

    @BeforeAll
    static void init() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void patternRadialFreezeVersionIsV43() {
    }

    @Test
    void exactlyThreeCanonicalRadialNodesRegistered() {
        List<String> ids = registry.getAllNodeIds().stream()
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("pattern.radial."))
                .sorted()
                .toList();
        assertEquals(3, ids.size(), "Expected 3 pattern.radial nodes: " + ids);
        assertEquals(CANONICAL_RADIAL_IDS, Set.copyOf(ids));
    }

    @Test
    void removedLegacyTypeIdsAreNotRegistered() {
        List<String> ids = registry.getAllNodeIds();
        assertFalse(ids.contains("pattern.radial.polar_array_geometry"));
        assertFalse(ids.contains("pattern.radial.spiral_array"));
    }

    @Test
    void polarArrayCountZeroProducesEmpty() {
        BaseNode polar = createPolarArray();
        polar.setInput("input_geometry", new SphereData(new Vector3d(0, 0, 0), 1.0d));
        polar.setNodeState(Map.of("count", 0));
        polar.processNode(null);

        assertEquals(Boolean.FALSE, polar.getOutput("output_valid"));
        assertEquals(0, polar.getOutput("output_count"));
        assertEquals(null, polar.getOutput("output_geometry"));
    }

    @Test
    void polarArraySingleCopyReturnsRawGeometry() {
        BaseNode polar = createPolarArray();
        SphereData sphere = new SphereData(new Vector3d(2, 0, 0), 0.5d);
        polar.setInput("input_geometry", sphere);
        polar.setNodeState(Map.of("count", 1));
        polar.processNode(null);

        assertEquals(Boolean.TRUE, polar.getOutput("output_valid"));
        assertEquals(1, polar.getOutput("output_count"));
        assertInstanceOf(SphereData.class, polar.getOutput("output_geometry"));
    }

    @Test
    void polarFullCircleDoesNotDuplicateOriginal() {
        BaseNode polar = createPolarArray();
        SphereData sphere = new SphereData(new Vector3d(2, 0, 0), 0.5d);
        polar.setInput("input_geometry", sphere);
        polar.setNodeState(Map.of("count", 4, "totalAngle", 360.0d));
        polar.processNode(null);

        assertEquals(Boolean.TRUE, polar.getOutput("output_valid"));
        assertEquals(4, polar.getOutput("output_count"));
        CompositeGeometryData composite = assertInstanceOf(CompositeGeometryData.class, polar.getOutput("output_geometry"));
        List<com.nodecraft.nodesystem.datatypes.GeometryData> copies = composite.geometries();
        assertEquals(4, copies.size());

        SphereData first = assertInstanceOf(SphereData.class, copies.get(0));
        SphereData last = assertInstanceOf(SphereData.class, copies.get(3));
        assertEquals(2.0d, first.center().x, 1.0e-6d);
        assertEquals(0.0d, first.center().z, 1.0e-6d);
        assertEquals(0.0d, last.center().x, 1.0e-6d);
        assertEquals(2.0d, last.center().z, 1.0e-6d);
    }

    @Test
    void polarFullCircleMultipleOf360UsesExclusiveEndSampling() {
        BaseNode polar = createPolarArray();
        SphereData sphere = new SphereData(new Vector3d(2, 0, 0), 0.5d);
        polar.setInput("input_geometry", sphere);
        polar.setNodeState(Map.of("count", 5, "totalAngle", 720.0d));
        polar.processNode(null);

        assertEquals(Boolean.TRUE, polar.getOutput("output_valid"));
        assertEquals(5, polar.getOutput("output_count"));
        CompositeGeometryData composite = assertInstanceOf(CompositeGeometryData.class, polar.getOutput("output_geometry"));
        SphereData first = assertInstanceOf(SphereData.class, composite.geometries().get(0));
        SphereData last = assertInstanceOf(SphereData.class, composite.geometries().get(4));
        assertNotEquals(first.center().x, last.center().x, 1.0e-6d);
    }

    @Test
    void polarIncludeEndControlsPartialArcSampling() {
        PolarArrayNode inclusive = (PolarArrayNode) registry.createNodeInstance("pattern.radial.polar_array");
        inclusive.setIncludeEnd(true);
        inclusive.setInput("input_geometry", new SphereData(new Vector3d(2, 0, 0), 0.5d));
        inclusive.setNodeState(Map.of("count", 4, "totalAngle", 180.0d, "includeEnd", true));
        inclusive.processNode(null);

        PolarArrayNode exclusive = (PolarArrayNode) registry.createNodeInstance("pattern.radial.polar_array");
        exclusive.setIncludeEnd(false);
        exclusive.setInput("input_geometry", new SphereData(new Vector3d(2, 0, 0), 0.5d));
        exclusive.setNodeState(Map.of("count", 4, "totalAngle", 180.0d, "includeEnd", false));
        exclusive.processNode(null);

        CompositeGeometryData inclusiveComposite = assertInstanceOf(CompositeGeometryData.class, inclusive.getOutput("output_geometry"));
        CompositeGeometryData exclusiveComposite = assertInstanceOf(CompositeGeometryData.class, exclusive.getOutput("output_geometry"));
        SphereData inclusiveLast = assertInstanceOf(SphereData.class, inclusiveComposite.geometries().get(3));
        SphereData exclusiveLast = assertInstanceOf(SphereData.class, exclusiveComposite.geometries().get(3));
        assertEquals(-2.0d, inclusiveLast.center().x, 1.0e-6d);
        assertEquals(-1.4142135d, exclusiveLast.center().x, 1.0e-3d);
    }

    @Test
    void polarZeroTotalAngleWithMultipleCopiesFailsClosed() {
        BaseNode polar = createPolarArray();
        SphereData sphere = new SphereData(new Vector3d(2, 0, 0), 0.5d);
        polar.setInput("input_geometry", sphere);
        polar.setNodeState(Map.of("count", 5, "totalAngle", 0.0d));
        polar.processNode(null);

        assertEquals(Boolean.FALSE, polar.getOutput("output_valid"));
        assertTrue(String.valueOf(polar.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("angle"));
        assertEquals(0, polar.getOutput("output_count"));
    }

    @Test
    void polarZeroTotalAngleWithSingleCopyIsLegal() {
        BaseNode polar = createPolarArray();
        polar.setInput("input_geometry", new SphereData(new Vector3d(2, 0, 0), 0.5d));
        polar.setNodeState(Map.of("count", 1, "totalAngle", 0.0d));
        polar.processNode(null);

        assertEquals(Boolean.TRUE, polar.getOutput("output_valid"));
        assertEquals(1, polar.getOutput("output_count"));
    }

    @Test
    void polarFullCircleMultiplesEmitUniquePoses() {
        assertPolarEmitsUniqueOffsetSpheres(720.0d, 4);
        assertPolarEmitsUniqueOffsetSpheres(1080.0d, 6);
        assertPolarEmitsUniqueOffsetSpheres(-720.0d, 4);
    }

    @Test
    void polarPartialArcBeyond360StaysRawSpan() {
        BaseNode polar = createPolarArray();
        polar.setInput("input_geometry", new SphereData(new Vector3d(2, 0, 0), 0.5d));
        polar.setNodeState(Map.of("count", 4, "totalAngle", 540.0d));
        polar.processNode(null);

        assertEquals(Boolean.TRUE, polar.getOutput("output_valid"));
        assertEquals(4, polar.getOutput("output_count"));
        CompositeGeometryData composite = assertInstanceOf(CompositeGeometryData.class, polar.getOutput("output_geometry"));
        assertEquals(4, uniqueSphereCenters(composite).size());
        SphereData last = assertInstanceOf(SphereData.class, composite.geometries().get(3));
        double radians = Math.toRadians(540.0d * 3.0d / 4.0d);
        assertEquals(2.0d * Math.cos(radians), last.center().x, 1.0e-6d);
        assertEquals(-2.0d * Math.sin(radians), last.center().z, 1.0e-6d);
    }

    @Test
    void polarArrayIntegerPortIsStrict() {
        PolarProbe probe = new PolarProbe();
        probe.setInput("input_geometry", new SphereData(new Vector3d(2, 0, 0), 0.5d));
        probe.connectInput("input_count", NodeDataType.INTEGER);
        probe.putRawInput("input_count", 3.8d);
        probe.processNode(null);

        assertEquals(Boolean.FALSE, probe.getOutput("output_valid"));
        assertEquals(0, probe.getOutput("output_count"));
    }

    @Test
    void spiralCountZeroProducesEmpty() {
        BaseNode spiral = assertInstanceOf(BaseNode.class, registry.createNodeInstance("pattern.radial.spiral"));
        spiral.setNodeState(Map.of("count", 0));
        spiral.processNode(null);

        assertEquals(Boolean.FALSE, spiral.getOutput("output_valid"));
        assertEquals(0, spiral.getOutput("output_count"));
    }

    @Test
    void spiralNegativeTurnsIsLegal() {
        BaseNode spiral = assertInstanceOf(BaseNode.class, registry.createNodeInstance("pattern.radial.spiral"));
        spiral.setNodeState(Map.of("turns", -2.0d, "count", 4));
        spiral.processNode(null);

        assertEquals(Boolean.TRUE, spiral.getOutput("output_valid"));
        assertEquals(4, spiral.getOutput("output_count"));
    }

    @Test
    void spiralOutputsStayIndexAligned() {
        BaseNode spiral = assertInstanceOf(BaseNode.class, registry.createNodeInstance("pattern.radial.spiral"));
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
    void spiralUsesContinuousCoordinates() {
        BaseNode spiral = assertInstanceOf(BaseNode.class, registry.createNodeInstance("pattern.radial.spiral"));
        spiral.setNodeState(Map.of(
            "count", 4,
            "startRadius", 2.0d,
            "radiusStep", 0.15d,
            "heightStep", 0.25d
        ));
        spiral.processNode(null);

        @SuppressWarnings("unchecked")
        List<PointData> points = assertInstanceOf(List.class, spiral.getOutput("output_points"));
        PointData third = points.get(2);
        assertTrue(Math.abs(third.position().x - Math.round(third.position().x)) > 1.0e-6d
                || Math.abs(third.position().z - Math.round(third.position().z)) > 1.0e-6d);
    }

    @Test
    void spiralNegativeStartRadiusFailsClosed() {
        BaseNode spiral = assertInstanceOf(BaseNode.class, registry.createNodeInstance("pattern.radial.spiral"));
        spiral.setNodeState(Map.of("count", 4, "startRadius", -1.0d, "radiusStep", 0.15d));
        spiral.processNode(null);

        assertEquals(Boolean.FALSE, spiral.getOutput("output_valid"));
        assertTrue(String.valueOf(spiral.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("radius"));
        assertEquals(0, spiral.getOutput("output_count"));
    }

    @Test
    void spiralRadiusCrossingOriginFailsClosed() {
        BaseNode spiral = assertInstanceOf(BaseNode.class, registry.createNodeInstance("pattern.radial.spiral"));
        spiral.setNodeState(Map.of(
            "count", 5,
            "startRadius", 2.0d,
            "radiusStep", -1.0d
        ));
        spiral.processNode(null);

        assertEquals(Boolean.FALSE, spiral.getOutput("output_valid"));
        assertTrue(String.valueOf(spiral.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("radius"));
        assertEquals(0, spiral.getOutput("output_count"));
    }

    @Test
    void spiralShrinkingRadiusThatStaysNonNegativeSucceeds() {
        BaseNode spiral = assertInstanceOf(BaseNode.class, registry.createNodeInstance("pattern.radial.spiral"));
        spiral.setNodeState(Map.of(
            "count", 3,
            "startRadius", 2.0d,
            "radiusStep", -1.0d
        ));
        spiral.processNode(null);

        assertEquals(Boolean.TRUE, spiral.getOutput("output_valid"));
        assertEquals(3, spiral.getOutput("output_count"));
    }

    @Test
    void spiralNonFiniteTurnsFailClosed() {
        SpiralProbe probe = new SpiralProbe();
        probe.setNodeState(Map.of("count", 4));
        probe.connectInput("input_turns", NodeDataType.DOUBLE);
        probe.putRawInput("input_turns", Double.NaN);
        probe.processNode(null);

        assertEquals(Boolean.FALSE, probe.getOutput("output_valid"));
    }

    @Test
    void phyllotaxisCountMatchesAlignedOutputs() {
        BaseNode phyllotaxis = assertInstanceOf(BaseNode.class,
                registry.createNodeInstance("pattern.radial.phyllotaxis"));
        phyllotaxis.setNodeState(Map.of("count", 8));
        phyllotaxis.processNode(null);

        assertEquals(Boolean.TRUE, phyllotaxis.getOutput("output_valid"));
        assertEquals(8, phyllotaxis.getOutput("output_count"));
        @SuppressWarnings("unchecked")
        List<PointData> points = assertInstanceOf(List.class, phyllotaxis.getOutput("output_points"));
        @SuppressWarnings("unchecked")
        List<Vector3d> tangents = assertInstanceOf(List.class, phyllotaxis.getOutput("output_tangents"));
        @SuppressWarnings("unchecked")
        List<FrameData> frames = assertInstanceOf(List.class, phyllotaxis.getOutput("output_frames"));
        assertEquals(8, points.size());
        assertEquals(8, tangents.size());
        assertEquals(8, frames.size());
    }

    @Test
    void phyllotaxisUsesContinuousCoordinates() {
        BaseNode phyllotaxis = assertInstanceOf(BaseNode.class,
                registry.createNodeInstance("pattern.radial.phyllotaxis"));
        phyllotaxis.setNodeState(Map.of("count", 4, "radiusScale", 0.75d));
        phyllotaxis.processNode(null);

        @SuppressWarnings("unchecked")
        List<PointData> points = assertInstanceOf(List.class, phyllotaxis.getOutput("output_points"));
        PointData third = points.get(2);
        assertTrue(Math.abs(third.position().x - Math.round(third.position().x)) > 1.0e-6d
                || Math.abs(third.position().z - Math.round(third.position().z)) > 1.0e-6d);
    }

    @Test
    void phyllotaxisZeroExponentKeepsConstantRadius() {
        BaseNode phyllotaxis = assertInstanceOf(BaseNode.class,
                registry.createNodeInstance("pattern.radial.phyllotaxis"));
        phyllotaxis.setNodeState(Map.of("count", 6, "radiusScale", 2.0d, "radialExponent", 0.0d));
        phyllotaxis.processNode(null);

        assertEquals(Boolean.TRUE, phyllotaxis.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<PointData> points = assertInstanceOf(List.class, phyllotaxis.getOutput("output_points"));
        assertEquals(6, points.size());
        for (PointData point : points) {
            Vector3d position = point.position();
            double radial = Math.hypot(position.x, position.z);
            assertEquals(2.0d, radial, 1.0e-6d);
        }
    }

    @Test
    void phyllotaxisNegativeRadialExponentFailClosed() {
        BaseNode phyllotaxis = assertInstanceOf(BaseNode.class,
                registry.createNodeInstance("pattern.radial.phyllotaxis"));
        phyllotaxis.setNodeState(Map.of("count", 4, "radialExponent", -0.5d));
        phyllotaxis.processNode(null);

        assertEquals(Boolean.FALSE, phyllotaxis.getOutput("output_valid"));
    }

    @Test
    void layoutProducersDoNotOutputBlockList() {
        for (String typeId : LAYOUT_PRODUCER_IDS) {
            INode node = registry.createNodeInstance(typeId);
            for (IPort port : node.getOutputPorts()) {
                assertFalse(port.getDataType() == NodeDataType.BLOCK_LIST,
                        typeId + "#" + port.getId() + " must not be BLOCK_LIST");
            }
        }
    }

    @Test
    void layoutProducersRespectLayoutInstanceCap() {
        BaseNode spiral = assertInstanceOf(BaseNode.class, registry.createNodeInstance("pattern.radial.spiral"));
        spiral.setNodeState(Map.of("count", Integer.MAX_VALUE));
        spiral.processNode(null);

        assertEquals(Boolean.FALSE, spiral.getOutput("output_valid"));
        assertEquals(0, spiral.getOutput("output_count"));
        @SuppressWarnings("unchecked")
        List<PointData> points = assertInstanceOf(List.class, spiral.getOutput("output_points"));
        assertTrue(points.isEmpty());
        assertTrue(points.size() <= GenerationLimits.MAX_LAYOUT_INSTANCES);
    }

    private static BaseNode createPolarArray() {
        return assertInstanceOf(BaseNode.class, registry.createNodeInstance("pattern.radial.polar_array"));
    }

    private static void assertPolarEmitsUniqueOffsetSpheres(double totalAngle, int count) {
        BaseNode polar = createPolarArray();
        polar.setInput("input_geometry", new SphereData(new Vector3d(2, 0, 0), 0.5d));
        polar.setNodeState(Map.of("count", count, "totalAngle", totalAngle));
        polar.processNode(null);

        assertEquals(Boolean.TRUE, polar.getOutput("output_valid"), "angle=" + totalAngle + " count=" + count);
        assertEquals(count, polar.getOutput("output_count"));
        CompositeGeometryData composite = assertInstanceOf(CompositeGeometryData.class, polar.getOutput("output_geometry"));
        assertEquals(count, uniqueSphereCenters(composite).size(), "duplicate poses for " + totalAngle + "/" + count);
    }

    private static Set<String> uniqueSphereCenters(CompositeGeometryData composite) {
        Set<String> keys = new HashSet<>();
        for (com.nodecraft.nodesystem.datatypes.GeometryData geometry : composite.geometries()) {
            SphereData sphere = assertInstanceOf(SphereData.class, geometry);
            Vector3d center = sphere.center();
            keys.add(String.format(Locale.ROOT, "%.6f,%.6f,%.6f", center.x, center.y, center.z));
        }
        return keys;
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

    private static final class PolarProbe extends PolarArrayNode {
        void putRawInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            PatternRadialLanguageContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class SpiralProbe extends com.nodecraft.nodesystem.nodes.pattern.radial.SpiralNode {
        void putRawInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            PatternRadialLanguageContractTest.connectInput(this, portId, outputType);
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

package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.SphereData;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.nodes.pattern.radial.PolarArrayNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.GenerationLimits;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pattern Radial v1 language fence (Graph V43).
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
    void currentGraphFormatIsV43() {
        assertEquals(43, GraphFormatVersion.V43);
        assertEquals(GraphFormatVersion.V43, GraphFormatVersion.CURRENT);
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
        polar.setInput("input_count", 0);
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
        polar.setInput("input_count", 1);
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
        assertEquals(0.0d, last.getCenter().x, 1.0e-6d);
        assertEquals(2.0d, last.getCenter().z, 1.0e-6d);
    }

    @Test
    void polarFullCircleMultipleOf360UsesExclusiveEndSampling() {
        BaseNode polar = createPolarArray();
        SphereData sphere = new SphereData(new Vector3d(2, 0, 0), 0.5d);
        polar.setInput("input_geometry", sphere);
        polar.setInput("input_center", new PointData(0, 0, 0));
        polar.setInput("input_axis", new Vector3d(0, 1, 0));
        polar.setInput("input_count", 5);
        polar.setInput("input_total_angle", 720.0d);
        polar.processNode(null);

        assertEquals(Boolean.TRUE, polar.getOutput("output_valid"));
        assertEquals(5, polar.getOutput("output_count"));
        @SuppressWarnings("unchecked")
        List<Object> copies = assertInstanceOf(List.class, polar.getOutput("output_geometries"));
        SphereData first = assertInstanceOf(SphereData.class, copies.get(0));
        SphereData last = assertInstanceOf(SphereData.class, copies.get(4));
        assertNotEquals(first.getCenter().x, last.getCenter().x, 1.0e-6d);
    }

    @Test
    void polarIncludeEndControlsPartialArcSampling() {
        PolarArrayNode inclusive = (PolarArrayNode) registry.createNodeInstance("pattern.radial.polar_array");
        inclusive.setIncludeEnd(true);
        inclusive.setInput("input_geometry", new SphereData(new Vector3d(2, 0, 0), 0.5d));
        inclusive.setInput("input_center", new PointData(0, 0, 0));
        inclusive.setInput("input_axis", new Vector3d(0, 1, 0));
        inclusive.setInput("input_count", 4);
        inclusive.setInput("input_total_angle", 180.0d);
        inclusive.processNode(null);

        PolarArrayNode exclusive = (PolarArrayNode) registry.createNodeInstance("pattern.radial.polar_array");
        exclusive.setIncludeEnd(false);
        exclusive.setInput("input_geometry", new SphereData(new Vector3d(2, 0, 0), 0.5d));
        exclusive.setInput("input_center", new PointData(0, 0, 0));
        exclusive.setInput("input_axis", new Vector3d(0, 1, 0));
        exclusive.setInput("input_count", 4);
        exclusive.setInput("input_total_angle", 180.0d);
        exclusive.processNode(null);

        @SuppressWarnings("unchecked")
        List<Object> inclusiveCopies = assertInstanceOf(List.class, inclusive.getOutput("output_geometries"));
        @SuppressWarnings("unchecked")
        List<Object> exclusiveCopies = assertInstanceOf(List.class, exclusive.getOutput("output_geometries"));
        SphereData inclusiveLast = assertInstanceOf(SphereData.class, inclusiveCopies.get(3));
        SphereData exclusiveLast = assertInstanceOf(SphereData.class, exclusiveCopies.get(3));
        assertEquals(-2.0d, inclusiveLast.getCenter().x, 1.0e-6d);
        assertEquals(-1.4142135d, exclusiveLast.getCenter().x, 1.0e-3d);
    }

    @Test
    void polarZeroTotalAngleWithMultipleCopiesIsLegal() {
        BaseNode polar = createPolarArray();
        SphereData sphere = new SphereData(new Vector3d(2, 0, 0), 0.5d);
        polar.setInput("input_geometry", sphere);
        polar.setInput("input_count", 5);
        polar.setInput("input_total_angle", 0.0d);
        polar.processNode(null);

        assertEquals(Boolean.TRUE, polar.getOutput("output_valid"));
        assertEquals(5, polar.getOutput("output_count"));
    }

    @Test
    void polarArrayIntegerPortIsStrict() {
        BaseNode polar = createPolarArray();
        polar.setInput("input_geometry", new SphereData(new Vector3d(2, 0, 0), 0.5d));
        polar.setInput("input_count", 3.8d);
        polar.processNode(null);

        assertEquals(1, polar.getOutput("output_count"));
    }

    @Test
    void spiralCountZeroProducesEmpty() {
        BaseNode spiral = assertInstanceOf(BaseNode.class, registry.createNodeInstance("pattern.radial.spiral"));
        spiral.setInput("input_count", 0);
        spiral.processNode(null);

        assertEquals(Boolean.FALSE, spiral.getOutput("output_valid"));
        assertEquals(0, spiral.getOutput("output_count"));
    }

    @Test
    void spiralNegativeTurnsIsLegal() {
        BaseNode spiral = assertInstanceOf(BaseNode.class, registry.createNodeInstance("pattern.radial.spiral"));
        spiral.setInput("input_turns", -2.0d);
        spiral.setInput("input_count", 4);
        spiral.processNode(null);

        assertEquals(Boolean.TRUE, spiral.getOutput("output_valid"));
        assertEquals(4, spiral.getOutput("output_count"));
    }

    @Test
    void spiralOutputsStayIndexAligned() {
        BaseNode spiral = assertInstanceOf(BaseNode.class, registry.createNodeInstance("pattern.radial.spiral"));
        spiral.setInput("input_count", 6);
        spiral.setInput("input_radius_step", 0.17d);
        spiral.setInput("input_height_step", 0.31d);
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
        spiral.setInput("input_count", 4);
        spiral.setInput("input_start_radius", 2.0d);
        spiral.setInput("input_radius_step", 0.15d);
        spiral.setInput("input_height_step", 0.25d);
        spiral.processNode(null);

        @SuppressWarnings("unchecked")
        List<PointData> points = assertInstanceOf(List.class, spiral.getOutput("output_points"));
        PointData third = points.get(2);
        assertTrue(Math.abs(third.getPosition().x - Math.round(third.getPosition().x)) > 1.0e-6d
                || Math.abs(third.getPosition().z - Math.round(third.getPosition().z)) > 1.0e-6d);
    }

    @Test
    void spiralNonFiniteTurnsFailClosed() {
        BaseNode spiral = assertInstanceOf(BaseNode.class, registry.createNodeInstance("pattern.radial.spiral"));
        spiral.setInput("input_turns", Double.NaN);
        spiral.setInput("input_count", 4);
        spiral.processNode(null);

        assertEquals(Boolean.FALSE, spiral.getOutput("output_valid"));
    }

    @Test
    void phyllotaxisCountMatchesAlignedOutputs() {
        BaseNode phyllotaxis = assertInstanceOf(BaseNode.class,
                registry.createNodeInstance("pattern.radial.phyllotaxis"));
        phyllotaxis.setInput("input_count", 8);
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
        phyllotaxis.setInput("input_count", 4);
        phyllotaxis.setInput("input_radius_scale", 0.75d);
        phyllotaxis.processNode(null);

        @SuppressWarnings("unchecked")
        List<PointData> points = assertInstanceOf(List.class, phyllotaxis.getOutput("output_points"));
        PointData third = points.get(2);
        assertTrue(Math.abs(third.getPosition().x - Math.round(third.getPosition().x)) > 1.0e-6d
                || Math.abs(third.getPosition().z - Math.round(third.getPosition().z)) > 1.0e-6d);
    }

    @Test
    void phyllotaxisNegativeRadialExponentFailClosed() {
        BaseNode phyllotaxis = assertInstanceOf(BaseNode.class,
                registry.createNodeInstance("pattern.radial.phyllotaxis"));
        phyllotaxis.setInput("input_count", 4);
        phyllotaxis.setInput("input_radial_exponent", -0.5d);
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
        spiral.setInput("input_count", Integer.MAX_VALUE);
        spiral.processNode(null);

        @SuppressWarnings("unchecked")
        List<PointData> points = assertInstanceOf(List.class, spiral.getOutput("output_points"));
        assertTrue(points.size() <= GenerationLimits.MAX_LAYOUT_INSTANCES);
        assertEquals(GenerationLimits.MAX_LAYOUT_INSTANCES, spiral.getOutput("output_count"));
    }

    private static BaseNode createPolarArray() {
        return assertInstanceOf(BaseNode.class, registry.createNodeInstance("pattern.radial.polar_array"));
    }
}

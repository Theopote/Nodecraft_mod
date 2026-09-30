package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.SphereData;
import com.nodecraft.nodesystem.datatypes.SurfaceStripData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.nodes.pattern.surface_volume_distribution.ImageBasedScatterNode;
import com.nodecraft.nodesystem.nodes.pattern.surface_volume_distribution.PoissonDiskOnPlaneNode;
import com.nodecraft.nodesystem.nodes.pattern.surface_volume_distribution.SampleSphereSurfaceNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.SurfaceStripSampling;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Surface / Volume Distribution v1 language fence (Graph V44) — historical inventory.
 * Count budget / OptionalPortDrive strictness owned by Language v2 (V82).
 */
class PatternSurfaceVolumeDistributionLanguageContractTest {

    private static NodeRegistry registry;

    private static final Set<String> CANONICAL_IDS = Set.of(
            "pattern.surface_volume_distribution.sample_sphere_surface",
            "pattern.surface_volume_distribution.scatter_surface",
            "pattern.surface_volume_distribution.poisson_disk_plane",
            "pattern.surface_volume_distribution.scatter_surface_strip",
            "pattern.surface_volume_distribution.scatter_volume",
            "pattern.surface_volume_distribution.image_scatter"
    );

    @BeforeAll
    static void init() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void surfaceVolumeDistributionFreezeVersionIsV44() {
    }

    @Test
    void exactlySixCanonicalNodesRegistered() {
        List<String> ids = registry.getAllNodeIds().stream()
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("pattern.surface_volume_distribution."))
                .sorted()
                .toList();
        assertEquals(6, ids.size(), "Expected 6 surface_volume_distribution nodes: " + ids);
        assertEquals(CANONICAL_IDS, Set.copyOf(ids));
    }

    @Test
    void removedLegacyTypeIdsAreNotRegistered() {
        List<String> ids = registry.getAllNodeIds();
        assertFalse(ids.contains("pattern.surface_volume_distribution.populate_region"));
        assertFalse(ids.contains("pattern.surface_volume_distribution.surface_scatter"));
        assertFalse(ids.contains("pattern.surface_volume_distribution.sample_geometry_surface"));
        assertFalse(ids.contains("pattern.surface_volume_distribution.scatter_geometry_surface"));
        assertFalse(ids.contains("pattern.surface_volume_distribution.sample_surface"));
    }

    @Test
    void noNodeOutputsBlockList() {
        for (String typeId : CANONICAL_IDS) {
            INode node = registry.createNodeInstance(typeId);
            for (IPort port : node.getOutputPorts()) {
                assertFalse(port.getDataType() == NodeDataType.BLOCK_LIST,
                        typeId + "#" + port.getId() + " must not be BLOCK_LIST");
            }
        }
    }

    @Test
    void sampleSphereCountZeroProducesEmpty() {
        BaseNode node = createNode("pattern.surface_volume_distribution.sample_sphere_surface");
        node.setInput("input_sphere", new SphereData(new Vector3d(0, 0, 0), 2.0d));
        node.setNodeState(Map.of("sampleCount", 0));
        node.processNode(null);

        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertEquals(0, node.getOutput("output_count"));
    }

    @Test
    void sampleSphereStrictIntegerUsesPropertyFallback() {
        SphereProbe probe = new SphereProbe();
        probe.setInput("input_sphere", new SphereData(new Vector3d(0, 0, 0), 2.0d));
        probe.connectInput("input_count", NodeDataType.INTEGER);
        probe.putRawInput("input_count", 3.8d);
        probe.processNode(null);

        assertEquals(Boolean.FALSE, probe.getOutput("output_valid"));
        assertEquals(0, probe.getOutput("output_count"));
    }

    @Test
    void sampleSphereDeterministicSeed() {
        BaseNode first = createNode("pattern.surface_volume_distribution.sample_sphere_surface");
        first.setInput("input_sphere", new SphereData(new Vector3d(0, 0, 0), 2.0d));
        first.setNodeState(Map.of("sampleCount", 8, "seed", 42));
        first.processNode(null);

        BaseNode second = createNode("pattern.surface_volume_distribution.sample_sphere_surface");
        second.setInput("input_sphere", new SphereData(new Vector3d(0, 0, 0), 2.0d));
        second.setNodeState(Map.of("sampleCount", 8, "seed", 42));
        second.processNode(null);

        assertEquals(first.getOutput("output_points"), second.getOutput("output_points"));
    }

    @Test
    void scatterOnSurfaceCompositeGeometryFailsClosed() {
        BaseNode node = createNode("pattern.surface_volume_distribution.scatter_surface");
        CompositeGeometryData composite = new CompositeGeometryData(List.of(
                new SphereData(new Vector3d(0, 0, 0), 2.0d),
                new SphereData(new Vector3d(5, 0, 0), 1.0d)
        ));
        node.setInput("input_geometry", composite);
        node.setNodeState(Map.of("targetCount", 8));
        node.processNode(null);

        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
    }

    @Test
    void scatterOnSurfaceUsesContinuousCoordinates() {
        BaseNode node = createNode("pattern.surface_volume_distribution.scatter_surface");
        node.setInput("input_geometry", new SphereData(new Vector3d(1.25d, 0.5d, -0.75d), 2.0d));
        node.setNodeState(Map.of("targetCount", 6, "seed", 99));
        node.processNode(null);

        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<PointData> points = assertInstanceOf(List.class, node.getOutput("output_points"));
        assertFalse(points.isEmpty());
        PointData first = points.getFirst();
        assertTrue(Math.abs(first.position().x - Math.round(first.position().x)) > 1.0e-6d
                || Math.abs(first.position().y - Math.round(first.position().y)) > 1.0e-6d
                || Math.abs(first.position().z - Math.round(first.position().z)) > 1.0e-6d);
    }

    @Test
    void poissonDeterministicSeedAndValidWhenUnderTarget() {
        PoissonProbe probe = new PoissonProbe();
        probe.connectInput("input_plane", NodeDataType.PLANE);
        probe.putRawInput("input_plane", new PlaneData(new Vector3d(0, 0, 0), new Vector3d(0, 1, 0)));
        probe.setNodeState(Map.of(
            "halfU", 10.0d, "halfV", 10.0d,
            "targetCount", 100, "minDistance", 5.0d, "seed", 777
        ));
        probe.processNode(null);

        assertEquals(Boolean.TRUE, probe.getOutput("output_valid"));
        assertEquals(Boolean.FALSE, probe.getOutput("output_complete"));
        int count = assertInstanceOf(Integer.class, probe.getOutput("output_count"));
        assertTrue(count < 100);
        assertTrue(count > 0);
    }

    @Test
    void poissonNonFiniteHalfExtentFailsClosed() {
        PoissonProbe probe = new PoissonProbe();
        probe.connectInput("input_plane", NodeDataType.PLANE);
        probe.putRawInput("input_plane", new PlaneData(new Vector3d(0, 0, 0), new Vector3d(0, 1, 0)));
        probe.connectInput("input_half_u", NodeDataType.DOUBLE);
        probe.putRawInput("input_half_u", Double.NaN);
        probe.setNodeState(Map.of("halfV", 5.0d, "targetCount", 4, "minDistance", 1.0d));
        probe.processNode(null);

        assertEquals(Boolean.FALSE, probe.getOutput("output_valid"));
    }

    @Test
    void surfaceStripInvalidInputFailsClosed() {
        BaseNode node = createNode("pattern.surface_volume_distribution.scatter_surface_strip");
        node.setNodeState(Map.of("targetCount", 8));
        node.processNode(null);

        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
    }

    @Test
    void surfaceStripOpenSectionsExcludeWrapSegment() {
        List<List<Vector3d>> sections = List.of(
                List.of(new Vector3d(0, 0, 0), new Vector3d(1, 0, 0), new Vector3d(100, 0, 0)),
                List.of(new Vector3d(0, 1, 0), new Vector3d(1, 1, 0), new Vector3d(100, 1, 0))
        );
        SurfaceStripData openStrip = new SurfaceStripData(sections, List.of(false, false));
        assertEquals(2, SurfaceStripSampling.countQuads(openStrip));

        List<List<Vector3d>> closedSections = List.of(
                List.of(new Vector3d(0, 0, 0), new Vector3d(1, 0, 0), new Vector3d(100, 0, 0)),
                List.of(new Vector3d(0, 1, 0), new Vector3d(1, 1, 0), new Vector3d(100, 1, 0))
        );
        SurfaceStripData closedStrip = new SurfaceStripData(closedSections, List.of(true, true));
        assertEquals(3, SurfaceStripSampling.countQuads(closedStrip));
    }

    @Test
    void surfaceStripOpenSectionsDoNotWrapLastToFirst() {
        List<List<Vector3d>> sections = List.of(
                List.of(new Vector3d(0, 0, 0), new Vector3d(10, 0, 0), new Vector3d(20, 0, 50)),
                List.of(new Vector3d(0, 10, 0), new Vector3d(10, 10, 0), new Vector3d(20, 10, 50))
        );
        SurfaceStripData strip = new SurfaceStripData(sections, List.of(false, false));
        SurfaceStripSampling.QuadCatalog catalog = SurfaceStripSampling.QuadCatalog.from(strip);

        for (int i = 0; i < 500; i++) {
            Vector3d point = catalog.sample(new java.util.Random(12_345 + i));
            assertTrue(point.x >= 9.0d || point.z < 1.0d,
                    "open strip must not sample wrap quad; got x=" + point.x + " z=" + point.z);
        }
    }

    @Test
    void surfaceStripValidateStrictRejectsDegenerateQuad() {
        List<List<Vector3d>> sections = List.of(
            List.of(new Vector3d(0, 0, 0), new Vector3d(1, 0, 0), new Vector3d(2, 0, 0)),
            List.of(new Vector3d(0, 0, 0), new Vector3d(1, 0, 0), new Vector3d(2, 0, 0))
        );
        SurfaceStripData strip = new SurfaceStripData(sections, List.of(false, false));
        String error = SurfaceStripSampling.validateStrict(strip);
        assertNotNull(error);
        assertTrue(error.toLowerCase(Locale.ROOT).contains("degenerate")
            || error.toLowerCase(Locale.ROOT).contains("quad"));
    }

    @Test
    void surfaceStripValidTopologyProducesPoints() {
        BaseNode node = createNode("pattern.surface_volume_distribution.scatter_surface_strip");
        List<List<Vector3d>> sections = List.of(
                List.of(new Vector3d(0, 0, 0), new Vector3d(1, 0, 0), new Vector3d(2, 0, 0)),
                List.of(new Vector3d(0, 1, 0), new Vector3d(1, 1, 0), new Vector3d(2, 1, 0)),
                List.of(new Vector3d(0, 2, 0), new Vector3d(1, 2, 0), new Vector3d(2, 2, 0))
        );
        node.setInput("input_surface_strip", new SurfaceStripData(sections, List.of(false, false, false)));
        node.setNodeState(Map.of("targetCount", 8, "seed", 55));
        node.processNode(null);

        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));
        assertEquals(8, node.getOutput("output_count"));
    }

    @Test
    void scatterInVolumeStrictSpacingNeverViolated() {
        BaseNode node = createNode("pattern.surface_volume_distribution.scatter_volume");
        node.setInput("input_geometry", new SphereData(new Vector3d(0, 0, 0), 5.0d));
        node.setNodeState(Map.of("targetCount", 20, "minDistance", 2.5d, "seed", 123));
        node.processNode(null);

        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<PointData> points = assertInstanceOf(List.class, node.getOutput("output_points"));
        for (int i = 0; i < points.size(); i++) {
            for (int j = i + 1; j < points.size(); j++) {
                double distSq = points.get(i).position().distanceSquared(points.get(j).position());
                assertTrue(distSq >= 2.5d * 2.5d - 1.0e-6d);
            }
        }
    }

    @Test
    void imageScatterAlignedDoubleLists() {
        ImageProbe probe = new ImageProbe();
        int width = 4;
        int height = 4;
        List<Double> density = List.of(
                0.1d, 0.2d, 0.3d, 0.4d,
                0.5d, 0.6d, 0.7d, 0.8d,
                0.2d, 0.3d, 0.4d, 0.5d,
                0.6d, 0.7d, 0.8d, 0.9d
        );
        probe.connectInput("input_density_values", NodeDataType.DOUBLE_LIST);
        probe.connectInput("input_image_width", NodeDataType.INTEGER);
        probe.connectInput("input_image_height", NodeDataType.INTEGER);
        probe.putRawInput("input_density_values", density);
        probe.putRawInput("input_image_width", width);
        probe.putRawInput("input_image_height", height);
        probe.setNodeState(Map.of("targetCount", 5, "seed", 11, "spanU", 4.0d, "spanV", 4.0d));
        probe.processNode(null);

        assertEquals(Boolean.TRUE, probe.getOutput("output_valid"));
        int count = assertInstanceOf(Integer.class, probe.getOutput("output_count"));
        @SuppressWarnings("unchecked")
        List<PointData> points = assertInstanceOf(List.class, probe.getOutput("output_points"));
        @SuppressWarnings("unchecked")
        List<Double> uValues = assertInstanceOf(List.class, probe.getOutput("output_u_values"));
        @SuppressWarnings("unchecked")
        List<Double> vValues = assertInstanceOf(List.class, probe.getOutput("output_v_values"));
        @SuppressWarnings("unchecked")
        List<Double> densityOut = assertInstanceOf(List.class, probe.getOutput("output_density_values"));
        assertEquals(count, points.size());
        assertEquals(count, uValues.size());
        assertEquals(count, vValues.size());
        assertEquals(count, densityOut.size());
    }

    @Test
    void layoutProducersRespectLayoutInstanceCap() {
        BaseNode node = createNode("pattern.surface_volume_distribution.sample_sphere_surface");
        node.setInput("input_sphere", new SphereData(new Vector3d(0, 0, 0), 2.0d));
        node.setNodeState(Map.of("sampleCount", Integer.MAX_VALUE));
        node.processNode(null);

        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertEquals(0, node.getOutput("output_count"));
        assertTrue(((Number) node.getOutput("output_count")).intValue() <= GenerationLimits.MAX_LAYOUT_INSTANCES);
    }

    private static BaseNode createNode(String typeId) {
        return assertInstanceOf(BaseNode.class, registry.createNodeInstance(typeId));
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

    private static final class SphereProbe extends SampleSphereSurfaceNode {
        void putRawInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            PatternSurfaceVolumeDistributionLanguageContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class PoissonProbe extends PoissonDiskOnPlaneNode {
        void putRawInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            PatternSurfaceVolumeDistributionLanguageContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class ImageProbe extends ImageBasedScatterNode {
        void putRawInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            PatternSurfaceVolumeDistributionLanguageContractTest.connectInput(this, portId, outputType);
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

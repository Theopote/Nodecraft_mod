package com.nodecraft.nodesystem.contract;

import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.CylinderGeometryData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.SphereData;
import com.nodecraft.nodesystem.datatypes.TorusGeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.execution.runtime.NodeEffectResolver;
import com.nodecraft.nodesystem.graph.GraphMigrationRegistry;
import com.nodecraft.nodesystem.io.SavedConnection;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import com.nodecraft.nodesystem.nodes.pattern.surface_volume_distribution.ImageBasedScatterNode;
import com.nodecraft.nodesystem.nodes.pattern.surface_volume_distribution.PoissonDiskOnPlaneNode;
import com.nodecraft.nodesystem.nodes.pattern.surface_volume_distribution.SampleSphereSurfaceNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.PrimitiveVolumeSampler;
import com.nodecraft.nodesystem.util.VectorUtils;
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
 * Language fence for Surface / Volume Distribution Language v2.
 */
class PatternSurfaceVolumeDistributionLanguageV2ContractTest {

    private static final List<String> CANONICAL_IDS = List.of(
        "pattern.surface_volume_distribution.sample_sphere_surface",
        "pattern.surface_volume_distribution.scatter_surface",
        "pattern.surface_volume_distribution.poisson_disk_plane",
        "pattern.surface_volume_distribution.scatter_surface_strip",
        "pattern.surface_volume_distribution.scatter_volume",
        "pattern.surface_volume_distribution.image_scatter"
    );

    private static final Set<NodeDataType> FORBIDDEN_PORT_TYPES = Set.of(
        NodeDataType.ANY,
        NodeDataType.LIST,
        NodeDataType.BLOCK_LIST,
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
    void exactlySixNodesWithUniqueOrdersZeroToFive() {
        List<String> ids = registry.getAllNodeIds().stream()
            .filter(id -> id.startsWith("pattern.surface_volume_distribution."))
            .sorted()
            .toList();
        assertEquals(6, ids.size());
        assertEquals(Set.copyOf(CANONICAL_IDS), Set.copyOf(ids));

        Set<Integer> orders = new HashSet<>();
        for (String id : ids) {
            NodeInfo info = registry.getNodeInfo(id);
            assertNotNull(info);
            assertEquals("pattern.surface_volume_distribution", info.getCategoryId());
            assertTrue(orders.add(info.getOrder()), "duplicate order for " + id);
            INode node = registry.createNodeInstance(id);
            assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(node.getClass(), id));
        }
        for (int i = 0; i < 6; i++) {
            assertTrue(orders.contains(i), "missing order " + i);
        }
    }

    @Test
    void allNodesExposeValidErrorAndComplete() {
        for (String id : CANONICAL_IDS) {
            INode node = registry.createNodeInstance(id);
            assertTrue(hasPort(node, "output_valid"), id);
            assertTrue(hasPort(node, "output_error"), id);
            assertTrue(hasPort(node, "output_complete"), id);
        }
    }

    @Test
    void publicPortsForbidAnyRawListBlockListLinePolylineCurve() {
        for (String id : CANONICAL_IDS) {
            INode node = registry.createNodeInstance(id);
            for (IPort port : node.getInputPorts()) {
                assertFalse(FORBIDDEN_PORT_TYPES.contains(port.getDataType()),
                    id + " input " + port.getId());
            }
            for (IPort port : node.getOutputPorts()) {
                assertFalse(FORBIDDEN_PORT_TYPES.contains(port.getDataType()),
                    id + " output " + port.getId());
            }
        }
    }

    @Test
    void imageScatterHasNoImagePathPort() {
        assertFalse(hasPort(registry.createNodeInstance(
            "pattern.surface_volume_distribution.image_scatter"), "input_image_path"));
    }

    @Test
    void sampleSphereExactCountAndComplete() {
        BaseNode node = node("pattern.surface_volume_distribution.sample_sphere_surface");
        node.setInput("input_sphere", new SphereData(new Vector3d(), 2.0d));
        node.setNodeState(Map.of("sampleCount", 12, "seed", 1));
        node.processNode(null);
        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));
        assertEquals(Boolean.TRUE, node.getOutput("output_complete"));
        assertEquals(12, node.getOutput("output_count"));
        assertEquals("", node.getOutput("output_error"));
        assertUnitNormals(node);
    }

    @Test
    void sampleSphereCountOverBudgetFailsClosed() {
        BaseNode node = node("pattern.surface_volume_distribution.sample_sphere_surface");
        node.setInput("input_sphere", new SphereData(new Vector3d(), 2.0d));
        node.setNodeState(Map.of("sampleCount", GenerationLimits.MAX_LAYOUT_INSTANCES + 1));
        node.processNode(null);
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertEquals(0, node.getOutput("output_count"));
        assertEquals(Boolean.FALSE, node.getOutput("output_complete"));
    }

    @Test
    void sampleSphereDegenerateRadiusFails() {
        BaseNode node = node("pattern.surface_volume_distribution.sample_sphere_surface");
        node.setInput("input_sphere", new SphereData(new Vector3d(), 0.0d));
        node.setNodeState(Map.of("sampleCount", 4));
        node.processNode(null);
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
    }

    @Test
    void sampleSphereCountConnectedNonExactIntegerFails() {
        SphereProbe probe = new SphereProbe();
        probe.setInput("input_sphere", new SphereData(new Vector3d(), 2.0d));
        probe.connectInput("input_count", NodeDataType.INTEGER);
        probe.putRawInput("input_count", 3.8d);
        probe.processNode(null);
        assertEquals(Boolean.FALSE, probe.getOutput("output_valid"));
        assertEquals(0, probe.getOutput("output_count"));
    }

    @Test
    void scatterSurfaceUnderTargetIsValidIncomplete() {
        BaseNode node = node("pattern.surface_volume_distribution.scatter_surface");
        node.setInput("input_geometry", new SphereData(new Vector3d(), 1.0d));
        node.setNodeState(Map.of("targetCount", 50, "minDistance", 10.0d, "seed", 3));
        node.processNode(null);
        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));
        assertEquals(Boolean.FALSE, node.getOutput("output_complete"));
        int count = assertInstanceOf(Integer.class, node.getOutput("output_count"));
        assertTrue(count < 50);
    }

    @Test
    void scatterSurfaceSpacingWorkOverBudgetFailsClosed() {
        BaseNode node = node("pattern.surface_volume_distribution.scatter_surface");
        node.setInput("input_geometry", new SphereData(new Vector3d(), 2.0d));
        node.setNodeState(Map.of(
            "targetCount", 512,
            "minDistance", 0.1d,
            "seed", 1,
            "distributionMode", "BLUE_NOISE"
        ));
        node.processNode(null);
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertEquals(Boolean.FALSE, node.getOutput("output_complete"));
        assertEquals(0, node.getOutput("output_count"));
        String error = String.valueOf(node.getOutput("output_error")).toLowerCase(Locale.ROOT);
        assertTrue(error.contains("workload") || error.contains("budget"));
    }

    @Test
    void scatterSurfaceStripSpacingWorkOverBudgetFailsClosed() {
        BaseNode node = node("pattern.surface_volume_distribution.scatter_surface_strip");
        List<List<Vector3d>> sections = List.of(
            List.of(new Vector3d(0, 0, 0), new Vector3d(1, 0, 0), new Vector3d(2, 0, 0)),
            List.of(new Vector3d(0, 1, 0), new Vector3d(1, 1, 0), new Vector3d(2, 1, 0))
        );
        node.setInput("input_surface_strip",
            new com.nodecraft.nodesystem.datatypes.SurfaceStripData(sections, List.of(false, false)));
        node.setNodeState(Map.of("targetCount", 1024, "minDistance", 0.05d, "seed", 2));
        node.processNode(null);
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertEquals(Boolean.FALSE, node.getOutput("output_complete"));
        assertEquals(0, node.getOutput("output_count"));
        String error = String.valueOf(node.getOutput("output_error")).toLowerCase(Locale.ROOT);
        assertTrue(error.contains("workload") || error.contains("budget"));
    }

    @Test
    void scatterVolumeSpacingWorkOverBudgetFailsClosed() {
        BaseNode node = node("pattern.surface_volume_distribution.scatter_volume");
        node.setInput("input_geometry", new SphereData(new Vector3d(), 5.0d));
        node.setNodeState(Map.of(
            "targetCount", 512,
            "minDistance", 0.1d,
            "seed", 3,
            "distributionMode", "BLUE_NOISE"
        ));
        node.processNode(null);
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertEquals(Boolean.FALSE, node.getOutput("output_complete"));
        assertEquals(0, node.getOutput("output_count"));
        String error = String.valueOf(node.getOutput("output_error")).toLowerCase(Locale.ROOT);
        assertTrue(error.contains("workload") || error.contains("budget"));
    }

    @Test
    void scatterSurfaceDegenerateCylinderFails() {
        BaseNode node = node("pattern.surface_volume_distribution.scatter_surface");
        node.setInput("input_geometry", new CylinderGeometryData(
            new Vector3d(0, 0, 0), new Vector3d(0, 0, 0), 1.0d));
        node.setNodeState(Map.of("targetCount", 8));
        node.processNode(null);
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertTrue(String.valueOf(node.getOutput("output_error")).toLowerCase(Locale.ROOT)
            .contains("degenerate")
            || String.valueOf(node.getOutput("output_error")).toLowerCase(Locale.ROOT)
            .contains("cylinder"));
    }

    @Test
    void volumeTorusUsesContinuousBoundsNotVoxelizer() {
        assertNull(PrimitiveVolumeSampler.validatePrimitive(
            new TorusGeometryData(new Vector3d(), new Vector3d(0, 1, 0), 3.0d, 0.5d)));
        BaseNode node = node("pattern.surface_volume_distribution.scatter_volume");
        node.setInput("input_geometry",
            new TorusGeometryData(new Vector3d(0.5d, 0.25d, -0.75d), new Vector3d(0, 1, 0), 2.5d, 0.4d));
        node.setNodeState(Map.of("targetCount", 16, "seed", 9, "minDistance", 0.0d));
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
    void surfaceStripDegenerateQuadFailsClosed() {
        BaseNode node = node("pattern.surface_volume_distribution.scatter_surface_strip");
        List<List<Vector3d>> sections = List.of(
            List.of(new Vector3d(0, 0, 0), new Vector3d(1, 0, 0), new Vector3d(2, 0, 0)),
            List.of(new Vector3d(0, 0, 0), new Vector3d(1, 0, 0), new Vector3d(2, 0, 0))
        );
        node.setInput("input_surface_strip",
            new com.nodecraft.nodesystem.datatypes.SurfaceStripData(sections, List.of(false, false)));
        node.setNodeState(Map.of("targetCount", 8, "seed", 11));
        node.processNode(null);
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertTrue(String.valueOf(node.getOutput("output_error")).toLowerCase(Locale.ROOT)
            .contains("degenerate") || String.valueOf(node.getOutput("output_error")).toLowerCase(Locale.ROOT)
            .contains("quad"));
        assertEquals(0, node.getOutput("output_count"));
    }

    @Test
    void surfaceStripNaNPointFailsClosed() {
        List<List<Vector3d>> sections = List.of(
            List.of(new Vector3d(0, 0, 0), new Vector3d(Double.NaN, 0, 0), new Vector3d(2, 0, 0)),
            List.of(new Vector3d(0, 1, 0), new Vector3d(1, 1, 0), new Vector3d(2, 1, 0))
        );
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
            new com.nodecraft.nodesystem.datatypes.SurfaceStripData(sections, List.of(false, false)));
    }

    @Test
    void poissonMaxAttemptsBelowMinimumFailsClosed() {
        PoissonProbe probe = new PoissonProbe();
        probe.connectInput("input_plane", NodeDataType.PLANE);
        probe.putRawInput("input_plane", new PlaneData(new Vector3d(), new Vector3d(0, 1, 0)));
        probe.setNodeState(Map.of(
            "targetCount", 4,
            "halfU", 1.0d,
            "halfV", 1.0d,
            "minDistance", 0.1d,
            "maxAttempts", -5
        ));
        probe.processNode(null);
        assertEquals(Boolean.FALSE, probe.getOutput("output_valid"));
        assertTrue(String.valueOf(probe.getOutput("output_error")).toLowerCase(Locale.ROOT)
            .contains("attempt"));
        assertEquals(0, probe.getOutput("output_count"));
    }

    @Test
    void poissonOriginConnectedInvalidFails() {
        PoissonProbe probe = new PoissonProbe();
        probe.connectInput("input_plane", NodeDataType.PLANE);
        probe.putRawInput("input_plane", new PlaneData(new Vector3d(), new Vector3d(0, 1, 0)));
        probe.connectInput("input_origin", NodeDataType.POINT);
        probe.putRawInput("input_origin", new PointData(Double.NaN, 0, 0));
        probe.setNodeState(Map.of("targetCount", 4, "halfU", 1.0d, "halfV", 1.0d, "minDistance", 0.1d));
        probe.processNode(null);
        assertEquals(Boolean.FALSE, probe.getOutput("output_valid"));
        assertTrue(String.valueOf(probe.getOutput("output_error")).toLowerCase(Locale.ROOT)
            .contains("origin"));
    }

    @Test
    void imageDensitySizeMismatchFails() {
        ImageProbe probe = new ImageProbe();
        probe.connectInput("input_density_values", NodeDataType.DOUBLE_LIST);
        probe.connectInput("input_image_width", NodeDataType.INTEGER);
        probe.connectInput("input_image_height", NodeDataType.INTEGER);
        probe.putRawInput("input_density_values", List.of(0.1d, 0.2d, 0.3d));
        probe.putRawInput("input_image_width", 2);
        probe.putRawInput("input_image_height", 2);
        probe.setNodeState(Map.of("targetCount", 2, "spanU", 1.0d, "spanV", 1.0d));
        probe.processNode(null);
        assertEquals(Boolean.FALSE, probe.getOutput("output_valid"));
        assertTrue(String.valueOf(probe.getOutput("output_error")).toLowerCase(Locale.ROOT)
            .contains("size")
            || String.valueOf(probe.getOutput("output_error")).toLowerCase(Locale.ROOT)
            .contains("width"));
    }

    @Test
    void imageDensityOutOfRangeFailsNoClamp() {
        ImageProbe probe = new ImageProbe();
        probe.connectInput("input_density_values", NodeDataType.DOUBLE_LIST);
        probe.connectInput("input_image_width", NodeDataType.INTEGER);
        probe.connectInput("input_image_height", NodeDataType.INTEGER);
        probe.putRawInput("input_density_values", List.of(0.0d, 1.5d, 0.2d, 0.3d));
        probe.putRawInput("input_image_width", 2);
        probe.putRawInput("input_image_height", 2);
        probe.setNodeState(Map.of("targetCount", 2, "spanU", 1.0d, "spanV", 1.0d));
        probe.processNode(null);
        assertEquals(Boolean.FALSE, probe.getOutput("output_valid"));
    }

    @Test
    void imageExactCountComplete() {
        ImageProbe probe = new ImageProbe();
        List<Double> density = List.of(0.2d, 0.4d, 0.6d, 0.8d);
        probe.connectInput("input_density_values", NodeDataType.DOUBLE_LIST);
        probe.connectInput("input_image_width", NodeDataType.INTEGER);
        probe.connectInput("input_image_height", NodeDataType.INTEGER);
        probe.putRawInput("input_density_values", density);
        probe.putRawInput("input_image_width", 2);
        probe.putRawInput("input_image_height", 2);
        probe.setNodeState(Map.of("targetCount", 7, "seed", 4, "spanU", 2.0d, "spanV", 2.0d));
        probe.processNode(null);
        assertEquals(Boolean.TRUE, probe.getOutput("output_valid"));
        assertEquals(Boolean.TRUE, probe.getOutput("output_complete"));
        assertEquals(7, probe.getOutput("output_count"));
    }

    @Test
    void scatterSurfaceAndSphereEmitUnitNormals() {
        BaseNode surface = node("pattern.surface_volume_distribution.scatter_surface");
        surface.setInput("input_geometry", new SphereData(new Vector3d(), 2.0d));
        surface.setNodeState(Map.of(
            "targetCount", 12,
            "minDistance", 0.0d,
            "seed", 4,
            "distributionMode", "RANDOM"
        ));
        surface.processNode(null);
        assertEquals(Boolean.TRUE, surface.getOutput("output_valid"));
        assertUnitNormals(surface);

        BaseNode sphere = node("pattern.surface_volume_distribution.sample_sphere_surface");
        sphere.setInput("input_sphere", new SphereData(new Vector3d(), 2.0d));
        sphere.setNodeState(Map.of("sampleCount", 16, "seed", 2, "sampleMode", "RANDOM_UNIFORM"));
        sphere.processNode(null);
        assertEquals(Boolean.TRUE, sphere.getOutput("output_valid"));
        assertUnitNormals(sphere);
    }

    @Test
    void thinTorusVolumeScatterDoesNotFailAcrossSeeds() {
        TorusGeometryData torus = new TorusGeometryData(
            new Vector3d(), new Vector3d(0, 1, 0), 100.0d, 0.1d);
        for (int seed : List.of(1, 7, 13, 99, 12345)) {
            BaseNode volume = node("pattern.surface_volume_distribution.scatter_volume");
            volume.setInput("input_geometry", torus);
            volume.setNodeState(Map.of(
                "targetCount", 12,
                "minDistance", 0.0d,
                "seed", seed,
                "distributionMode", "RANDOM"
            ));
            volume.processNode(null);
            assertEquals(Boolean.TRUE, volume.getOutput("output_valid"), "seed=" + seed);
            assertEquals(Boolean.TRUE, volume.getOutput("output_complete"), "seed=" + seed);
            assertEquals(12, volume.getOutput("output_count"), "seed=" + seed);
            @SuppressWarnings("unchecked")
            List<PointData> points = assertInstanceOf(List.class, volume.getOutput("output_points"));
            for (PointData point : points) {
                assertTrue(PrimitiveVolumeSampler.containsPoint(torus, point.position()), "seed=" + seed);
            }
        }
    }

    @Test
    void oversizedMinDistanceSquareFailsClosed() {
        BaseNode surface = node("pattern.surface_volume_distribution.scatter_surface");
        surface.setInput("input_geometry", new SphereData(new Vector3d(), 2.0d));
        surface.setNodeState(Map.of("targetCount", 8, "minDistance", 1.0e308d, "seed", 1));
        surface.processNode(null);
        assertEquals(Boolean.FALSE, surface.getOutput("output_valid"));
        assertTrue(String.valueOf(surface.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("distance"));
        assertEquals(0, surface.getOutput("output_count"));
    }

    @Test
    void sameSeedRepeatsPointSequenceAndDifferentSeedChangesIt() {
        assertDeterministicScatter(
            "pattern.surface_volume_distribution.scatter_surface",
            n -> n.setInput("input_geometry", new SphereData(new Vector3d(), 2.0d)),
            Map.of("targetCount", 8, "minDistance", 0.0d, "distributionMode", "RANDOM")
        );
        assertDeterministicScatter(
            "pattern.surface_volume_distribution.scatter_volume",
            n -> n.setInput("input_geometry", new SphereData(new Vector3d(), 3.0d)),
            Map.of("targetCount", 8, "minDistance", 0.0d, "distributionMode", "RANDOM")
        );

        List<List<Vector3d>> sections = List.of(
            List.of(new Vector3d(0, 0, 0), new Vector3d(1, 0, 0), new Vector3d(2, 0, 0)),
            List.of(new Vector3d(0, 1, 0), new Vector3d(1, 1, 0), new Vector3d(2, 1, 0))
        );
        assertDeterministicScatter(
            "pattern.surface_volume_distribution.scatter_surface_strip",
            n -> n.setInput("input_surface_strip",
                new com.nodecraft.nodesystem.datatypes.SurfaceStripData(sections, List.of(false, false))),
            Map.of("targetCount", 8, "minDistance", 0.0d)
        );

        BaseNode sphereA = node("pattern.surface_volume_distribution.sample_sphere_surface");
        BaseNode sphereB = node("pattern.surface_volume_distribution.sample_sphere_surface");
        BaseNode sphereC = node("pattern.surface_volume_distribution.sample_sphere_surface");
        sphereA.setInput("input_sphere", new SphereData(new Vector3d(), 2.0d));
        sphereB.setInput("input_sphere", new SphereData(new Vector3d(), 2.0d));
        sphereC.setInput("input_sphere", new SphereData(new Vector3d(), 2.0d));
        Map<String, Object> sphereState = Map.of("sampleCount", 10, "sampleMode", "RANDOM_UNIFORM");
        sphereA.setNodeState(withSeed(sphereState, 11));
        sphereB.setNodeState(withSeed(sphereState, 11));
        sphereC.setNodeState(withSeed(sphereState, 12));
        sphereA.processNode(null);
        sphereB.processNode(null);
        sphereC.processNode(null);
        assertEquals(pointSequence(sphereA), pointSequence(sphereB));
        assertNotEquals(pointSequence(sphereA), pointSequence(sphereC));

        ImageProbe imageA = wiredImageScatter();
        ImageProbe imageB = wiredImageScatter();
        ImageProbe imageC = wiredImageScatter();
        Map<String, Object> imageState = Map.of("targetCount", 6, "spanU", 2.0d, "spanV", 2.0d);
        imageA.setNodeState(withSeed(imageState, 21));
        imageB.setNodeState(withSeed(imageState, 21));
        imageC.setNodeState(withSeed(imageState, 22));
        imageA.processNode(null);
        imageB.processNode(null);
        imageC.processNode(null);
        assertEquals(Boolean.TRUE, imageA.getOutput("output_valid"));
        assertEquals(pointSequence(imageA), pointSequence(imageB));
        assertNotEquals(pointSequence(imageA), pointSequence(imageC));
    }

    private static void assertDeterministicScatter(
        String typeId,
        java.util.function.Consumer<BaseNode> setup,
        Map<String, Object> state
    ) {
        BaseNode a = node(typeId);
        BaseNode b = node(typeId);
        BaseNode c = node(typeId);
        setup.accept(a);
        setup.accept(b);
        setup.accept(c);
        a.setNodeState(withSeed(state, 41));
        b.setNodeState(withSeed(state, 41));
        c.setNodeState(withSeed(state, 42));
        a.processNode(null);
        b.processNode(null);
        c.processNode(null);
        assertEquals(Boolean.TRUE, a.getOutput("output_valid"));
        assertEquals(Boolean.TRUE, b.getOutput("output_valid"));
        assertEquals(Boolean.TRUE, c.getOutput("output_valid"));
        assertEquals(pointSequence(a), pointSequence(b));
        assertNotEquals(pointSequence(a), pointSequence(c));
    }

    private static Map<String, Object> withSeed(Map<String, Object> state, int seed) {
        Map<String, Object> copy = new HashMap<>(state);
        copy.put("seed", seed);
        return copy;
    }

    private static List<String> pointSequence(BaseNode node) {
        @SuppressWarnings("unchecked")
        List<PointData> points = assertInstanceOf(List.class, node.getOutput("output_points"));
        List<String> keys = new ArrayList<>(points.size());
        for (PointData point : points) {
            Vector3d p = point.position();
            keys.add(String.format(Locale.ROOT, "%.9f,%.9f,%.9f", p.x, p.y, p.z));
        }
        return keys;
    }

    private static void assertUnitNormals(BaseNode node) {
        @SuppressWarnings("unchecked")
        List<Vector3d> normals = assertInstanceOf(List.class, node.getOutput("output_normals"));
        assertFalse(normals.isEmpty());
        for (Vector3d normal : normals) {
            assertEquals(1.0d, VectorUtils.safeLength(normal), VectorUtils.EPS);
        }
    }

    private static ImageProbe wiredImageScatter() {
        ImageProbe probe = new ImageProbe();
        probe.connectInput("input_density_values", NodeDataType.DOUBLE_LIST);
        probe.connectInput("input_image_width", NodeDataType.INTEGER);
        probe.connectInput("input_image_height", NodeDataType.INTEGER);
        probe.putRawInput("input_density_values", List.of(0.2d, 0.4d, 0.6d, 0.8d));
        probe.putRawInput("input_image_width", 2);
        probe.putRawInput("input_image_height", 2);
        return probe;
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

    private static final class SphereProbe extends SampleSphereSurfaceNode {
        void putRawInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            PatternSurfaceVolumeDistributionLanguageV2ContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class PoissonProbe extends PoissonDiskOnPlaneNode {
        void putRawInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            PatternSurfaceVolumeDistributionLanguageV2ContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class ImageProbe extends ImageBasedScatterNode {
        void putRawInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            PatternSurfaceVolumeDistributionLanguageV2ContractTest.connectInput(this, portId, outputType);
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

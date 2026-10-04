package com.nodecraft.nodesystem.contract;

import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BooleanSdfData;
import com.nodecraft.nodesystem.datatypes.BoxSdfData;
import com.nodecraft.nodesystem.datatypes.CapsuleSdfData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.SignedDistanceFieldData;
import com.nodecraft.nodesystem.datatypes.SphereSdfData;
import com.nodecraft.nodesystem.datatypes.TransformedSdfData;
import com.nodecraft.nodesystem.datatypes.VectorData;
import com.nodecraft.nodesystem.execution.runtime.NodeEffectResolver;
import com.nodecraft.nodesystem.graph.GraphMigrationRegistry;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.nodes.geometry.boolops.SdfBlendMaterialMaskNode;
import com.nodecraft.nodesystem.nodes.geometry.boolops.SdfBooleanNode;
import com.nodecraft.nodesystem.nodes.geometry.boolops.SdfBoxNode;
import com.nodecraft.nodesystem.nodes.geometry.boolops.SdfCapsuleNode;
import com.nodecraft.nodesystem.nodes.geometry.boolops.SdfDomainWarpNode;
import com.nodecraft.nodesystem.nodes.geometry.boolops.SdfGradientPointNode;
import com.nodecraft.nodesystem.nodes.geometry.boolops.SdfNoiseDisplaceNode;
import com.nodecraft.nodesystem.nodes.geometry.boolops.SdfSamplePointsNode;
import com.nodecraft.nodesystem.nodes.geometry.boolops.SdfSphereNode;
import com.nodecraft.nodesystem.nodes.geometry.boolops.SdfToGeometryNode;
import com.nodecraft.nodesystem.nodes.geometry.boolops.SdfTorusNode;
import com.nodecraft.nodesystem.nodes.geometry.boolops.SdfTransformNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.SdfBoundsEstimator;
import org.joml.Matrix3d;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.AbstractList;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Language fence for SDF Language v1 (Graph V93).
 */
class GeometrySdfLanguageContractTest {

    private static final String[] SDF_IDS = {
        "geometry.boolean.sdf_sphere",
        "geometry.boolean.sdf_box",
        "geometry.boolean.sdf_capsule",
        "geometry.boolean.sdf_torus",
        "geometry.boolean.sdf_boolean",
        "geometry.boolean.sdf_to_geometry",
        "geometry.boolean.sdf_sample_point",
        "geometry.boolean.sdf_sample_points",
        "geometry.boolean.sdf_gradient_point",
        "geometry.boolean.sdf_noise_displace",
        "geometry.boolean.sdf_transform",
        "geometry.boolean.sdf_blend_material_mask",
        "geometry.boolean.sdf_domain_warp"
    };

    private static NodeRegistry registry;

    @BeforeAll
    static void init() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void currentGraphFormatIsStampOnlyOne() {
        assertEquals(1, GraphFormatVersion.CURRENT);
        assertTrue(GraphFormatVersion.isCurrent(GraphFormatVersion.CURRENT));
    }


    @Test
    void exactlyThirteenSdfNodesInGeometrySdfCategory() {
        List<String> ids = registry.getAllNodeIds().stream()
            .filter(id -> {
                NodeInfo info = registry.getNodeInfo(id);
                return info != null && "geometry.sdf".equals(info.getCategoryId());
            })
            .sorted()
            .toList();
        assertEquals(13, ids.size());
        assertEquals(Set.of(SDF_IDS), Set.copyOf(ids));

        Set<Integer> orders = new HashSet<>();
        for (String id : ids) {
            NodeInfo info = registry.getNodeInfo(id);
            assertNotNull(info);
            assertTrue(orders.add(info.getOrder()), "duplicate order for " + id);
            INode node = registry.createNodeInstance(id);
            assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(node.getClass(), id));
            assertTrue(hasPort(node, "output_valid"), id + " missing Valid");
            assertTrue(hasPort(node, "output_error"), id + " missing Error");
            assertFalse(usesRawList(node), id + " uses raw LIST");
        }
    }

    @Test
    void samplePointsAndBlendMaskUseTypedLists() {
        assertPortType("geometry.boolean.sdf_sample_points", "input_points", true, NodeDataType.POINT_LIST);
        assertPortType("geometry.boolean.sdf_sample_points", "output_distances", false, NodeDataType.DOUBLE_LIST);
        assertPortType("geometry.boolean.sdf_sample_points", "output_inside", false, NodeDataType.BOOLEAN_LIST);
        assertPortType("geometry.boolean.sdf_blend_material_mask", "input_distances", true, NodeDataType.DOUBLE_LIST);
        assertPortType("geometry.boolean.sdf_blend_material_mask", "output_weights", false, NodeDataType.DOUBLE_LIST);
        assertPortType("geometry.boolean.sdf_blend_material_mask", "output_inside", false, NodeDataType.BOOLEAN_LIST);
    }

    @Test
    void sphereRejectsNaNRadiusWithError() {
        SdfSphereNode sphere = new SdfSphereNode();
        connectInput(sphere, "input_center", NodeDataType.POINT);
        connectInput(sphere, "input_radius", NodeDataType.DOUBLE);
        sphere.setInput("input_center", new PointData(0, 0, 0));
        sphere.setInput("input_radius", Double.NaN);
        sphere.processNode(null);
        assertEquals(Boolean.FALSE, sphere.getOutput("output_valid"));
        assertNull(sphere.getOutput("output_sdf"));
        assertFalse(String.valueOf(sphere.getOutput("output_error")).isBlank());
    }

    @Test
    void boxRejectsZeroThicknessAxis() {
        SdfBoxNode box = new SdfBoxNode();
        connectInput(box, "input_center", NodeDataType.POINT);
        connectInput(box, "input_half_extents", NodeDataType.VECTOR);
        box.setInput("input_center", new PointData(0, 0, 0));
        box.setInput("input_half_extents", new Vector3d(5.0d, 0.0d, 5.0d));
        box.processNode(null);
        assertEquals(Boolean.FALSE, box.getOutput("output_valid"));
        assertTrue(String.valueOf(box.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("extent"));
    }

    @Test
    void boxRejectsNegativeHalfExtent() {
        SdfBoxNode box = new SdfBoxNode();
        connectInput(box, "input_center", NodeDataType.POINT);
        connectInput(box, "input_half_extents", NodeDataType.VECTOR);
        box.setInput("input_center", new PointData(0, 0, 0));
        box.setInput("input_half_extents", new Vector3d(-5.0d, 4.0d, 4.0d));
        box.processNode(null);
        assertEquals(Boolean.FALSE, box.getOutput("output_valid"));
        assertNull(box.getOutput("output_sdf"));
        assertTrue(String.valueOf(box.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("extent"));
    }

    @Test
    void capsuleRejectsZeroAxisLength() {
        SdfCapsuleNode capsule = new SdfCapsuleNode();
        connectInput(capsule, "input_start", NodeDataType.POINT);
        connectInput(capsule, "input_end", NodeDataType.POINT);
        capsule.setInput("input_start", new PointData(0, 0, 0));
        capsule.setInput("input_end", new PointData(0, 0, 0));
        capsule.processNode(null);
        assertEquals(Boolean.FALSE, capsule.getOutput("output_valid"));
        assertTrue(String.valueOf(capsule.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("axis"));
    }

    @Test
    void torusRequiresRingPolicy() {
        SdfTorusNode torus = new SdfTorusNode();
        connectInput(torus, "input_center", NodeDataType.POINT);
        connectInput(torus, "input_major_radius", NodeDataType.DOUBLE);
        connectInput(torus, "input_minor_radius", NodeDataType.DOUBLE);
        torus.setInput("input_center", new PointData(0, 0, 0));
        torus.setInput("input_major_radius", 2.0d);
        torus.setInput("input_minor_radius", 3.0d);
        torus.processNode(null);
        assertEquals(Boolean.FALSE, torus.getOutput("output_valid"));
        assertTrue(String.valueOf(torus.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("ring"));
    }

    @Test
    void transformScaleZeroFailsClosedWithoutException() {
        SdfSphereNode sphere = validSphere(4.0d);
        SdfTransformNode transform = new SdfTransformNode();
        connectInput(transform, "input_scale", NodeDataType.DOUBLE);
        transform.setInput("input_sdf", sphere.getOutput("output_sdf"));
        transform.setInput("input_scale", 0.0d);
        transform.processNode(null);
        assertEquals(Boolean.FALSE, transform.getOutput("output_valid"));
        assertNull(transform.getOutput("output_sdf"));
        assertTrue(String.valueOf(transform.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("scale"));
    }

    @Test
    void samplePointsRejectsInvalidEntryWithoutSilentDrop() {
        SdfSphereNode sphere = validSphere(4.0d);
        SdfSamplePointsNode sample = new SdfSamplePointsNode();
        List<Object> points = new ArrayList<>();
        points.add(new PointData(0, 0, 0));
        points.add("not-a-point");
        points.add(new PointData(1, 0, 0));
        sample.setInput("input_sdf", sphere.getOutput("output_sdf"));
        sample.setInput("input_points", points);
        sample.processNode(null);
        assertEquals(Boolean.FALSE, sample.getOutput("output_valid"));
        assertEquals(0, sample.getOutput("output_count"));
        assertTrue(String.valueOf(sample.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("silent")
            || String.valueOf(sample.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("point"));
    }

    @Test
    void samplePointsEmptyListSucceedsWithZeroCount() {
        SdfSphereNode sphere = validSphere(4.0d);
        SdfSamplePointsNode sample = new SdfSamplePointsNode();
        sample.setInput("input_sdf", sphere.getOutput("output_sdf"));
        sample.setInput("input_points", List.of());
        sample.processNode(null);
        assertEquals(Boolean.TRUE, sample.getOutput("output_valid"),
            String.valueOf(sample.getOutput("output_error")));
        assertEquals(0, sample.getOutput("output_count"));
    }

    @Test
    void blendMaskRejectsNegativeHalfWidth() {
        SdfBlendMaterialMaskNode mask = new SdfBlendMaterialMaskNode();
        connectInput(mask, "input_half_width", NodeDataType.DOUBLE);
        mask.setInput("input_distances", List.of(0.0d, 1.0d));
        mask.setInput("input_half_width", -5.0d);
        mask.processNode(null);
        assertEquals(Boolean.FALSE, mask.getOutput("output_valid"));
        assertTrue(String.valueOf(mask.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("half"));
    }

    @Test
    void noiseDisplaceRejectsNegativeAmplitudeOrFrequency() {
        SdfSphereNode sphere = validSphere(4.0d);

        SdfNoiseDisplaceNode amp = new SdfNoiseDisplaceNode();
        connectInput(amp, "input_amplitude", NodeDataType.DOUBLE);
        amp.setInput("input_sdf", sphere.getOutput("output_sdf"));
        amp.setInput("input_amplitude", -5.0d);
        amp.processNode(null);
        assertEquals(Boolean.FALSE, amp.getOutput("output_valid"));
        assertNull(amp.getOutput("output_sdf"));
        assertTrue(String.valueOf(amp.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("amplitude"));

        SdfNoiseDisplaceNode freq = new SdfNoiseDisplaceNode();
        connectInput(freq, "input_frequency", NodeDataType.DOUBLE);
        freq.setInput("input_sdf", sphere.getOutput("output_sdf"));
        freq.setInput("input_frequency", -2.0d);
        freq.processNode(null);
        assertEquals(Boolean.FALSE, freq.getOutput("output_valid"));
        assertNull(freq.getOutput("output_sdf"));
        assertTrue(String.valueOf(freq.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("frequency"));
    }

    @Test
    void domainWarpRejectsNegativeAmplitudeOrFrequency() {
        SdfSphereNode sphere = validSphere(4.0d);

        SdfDomainWarpNode amp = new SdfDomainWarpNode();
        connectInput(amp, "input_warp_amplitude", NodeDataType.DOUBLE);
        amp.setInput("input_sdf", sphere.getOutput("output_sdf"));
        amp.setInput("input_warp_amplitude", -5.0d);
        amp.processNode(null);
        assertEquals(Boolean.FALSE, amp.getOutput("output_valid"));
        assertNull(amp.getOutput("output_sdf"));
        assertTrue(String.valueOf(amp.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("amplitude"));

        SdfDomainWarpNode freq = new SdfDomainWarpNode();
        connectInput(freq, "input_warp_frequency", NodeDataType.DOUBLE);
        freq.setInput("input_sdf", sphere.getOutput("output_sdf"));
        freq.setInput("input_warp_frequency", -2.0d);
        freq.processNode(null);
        assertEquals(Boolean.FALSE, freq.getOutput("output_valid"));
        assertNull(freq.getOutput("output_sdf"));
        assertTrue(String.valueOf(freq.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("frequency"));
    }

    @Test
    void toGeometryPartialBoundsFailClosed() {
        SdfSphereNode sphere = validSphere(4.0d);
        SdfToGeometryNode toGeom = new SdfToGeometryNode();
        connectInput(toGeom, "input_min", NodeDataType.POINT);
        toGeom.setInput("input_sdf", sphere.getOutput("output_sdf"));
        toGeom.setInput("input_min", new PointData(-5, -5, -5));
        toGeom.processNode(null);
        assertEquals(Boolean.FALSE, toGeom.getOutput("output_valid"));
        assertTrue(String.valueOf(toGeom.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("both"));
    }

    @Test
    void differenceAutoBoundsAreMinuendConservative() {
        SignedDistanceFieldData left = new SphereSdfData(new Vector3d(0, 0, 0), 2.0d);
        SignedDistanceFieldData right = new SphereSdfData(new Vector3d(1000, 0, 0), 2.0d);
        BooleanSdfData hardDiff = new BooleanSdfData(left, right, BooleanSdfData.Operation.DIFFERENCE, 0.0d);

        SdfBoundsEstimator.AxisAlignedBounds leftBounds = SdfBoundsEstimator.estimate(left);
        SdfBoundsEstimator.AxisAlignedBounds diffBounds = SdfBoundsEstimator.estimate(hardDiff);
        assertNotNull(leftBounds);
        assertNotNull(diffBounds);
        assertEquals(leftBounds.min().x, diffBounds.min().x, 1e-9d);
        assertEquals(leftBounds.max().x, diffBounds.max().x, 1e-9d);
        assertTrue(diffBounds.max().x < 100.0d);
    }

    @Test
    void connectedInvalidRadiusDoesNotWashOutToProperty() {
        SdfSphereNode sphere = new SdfSphereNode();
        connectInput(sphere, "input_center", NodeDataType.POINT);
        connectInput(sphere, "input_radius", NodeDataType.DOUBLE);
        sphere.setInput("input_center", new PointData(0, 0, 0));
        sphere.setInput("input_radius", -1.0d);
        sphere.processNode(null);
        assertEquals(Boolean.FALSE, sphere.getOutput("output_valid"));
        assertNull(sphere.getOutput("output_sdf"));
    }

    @Test
    void sdfValueTypesRejectInvalidConstruction() {
        assertThrows(IllegalArgumentException.class,
            () -> new SphereSdfData(new Vector3d(0, 0, 0), Double.NaN));
        assertThrows(IllegalArgumentException.class,
            () -> new SphereSdfData(new Vector3d(0, 0, 0), -1.0d));
        assertThrows(IllegalArgumentException.class,
            () -> new BoxSdfData(new Vector3d(0, 0, 0), new Vector3d(1, 0, 1)));
        assertThrows(IllegalArgumentException.class,
            () -> new CapsuleSdfData(
                new Vector3d(-1.0e308d, 0, 0),
                new Vector3d(1.0e308d, 0, 0),
                1.0d));
        Matrix3d shear = new Matrix3d().identity();
        shear.m01 = 1.0d;
        SphereSdfData sphere = new SphereSdfData(new Vector3d(0, 0, 0), 1.0d);
        assertThrows(IllegalArgumentException.class,
            () -> new TransformedSdfData(sphere, new Vector3d(0, 0, 0), shear, 1.0d));
    }

    @Test
    void signConventionNegativeInside() {
        SphereSdfData sphere = new SphereSdfData(new Vector3d(0, 0, 0), 2.0d);
        assertTrue(sphere.sampleDistance(new Vector3d(0, 0, 0)) < 0.0d);
        assertEquals(0.0d, sphere.sampleDistance(new Vector3d(2, 0, 0)), 1e-9d);
        assertTrue(sphere.sampleDistance(new Vector3d(4, 0, 0)) > 0.0d);
    }

    @Test
    void noiseOffsetNaNFailsClosed() {
        SdfSphereNode sphere = validSphere(4.0d);
        SdfNoiseDisplaceNode noise = new SdfNoiseDisplaceNode();
        noise.setInput("input_sdf", sphere.getOutput("output_sdf"));
        Map<String, Object> state = new HashMap<>();
        state.put("offsetX", Double.NaN);
        state.put("offsetY", 0.0d);
        state.put("offsetZ", 0.0d);
        noise.setNodeState(state);
        noise.processNode(null);
        assertEquals(Boolean.FALSE, noise.getOutput("output_valid"));
        assertNull(noise.getOutput("output_sdf"));
    }

    @Test
    void gradientHugeComponentsFailAndInvalidDistanceIsNan() {
        SignedDistanceFieldData steep = point -> 5.0e307d * (point.x + point.y + point.z);
        SdfGradientPointNode gradient = new SdfGradientPointNode();
        gradient.setInput("input_sdf", steep);
        gradient.setInput("input_point", new PointData(0, 0, 0));
        connectInput(gradient, "input_step", NodeDataType.DOUBLE);
        gradient.setInput("input_step", 1.0d);
        gradient.processNode(null);
        assertEquals(Boolean.FALSE, gradient.getOutput("output_valid"));
        assertNull(gradient.getOutput("output_gradient"));

        SdfGradientPointNode missing = new SdfGradientPointNode();
        missing.processNode(null);
        assertEquals(Boolean.FALSE, missing.getOutput("output_valid"));
        assertTrue(Double.isNaN(((Number) missing.getOutput("output_distance")).doubleValue()));
    }

    @Test
    void transformBoundsExtremeMinMaxAreNullNotInfinite() {
        SphereSdfData sphere = new SphereSdfData(new Vector3d(1.0e308d, 0, 0), 1.0d);
        TransformedSdfData transformed = new TransformedSdfData(
            sphere, new Vector3d(1.0e308d, 0, 0), 0, 0, 0, 1.0d);
        SdfBoundsEstimator.AxisAlignedBounds bounds = SdfBoundsEstimator.estimate(transformed);
        assertNull(bounds);
    }

    @Test
    void booleanUnknownOperationDoesNotProcessAsUnion() {
        SdfSphereNode a = validSphere(2.0d);
        SdfSphereNode b = validSphere(3.0d);
        SdfBooleanNode bool = new SdfBooleanNode();
        bool.setInput("input_a", a.getOutput("output_sdf"));
        bool.setInput("input_b", b.getOutput("output_sdf"));
        bool.setNodeState(Map.of("operation", "BANANA", "smoothK", 0.0d));
        bool.processNode(null);
        assertEquals(Boolean.FALSE, bool.getOutput("output_valid"));
        assertNull(bool.getOutput("output_sdf"));
    }

    @Test
    void blendMaskRejectsIntegerListEntries() {
        SdfBlendMaterialMaskNode mask = new SdfBlendMaterialMaskNode();
        mask.setInput("input_distances", List.of(1));
        mask.processNode(null);
        assertEquals(Boolean.FALSE, mask.getOutput("output_valid"));
    }

    @Test
    void samplePointsRejectsOversizedList() {
        SdfSphereNode sphere = validSphere(4.0d);
        SdfSamplePointsNode sample = new SdfSamplePointsNode();
        sample.setInput("input_sdf", sphere.getOutput("output_sdf"));
        sample.setInput("input_points", new AbstractList<PointData>() {
            @Override
            public PointData get(int index) {
                return new PointData(0, 0, 0);
            }

            @Override
            public int size() {
                return GenerationLimits.MAX_SDF_SAMPLE_POINTS + 1;
            }
        });
        sample.processNode(null);
        assertEquals(Boolean.FALSE, sample.getOutput("output_valid"));
    }

    private static SdfSphereNode validSphere(double radius) {
        SdfSphereNode sphere = new SdfSphereNode();
        connectInput(sphere, "input_center", NodeDataType.POINT);
        connectInput(sphere, "input_radius", NodeDataType.DOUBLE);
        sphere.setInput("input_center", new PointData(0, 0, 0));
        sphere.setInput("input_radius", radius);
        sphere.processNode(null);
        assertEquals(Boolean.TRUE, sphere.getOutput("output_valid"),
            String.valueOf(sphere.getOutput("output_error")));
        return sphere;
    }

    private static boolean hasPort(INode node, String portId) {
        return node.getOutputPorts().stream().anyMatch(p -> portId.equals(p.getId()))
            || node.getInputPorts().stream().anyMatch(p -> portId.equals(p.getId()));
    }

    private static boolean usesRawList(INode node) {
        for (IPort port : node.getInputPorts()) {
            if (port.getDataType() == NodeDataType.LIST) {
                return true;
            }
        }
        for (IPort port : node.getOutputPorts()) {
            if (port.getDataType() == NodeDataType.LIST) {
                return true;
            }
        }
        return false;
    }

    private static void assertPortType(String typeId, String portId, boolean input, NodeDataType expected) {
        INode node = registry.createNodeInstance(typeId);
        assertNotNull(node, typeId);
        List<? extends IPort> ports = input ? node.getInputPorts() : node.getOutputPorts();
        IPort port = ports.stream().filter(p -> portId.equals(p.getId())).findFirst().orElse(null);
        assertNotNull(port, typeId + " missing port " + portId);
        assertEquals(expected, port.getDataType(), typeId + "." + portId);
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

    private static final class PortStubNode extends BaseNode {
        PortStubNode(NodeDataType outputType) {
            super(UUID.randomUUID(), "test.port_stub");
            addOutputPort(new BasePort("output_stub", "Stub", "", outputType, this));
        }

        @Override
        public void processNode(com.nodecraft.nodesystem.execution.ExecutionContext context) {
        }
    }
}

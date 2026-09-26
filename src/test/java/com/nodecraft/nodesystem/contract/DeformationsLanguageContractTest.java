package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.datatypes.SignedDistanceFieldData;
import com.nodecraft.nodesystem.datatypes.VectorData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.nodes.transform.deformations.BendGeometryNode;
import com.nodecraft.nodesystem.nodes.transform.deformations.NoiseDisplacePointListNode;
import com.nodecraft.nodesystem.nodes.transform.deformations.TwistGeometryNode;
import com.nodecraft.nodesystem.nodes.transform.deformations.TwistPointListNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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
 * Deformations v1 language fence (Graph V53).
 */
class DeformationsLanguageContractTest {

    private static final Set<String> CANONICAL_IDS = Set.of(
            "transform.deformations.twist",
            "transform.deformations.bend",
            "transform.deformations.taper",
            "transform.deformations.shear_point_list",
            "transform.deformations.noise_displace",
            "transform.deformations.spherical_displace",
            "transform.deformations.curve_attract",
            "transform.deformations.relax_points",
            "transform.deformations.lattice_deform",
            "transform.deformations.twist_geometry",
            "transform.deformations.bend_geometry"
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
    void currentGraphFormatIsV53() {
        assertEquals(53, GraphFormatVersion.V53);
        assertEquals(GraphFormatVersion.V53, GraphFormatVersion.CURRENT);
    }

    @Test
    void exactlyElevenCanonicalDeformationNodesRegistered() {
        List<String> ids = registry.getAllNodeIds().stream()
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("transform.deformations."))
                .sorted()
                .toList();
        assertEquals(11, ids.size(), "Expected 11 deformations nodes: " + ids);
        assertEquals(CANONICAL_IDS, Set.copyOf(ids));
    }

    @Test
    void deformationNodesHaveUniqueOrderZeroThroughTen() {
        int[] orders = CANONICAL_IDS.stream()
                .mapToInt(typeId -> {
                    INode created = registry.createNodeInstance(typeId);
                    assertNotNull(created, typeId);
                    NodeInfo info = created.getClass().getAnnotation(NodeInfo.class);
                    assertNotNull(info, typeId);
                    return info.order();
                })
                .sorted()
                .toArray();
        assertEquals(11, orders.length);
        for (int i = 0; i < orders.length; i++) {
            assertEquals(i, orders[i], "Expected unique order " + i);
        }
    }

    @Test
    void shearIsOrderThree() {
        INode shear = registry.createNodeInstance("transform.deformations.shear_point_list");
        assertEquals(3, shear.getClass().getAnnotation(NodeInfo.class).order());
    }

    @Test
    void twistStrictPointListRejectsMalformedMember() {
        BaseNode twist = node("transform.deformations.twist");
        twist.setInput("input_points", List.of(new PointData(0, 0, 0), "bad", new PointData(1, 0, 0)));
        twist.setInput("input_axis_origin", new PointData(0, 0, 0));
        twist.setInput("input_axis_direction", new Vector3d(0, 1, 0));
        twist.processNode(null);
        assertEquals(Boolean.FALSE, twist.getOutput("output_valid"));
        assertEquals(0, twist.getOutput("output_count"));
    }

    @Test
    void twistConnectedNullAngleFailsClosed() {
        TwistProbe twist = new TwistProbe();
        twist.setInput("input_points", List.of(new PointData(1, 0, 0)));
        twist.setInput("input_axis_origin", new PointData(0, 0, 0));
        twist.setInput("input_axis_direction", new Vector3d(0, 1, 0));
        twist.connectInput("input_angle_degrees", NodeDataType.DOUBLE);
        twist.putRawInput("input_angle_degrees", null);
        twist.processNode(null);
        assertEquals(Boolean.FALSE, twist.getOutput("output_valid"));
    }

    @Test
    void twistLengthZeroFailsClosed() {
        BaseNode twist = node("transform.deformations.twist");
        twist.setInput("input_points", List.of(new PointData(1, 0, 0)));
        twist.setInput("input_axis_origin", new PointData(0, 0, 0));
        twist.setInput("input_axis_direction", new Vector3d(0, 1, 0));
        twist.setNodeState(Map.of("twistLength", 0.0d, "angleDegrees", 90.0d));
        // property 0 is rejected on set; use connected drive of 0
        TwistProbe probe = new TwistProbe();
        probe.setInput("input_points", List.of(new PointData(1, 0, 0)));
        probe.setInput("input_axis_origin", new PointData(0, 0, 0));
        probe.setInput("input_axis_direction", new Vector3d(0, 1, 0));
        probe.connectInput("input_twist_length", NodeDataType.DOUBLE);
        probe.putRawInput("input_twist_length", 0.0d);
        probe.processNode(null);
        assertEquals(Boolean.FALSE, probe.getOutput("output_valid"));
    }

    @Test
    void taperScaleZeroIsValidNegativeRejected() {
        BaseNode taper = node("transform.deformations.taper");
        assertFalse(hasPropertyField(taper, "minScale"));

        taper.setInput("input_points", List.of(new PointData(1, 0, 0), new PointData(1, 5, 0)));
        taper.setInput("input_axis_origin", new PointData(0, 0, 0));
        taper.setInput("input_axis_direction", new Vector3d(0, 1, 0));
        taper.setNodeState(Map.of("startScale", 1.0d, "endScale", 0.0d, "taperLength", 5.0d));
        taper.processNode(null);
        assertEquals(Boolean.TRUE, taper.getOutput("output_valid"));

        BaseNode negative = node("transform.deformations.taper");
        negative.setInput("input_points", List.of(new PointData(1, 0, 0)));
        negative.setInput("input_axis_origin", new PointData(0, 0, 0));
        negative.setInput("input_axis_direction", new Vector3d(0, 1, 0));
        negative.setNodeState(Map.of("startScale", -1.0d, "endScale", 1.0d, "taperLength", 5.0d));
        // negative startScale rejected by setter → property stays default 1; drive negative instead
        TaperProbe probe = new TaperProbe();
        probe.setInput("input_points", List.of(new PointData(1, 0, 0)));
        probe.setInput("input_axis_origin", new PointData(0, 0, 0));
        probe.setInput("input_axis_direction", new Vector3d(0, 1, 0));
        probe.connectInput("input_start_scale", NodeDataType.DOUBLE);
        probe.putRawInput("input_start_scale", -1.0d);
        probe.processNode(null);
        assertEquals(Boolean.FALSE, probe.getOutput("output_valid"));
    }

    @Test
    void noiseSeedRejectsNonExactInteger() {
        NoiseProbe noise = new NoiseProbe();
        noise.setInput("input_points", List.of(new PointData(0, 0, 0)));
        noise.connectInput("input_seed", NodeDataType.INTEGER);
        noise.putRawInput("input_seed", 3.8d);
        noise.processNode(null);
        assertEquals(Boolean.FALSE, noise.getOutput("output_valid"));
    }

    @Test
    void latticeRejectsSwappedMinMaxAndAcceptsVectorDataOffsets() {
        BaseNode lattice = node("transform.deformations.lattice_deform");
        lattice.setInput("input_points", List.of(new PointData(0.5, 0.5, 0.5)));
        lattice.setInput("input_min", new PointData(10, 0, 0));
        lattice.setInput("input_max", new PointData(0, 10, 10));
        lattice.setNodeState(Map.of("gridX", 1, "gridY", 1, "gridZ", 1));
        List<VectorData> offsets = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            offsets.add(VectorData.canonical(new Vector3d(0, 0, 0)));
        }
        lattice.setInput("input_offsets", offsets);
        lattice.processNode(null);
        assertEquals(Boolean.FALSE, lattice.getOutput("output_valid"));

        BaseNode ok = node("transform.deformations.lattice_deform");
        ok.setInput("input_points", List.of(new PointData(0.5, 0.5, 0.5)));
        ok.setInput("input_min", new PointData(0, 0, 0));
        ok.setInput("input_max", new PointData(2, 2, 2));
        ok.setNodeState(Map.of("gridX", 1, "gridY", 1, "gridZ", 1));
        ok.setInput("input_offsets", offsets);
        ok.processNode(null);
        assertEquals(Boolean.TRUE, ok.getOutput("output_valid"));
    }

    @Test
    void sphericalHasNoAffectOutsideAndLeavesOutsideUnchanged() {
        BaseNode spherical = node("transform.deformations.spherical_displace");
        assertFalse(hasPropertyField(spherical, "affectOutsideRadius"));

        spherical.setInput("input_points", List.of(new PointData(100, 0, 0)));
        spherical.setInput("input_center", new PointData(0, 0, 0));
        spherical.setNodeState(Map.of("strength", 5.0d, "radius", 1.0d, "falloffPower", 1.0d));
        spherical.processNode(null);
        assertEquals(Boolean.TRUE, spherical.getOutput("output_valid"));
        PointData out = assertInstanceOf(PointData.class, ((List<?>) spherical.getOutput("output_points")).getFirst());
        assertEquals(100.0d, out.position().x, 1.0e-9d);
    }

    @Test
    void pathAttractTangentialWithZeroTangentFailsClosed() {
        BaseNode attract = node("transform.deformations.curve_attract");
        attract.setInput("input_points", List.of(new PointData(1, 0, 0)));
        attract.setInput("input_path", PathData.fromPolyline(new PolylineData(List.of(
                new Vec3d(0, 0, 0),
                new Vec3d(0, 0, 0)
        ))));
        attract.setNodeState(Map.of("displacementMode", "TANGENTIAL", "strength", 1.0d, "radius", 10.0d));
        attract.processNode(null);
        assertEquals(Boolean.FALSE, attract.getOutput("output_valid"));
    }

    @Test
    void twistGeometryConnectedNullGeometryFailsEvenWithSdf() {
        TwistGeometryProbe twist = new TwistGeometryProbe();
        twist.connectInput("input_geometry", NodeDataType.GEOMETRY);
        twist.putRawInput("input_geometry", null);
        SignedDistanceFieldData sdf = point -> point.length() - 1.0d;
        twist.setInput("input_sdf", sdf);
        twist.setInput("input_axis_origin", new PointData(0, 0, 0));
        twist.setInput("input_axis_direction", new Vector3d(0, 1, 0));
        twist.processNode(null);
        assertEquals(Boolean.FALSE, twist.getOutput("output_valid"));
        assertNull(twist.getOutput("output_geometry"));
    }

    @Test
    void twistGeometryHalfConnectedBoundsFailsClosed() {
        SignedDistanceFieldData sdf = point -> point.length() - 2.0d;

        TwistGeometryProbe minOnly = new TwistGeometryProbe();
        minOnly.setInput("input_sdf", sdf);
        minOnly.setInput("input_axis_origin", new PointData(0, 0, 0));
        minOnly.setInput("input_axis_direction", new Vector3d(0, 1, 0));
        minOnly.connectInput("input_bounds_min", NodeDataType.POINT);
        minOnly.putRawInput("input_bounds_min", new PointData(0, 0, 0));
        minOnly.processNode(null);
        assertEquals(Boolean.FALSE, minOnly.getOutput("output_valid"));
        assertNull(minOnly.getOutput("output_geometry"));

        TwistGeometryProbe maxOnly = new TwistGeometryProbe();
        maxOnly.setInput("input_sdf", sdf);
        maxOnly.setInput("input_axis_origin", new PointData(0, 0, 0));
        maxOnly.setInput("input_axis_direction", new Vector3d(0, 1, 0));
        maxOnly.connectInput("input_bounds_max", NodeDataType.POINT);
        maxOnly.putRawInput("input_bounds_max", new PointData(4, 4, 4));
        maxOnly.processNode(null);
        assertEquals(Boolean.FALSE, maxOnly.getOutput("output_valid"));
        assertNull(maxOnly.getOutput("output_geometry"));
    }

    @Test
    void bendGeometryHalfConnectedBoundsFailsClosed() {
        SignedDistanceFieldData sdf = point -> point.length() - 2.0d;

        BendGeometryProbe minOnly = new BendGeometryProbe();
        minOnly.setInput("input_sdf", sdf);
        minOnly.setInput("input_axis_origin", new PointData(0, 0, 0));
        minOnly.setInput("input_axis_direction", new Vector3d(0, 1, 0));
        minOnly.setInput("input_bend_normal", new Vector3d(1, 0, 0));
        minOnly.connectInput("input_bounds_min", NodeDataType.POINT);
        minOnly.putRawInput("input_bounds_min", new PointData(0, 0, 0));
        minOnly.processNode(null);
        assertEquals(Boolean.FALSE, minOnly.getOutput("output_valid"));
        assertNull(minOnly.getOutput("output_geometry"));

        BendGeometryProbe maxOnly = new BendGeometryProbe();
        maxOnly.setInput("input_sdf", sdf);
        maxOnly.setInput("input_axis_origin", new PointData(0, 0, 0));
        maxOnly.setInput("input_axis_direction", new Vector3d(0, 1, 0));
        maxOnly.setInput("input_bend_normal", new Vector3d(1, 0, 0));
        maxOnly.connectInput("input_bounds_max", NodeDataType.POINT);
        maxOnly.putRawInput("input_bounds_max", new PointData(4, 4, 4));
        maxOnly.processNode(null);
        assertEquals(Boolean.FALSE, maxOnly.getOutput("output_valid"));
        assertNull(maxOnly.getOutput("output_geometry"));
    }

    private static boolean hasPropertyField(BaseNode node, String fieldName) {
        try {
            node.getClass().getDeclaredField(fieldName);
            return true;
        } catch (NoSuchFieldException e) {
            return false;
        }
    }

    private static BaseNode node(String typeId) {
        BaseNode created = (BaseNode) registry.createNodeInstance(typeId);
        assertNotNull(created, typeId);
        return created;
    }

    private static void connectInput(BaseNode target, String inputPortId, NodeDataType outputType) {
        PortStubNode stub = new PortStubNode(outputType);
        BasePort output = (BasePort) stub.getOutputPorts().getFirst();
        BasePort input = (BasePort) target.getInputPorts().stream()
                .filter(port -> inputPortId.equals(port.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(target.getTypeId() + " missing port " + inputPortId));
        assertTrue(output.connectTo(input), inputPortId + " connect failed");
    }

    private static final class TwistProbe extends TwistPointListNode {
        void putRawInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            DeformationsLanguageContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class TaperProbe
            extends com.nodecraft.nodesystem.nodes.transform.deformations.TaperPointListNode {
        void putRawInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            DeformationsLanguageContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class NoiseProbe extends NoiseDisplacePointListNode {
        void putRawInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            DeformationsLanguageContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class TwistGeometryProbe extends TwistGeometryNode {
        void putRawInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            DeformationsLanguageContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class BendGeometryProbe extends BendGeometryNode {
        void putRawInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            DeformationsLanguageContractTest.connectInput(this, portId, outputType);
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

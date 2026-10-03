package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.datatypes.SignedDistanceFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.transform.deformations.BendPointListNode;
import com.nodecraft.nodesystem.nodes.transform.deformations.TwistSdfNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.VectorUtils;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fail-closed numerical contract for transform.deformations shared geometry math.
 */
class DeformationNumericalRobustnessContractTest {

    private static NodeRegistry registry;

    @BeforeAll
    static void init() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void twistTaperBendHugeFiniteAxesStayFiniteOrFailClosed() {
        assertAxisDeformFinite("transform.deformations.twist", new Vector3d(1e308d, 0, 0));
        assertAxisDeformFinite("transform.deformations.taper", new Vector3d(0, -1e308d, 0));
        assertAxisDeformFinite("transform.deformations.bend", new Vector3d(1e308d, 1e308d, 1e308d));
    }

    @Test
    void sphericalDisplaceHugeOffsetStaysFiniteOrFailClosed() {
        BaseNode node = node("transform.deformations.spherical_displace");
        node.setInput("input_points", List.of(new PointData(1e308d, 1e308d, 0)));
        node.setInput("input_center", new PointData(0, 0, 0));
        node.setNodeState(Map.of("strength", 1.0d, "radius", 1e308d, "falloffPower", 1.0d));
        node.processNode(null);
        assertFinitePointsOrInvalid(node);
    }

    @Test
    void curveAttractHugeFiniteSegmentStaysFiniteOrFailClosed() {
        BaseNode attract = node("transform.deformations.curve_attract");
        attract.setInput("input_points", List.of(new PointData(0, 0, 0)));
        attract.setInput("input_path", PathData.fromPolyline(new PolylineData(List.of(
            new Vec3d(1e308d, 0, 0),
            new Vec3d(0, 1e308d, 0)
        ))));
        attract.setNodeState(Map.of("strength", 1.0d, "radius", 1e308d));
        attract.processNode(null);
        assertFinitePointsOrInvalid(attract);
    }

    @Test
    void twistSdfHugeBoundsFailClosedOrStayFinite() {
        TwistSdfProbe probe = new TwistSdfProbe();
        SignedDistanceFieldData sdf = point -> point.length() - 1.0d;
        probe.setInput("input_sdf", sdf);
        probe.setInput("input_axis_origin", new PointData(0, 0, 0));
        probe.connectInput("input_axis_direction", NodeDataType.VECTOR);
        probe.setInput("input_axis_direction", new Vector3d(0, 1, 0));
        probe.connectInput("input_bounds_min", NodeDataType.POINT);
        probe.connectInput("input_bounds_max", NodeDataType.POINT);
        probe.setInput("input_bounds_min", new PointData(-1e308d, -1e308d, -1e308d));
        probe.setInput("input_bounds_max", new PointData(1e308d, 1e308d, 1e308d));
        probe.processNode(null);
        if (Boolean.TRUE.equals(probe.getOutput("output_valid"))) {
            PointData min = (PointData) probe.getOutput("output_bounds_min");
            PointData max = (PointData) probe.getOutput("output_bounds_max");
            assertTrue(VectorUtils.isFinite(min.position()));
            assertTrue(VectorUtils.isFinite(max.position()));
        } else {
            assertEquals(Boolean.FALSE, probe.getOutput("output_valid"));
            assertNull(probe.getOutput("output_sdf"));
        }
    }

    @Test
    void bendCustomParallelNormalFailsClosed() {
        BendPointListProbe bend = new BendPointListProbe();
        bend.setNodeState(Map.of("bendPlaneMode", "CUSTOM", "bendDegrees", 45.0d, "bendLength", 10.0d));
        bend.setInput("input_points", List.of(new PointData(1, 0, 0)));
        bend.setInput("input_axis_origin", new PointData(0, 0, 0));
        bend.connectInput("input_axis_direction", NodeDataType.VECTOR);
        bend.connectInput("input_bend_normal", NodeDataType.VECTOR);
        bend.setInput("input_axis_direction", new Vector3d(0, 1, 0));
        bend.setInput("input_bend_normal", new Vector3d(0, 1e308d, 0));
        bend.processNode(null);
        assertEquals(Boolean.FALSE, bend.getOutput("output_valid"));
    }

    @Test
    void latticeRejectsOversizedOffsetListAtBudget() {
        BaseNode lattice = node("transform.deformations.lattice_deform");
        lattice.setInput("input_points", List.of(new PointData(0.5, 0.5, 0.5)));
        lattice.setInput("input_min", new PointData(0, 0, 0));
        lattice.setInput("input_max", new PointData(1, 1, 1));
        lattice.setNodeState(Map.of("gridX", 1, "gridY", 1, "gridZ", 1));
        List<Vector3d> offsets = new ArrayList<>();
        for (int i = 0; i < 730; i++) {
            offsets.add(new Vector3d(0, 0, 0));
        }
        lattice.setInput("input_offsets", offsets);
        lattice.processNode(null);
        assertEquals(Boolean.FALSE, lattice.getOutput("output_valid"));
        assertNotEquals("", String.valueOf(lattice.getOutput("output_error")));
    }

    private static void assertAxisDeformFinite(String typeId, Vector3d axis) {
        BaseNode node = node(typeId);
        node.setInput("input_points", List.of(new PointData(1, 0, 0), new PointData(0, 1, 0)));
        node.setInput("input_axis_origin", new PointData(0, 0, 0));
        node.setInput("input_axis_direction", axis);
        node.processNode(null);
        assertFinitePointsOrInvalid(node);
    }

    private static void assertFinitePointsOrInvalid(BaseNode node) {
        if (Boolean.TRUE.equals(node.getOutput("output_valid"))) {
            @SuppressWarnings("unchecked")
            List<PointData> points = (List<PointData>) node.getOutput("output_points");
            assertFalse(points.isEmpty());
            for (PointData point : points) {
                assertTrue(VectorUtils.isFinite(point.position()), "Valid output must be finite");
            }
        } else {
            assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        }
    }

    private static BaseNode node(String typeId) {
        return (BaseNode) registry.createNodeInstance(typeId);
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

    private static final class TwistSdfProbe extends TwistSdfNode {
        void connectInput(String portId, NodeDataType outputType) {
            DeformationNumericalRobustnessContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class BendPointListProbe extends BendPointListNode {
        void connectInput(String portId, NodeDataType outputType) {
            DeformationNumericalRobustnessContractTest.connectInput(this, portId, outputType);
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

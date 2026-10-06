package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.datatypes.FrameDataTestAccess;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.VectorData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.FrameUtils;
import com.nodecraft.nodesystem.util.VectorUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Numerical fail-closed contract for Orientation / Frame / Plane shared normalization.
 */
class OrientationNumericalRobustnessContractTest {

    private static NodeRegistry registry;

    @BeforeAll
    static void init() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void rotateVectorHugeFiniteAxesStayFiniteOrFailClosed() {
        assertRotateAxisFinite(new Vector3d(1e308d, 0, 0));
        assertRotateAxisFinite(new Vector3d(0, -1e308d, 0));
        assertRotateAxisFinite(new Vector3d(1e308d, 1e308d, 1e308d));
    }

    @Test
    void rotateVectorNearZeroAxisFailsClosed() {
        RotateProbe rotate = new RotateProbe();
        rotate.connectInput("input_axis", NodeDataType.VECTOR);
        rotate.setInput("input_vector", new VectorData(1, 0, 0));
        rotate.setInput("input_axis", new VectorData(Double.MIN_NORMAL, 0, 0));
        rotate.setInput("input_angle", 90.0d);
        rotate.processNode(null);
        assertInvalidFiniteFree(rotate, "output_rotated_vector");
    }

    @Test
    void planeCanonicalHugeFiniteNormalIsUnitOrNull() {
        assertPlaneNormalRobust(new Vector3d(1e308d, 0, 0));
        assertPlaneNormalRobust(new Vector3d(0, -1e308d, 0));
        assertPlaneNormalRobust(new Vector3d(1e308d, 1e308d, 1e308d));
        assertNull(PlaneData.canonical(new Vector3d(), new Vector3d(Double.MIN_NORMAL, 0, 0)));
        assertNull(PlaneData.canonical(new Vector3d(Double.NaN, 0, 0), new Vector3d(0, 1, 0)));
        assertNull(PlaneData.canonical(new Vector3d(), new Vector3d(Double.NaN, 1, 0)));
    }

    @Test
    void frameOrthonormalHugeFiniteAxesStayCanonicalOrNull() {
        FrameData xHuge = FrameData.orthonormal(
            new Vector3d(), new Vector3d(1e308d, 0, 0), new Vector3d(0, 1, 0), new Vector3d(0, 0, 1));
        assertCanonicalOrNull(xHuge);
        FrameData diag = FrameData.orthonormal(
            new Vector3d(),
            new Vector3d(1e308d, 1e308d, 1e308d),
            new Vector3d(0, 1e308d, 0),
            new Vector3d(0, 0, 1e308d));
        assertCanonicalOrNull(diag);
        assertNull(FrameData.orthonormal(
            new Vector3d(), new Vector3d(Double.MIN_NORMAL, 0, 0), new Vector3d(0, 1, 0), new Vector3d(0, 0, 1)));
    }

    @Test
    void alignHugeFiniteNormalStaysFiniteOrFailClosed() {
        AlignProbe align = new AlignProbe();
        align.setInput("input_points", List.of(new PointData(0, 0, 0)));
        align.setInput("input_normals", List.of(new VectorData(0, 1e308d, 0)));
        align.processNode(null);
        assertAlignFiniteOrInvalid(align);
    }

    @Test
    void alignHugeForwardHintStaysFiniteOrFailClosed() {
        AlignProbe align = new AlignProbe();
        align.setInput("input_points", List.of(new PointData(0, 0, 0)));
        align.setInput("input_normals", List.of(new VectorData(0, 1, 0)));
        align.connectInput("input_forward_hint", NodeDataType.VECTOR);
        align.setInput("input_forward_hint", new VectorData(1e308d, 0, 0));
        align.processNode(null);
        assertAlignFiniteOrInvalid(align);
    }

    @Test
    void alignParallelForwardHintFailsClosed() {
        AlignProbe align = new AlignProbe();
        align.setInput("input_points", List.of(new PointData(0, 0, 0)));
        align.setInput("input_normals", List.of(new VectorData(0, 1, 0)));
        align.connectInput("input_forward_hint", NodeDataType.VECTOR);
        align.setInput("input_forward_hint", new VectorData(0, 1e308d, 0));
        align.processNode(null);
        assertEquals(Boolean.FALSE, align.getOutput("output_valid"));
        assertEquals(0, align.getOutput("output_count"));
    }

    @Test
    void alignNonFinitePointOrNormalFailsClosed() {
        AlignProbe nanPoint = new AlignProbe();
        nanPoint.setInput("input_points", List.of(new Vector3d(Double.NaN, 0, 0)));
        nanPoint.setInput("input_normals", List.of(new VectorData(0, 1, 0)));
        nanPoint.processNode(null);
        assertEquals(Boolean.FALSE, nanPoint.getOutput("output_valid"));

        AlignProbe nanNormal = new AlignProbe();
        nanNormal.setInput("input_points", List.of(new PointData(0, 0, 0)));
        nanNormal.setInput("input_normals", List.of(new Vector3d(0, Double.NaN, 0)));
        nanNormal.processNode(null);
        assertEquals(Boolean.FALSE, nanNormal.getOutput("output_valid"));

        AlignProbe nearZero = new AlignProbe();
        nearZero.setInput("input_points", List.of(new PointData(0, 0, 0)));
        nearZero.setInput("input_normals", List.of(new VectorData(Double.MIN_NORMAL, 0, 0)));
        nearZero.processNode(null);
        assertEquals(Boolean.FALSE, nearZero.getOutput("output_valid"));
    }

    @Test
    void projectPointToPlaneInvalidOutputsNullNotNaN() {
        BaseNode project = (BaseNode) registry.createNodeInstance("transform.orientation.project_to_plane");
        project.processNode(null);
        assertEquals(Boolean.FALSE, project.getOutput("output_valid"));
        assertNull(project.getOutput("output_point"));
        assertNull(project.getOutput("output_distance"));
        assertNull(project.getOutput("output_signed_distance"));
    }

    @Test
    void resolveStrictFrameListRejectsZeroAxisEntry() {
        FrameData good = FrameDataTestAccess.unchecked(
            new Vector3d(), new Vector3d(1, 0, 0), new Vector3d(0, 1, 0), new Vector3d(0, 0, 1));
        FrameData bad = FrameDataTestAccess.unchecked(
            new Vector3d(1, 0, 0), new Vector3d(), new Vector3d(), new Vector3d());
        assertTrue(good.isCanonical());
        assertFalse(bad.isCanonical());
        assertNull(FrameUtils.resolveStrictFrameList(List.of(good, bad)));
        assertEquals(1, FrameUtils.resolveStrictFrameList(List.of(good)).size());
    }

    private static void assertRotateAxisFinite(Vector3d axis) {
        RotateProbe rotate = new RotateProbe();
        rotate.connectInput("input_axis", NodeDataType.VECTOR);
        rotate.setInput("input_vector", new VectorData(1, 0, 0));
        rotate.setInput("input_axis", new VectorData(axis));
        rotate.setInput("input_angle", 90.0d);
        rotate.processNode(null);
        Object valid = rotate.getOutput("output_valid");
        Object rotated = rotate.getOutput("output_rotated_vector");
        if (Boolean.TRUE.equals(valid)) {
            Vector3d result = VectorUtils.toVector(rotated);
            assertTrue(VectorUtils.isFinite(result), "Valid rotate must emit a finite vector for axis " + axis);
        } else {
            assertEquals(Boolean.FALSE, valid);
            assertNull(rotated);
        }
    }

    private static void assertPlaneNormalRobust(Vector3d normal) {
        PlaneData plane = PlaneData.canonical(new Vector3d(), normal);
        if (plane == null) {
            return;
        }
        Vector3d n = plane.getNormal();
        assertTrue(VectorUtils.isFinite(n));
        assertEquals(1.0d, VectorUtils.safeLength(n), 1.0e-9d);
    }

    private static void assertCanonicalOrNull(@Nullable FrameData frame) {
        if (frame == null) {
            return;
        }
        assertTrue(frame.isCanonical());
        assertTrue(VectorUtils.isFinite(frame.getXAxis()));
        assertTrue(VectorUtils.isFinite(frame.getYAxis()));
        assertTrue(VectorUtils.isFinite(frame.getZAxis()));
    }

    private static void assertAlignFiniteOrInvalid(AlignProbe align) {
        if (Boolean.TRUE.equals(align.getOutput("output_valid"))) {
            @SuppressWarnings("unchecked")
            List<FrameData> frames = (List<FrameData>) align.getOutput("output_frames");
            assertNotNull(frames);
            assertFalse(frames.isEmpty());
            FrameData frame = frames.getFirst();
            assertTrue(frame.isCanonical());
            @SuppressWarnings("unchecked")
            List<PlaneData> planes = (List<PlaneData>) align.getOutput("output_planes");
            assertTrue(VectorUtils.isFinite(planes.getFirst().getNormal()));
        } else {
            assertEquals(Boolean.FALSE, align.getOutput("output_valid"));
        }
    }

    private static void assertInvalidFiniteFree(BaseNode node, String outputId) {
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertNull(node.getOutput(outputId));
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

    private static final class AlignProbe
            extends com.nodecraft.nodesystem.nodes.transform.orientation.AlignPointsToSurfaceNormalsNode {
        void connectInput(String portId, NodeDataType outputType) {
            OrientationNumericalRobustnessContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class RotateProbe
            extends com.nodecraft.nodesystem.nodes.transform.orientation.RotateVectorNode {
        void connectInput(String portId, NodeDataType outputType) {
            OrientationNumericalRobustnessContractTest.connectInput(this, portId, outputType);
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

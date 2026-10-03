package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.ArchitecturalPrimitiveSupport;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.MoldingProfileNode;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Face-driven molding profiles must keep FaceFrame X/Y, not rebuild from plane normal.
 */
class MoldingProfileOrientationContractTest {

    private static final double TOL = 0.05d;

    @Test
    void faceInputPreservesFaceLocalAxesForFlatProfile() {
        // Face in XY with normal +Z: FaceFrame X = world X (edge 0→1).
        // createBasis(normal) would instead pick world Y as X — regression target.
        BoxFaceData face = sampleFace(6.0d, 4.0d);
        ArchitecturalPrimitiveSupport.FaceFrame frame = ArchitecturalPrimitiveSupport.resolveFaceFrame(face);
        assertTrue(frame != null);

        MoldingProbe node = new MoldingProbe();
        node.connectInput("input_profile_type", NodeDataType.STRING);
        node.connectInput("input_width", NodeDataType.DOUBLE);
        node.connectInput("input_height", NodeDataType.DOUBLE);
        node.setInput("input_face", face);
        node.setInput("input_profile_type", "flat");
        node.setInput("input_width", 2.0d);
        node.setInput("input_height", 1.0d);
        node.processNode(null);

        assertEquals(Boolean.TRUE, node.getOutput("output_valid"), String.valueOf(node.getOutput("output_error")));

        @SuppressWarnings("unchecked")
        List<PointData> points = (List<PointData>) node.getOutput("output_points");
        assertTrue(points.size() >= 4);

        Vector3d center = frame.center();
        Vector3d right = farthestAlong(points, center, frame.xAxis());
        Vector3d left = farthestAlong(points, center, new Vector3d(frame.xAxis()).negate());
        Vector3d up = farthestAlong(points, center, frame.yAxis());

        // Flat profile half-width/half-height extents along face axes.
        assertEquals(1.0d, new Vector3d(right).sub(center).dot(frame.xAxis()), TOL);
        assertEquals(0.5d, new Vector3d(up).sub(center).dot(frame.yAxis()), TOL);

        // Width edge must align with FaceFrame X, not plane-normal fallback X (world Y).
        Vector3d widthEdge = new Vector3d(right).sub(left).normalize();
        assertEquals(1.0d, Math.abs(widthEdge.dot(frame.xAxis())), TOL);
        Vector3d fallbackX = new Vector3d(0.0d, 1.0d, 0.0d);
        assertTrue(Math.abs(widthEdge.dot(fallbackX)) < 0.2d,
            "profile width should follow face X, not plane-normal fallback X");
    }

    private static Vector3d farthestAlong(List<PointData> points, Vector3d origin, Vector3d axis) {
        Vector3d best = points.getFirst().position();
        double bestDot = new Vector3d(best).sub(origin).dot(axis);
        for (PointData point : points) {
            Vector3d position = point.position();
            double dot = new Vector3d(position).sub(origin).dot(axis);
            if (dot > bestDot) {
                bestDot = dot;
                best = position;
            }
        }
        return best;
    }

    private static BoxFaceData sampleFace(double width, double height) {
        double halfW = width / 2.0d;
        List<Vector3d> corners = List.of(
            new Vector3d(-halfW, 0, 0),
            new Vector3d(halfW, 0, 0),
            new Vector3d(halfW, height, 0),
            new Vector3d(-halfW, height, 0)
        );
        return new BoxFaceData(0, "front", List.of(0, 1, 2, 3), corners,
            new Vector3d(0, height / 2.0d, 0), new Vector3d(0, 0, 1));
    }

    private static void connectInput(BaseNode target, String inputPortId, NodeDataType outputType) {
        PortStubNode stub = new PortStubNode(outputType);
        BasePort output = (BasePort) stub.getOutputPorts().getFirst();
        BasePort input = (BasePort) target.getInputPorts().stream()
            .filter(port -> inputPortId.equals(port.getId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError(target.getTypeId() + " missing port " + inputPortId));
        assertTrue(output.connectTo(input), inputPortId + " connect failed");
        target.getInput(inputPortId);
    }

    private static final class MoldingProbe extends MoldingProfileNode {
        void connectInput(String portId, NodeDataType outputType) {
            MoldingProfileOrientationContractTest.connectInput(this, portId, outputType);
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

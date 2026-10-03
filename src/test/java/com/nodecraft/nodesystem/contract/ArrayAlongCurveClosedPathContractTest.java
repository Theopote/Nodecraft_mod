package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.ArrayAlongCurveNode;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Closed-path sampling must not duplicate the seam (distance 0 == distance total).
 */
class ArrayAlongCurveClosedPathContractTest {

    private static final double TOL = 0.15d;

    @Test
    void closedSquareCountFourYieldsFourDistinctPositions() {
        ArrayProbe node = new ArrayProbe();
        node.connectInput("input_path", NodeDataType.PATH);
        node.connectInput("input_count", NodeDataType.INTEGER);
        node.connectInput("input_width", NodeDataType.DOUBLE);
        node.connectInput("input_height", NodeDataType.DOUBLE);
        node.connectInput("input_depth", NodeDataType.DOUBLE);
        node.setInput("input_path", closedSquarePath(4.0d));
        node.setInput("input_count", 4);
        node.setInput("input_width", 0.2d);
        node.setInput("input_height", 1.0d);
        node.setInput("input_depth", 0.2d);
        node.processNode(null);

        assertEquals(Boolean.TRUE, node.getOutput("output_valid"), String.valueOf(node.getOutput("output_error")));
        assertEquals(4, node.getOutput("output_count"));

        List<Vector3d> centers = boxCenters(node.getOutput("output_geometry"));
        assertEquals(4, centers.size());
        assertEquals(4, distinctHorizKeys(centers).size(), "closed Count must not seam-duplicate");
    }

    @Test
    void closedSquareSpacingDoesNotDuplicateSeam() {
        ArrayProbe node = new ArrayProbe();
        node.connectInput("input_path", NodeDataType.PATH);
        node.connectInput("input_spacing", NodeDataType.DOUBLE);
        node.connectInput("input_width", NodeDataType.DOUBLE);
        node.connectInput("input_height", NodeDataType.DOUBLE);
        node.connectInput("input_depth", NodeDataType.DOUBLE);
        // Perimeter 16; spacing 4 → samples at 0,4,8,12 (not 16).
        node.setInput("input_path", closedSquarePath(4.0d));
        node.setInput("input_spacing", 4.0d);
        node.setInput("input_width", 0.2d);
        node.setInput("input_height", 1.0d);
        node.setInput("input_depth", 0.2d);
        node.processNode(null);

        assertEquals(Boolean.TRUE, node.getOutput("output_valid"), String.valueOf(node.getOutput("output_error")));
        List<Vector3d> centers = boxCenters(node.getOutput("output_geometry"));
        assertEquals(4, centers.size());
        assertEquals(4, distinctHorizKeys(centers).size(), "closed Spacing must not seam-duplicate");
    }

    @Test
    void openPathCountStillIncludesEndpoints() {
        ArrayProbe node = new ArrayProbe();
        node.connectInput("input_path", NodeDataType.PATH);
        node.connectInput("input_count", NodeDataType.INTEGER);
        node.connectInput("input_width", NodeDataType.DOUBLE);
        node.connectInput("input_height", NodeDataType.DOUBLE);
        node.connectInput("input_depth", NodeDataType.DOUBLE);
        node.setInput("input_path", openLinePath(10.0d));
        node.setInput("input_count", 3);
        node.setInput("input_width", 0.2d);
        node.setInput("input_height", 1.0d);
        node.setInput("input_depth", 0.2d);
        node.processNode(null);

        assertEquals(Boolean.TRUE, node.getOutput("output_valid"), String.valueOf(node.getOutput("output_error")));
        List<Vector3d> centers = boxCenters(node.getOutput("output_geometry"));
        assertEquals(3, centers.size());
        assertTrue(centers.stream().anyMatch(c -> Math.abs(c.x - 0.0d) < TOL));
        assertTrue(centers.stream().anyMatch(c -> Math.abs(c.x - 10.0d) < TOL));
    }

    private static PathData closedSquarePath(double side) {
        return PathData.fromPolyline(new PolylineData(List.of(
            new Vec3d(0.0d, 0.0d, 0.0d),
            new Vec3d(side, 0.0d, 0.0d),
            new Vec3d(side, 0.0d, side),
            new Vec3d(0.0d, 0.0d, side),
            new Vec3d(0.0d, 0.0d, 0.0d)
        )));
    }

    private static PathData openLinePath(double length) {
        return PathData.fromPolyline(new PolylineData(List.of(
            new Vec3d(0.0d, 0.0d, 0.0d),
            new Vec3d(length, 0.0d, 0.0d)
        )));
    }

    private static List<Vector3d> boxCenters(Object geometryOutput) {
        CompositeGeometryData geometry = assertInstanceOf(CompositeGeometryData.class, geometryOutput);
        return geometry.geometries().stream()
            .map(g -> assertInstanceOf(BoxGeometryData.class, g).getCenter())
            .toList();
    }

    private static Set<String> distinctHorizKeys(List<Vector3d> centers) {
        Set<String> keys = new HashSet<>();
        for (Vector3d center : centers) {
            keys.add(Math.round(center.x / TOL) + ":" + Math.round(center.z / TOL));
        }
        return keys;
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

    private static final class ArrayProbe extends ArrayAlongCurveNode {
        void connectInput(String portId, NodeDataType outputType) {
            ArrayAlongCurveClosedPathContractTest.connectInput(this, portId, outputType);
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

package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.PrismGeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.ArchOpeningNode;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Arch Opening spatial alignment: rectangle uses face Y for stem and +Z for depth.
 */
class ArchOpeningGeometryContractTest {

    private static final double TOL = 0.05d;

    @Test
    void rectangleCenterUsesFaceVerticalAndDepthAxes() {
        ArchProbe node = new ArchProbe();
        node.connectInput("input_width", NodeDataType.DOUBLE);
        node.connectInput("input_height", NodeDataType.DOUBLE);
        node.connectInput("input_depth", NodeDataType.DOUBLE);
        node.connectInput("input_arch_type", NodeDataType.STRING);
        node.setInput("input_face", sampleFace(8.0d, 6.0d));
        node.setInput("input_width", 2.0d);
        node.setInput("input_height", 3.0d);
        node.setInput("input_depth", 0.5d);
        node.setInput("input_arch_type", "rectangle");
        node.processNode(null);

        assertEquals(Boolean.TRUE, node.getOutput("output_valid"), String.valueOf(node.getOutput("output_error")));
        BoxGeometryData box = assertInstanceOf(BoxGeometryData.class, node.getOutput("output_geometry"));

        // Face center (0, 3, 0); stemHeight 3 → local Y = -3 + 1.5 = -1.5 → world Y = 1.5
        // Depth 0.5 → center along +Z by 0.25
        assertEquals(0.0d, box.getCenter().x, TOL);
        assertEquals(1.5d, box.getCenter().y, TOL);
        assertEquals(0.25d, box.getCenter().z, TOL);
        assertEquals(1.0d, box.getHalfExtents().x, TOL);
        assertEquals(1.5d, box.getHalfExtents().y, TOL);
        assertEquals(0.25d, box.getHalfExtents().z, TOL);
    }

    @Test
    void roundAndPointedExtrudeAlongFaceNormal() {
        for (String archType : List.of("round", "pointed")) {
            ArchProbe node = new ArchProbe();
            node.connectInput("input_width", NodeDataType.DOUBLE);
            node.connectInput("input_height", NodeDataType.DOUBLE);
            node.connectInput("input_depth", NodeDataType.DOUBLE);
            node.connectInput("input_arch_type", NodeDataType.STRING);
            node.setInput("input_face", sampleFace(8.0d, 6.0d));
            node.setInput("input_width", 2.0d);
            node.setInput("input_height", 3.0d);
            node.setInput("input_depth", 0.5d);
            node.setInput("input_arch_type", archType);
            node.processNode(null);

            assertEquals(Boolean.TRUE, node.getOutput("output_valid"), archType + ": " + node.getOutput("output_error"));
            GeometryData geometry = assertInstanceOf(GeometryData.class, node.getOutput("output_geometry"));
            PrismGeometryData prism = firstPrism(geometry);
            Vector3d extrusion = prism.extrusionVector();
            assertEquals(0.0d, extrusion.x, TOL, archType);
            assertEquals(0.0d, extrusion.y, TOL, archType);
            assertEquals(0.5d, extrusion.z, TOL, archType);

            double minY = prism.baseVertices().stream().mapToDouble(v -> v.y).min().orElseThrow();
            assertEquals(0.0d, minY, TOL, archType + " bottom should align with face bottom edge");
        }
    }

    @Test
    void rectangleBottomAlignsWithFaceBottom() {
        ArchProbe node = new ArchProbe();
        node.connectInput("input_width", NodeDataType.DOUBLE);
        node.connectInput("input_height", NodeDataType.DOUBLE);
        node.connectInput("input_depth", NodeDataType.DOUBLE);
        node.connectInput("input_arch_type", NodeDataType.STRING);
        node.setInput("input_face", sampleFace(8.0d, 6.0d));
        node.setInput("input_width", 2.0d);
        node.setInput("input_height", 3.0d);
        node.setInput("input_depth", 0.5d);
        node.setInput("input_arch_type", "rectangle");
        node.processNode(null);

        BoxGeometryData box = assertInstanceOf(BoxGeometryData.class, node.getOutput("output_geometry"));
        double bottomY = box.getCenter().y - box.getHalfExtents().y;
        assertEquals(0.0d, bottomY, TOL);
    }

    @Test
    void stemHeightPortDisplayName() {
        ArchOpeningNode node = new ArchOpeningNode();
        BasePort height = (BasePort) node.getInputPorts().stream()
            .filter(port -> "input_height".equals(port.getId()))
            .findFirst()
            .orElseThrow();
        assertEquals("Stem Height", height.getDisplayName());
    }

    private static PrismGeometryData firstPrism(GeometryData geometry) {
        if (geometry instanceof PrismGeometryData prism) {
            return prism;
        }
        CompositeGeometryData composite = assertInstanceOf(CompositeGeometryData.class, geometry);
        assertTrue(!composite.isEmpty());
        return assertInstanceOf(PrismGeometryData.class, composite.geometries().getFirst());
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

    private static final class ArchProbe extends ArchOpeningNode {
        void connectInput(String portId, NodeDataType outputType) {
            ArchOpeningGeometryContractTest.connectInput(this, portId, outputType);
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

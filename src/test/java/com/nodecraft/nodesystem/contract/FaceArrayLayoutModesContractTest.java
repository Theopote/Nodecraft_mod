package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.WindowArrayNode;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FaceArrayLayoutModesContractTest {

    @Test
    void distributeModeSpacesWindowsEvenly() {
        WindowArrayProbe node = new WindowArrayProbe();
        node.connectInput("input_columns", NodeDataType.INTEGER);
        node.connectInput("input_rows", NodeDataType.INTEGER);
        node.connectInput("input_window_width", NodeDataType.DOUBLE);
        node.connectInput("input_window_height", NodeDataType.DOUBLE);
        node.connectInput("input_margin", NodeDataType.DOUBLE);
        node.connectInput("input_layout_mode", NodeDataType.STRING);
        node.setInput("input_face", sampleFace(10.0d, 4.0d));
        node.setInput("input_columns", 3);
        node.setInput("input_rows", 1);
        node.setInput("input_window_width", 1.0d);
        node.setInput("input_window_height", 1.0d);
        node.setInput("input_margin", 1.0d);
        node.setInput("input_layout_mode", "distribute");
        node.processNode(null);
        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));

        @SuppressWarnings("unchecked")
        List<PointData> centers = (List<PointData>) node.getOutput("output_centers");
        assertEquals(3, centers.size());
        double span = centers.get(2).getX() - centers.get(0).getX();
        assertTrue(span > 2.0d, "distribute should spread windows across the face");
    }

    @Test
    void fixedGapModeUsesExplicitPierWidth() {
        WindowArrayProbe node = new WindowArrayProbe();
        node.connectInput("input_columns", NodeDataType.INTEGER);
        node.connectInput("input_rows", NodeDataType.INTEGER);
        node.connectInput("input_window_width", NodeDataType.DOUBLE);
        node.connectInput("input_window_height", NodeDataType.DOUBLE);
        node.connectInput("input_margin", NodeDataType.DOUBLE);
        node.connectInput("input_layout_mode", NodeDataType.STRING);
        node.connectInput("input_horizontal_gap", NodeDataType.DOUBLE);
        node.setInput("input_face", sampleFace(10.0d, 4.0d));
        node.setInput("input_columns", 2);
        node.setInput("input_rows", 1);
        node.setInput("input_window_width", 1.0d);
        node.setInput("input_window_height", 1.0d);
        node.setInput("input_margin", 1.0d);
        node.setInput("input_layout_mode", "fixed_gap");
        node.setInput("input_horizontal_gap", 2.0d);
        node.processNode(null);
        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));

        @SuppressWarnings("unchecked")
        List<PointData> centers = (List<PointData>) node.getOutput("output_centers");
        double gap = centers.get(1).getX() - centers.get(0).getX() - 1.0d;
        assertEquals(2.0d, gap, 0.05d);
    }

    @Test
    void bayModeUsesCenterToCenterSpacing() {
        WindowArrayProbe node = new WindowArrayProbe();
        node.connectInput("input_columns", NodeDataType.INTEGER);
        node.connectInput("input_rows", NodeDataType.INTEGER);
        node.connectInput("input_window_width", NodeDataType.DOUBLE);
        node.connectInput("input_window_height", NodeDataType.DOUBLE);
        node.connectInput("input_margin", NodeDataType.DOUBLE);
        node.connectInput("input_layout_mode", NodeDataType.STRING);
        node.connectInput("input_bay_width", NodeDataType.DOUBLE);
        node.setInput("input_face", sampleFace(10.0d, 4.0d));
        node.setInput("input_columns", 2);
        node.setInput("input_rows", 1);
        node.setInput("input_window_width", 1.0d);
        node.setInput("input_window_height", 1.0d);
        node.setInput("input_margin", 1.0d);
        node.setInput("input_layout_mode", "bay");
        node.setInput("input_bay_width", 3.0d);
        node.processNode(null);
        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));

        @SuppressWarnings("unchecked")
        List<PointData> centers = (List<PointData>) node.getOutput("output_centers");
        assertEquals(3.0d, centers.get(1).getX() - centers.get(0).getX(), 0.05d);
    }

    @Test
    void bayModeDoesNotApplyBayWidthToVerticalSpacing() {
        WindowArrayProbe node = new WindowArrayProbe();
        node.connectInput("input_columns", NodeDataType.INTEGER);
        node.connectInput("input_rows", NodeDataType.INTEGER);
        node.connectInput("input_window_width", NodeDataType.DOUBLE);
        node.connectInput("input_window_height", NodeDataType.DOUBLE);
        node.connectInput("input_margin", NodeDataType.DOUBLE);
        node.connectInput("input_layout_mode", NodeDataType.STRING);
        node.connectInput("input_bay_width", NodeDataType.DOUBLE);
        node.connectInput("input_vertical_gap", NodeDataType.DOUBLE);
        // Tall face: bay width 3.0 would not fit as vertical center spacing for 2 rows of height 1.
        node.setInput("input_face", sampleFace(10.0d, 6.0d));
        node.setInput("input_columns", 2);
        node.setInput("input_rows", 2);
        node.setInput("input_window_width", 1.0d);
        node.setInput("input_window_height", 1.0d);
        node.setInput("input_margin", 0.5d);
        node.setInput("input_layout_mode", "bay");
        node.setInput("input_bay_width", 3.0d);
        node.setInput("input_vertical_gap", 0.5d);
        node.processNode(null);
        assertEquals(Boolean.TRUE, node.getOutput("output_valid"),
            String.valueOf(node.getOutput("output_error")));

        @SuppressWarnings("unchecked")
        List<PointData> centers = (List<PointData>) node.getOutput("output_centers");
        assertEquals(4, centers.size());
        // Horizontal: bay center spacing
        assertEquals(3.0d, centers.get(1).getX() - centers.get(0).getX(), 0.05d);
        // Vertical: FIXED_GAP 0.5 → center delta = height + gap = 1.5, NOT bay width 3.0
        PointData topLeft = centers.get(0);
        PointData bottomLeft = centers.stream()
            .filter(c -> Math.abs(c.getX() - topLeft.getX()) < 0.05d && c.getY() < topLeft.getY())
            .findFirst()
            .orElseThrow(() -> new AssertionError("missing lower row center"));
        assertEquals(1.5d, topLeft.getY() - bottomLeft.getY(), 0.05d);
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
        output.connectTo(input);
        target.getInput(inputPortId);
    }

    private static final class WindowArrayProbe extends WindowArrayNode {
        void connectInput(String portId, NodeDataType outputType) {
            FaceArrayLayoutModesContractTest.connectInput(this, portId, outputType);
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

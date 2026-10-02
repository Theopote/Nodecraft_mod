package com.nodecraft.nodesystem.nodes.geometry.architectural_primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowFrameNodeTest {

    @Test
    void generatesFourBarFrameAtOrigin() {
        WindowFrameProbe node = new WindowFrameProbe();
        node.connectInput("input_frame_width", NodeDataType.DOUBLE);
        node.connectInput("input_frame_height", NodeDataType.DOUBLE);
        node.connectInput("input_frame_thickness", NodeDataType.DOUBLE);
        node.connectInput("input_depth", NodeDataType.DOUBLE);
        node.setInput("input_frame_width", 2.0d);
        node.setInput("input_frame_height", 1.5d);
        node.setInput("input_frame_thickness", 0.1d);
        node.setInput("input_depth", 0.2d);
        node.processNode(null);

        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));
        GeometryData geometry = assertInstanceOf(GeometryData.class, node.getOutput("output_geometry"));
        CompositeGeometryData composite = assertInstanceOf(CompositeGeometryData.class, geometry);
        assertEquals(4, composite.size());
    }

    @Test
    void rejectsBarsThickerThanOuterFrame() {
        WindowFrameProbe node = new WindowFrameProbe();
        node.connectInput("input_frame_width", NodeDataType.DOUBLE);
        node.connectInput("input_frame_height", NodeDataType.DOUBLE);
        node.connectInput("input_frame_thickness", NodeDataType.DOUBLE);
        node.setInput("input_frame_width", 1.0d);
        node.setInput("input_frame_height", 1.0d);
        node.setInput("input_frame_thickness", 0.6d);
        node.processNode(null);

        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertTrue(String.valueOf(node.getOutput("output_error")).toLowerCase().contains("thickness"));
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

    private static final class WindowFrameProbe extends WindowFrameNode {
        void connectInput(String portId, NodeDataType outputType) {
            WindowFrameNodeTest.connectInput(this, portId, outputType);
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

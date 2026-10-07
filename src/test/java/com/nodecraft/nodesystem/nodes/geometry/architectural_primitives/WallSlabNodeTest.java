package com.nodecraft.nodesystem.nodes.geometry.architectural_primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WallSlabNodeTest {

    @Test
    void faceAndThicknessProduceValidSlab() {
        BoxGeometryData box = new BoxGeometryData(
            new Vector3d(4.0d, 1.5d, 0.25d),
            new Vector3d(4.0d, 1.5d, 0.25d)
        );
        BoxFaceData face = box.getFaces().stream()
            .filter(f -> "Front".equalsIgnoreCase(f.getName()))
            .findFirst()
            .orElseThrow();

        WallSlabProbe wall = new WallSlabProbe();
        wall.connectInput("input_wall_thickness", NodeDataType.DOUBLE);
        wall.setInput("input_face", face);
        wall.setInput("input_wall_thickness", 0.4d);
        wall.processNode(null);

        assertEquals(Boolean.TRUE, wall.getOutput("output_valid"));
        assertInstanceOf(GeometryData.class, wall.getOutput("output_geometry"));
        assertInstanceOf(BoxFaceData.class, wall.getOutput("output_exterior_face"));
        assertInstanceOf(BoxFaceData.class, wall.getOutput("output_interior_face"));
    }

    @Test
    void nonPositiveThicknessFailsClosed() {
        BoxGeometryData box = new BoxGeometryData(
            new Vector3d(4.0d, 1.5d, 0.25d),
            new Vector3d(4.0d, 1.5d, 0.25d)
        );
        BoxFaceData face = box.getFaces().stream()
            .filter(f -> "Front".equalsIgnoreCase(f.getName()))
            .findFirst()
            .orElseThrow();

        WallSlabProbe wall = new WallSlabProbe();
        wall.connectInput("input_wall_thickness", NodeDataType.DOUBLE);
        wall.setInput("input_face", face);
        wall.setInput("input_wall_thickness", -0.1d);
        wall.processNode(null);

        assertEquals(Boolean.FALSE, wall.getOutput("output_valid"));
        assertTrue(String.valueOf(wall.getOutput("output_error")).toLowerCase().contains("thickness"));
    }

    private static void connectInput(BaseNode target, String inputPortId, NodeDataType outputType) {
        PortStubNode stub = new PortStubNode(outputType);
        BasePort output = (BasePort) stub.getOutputPorts().getFirst();
        BasePort input = (BasePort) target.getInputPorts().stream()
            .filter(port -> inputPortId.equals(port.getId()))
            .findFirst()
            .orElseThrow();
        assertTrue(output.connectTo(input));
        target.getInput(inputPortId);
    }

    private static final class WallSlabProbe extends WallSlabNode {
        void connectInput(String portId, NodeDataType outputType) {
            WallSlabNodeTest.connectInput(this, portId, outputType);
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

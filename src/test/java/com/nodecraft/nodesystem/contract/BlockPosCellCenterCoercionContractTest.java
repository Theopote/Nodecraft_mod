package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.SphereSdfData;
import com.nodecraft.nodesystem.datatypes.VectorData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.reference.points.BlockToPointNode;
import com.nodecraft.nodesystem.nodes.transform.deformations.TwistSdfNode;
import com.nodecraft.nodesystem.util.BlockSpace;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Freezes Spatial Convention v1: when BlockPos is coerced to a continuous location,
 * it must be the cell center — never the min corner.
 * Canonical path is {@link BlockToPointNode} → POINT → deformation Axis Origin.
 */
class BlockPosCellCenterCoercionContractTest {

    @Test
    void blockToPointDefaultCenterMatchesCellCenter() {
        BlockToPointNode converter = new BlockToPointNode();
        converter.setInput("input_coordinate", new BlockPos(0, 0, 0));
        converter.processNode(null);

        assertEquals(Boolean.TRUE, converter.getOutput("output_valid"));
        PointData point = assertInstanceOf(PointData.class, converter.getOutput("output_point"));
        Vector3d expected = BlockSpace.cellCenter(0, 0, 0);
        assertEquals(expected.x, point.position().x, 1.0e-9);
        assertEquals(expected.y, point.position().y, 1.0e-9);
        assertEquals(expected.z, point.position().z, 1.0e-9);
    }

    @Test
    void twistSdfAxisOriginFromBlockCenterMatchesExplicitCellCenterPoint() {
        SphereSdfData sdf = new SphereSdfData(new Vector3d(0.5, 0.5, 0.5), 1.0d);

        BlockToPointNode converter = new BlockToPointNode();
        converter.setInput("input_coordinate", new BlockPos(0, 0, 0));
        converter.processNode(null);
        PointData fromBlock = assertInstanceOf(PointData.class, converter.getOutput("output_point"));

        TwistProbe fromCenter = twistWithConnectedOrigin(sdf, fromBlock);
        TwistProbe fromExplicit = twistWithConnectedOrigin(sdf, new PointData(BlockSpace.cellCenter(0, 0, 0)));
        TwistProbe fromCorner = twistWithConnectedOrigin(sdf, new PointData(0, 0, 0));

        assertEquals(Boolean.TRUE, fromCenter.getOutput("output_valid"));
        assertEquals(Boolean.TRUE, fromExplicit.getOutput("output_valid"));
        assertEquals(Boolean.TRUE, fromCorner.getOutput("output_valid"));

        PointData centerMin = assertInstanceOf(PointData.class, fromCenter.getOutput("output_bounds_min"));
        PointData explicitMin = assertInstanceOf(PointData.class, fromExplicit.getOutput("output_bounds_min"));
        PointData cornerMin = assertInstanceOf(PointData.class, fromCorner.getOutput("output_bounds_min"));

        assertEquals(explicitMin.position().x, centerMin.position().x, 1.0e-9);
        assertEquals(explicitMin.position().y, centerMin.position().y, 1.0e-9);
        assertEquals(explicitMin.position().z, centerMin.position().z, 1.0e-9);

        double dx = Math.abs(cornerMin.position().x - centerMin.position().x);
        double dy = Math.abs(cornerMin.position().y - centerMin.position().y);
        double dz = Math.abs(cornerMin.position().z - centerMin.position().z);
        assertTrue(dx + dy + dz > 1.0e-6,
            "BlockPos cell-center coercion must not match integer min-corner origin under twist");
    }

    private static TwistProbe twistWithConnectedOrigin(SphereSdfData sdf, Object origin) {
        TwistProbe node = new TwistProbe();
        connectAndSet(node, "input_sdf", NodeDataType.SDF, sdf);
        connectAndSet(node, "input_axis_origin", NodeDataType.POINT, origin);
        connectAndSet(node, "input_axis_direction", NodeDataType.VECTOR, new VectorData(0, 1, 0));
        connectAndSet(node, "input_angle_degrees", NodeDataType.DOUBLE, 90.0d);
        connectAndSet(node, "input_twist_length", NodeDataType.DOUBLE, 2.0d);
        node.processNode(null);
        return node;
    }

    private static void connectAndSet(BaseNode target, String portId, NodeDataType stubType, Object value) {
        PortStubNode stub = new PortStubNode(stubType);
        BasePort output = (BasePort) stub.getOutputPorts().getFirst();
        BasePort input = (BasePort) target.getInputPorts().stream()
                .filter(port -> portId.equals(port.getId()))
                .findFirst()
                .orElseThrow();
        assertTrue(output.connectTo(input), portId + " connect failed");
        target.getInput(portId);
        ((TwistProbe) target).putInput(portId, value);
    }

    private static final class TwistProbe extends TwistSdfNode {
        void putInput(String portId, Object value) {
            inputValues.put(portId, value);
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

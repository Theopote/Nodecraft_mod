package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.SdfGeometryData;
import com.nodecraft.nodesystem.datatypes.SphereSdfData;
import com.nodecraft.nodesystem.datatypes.VectorData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.transform.deformations.TwistGeometryNode;
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
 */
class BlockPosCellCenterCoercionContractTest {

    @Test
    void twistGeometryAxisOriginBlockPosMatchesCellCenterPoint() {
        SdfGeometryData geometry = sampleSphereGeometry();

        TwistProbe fromBlock = twistWithConnectedOrigin(geometry, new BlockPos(0, 0, 0));
        TwistProbe fromCenter = twistWithConnectedOrigin(geometry, new PointData(BlockSpace.cellCenter(0, 0, 0)));
        TwistProbe fromCorner = twistWithConnectedOrigin(geometry, new PointData(0, 0, 0));

        assertEquals(Boolean.TRUE, fromBlock.getOutput("output_valid"));
        assertEquals(Boolean.TRUE, fromCenter.getOutput("output_valid"));
        assertEquals(Boolean.TRUE, fromCorner.getOutput("output_valid"));

        PointData blockMin = assertInstanceOf(PointData.class, fromBlock.getOutput("output_bounds_min"));
        PointData centerMin = assertInstanceOf(PointData.class, fromCenter.getOutput("output_bounds_min"));
        PointData cornerMin = assertInstanceOf(PointData.class, fromCorner.getOutput("output_bounds_min"));

        assertEquals(centerMin.position().x, blockMin.position().x, 1.0e-9);
        assertEquals(centerMin.position().y, blockMin.position().y, 1.0e-9);
        assertEquals(centerMin.position().z, blockMin.position().z, 1.0e-9);

        double dx = Math.abs(cornerMin.position().x - blockMin.position().x);
        double dy = Math.abs(cornerMin.position().y - blockMin.position().y);
        double dz = Math.abs(cornerMin.position().z - blockMin.position().z);
        assertEquals(true, dx + dy + dz > 1.0e-6,
            "BlockPos coercion must not match integer min-corner origin under twist");
    }

    private static SdfGeometryData sampleSphereGeometry() {
        SphereSdfData sphere = new SphereSdfData(new Vector3d(0.5, 0.5, 0.5), 1.0d);
        return new SdfGeometryData(
            sphere,
            new Vector3d(-1, -1, -1),
            new Vector3d(2, 2, 2),
            0.0d
        );
    }

    private static TwistProbe twistWithConnectedOrigin(SdfGeometryData geometry, Object origin) {
        TwistProbe node = new TwistProbe();
        connectAndSet(node, "input_geometry", NodeDataType.GEOMETRY, geometry);
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

    private static final class TwistProbe extends TwistGeometryNode {
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

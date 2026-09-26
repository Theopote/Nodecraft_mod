package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.SdfGeometryData;
import com.nodecraft.nodesystem.datatypes.SphereSdfData;
import com.nodecraft.nodesystem.nodes.transform.deformations.TwistGeometryNode;
import com.nodecraft.nodesystem.nodes.world.query.IsGridPointNode;
import com.nodecraft.nodesystem.util.BlockSpace;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Freezes Spatial Convention v1: when BlockPos is coerced to a continuous location,
 * it must be the cell center â€?never the min corner.
 */
class BlockPosCellCenterCoercionContractTest {

    @Test
    void isGridPointTreatsBlockPosAsCellCenterNotMinCorner() {
        IsGridPointNode node = new IsGridPointNode();
        node.setInput("input_point", new BlockPos(0, 0, 0));
        node.processNode(null);

        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));
        // Cell center (0.5,0.5,0.5) is not on the integer grid.
        assertEquals(Boolean.FALSE, node.getOutput("output_is_grid_point"));

        Vector3d offset = (Vector3d) node.getOutput("output_offset_vector");
        Vector3d center = BlockSpace.cellCenter(0, 0, 0);
        int nearestX = (int) Math.round(center.x);
        int nearestY = (int) Math.round(center.y);
        int nearestZ = (int) Math.round(center.z);
        assertEquals(center.x - nearestX, offset.x, 1.0e-12);
        assertEquals(center.y - nearestY, offset.y, 1.0e-12);
        assertEquals(center.z - nearestZ, offset.z, 1.0e-12);
    }

    @Test
    void isGridPointAcceptsExplicitPointAtIntegerCorner() {
        IsGridPointNode node = new IsGridPointNode();
        node.setInput("input_point", new PointData(0, 0, 0));
        node.processNode(null);

        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));
        assertEquals(Boolean.TRUE, node.getOutput("output_is_grid_point"));
    }

    @Test
    void twistGeometryAxisOriginBlockPosMatchesCellCenterPoint() {
        SdfGeometryData geometry = sampleSphereGeometry();

        // Bypass typed setInput so we exercise the legacy BlockPos coerce path on POINT ports.
        TwistProbe fromBlock = twistWithOriginBypassingTypeCheck(geometry, new BlockPos(0, 0, 0));
        TwistProbe fromCenter = twistWithOriginBypassingTypeCheck(geometry, new PointData(BlockSpace.cellCenter(0, 0, 0)));
        TwistProbe fromCorner = twistWithOriginBypassingTypeCheck(geometry, new PointData(0, 0, 0));

        assertEquals(Boolean.TRUE, fromBlock.getOutput("output_valid"));
        assertEquals(Boolean.TRUE, fromCenter.getOutput("output_valid"));
        assertEquals(Boolean.TRUE, fromCorner.getOutput("output_valid"));

        PointData blockMin = assertInstanceOf(PointData.class, fromBlock.getOutput("output_bounds_min"));
        PointData centerMin = assertInstanceOf(PointData.class, fromCenter.getOutput("output_bounds_min"));
        PointData cornerMin = assertInstanceOf(PointData.class, fromCorner.getOutput("output_bounds_min"));

        assertEquals(centerMin.position().x, blockMin.position().x, 1.0e-9);
        assertEquals(centerMin.position().y, blockMin.position().y, 1.0e-9);
        assertEquals(centerMin.position().z, blockMin.position().z, 1.0e-9);

        // Corner origin must differ from cell-center origin under a non-zero twist.
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

    private static TwistProbe twistWithOriginBypassingTypeCheck(SdfGeometryData geometry, Object origin) {
        TwistProbe node = new TwistProbe();
        node.putInput("input_geometry", geometry);
        node.putInput("input_axis_origin", origin);
        node.putInput("input_axis_direction", new Vector3d(0, 1, 0));
        node.putInput("input_angle_degrees", 90.0d);
        node.putInput("input_twist_length", 2.0d);
        node.processNode(null);
        return node;
    }

    /** Exposes protected inputValues so legacy BlockPos coerce can be exercised. */
    private static final class TwistProbe extends TwistGeometryNode {
        void putInput(String portId, Object value) {
            inputValues.put(portId, value);
        }
    }
}

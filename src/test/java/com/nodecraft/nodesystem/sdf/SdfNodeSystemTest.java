package com.nodecraft.nodesystem.sdf;

import com.nodecraft.nodesystem.datatypes.BooleanSdfData;
import com.nodecraft.nodesystem.datatypes.BentSdfData;
import com.nodecraft.nodesystem.datatypes.SdfGeometryData;
import com.nodecraft.nodesystem.datatypes.SignedDistanceFieldData;
import com.nodecraft.nodesystem.datatypes.SphereSdfData;
import com.nodecraft.nodesystem.nodes.transform.deformations.BendGeometryNode;
import com.nodecraft.nodesystem.nodes.output.execute.SdfToBlocksNode;
import com.nodecraft.nodesystem.util.BlockPosList;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SdfNodeSystemTest {

    @Test
    void smoothUnionRoundsTheJoinBetweenOverlappingFields() {
        SignedDistanceFieldData left = new SphereSdfData(new Vector3d(-1.0d, 0.0d, 0.0d), 1.25d);
        SignedDistanceFieldData right = new SphereSdfData(new Vector3d(1.0d, 0.0d, 0.0d), 1.25d);
        SignedDistanceFieldData hardUnion = new BooleanSdfData(left, right, BooleanSdfData.Operation.UNION, 0.0d);
        SignedDistanceFieldData smoothUnion = new BooleanSdfData(left, right, BooleanSdfData.Operation.UNION, 1.0d);

        Vector3d midpoint = new Vector3d(0.0d, 0.0d, 0.0d);

        assertTrue(smoothUnion.sampleDistance(midpoint) < hardUnion.sampleDistance(midpoint));
        assertEquals(-0.25d, hardUnion.sampleDistance(midpoint), 1.0e-9d);
        assertEquals(-0.5d, smoothUnion.sampleDistance(midpoint), 1.0e-9d);
    }

    @Test
    void sdfToBlocksVoxelizesWithAutoBounds() {
        SdfToBlocksNode node = new SdfToBlocksNode();
        SignedDistanceFieldData sdf = new SphereSdfData(new Vector3d(0.0d, 0.0d, 0.0d), 1.0d);

        Map<String, Object> outputs = node.compute(Map.of(
                "input_sdf", sdf,
                "input_padding", 0.0d
        ));

        assertEquals(true, outputs.get("output_valid"));
        assertEquals(8, outputs.get("output_count"));
        BlockPosList blocks = assertInstanceOf(BlockPosList.class, outputs.get("output_blocks"));
        assertTrue(blocks.getPositions().contains(new BlockPos(-1, -1, -1)));
        assertTrue(blocks.getPositions().contains(new BlockPos(0, 0, 0)));
        assertNotNull(outputs.get("output_region"));
        SdfGeometryData geometry = assertInstanceOf(SdfGeometryData.class, outputs.get("output_geometry"));
        assertEquals(0.0d, geometry.isoValue(), 1.0e-9d);
    }

    @Test
    void bentSdfMapsAxisIntoArc() {
        BentSdfData bent = new BentSdfData(
                new SphereSdfData(new Vector3d(0.0d, 0.0d, 0.0d), 1.0d),
                new Vector3d(0.0d, 0.0d, 0.0d),
                new Vector3d(1.0d, 0.0d, 0.0d),
                new Vector3d(0.0d, 1.0d, 0.0d),
                90.0d,
                10.0d,
                BentSdfData.ClampMode.CLAMP
        );

        Vector3d bentEndpoint = bent.bendPoint(new Vector3d(10.0d, 0.0d, 0.0d));
        double expectedRadius = 10.0d / (Math.PI * 0.5d);

        assertEquals(expectedRadius, bentEndpoint.x, 1.0e-6d);
        assertEquals(expectedRadius, bentEndpoint.y, 1.0e-6d);
        assertEquals(0.0d, bentEndpoint.z, 1.0e-6d);
    }

    @Test
    void bendGeometryOutputsContinuousSdfGeometry() {
        BendGeometryNode node = new BendGeometryNode();
        SignedDistanceFieldData sdf = new SphereSdfData(new Vector3d(0.0d, 0.0d, 0.0d), 1.0d);

        Map<String, Object> outputs = node.compute(Map.of(
                "input_sdf", sdf,
                "input_axis_origin", new Vector3d(0.0d, 0.0d, 0.0d),
                "input_axis_direction", new Vector3d(1.0d, 0.0d, 0.0d),
                "input_bend_normal", new Vector3d(0.0d, 1.0d, 0.0d),
                "input_bend_degrees", 90.0d,
                "input_bend_length", 10.0d
        ));

        assertEquals(true, outputs.get("output_valid"));
        assertEquals(false, outputs.get("output_approximate"));
        assertInstanceOf(BentSdfData.class, outputs.get("output_sdf"));
        SdfGeometryData geometry = assertInstanceOf(SdfGeometryData.class, outputs.get("output_geometry"));
        assertInstanceOf(BentSdfData.class, geometry.sdf());
        assertNotNull(outputs.get("output_bounds_min"));
        assertNotNull(outputs.get("output_bounds_max"));
    }
}

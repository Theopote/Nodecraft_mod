package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.SdfGeometryData;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeometryVoxelizerTest {

    @Test
    void voxelizeAxisAlignedBoxFillSolidProducesVolumeBlocks() {
        BoxGeometryData box = new BoxGeometryData(
                new Vector3d(0.5d, 0.5d, 0.5d),
                new Vector3d(0.5d, 0.5d, 0.5d)
        );

        BlockPosList blocks = GeometryVoxelizer.voxelizeBox(box, true);

        assertEquals(8, blocks.size());
    }

    @Test
    void voxelizeAxisAlignedBoxShellProducesFewerBlocksThanSolidFill() {
        BoxGeometryData box = new BoxGeometryData(
                new Vector3d(1.5d, 1.5d, 1.5d),
                new Vector3d(1.5d, 1.5d, 1.5d)
        );

        BlockPosList solid = GeometryVoxelizer.voxelizeBox(box, true);
        BlockPosList shell = GeometryVoxelizer.voxelizeBox(box, false);

        assertTrue(solid.size() > shell.size());
        assertTrue(shell.size() > 0);
    }

    @Test
    void voxelizeSdfGeometrySkipsWhenBoundsVolumeExceedsLimit() {
        SdfGeometryData oversized = new SdfGeometryData(
                point -> 1.0d,
                new Vector3d(0.0d, 0.0d, 0.0d),
                new Vector3d(65.0d, 65.0d, 65.0d),
                0.0d
        );

        BlockPosList blocks = GeometryVoxelizer.voxelizeSdfGeometry(oversized, true);

        assertTrue(blocks.isEmpty());
    }

    @Test
    void voxelizeSdfGeometrySamplesWithinVolumeLimit() {
        SdfGeometryData solidBlock = new SdfGeometryData(
                point -> -1.0d,
                new Vector3d(0.0d, 0.0d, 0.0d),
                new Vector3d(2.0d, 2.0d, 2.0d),
                0.0d
        );

        BlockPosList blocks = GeometryVoxelizer.voxelizeSdfGeometry(solidBlock, true);

        assertEquals(27, blocks.size());
    }
}

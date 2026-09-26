package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.RegionData;
import net.minecraft.util.math.BlockPos;
import org.joml.Matrix3d;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockSpaceTest {

    @Test
    void cellCenterIsHalfOffsetFromCorner() {
        BlockPos cell = new BlockPos(10, 64, -3);
        Vector3d center = BlockSpace.cellCenter(cell);
        assertEquals(10.5d, center.x, 1e-12);
        assertEquals(64.5d, center.y, 1e-12);
        assertEquals(-2.5d, center.z, 1e-12);
    }

    @Test
    void floorSnapRoundTripsCellCenter() {
        Vector3d center = BlockSpace.cellCenter(0, 0, 0);
        assertEquals(new BlockPos(0, 0, 0), BlockSpace.snapCellCenter(center));
        assertEquals(new BlockPos(0, 0, 0), BlockSpace.pointToBlockFloor(center));
    }

    @Test
    void nearestSnapBreaksCellCenterRoundTrip() {
        Vector3d center = BlockSpace.cellCenter(0, 0, 0);
        assertEquals(new BlockPos(1, 1, 1), BlockSpace.pointToBlockNearest(center));
    }

    @Test
    void sizeOneBoxOnBlockCenterYieldsSingleCell() {
        Vector3d center = BlockSpace.cellCenter(10, 64, 20);
        Vector3d half = new Vector3d(0.5d, 0.5d, 0.5d);
        RegionData region = BlockSpace.inclusiveRegionFromCenterHalfExtents(center, half);

        assertNotNull(region.getMinCorner());
        assertNotNull(region.getMaxCorner());
        assertEquals(new BlockPos(10, 64, 20), region.getMinCorner());
        assertEquals(new BlockPos(10, 64, 20), region.getMaxCorner());
        assertTrue(BlockSpace.cellCenterInsideAxisAlignedBox(10, 64, 20, center, half));
        assertFalse(BlockSpace.cellCenterInsideAxisAlignedBox(11, 64, 20, center, half));
    }

    @Test
    void oddSizeFiveOnBlockCenterYieldsFiveCellsPerAxis() {
        Vector3d center = BlockSpace.cellCenter(10, 64, 20);
        Vector3d half = new Vector3d(2.5d, 2.5d, 2.5d);
        RegionData region = BlockSpace.inclusiveRegionFromCenterHalfExtents(center, half);

        assertEquals(new BlockPos(8, 62, 18), region.getMinCorner());
        assertEquals(new BlockPos(12, 66, 22), region.getMaxCorner());

        int countX = region.getMaxCorner().getX() - region.getMinCorner().getX() + 1;
        assertEquals(5, countX);
    }

    @Test
    void axisAlignedAndIdentityOrientedRegionsMatch() {
        Vector3d center = BlockSpace.cellCenter(0, 0, 0);
        Vector3d half = new Vector3d(1.5d, 1.5d, 1.5d);
        RegionData aa = BlockSpace.inclusiveRegionFromCenterHalfExtents(center, half);
        RegionData oriented = BoxBlockGenerator.createOrientedBoundingRegion(
            center, half, new Matrix3d().identity()
        );

        assertEquals(aa.getMinCorner(), oriented.getMinCorner());
        assertEquals(aa.getMaxCorner(), oriented.getMaxCorner());
    }
}

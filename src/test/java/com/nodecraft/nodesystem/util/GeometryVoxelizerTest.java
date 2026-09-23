package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.datatypes.SdfGeometryData;
import net.minecraft.util.math.BlockPos;
import org.joml.Matrix3d;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeometryVoxelizerTest {

    @Test
    void voxelizeSizeOneOnBlockCenterProducesOneBlock() {
        BoxGeometryData box = new BoxGeometryData(
                BlockSpace.cellCenter(0, 0, 0),
                new Vector3d(0.5d, 0.5d, 0.5d)
        );

        BlockPosList blocks = GeometryVoxelizer.voxelizeBox(box, true);

        assertEquals(1, blocks.size());
        assertEquals(new BlockPos(0, 0, 0), blocks.getPositions().getFirst());
    }

    @Test
    void voxelizeOddSizeFiveOnBlockCenterProduces125Blocks() {
        BoxGeometryData box = new BoxGeometryData(
                BlockSpace.cellCenter(10, 64, 20),
                new Vector3d(2.5d, 2.5d, 2.5d)
        );

        BlockPosList blocks = GeometryVoxelizer.voxelizeBox(box, true);

        assertEquals(125, blocks.size());
        RegionData region = GeometryVoxelizer.createAxisAlignedRegion(box);
        assertEquals(new BlockPos(8, 62, 18), region.getMinCorner());
        assertEquals(new BlockPos(12, 66, 22), region.getMaxCorner());
    }

    @Test
    void voxelizeAxisAlignedMatchesIdentityOriented() {
        Vector3d center = BlockSpace.cellCenter(5, 10, 15);
        Vector3d half = new Vector3d(1.5d, 2.5d, 0.5d);
        BoxGeometryData axisAligned = new BoxGeometryData(center, half);
        BoxGeometryData oriented = new BoxGeometryData(center, half, new Matrix3d().identity(), true);

        BlockPosList aa = GeometryVoxelizer.voxelizeBox(axisAligned, true);
        BlockPosList obb = GeometryVoxelizer.voxelizeBox(oriented, true);

        assertEquals(aa.size(), obb.size());
        assertTrue(aa.getPositions().containsAll(obb.getPositions()));
        assertTrue(obb.getPositions().containsAll(aa.getPositions()));
    }

    @Test
    void voxelizeAxisAlignedBoxShellProducesFewerBlocksThanSolidFill() {
        BoxGeometryData box = new BoxGeometryData(
                BlockSpace.cellCenter(1, 1, 1),
                new Vector3d(1.5d, 1.5d, 1.5d)
        );

        BlockPosList solid = GeometryVoxelizer.voxelizeBox(box, true);
        BlockPosList shell = GeometryVoxelizer.voxelizeBox(box, false);

        assertEquals(27, solid.size());
        assertTrue(solid.size() > shell.size());
        assertTrue(shell.size() > 0);
    }

    @Test
    void continuousAabbMatchesInclusiveRegionExtent() {
        // Continuous solid [8,13] around center 10.5 half 2.5 must match ghost cell span 8..12
        // (RegionBoxElement draws inclusive max + 1 → world max corner 13).
        BoxGeometryData box = new BoxGeometryData(
                BlockSpace.cellCenter(10, 64, 20),
                new Vector3d(2.5d, 2.5d, 2.5d)
        );
        RegionData region = GeometryVoxelizer.createAxisAlignedRegion(box);
        Vector3d center = box.getCenter();
        Vector3d half = box.getHalfExtents();

        assertEquals(center.x - half.x, region.getMinCorner().getX(), 1e-12);
        assertEquals(center.x + half.x, region.getMaxCorner().getX() + 1.0d, 1e-12);
    }

    @Test
    void voxelizeSkipsWhenBoundsVolumeExceedsLimit() {
        SdfGeometryData oversizedSdf = new SdfGeometryData(
                point -> 1.0d,
                new Vector3d(0.0d, 0.0d, 0.0d),
                new Vector3d(65.0d, 65.0d, 65.0d),
                0.0d
        );
        BoxGeometryData oversizedBox = new BoxGeometryData(
                new Vector3d(0.0d, 0.0d, 0.0d),
                new Vector3d(33.0d, 33.0d, 33.0d)
        );

        assertTrue(GeometryVoxelizer.voxelize(oversizedSdf, true).isEmpty());
        assertTrue(GeometryVoxelizer.voxelize(oversizedBox, true).isEmpty());
        assertTrue(GeometryVoxelizer.voxelizeBox(oversizedBox, true).isEmpty());
    }

    @Test
    void voxelizeSdfGeometrySamplesWithinVolumeLimit() {
        SdfGeometryData solidBlock = new SdfGeometryData(
                point -> -1.0d,
                new Vector3d(0.0d, 0.0d, 0.0d),
                new Vector3d(2.0d, 2.0d, 2.0d),
                0.0d
        );

        BlockPosList blocks = GeometryVoxelizer.voxelize(solidBlock, true);

        assertEquals(27, blocks.size());
    }

    @Test
    void differenceBoundingRegionUsesMinuendOnly() {
        BoxGeometryData base = new BoxGeometryData(
                new Vector3d(2.0d, 2.0d, 2.0d),
                new Vector3d(2.0d, 2.0d, 2.0d)
        );
        BoxGeometryData cutterOutside = new BoxGeometryData(
                new Vector3d(20.0d, 2.0d, 2.0d),
                new Vector3d(2.0d, 2.0d, 2.0d)
        );
        var difference = new com.nodecraft.nodesystem.datatypes.DifferenceGeometryData(base, cutterOutside);

        var baseRegion = GeometryVoxelizer.createBoundingRegion(base);
        var differenceRegion = GeometryVoxelizer.createBoundingRegion(difference);

        assertNotNull(baseRegion);
        assertNotNull(differenceRegion);
        assertEquals(baseRegion.getMinCorner(), differenceRegion.getMinCorner());
        assertEquals(baseRegion.getMaxCorner(), differenceRegion.getMaxCorner());
    }
}

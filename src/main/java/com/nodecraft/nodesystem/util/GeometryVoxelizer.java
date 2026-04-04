package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.CylinderGeometryData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.datatypes.TorusGeometryData;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

/**
 * Shared geometry-to-voxel bridge for nodes that consume abstract geometry.
 */
public final class GeometryVoxelizer {

    private GeometryVoxelizer() {
    }

    public static BlockPosList resolveBlocks(@Nullable Object blocksObj,
                                             @Nullable Object boxGeometryObj,
                                             @Nullable Object cylinderGeometryObj,
                                             @Nullable Object torusGeometryObj,
                                             boolean fillSolid) {
        if (blocksObj instanceof BlockPosList blockPosList) {
            return blockPosList;
        }

        if (boxGeometryObj instanceof BoxGeometryData boxGeometry) {
            return voxelizeBox(boxGeometry, fillSolid);
        }

        if (cylinderGeometryObj instanceof CylinderGeometryData cylinderGeometry) {
            return voxelizeCylinder(cylinderGeometry, fillSolid);
        }

        if (torusGeometryObj instanceof TorusGeometryData torusGeometry) {
            return voxelizeTorus(torusGeometry, fillSolid);
        }

        return new BlockPosList();
    }

    public static BlockPosList voxelizeBox(BoxGeometryData geometry, boolean fillSolid) {
        BlockPosList blocks = new BlockPosList();

        if (geometry.isOriented()) {
            RegionData region = BoxBlockGenerator.createOrientedBoundingRegion(
                geometry.getCenter(),
                geometry.getHalfExtents(),
                geometry.getOrientationMatrix()
            );
            if (region == null || !region.isComplete()) {
                return blocks;
            }

            BlockPos minCorner = region.getMinCorner();
            BlockPos maxCorner = region.getMaxCorner();
            if (minCorner == null || maxCorner == null) {
                return blocks;
            }

            BoxBlockGenerator.populateOrientedBox(
                blocks,
                minCorner,
                maxCorner,
                geometry.getCenter(),
                geometry.getHalfExtents(),
                geometry.getOrientationMatrix(),
                fillSolid
            );
            return blocks;
        }

        RegionData region = createAxisAlignedRegion(geometry);
        if (region == null || !region.isComplete()) {
            return blocks;
        }

        BlockPos minCorner = region.getMinCorner();
        BlockPos maxCorner = region.getMaxCorner();
        if (minCorner == null || maxCorner == null) {
            return blocks;
        }

        BoxBlockGenerator.populateAxisAlignedBox(blocks, minCorner, maxCorner, fillSolid);
        return blocks;
    }

    public static BlockPosList voxelizeTorus(TorusGeometryData geometry, boolean fillSolid) {
        BlockPosList blocks = new BlockPosList();
        RegionData region = TorusBlockGenerator.createBoundingRegion(geometry);
        TorusBlockGenerator.populateTorus(blocks, region, geometry, fillSolid);
        return blocks;
    }

    public static BlockPosList voxelizeCylinder(CylinderGeometryData geometry, boolean fillSolid) {
        BlockPosList blocks = new BlockPosList();
        RegionData region = CylinderBlockGenerator.createBoundingRegion(geometry);
        CylinderBlockGenerator.populateCylinder(blocks, region, geometry, fillSolid);
        return blocks;
    }

    public static RegionData createAxisAlignedRegion(BoxGeometryData geometry) {
        Vector3d center = geometry.getCenter();
        Vector3d halfExtents = geometry.getHalfExtents();

        BlockPos minCorner = BlockPos.ofFloored(
            center.x - halfExtents.x,
            center.y - halfExtents.y,
            center.z - halfExtents.z
        );
        BlockPos maxCorner = BlockPos.ofFloored(
            center.x + halfExtents.x,
            center.y + halfExtents.y,
            center.z + halfExtents.z
        );
        return new RegionData(minCorner, maxCorner);
    }
}

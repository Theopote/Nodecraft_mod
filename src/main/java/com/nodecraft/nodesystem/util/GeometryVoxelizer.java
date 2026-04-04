package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.CylinderGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.datatypes.SphereData;
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
                                             @Nullable Object geometryObj,
                                             @Nullable Object boxGeometryObj,
                                             @Nullable Object cylinderGeometryObj,
                                             @Nullable Object sphereGeometryObj,
                                             @Nullable Object torusGeometryObj,
                                             boolean fillSolid) {
        if (blocksObj instanceof BlockPosList blockPosList) {
            return blockPosList;
        }

        GeometryData geometry = resolveGeometry(geometryObj, boxGeometryObj, cylinderGeometryObj, sphereGeometryObj, torusGeometryObj);
        if (geometry != null) {
            return voxelize(geometry, fillSolid);
        }

        return new BlockPosList();
    }

    public static @Nullable GeometryData resolveGeometry(@Nullable Object geometryObj,
                                                         @Nullable Object boxGeometryObj,
                                                         @Nullable Object cylinderGeometryObj,
                                                         @Nullable Object sphereGeometryObj,
                                                         @Nullable Object torusGeometryObj) {
        if (geometryObj instanceof GeometryData geometry) {
            return geometry;
        }

        if (boxGeometryObj instanceof GeometryData geometry) {
            return geometry;
        }

        if (cylinderGeometryObj instanceof GeometryData geometry) {
            return geometry;
        }

        if (sphereGeometryObj instanceof GeometryData geometry) {
            return geometry;
        }

        if (torusGeometryObj instanceof GeometryData geometry) {
            return geometry;
        }

        return null;
    }

    public static BlockPosList voxelize(GeometryData geometry, boolean fillSolid) {
        if (geometry instanceof BoxGeometryData boxGeometry) {
            return voxelizeBox(boxGeometry, fillSolid);
        }
        if (geometry instanceof CylinderGeometryData cylinderGeometry) {
            return voxelizeCylinder(cylinderGeometry, fillSolid);
        }
        if (geometry instanceof SphereData sphereGeometry) {
            return voxelizeSphere(sphereGeometry, fillSolid);
        }
        if (geometry instanceof TorusGeometryData torusGeometry) {
            return voxelizeTorus(torusGeometry, fillSolid);
        }
        return new BlockPosList();
    }

    public static @Nullable RegionData createBoundingRegion(GeometryData geometry) {
        if (geometry instanceof BoxGeometryData boxGeometry) {
            return boxGeometry.isOriented()
                ? BoxBlockGenerator.createOrientedBoundingRegion(
                    boxGeometry.getCenter(),
                    boxGeometry.getHalfExtents(),
                    boxGeometry.getOrientationMatrix()
                )
                : createAxisAlignedRegion(boxGeometry);
        }
        if (geometry instanceof CylinderGeometryData cylinderGeometry) {
            return CylinderBlockGenerator.createBoundingRegion(cylinderGeometry);
        }
        if (geometry instanceof SphereData sphereGeometry) {
            return SphereBlockGenerator.createBoundingRegion(sphereGeometry);
        }
        if (geometry instanceof TorusGeometryData torusGeometry) {
            return TorusBlockGenerator.createBoundingRegion(torusGeometry);
        }
        return null;
    }

    public static BlockPosList voxelizeBox(BoxGeometryData geometry, boolean fillSolid) {
        BlockPosList blocks = new BlockPosList();

        if (geometry.isOriented()) {
            RegionData region = BoxBlockGenerator.createOrientedBoundingRegion(
                geometry.getCenter(),
                geometry.getHalfExtents(),
                geometry.getOrientationMatrix()
            );
            if (!region.isComplete()) {
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
        if (!region.isComplete()) {
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

    public static BlockPosList voxelizeSphere(SphereData geometry, boolean fillSolid) {
        BlockPosList blocks = new BlockPosList();
        RegionData region = SphereBlockGenerator.createBoundingRegion(geometry);
        SphereBlockGenerator.populateSphere(blocks, region, geometry, fillSolid);
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

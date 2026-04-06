package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.ConeGeometryData;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.CylinderGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.PrismGeometryData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.datatypes.SphereData;
import com.nodecraft.nodesystem.datatypes.TorusGeometryData;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.LinkedHashSet;
import java.util.Set;

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
        if (geometry instanceof CompositeGeometryData compositeGeometry) {
            return voxelizeComposite(compositeGeometry, fillSolid);
        }
        if (geometry instanceof BoxGeometryData boxGeometry) {
            return voxelizeBox(boxGeometry, fillSolid);
        }
        if (geometry instanceof ConeGeometryData coneGeometry) {
            return voxelizeCone(coneGeometry, fillSolid);
        }
        if (geometry instanceof CylinderGeometryData cylinderGeometry) {
            return voxelizeCylinder(cylinderGeometry, fillSolid);
        }
        if (geometry instanceof PrismGeometryData prismGeometry) {
            return voxelizePrism(prismGeometry, fillSolid);
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
        if (geometry instanceof CompositeGeometryData compositeGeometry) {
            return createCompositeBoundingRegion(compositeGeometry);
        }
        if (geometry instanceof BoxGeometryData boxGeometry) {
            return boxGeometry.isOriented()
                ? BoxBlockGenerator.createOrientedBoundingRegion(
                    boxGeometry.getCenter(),
                    boxGeometry.getHalfExtents(),
                    boxGeometry.getOrientationMatrix()
                )
                : createAxisAlignedRegion(boxGeometry);
        }
        if (geometry instanceof ConeGeometryData coneGeometry) {
            return ConeBlockGenerator.createBoundingRegion(coneGeometry);
        }
        if (geometry instanceof CylinderGeometryData cylinderGeometry) {
            return CylinderBlockGenerator.createBoundingRegion(cylinderGeometry);
        }
        if (geometry instanceof PrismGeometryData prismGeometry) {
            return createPrismBoundingRegion(prismGeometry);
        }
        if (geometry instanceof SphereData sphereGeometry) {
            return SphereBlockGenerator.createBoundingRegion(sphereGeometry);
        }
        if (geometry instanceof TorusGeometryData torusGeometry) {
            return TorusBlockGenerator.createBoundingRegion(torusGeometry);
        }
        return null;
    }

    public static BlockPosList voxelizeComposite(CompositeGeometryData geometry, boolean fillSolid) {
        Set<BlockPos> mergedPositions = new LinkedHashSet<>();

        for (GeometryData child : geometry.getGeometries()) {
            BlockPosList childBlocks = voxelize(child, fillSolid);
            for (BlockPos pos : childBlocks) {
                mergedPositions.add(pos.toImmutable());
            }
        }

        return new BlockPosList(mergedPositions);
    }

    public static @Nullable RegionData createCompositeBoundingRegion(CompositeGeometryData geometry) {
        BlockPos minCorner = null;
        BlockPos maxCorner = null;

        for (GeometryData child : geometry.getGeometries()) {
            RegionData childRegion = createBoundingRegion(child);
            if (childRegion == null || !childRegion.isComplete()) {
                continue;
            }

            BlockPos childMin = childRegion.getMinCorner();
            BlockPos childMax = childRegion.getMaxCorner();
            if (childMin == null || childMax == null) {
                continue;
            }

            if (minCorner == null || maxCorner == null) {
                minCorner = childMin;
                maxCorner = childMax;
                continue;
            }

            minCorner = new BlockPos(
                Math.min(minCorner.getX(), childMin.getX()),
                Math.min(minCorner.getY(), childMin.getY()),
                Math.min(minCorner.getZ(), childMin.getZ())
            );
            maxCorner = new BlockPos(
                Math.max(maxCorner.getX(), childMax.getX()),
                Math.max(maxCorner.getY(), childMax.getY()),
                Math.max(maxCorner.getZ(), childMax.getZ())
            );
        }

        return minCorner != null && maxCorner != null ? new RegionData(minCorner, maxCorner) : null;
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

    public static BlockPosList voxelizeCone(ConeGeometryData geometry, boolean fillSolid) {
        BlockPosList blocks = new BlockPosList();
        RegionData region = ConeBlockGenerator.createBoundingRegion(geometry);
        ConeBlockGenerator.populateCone(blocks, region, geometry, fillSolid);
        return blocks;
    }

    public static BlockPosList voxelizePrism(PrismGeometryData geometry, boolean fillSolid) {
        // First-pass bridge: prism side walls and section loops are approximated as a surface lattice.
        // This keeps the new primitive in the voxel workflow until a dedicated solid prism rasterizer is added.
        return SurfaceStripBridge.voxelize(
            geometry.getSideSurfaceStrip(),
            Math.max(1, (int) Math.ceil(Math.max(1.0d, geometry.getHeight()))),
            SurfaceStripBridge.BridgeMode.LATTICE
        );
    }

    public static BlockPosList voxelizeSphere(SphereData geometry, boolean fillSolid) {
        return voxelizeSphere(
            geometry,
            fillSolid ? SphereBlockGenerator.VoxelMode.SOLID : SphereBlockGenerator.VoxelMode.SHELL,
            1.0d
        );
    }

    public static BlockPosList voxelizeSphere(SphereData geometry,
                                              SphereBlockGenerator.VoxelMode voxelMode,
                                              double shellThickness) {
        BlockPosList blocks = new BlockPosList();
        RegionData region = SphereBlockGenerator.createBoundingRegion(geometry);
        SphereBlockGenerator.populateSphere(blocks, region, geometry, voxelMode, shellThickness);
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

    public static RegionData createPrismBoundingRegion(PrismGeometryData geometry) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        for (Vector3d point : geometry.getBaseVertices()) {
            minX = Math.min(minX, point.x);
            minY = Math.min(minY, point.y);
            minZ = Math.min(minZ, point.z);
            maxX = Math.max(maxX, point.x);
            maxY = Math.max(maxY, point.y);
            maxZ = Math.max(maxZ, point.z);
        }
        for (Vector3d point : geometry.getTopVertices()) {
            minX = Math.min(minX, point.x);
            minY = Math.min(minY, point.y);
            minZ = Math.min(minZ, point.z);
            maxX = Math.max(maxX, point.x);
            maxY = Math.max(maxY, point.y);
            maxZ = Math.max(maxZ, point.z);
        }

        return new RegionData(
            BlockPos.ofFloored(minX, minY, minZ),
            BlockPos.ofFloored(maxX, maxY, maxZ)
        );
    }
}

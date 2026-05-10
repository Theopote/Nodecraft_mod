package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.BoundingBoxData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import net.minecraft.util.math.BlockPos;
import org.joml.Matrix3d;
import org.joml.Vector3d;

/**
 * Shared helpers for turning box definitions into block coordinates.
 */
public final class BoxBlockGenerator {

    private BoxBlockGenerator() {
    }

    public static RegionData createAxisAlignedRegion(BlockPos center, int sizeX, int sizeY, int sizeZ) {
        // 现在 sizeX/sizeY/sizeZ 直接等于几何长度
        int halfX = sizeX / 2;
        int halfY = sizeY / 2;
        int halfZ = sizeZ / 2;
        BlockPos minCorner = new BlockPos(
            center.getX() - halfX,
            center.getY() - halfY,
            center.getZ() - halfZ
        );
        BlockPos maxCorner = new BlockPos(
            center.getX() + (sizeX % 2 == 0 ? halfX - 1 : halfX),
            center.getY() + (sizeY % 2 == 0 ? halfY - 1 : halfY),
            center.getZ() + (sizeZ % 2 == 0 ? halfZ - 1 : halfZ)
        );
        return new RegionData(minCorner, maxCorner);
    }

    public static RegionData createOrientedBoundingRegion(Vector3d center, Vector3d halfExtents, Matrix3d orientationMatrix) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    Vector3d corner = new Vector3d(
                        sx * halfExtents.x,
                        sy * halfExtents.y,
                        sz * halfExtents.z
                    );
                    orientationMatrix.transform(corner);
                    corner.add(center);

                    minX = Math.min(minX, corner.x);
                    minY = Math.min(minY, corner.y);
                    minZ = Math.min(minZ, corner.z);
                    maxX = Math.max(maxX, corner.x);
                    maxY = Math.max(maxY, corner.y);
                    maxZ = Math.max(maxZ, corner.z);
                }
            }
        }

        BlockPos minCorner = BlockPos.ofFloored(minX, minY, minZ);
        BlockPos maxCorner = BlockPos.ofFloored(maxX - 1e-9d, maxY - 1e-9d, maxZ - 1e-9d);
        return new RegionData(minCorner, maxCorner);
    }

    public static RegionData regionFromBoundingBox(BoundingBoxData boundingBox) {
        Vector3d min = boundingBox.getMin();
        Vector3d max = boundingBox.getMax();

        BlockPos minCorner = BlockPos.ofFloored(min.x, min.y, min.z);
        BlockPos maxCorner = BlockPos.ofFloored(max.x - 1.0e-9d, max.y - 1.0e-9d, max.z - 1.0e-9d);
        return new RegionData(minCorner, maxCorner);
    }

    public static void populateAxisAlignedBox(BlockPosList blocks, BlockPos minCorner, BlockPos maxCorner, boolean fillBox) {
        for (int x = minCorner.getX(); x <= maxCorner.getX(); x++) {
            for (int y = minCorner.getY(); y <= maxCorner.getY(); y++) {
                for (int z = minCorner.getZ(); z <= maxCorner.getZ(); z++) {
                    if (fillBox || isAxisAlignedShellBlock(x, y, z, minCorner, maxCorner)) {
                        blocks.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
    }

    public static void populateOrientedBox(
        BlockPosList blocks,
        BlockPos minCorner,
        BlockPos maxCorner,
        Vector3d center,
        Vector3d halfExtents,
        Matrix3d orientationMatrix,
        boolean fillBox
    ) {
        for (int x = minCorner.getX(); x <= maxCorner.getX(); x++) {
            for (int y = minCorner.getY(); y <= maxCorner.getY(); y++) {
                for (int z = minCorner.getZ(); z <= maxCorner.getZ(); z++) {
                    if (!containsOrientedBox(center, halfExtents, orientationMatrix, x, y, z)) {
                        continue;
                    }

                    if (fillBox || isOrientedShellBlock(center, halfExtents, orientationMatrix, x, y, z)) {
                        blocks.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
    }

    public static boolean containsOrientedBox(
        Vector3d center,
        Vector3d halfExtents,
        Matrix3d orientationMatrix,
        int x,
        int y,
        int z
    ) {
        Matrix3d inverseRotation = new Matrix3d(orientationMatrix).transpose();
        Vector3d local = new Vector3d(x, y, z).sub(center);
        inverseRotation.transform(local);

        return Math.abs(local.x) <= halfExtents.x
            && Math.abs(local.y) <= halfExtents.y
            && Math.abs(local.z) <= halfExtents.z;
    }

    public static boolean isAxisAlignedShellBlock(int x, int y, int z, BlockPos minCorner, BlockPos maxCorner) {
        return x == minCorner.getX() || x == maxCorner.getX()
            || y == minCorner.getY() || y == maxCorner.getY()
            || z == minCorner.getZ() || z == maxCorner.getZ();
    }

    public static boolean isOrientedShellBlock(
        Vector3d center,
        Vector3d halfExtents,
        Matrix3d orientationMatrix,
        int x,
        int y,
        int z
    ) {
        return !containsOrientedBox(center, halfExtents, orientationMatrix, x + 1, y, z)
            || !containsOrientedBox(center, halfExtents, orientationMatrix, x - 1, y, z)
            || !containsOrientedBox(center, halfExtents, orientationMatrix, x, y + 1, z)
            || !containsOrientedBox(center, halfExtents, orientationMatrix, x, y - 1, z)
            || !containsOrientedBox(center, halfExtents, orientationMatrix, x, y, z + 1)
            || !containsOrientedBox(center, halfExtents, orientationMatrix, x, y, z - 1);
    }
}

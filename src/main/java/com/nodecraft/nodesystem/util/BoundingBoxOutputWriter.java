package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.BoundingBoxData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.Map;

/**
 * Shared bounding-box outputs for nodes that derive an AABB from blocks or geometry.
 */
public final class BoundingBoxOutputWriter {

    private BoundingBoxOutputWriter() {
    }

    public static void writeOrClear(
        Map<String, Object> outputValues,
        @Nullable RegionData region,
        String boundingBoxPortId,
        String regionPortId,
        String minCornerPortId,
        String maxCornerPortId,
        String sizeXPortId,
        String sizeYPortId,
        String sizeZPortId,
        String volumePortId,
        String centerPortId
    ) {
        if (region == null || !region.isComplete()) {
            outputValues.clear();
            return;
        }

        BlockPos minCorner = region.getMinCorner();
        BlockPos maxCorner = region.getMaxCorner();
        if (minCorner == null || maxCorner == null) {
            outputValues.clear();
            return;
        }

        BoundingBoxData boundingBox = new BoundingBoxData(
            new Vector3d(minCorner.getX(), minCorner.getY(), minCorner.getZ()),
            new Vector3d(maxCorner.getX() + 1.0d, maxCorner.getY() + 1.0d, maxCorner.getZ() + 1.0d)
        );

        int sizeX = maxCorner.getX() - minCorner.getX() + 1;
        int sizeY = maxCorner.getY() - minCorner.getY() + 1;
        int sizeZ = maxCorner.getZ() - minCorner.getZ() + 1;
        int volume = sizeX * sizeY * sizeZ;

        BlockPos center = new BlockPos(
            minCorner.getX() + ((sizeX - 1) / 2),
            minCorner.getY() + ((sizeY - 1) / 2),
            minCorner.getZ() + ((sizeZ - 1) / 2)
        );

        outputValues.put(boundingBoxPortId, boundingBox);
        outputValues.put(regionPortId, region);
        outputValues.put(minCornerPortId, minCorner);
        outputValues.put(maxCornerPortId, maxCorner);
        outputValues.put(sizeXPortId, sizeX);
        outputValues.put(sizeYPortId, sizeY);
        outputValues.put(sizeZPortId, sizeZ);
        outputValues.put(volumePortId, volume);
        outputValues.put(centerPortId, center);
    }
}

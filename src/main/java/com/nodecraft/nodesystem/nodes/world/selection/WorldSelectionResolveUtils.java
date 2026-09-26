package com.nodecraft.nodesystem.nodes.world.selection;

import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.util.BlockSpace;
import com.nodecraft.nodesystem.util.Coordinate;
import com.nodecraft.nodesystem.util.Vector3;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

final class WorldSelectionResolveUtils {

    private WorldSelectionResolveUtils() {
    }

    static @Nullable Vector3d toPointPosition(Object value) {
        if (value instanceof PointData pointData) {
            return new Vector3d(pointData.position());
        }
        return null;
    }

    static @Nullable Vector3d resolveVector3d(Object value) {
        Vector3d strict = toPointPosition(value);
        if (strict != null) {
            return strict;
        }
        if (value instanceof Vector3d vector) {
            return new Vector3d(vector);
        }
        if (value instanceof Vec3d vector) {
            return new Vector3d(vector.x, vector.y, vector.z);
        }
        if (value instanceof Vector3(float x, float y, float z)) {
            return new Vector3d(x, y, z);
        }
        if (value instanceof BlockPos blockPos) {
            return BlockSpace.cellCenter(blockPos);
        }
        if (value instanceof Coordinate(int x, int y, int z)) {
            return BlockSpace.cellCenter(x, y, z);
        }
        return null;
    }

    static @Nullable BlockPos resolveBlockPos(Object value) {
        if (value instanceof BlockPos pos) {
            return pos.toImmutable();
        }
        if (value instanceof Coordinate(int x, int y, int z)) {
            return new BlockPos(x, y, z);
        }
        Vector3d vector = resolveVector3d(value);
        if (vector != null) {
            return BlockSpace.pointToBlockFloor(vector);
        }
        return null;
    }
}

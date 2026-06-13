package com.nodecraft.nodesystem.nodes.world.selection;

import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.util.Coordinate;
import com.nodecraft.nodesystem.util.Vector3;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

final class WorldSelectionResolveUtils {

    private WorldSelectionResolveUtils() {
    }

    static @Nullable Vector3d resolveVector3d(Object value) {
        if (value instanceof PointData pointData) {
            return new Vector3d(pointData.getPosition());
        }
        if (value instanceof Vector3d vector) {
            return new Vector3d(vector);
        }
        if (value instanceof Vec3d vector) {
            return new Vector3d(vector.x, vector.y, vector.z);
        }
        if (value instanceof Vector3 vector) {
            return new Vector3d(vector.getX(), vector.getY(), vector.getZ());
        }
        if (value instanceof BlockPos blockPos) {
            return new Vector3d(blockPos.getX(), blockPos.getY(), blockPos.getZ());
        }
        if (value instanceof Coordinate coordinate) {
            return new Vector3d(coordinate.getX(), coordinate.getY(), coordinate.getZ());
        }
        return null;
    }

    static @Nullable BlockPos resolveBlockPos(Object value) {
        if (value instanceof BlockPos pos) {
            return pos.toImmutable();
        }
        if (value instanceof Coordinate coordinate) {
            return new BlockPos(coordinate.getX(), coordinate.getY(), coordinate.getZ());
        }
        Vector3d vector = resolveVector3d(value);
        if (vector != null) {
            return BlockPos.ofFloored(vector.x, vector.y, vector.z);
        }
        return null;
    }
}

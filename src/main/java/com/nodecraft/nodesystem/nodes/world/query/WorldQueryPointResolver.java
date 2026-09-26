package com.nodecraft.nodesystem.nodes.world.query;

import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.util.BlockSpace;
import com.nodecraft.nodesystem.util.Vector3;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

final class WorldQueryPointResolver {

    private WorldQueryPointResolver() {
    }

    static @Nullable Vector3d resolveVector(Object value) {
        if (value instanceof PointData pointData) {
            return new Vector3d(pointData.position());
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
        if (value instanceof BlockPos pos) {
            // Spatial Convention v1: BlockPos as continuous location = cell center, not min corner.
            return BlockSpace.cellCenter(pos);
        }
        return null;
    }
}

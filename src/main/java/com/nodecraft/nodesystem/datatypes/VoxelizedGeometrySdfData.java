package com.nodecraft.nodesystem.datatypes;

import net.minecraft.util.math.BlockPos;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Approximate SDF backed by voxel block cells from an arbitrary GeometryData value.
 */
public class VoxelizedGeometrySdfData implements SignedDistanceFieldData {
    private final List<BlockPos> voxels;
    private final Set<BlockPos> voxelSet;

    public VoxelizedGeometrySdfData(Iterable<BlockPos> voxels) {
        List<BlockPos> list = new ArrayList<>();
        Set<BlockPos> set = new HashSet<>();
        for (BlockPos pos : voxels) {
            BlockPos immutable = pos.toImmutable();
            list.add(immutable);
            set.add(immutable);
        }
        this.voxels = List.copyOf(list);
        this.voxelSet = Set.copyOf(set);
    }

    public int getVoxelCount() {
        return voxels.size();
    }

    @Override
    public double sampleDistance(Vector3d point) {
        if (voxels.isEmpty()) {
            return Double.POSITIVE_INFINITY;
        }

        double bestSq = Double.POSITIVE_INFINITY;
        for (BlockPos voxel : voxels) {
            double dx = axisDistanceToUnitInterval(point.x, voxel.getX(), voxel.getX() + 1.0d);
            double dy = axisDistanceToUnitInterval(point.y, voxel.getY(), voxel.getY() + 1.0d);
            double dz = axisDistanceToUnitInterval(point.z, voxel.getZ(), voxel.getZ() + 1.0d);
            double dSq = dx * dx + dy * dy + dz * dz;
            if (dSq < bestSq) {
                bestSq = dSq;
            }
        }

        double unsigned = Math.sqrt(bestSq);
        BlockPos containing = BlockPos.ofFloored(point.x, point.y, point.z);
        if (!voxelSet.contains(containing)) {
            return unsigned;
        }

        double inside = Math.min(
            Math.min(point.x - containing.getX(), containing.getX() + 1.0d - point.x),
            Math.min(
                Math.min(point.y - containing.getY(), containing.getY() + 1.0d - point.y),
                Math.min(point.z - containing.getZ(), containing.getZ() + 1.0d - point.z)
            )
        );
        return -Math.max(0.0d, inside);
    }

    private static double axisDistanceToUnitInterval(double value, double min, double max) {
        if (value < min) {
            return min - value;
        }
        if (value > max) {
            return value - max;
        }
        return 0.0d;
    }
}

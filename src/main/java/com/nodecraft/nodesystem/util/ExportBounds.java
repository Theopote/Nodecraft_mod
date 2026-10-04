package com.nodecraft.nodesystem.util;

import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Axis-aligned export bounds with overflow-safe long sizes and dense-volume validation.
 */
public record ExportBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    public static ExportBounds fromPositions(Iterable<BlockPos> positions) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        boolean any = false;
        for (BlockPos pos : positions) {
            if (pos == null) {
                continue;
            }
            any = true;
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        if (!any) {
            throw new IllegalArgumentException("empty_bounds");
        }
        return new ExportBounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public static ExportBounds fromPlacements(List<BlockPlacementData> placements) {
        return fromPositions(placements.stream().map(BlockPlacementData::pos).toList());
    }

    public long sizeX() {
        return (long) maxX - (long) minX + 1L;
    }

    public long sizeY() {
        return (long) maxY - (long) minY + 1L;
    }

    public long sizeZ() {
        return (long) maxZ - (long) minZ + 1L;
    }

    /**
     * Overflow-safe volume product.
     *
     * @throws ArithmeticException when the product overflows {@code long}
     */
    public long checkedVolume() {
        return Math.multiplyExact(Math.multiplyExact(sizeX(), sizeY()), sizeZ());
    }

    /**
     * Validates dense-export AABB against volume and optional per-axis caps.
     *
     * @return error message, or {@code null} when valid
     */
    public @Nullable String validateDense(long maxVolume, @Nullable Integer maxAxis) {
        long sx = sizeX();
        long sy = sizeY();
        long sz = sizeZ();
        if (sx <= 0L || sy <= 0L || sz <= 0L) {
            return "invalid_bounds";
        }
        if (maxAxis != null && (sx > maxAxis || sy > maxAxis || sz > maxAxis)) {
            return "axis_exceeds_MAX_WORLD_EDIT_AXIS";
        }
        if (sx > Integer.MAX_VALUE || sy > Integer.MAX_VALUE || sz > Integer.MAX_VALUE) {
            return "axis_exceeds_int";
        }
        final long volume;
        try {
            volume = checkedVolume();
        } catch (ArithmeticException ignored) {
            return "volume_overflow";
        }
        if (volume > maxVolume) {
            return "volume_exceeds_MAX_DENSE_EXPORT_VOLUME";
        }
        if (volume > Integer.MAX_VALUE) {
            return "volume_exceeds_int_array";
        }
        return null;
    }

    /**
     * Validates sparse-export AABB sizes fit signed 32-bit NBT ints.
     */
    public @Nullable String validateSparse() {
        long sx = sizeX();
        long sy = sizeY();
        long sz = sizeZ();
        if (sx <= 0L || sy <= 0L || sz <= 0L) {
            return "invalid_bounds";
        }
        if (sx > Integer.MAX_VALUE || sy > Integer.MAX_VALUE || sz > Integer.MAX_VALUE) {
            return "axis_exceeds_int";
        }
        return null;
    }

    public int sizeXInt() {
        return Math.toIntExact(sizeX());
    }

    public int sizeYInt() {
        return Math.toIntExact(sizeY());
    }

    public int sizeZInt() {
        return Math.toIntExact(sizeZ());
    }
}

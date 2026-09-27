package com.nodecraft.nodesystem.util;

import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

/**
 * Safe block-grid arithmetic and snap helpers for transform.placement (Graph V76).
 */
public final class PlacementBlockUtils {

    private PlacementBlockUtils() {
    }

    /**
     * Rounds a finite offset component to int without silent wrap.
     */
    public static @Nullable Integer roundOffsetToInt(double value) {
        if (!Double.isFinite(value)) {
            return null;
        }
        long rounded = Math.round(value);
        if (rounded < Integer.MIN_VALUE || rounded > Integer.MAX_VALUE) {
            return null;
        }
        return (int) rounded;
    }

    /**
     * Adds integer offsets to a block position with overflow checks.
     */
    public static @Nullable BlockPos addBlockPos(BlockPos source, int dx, int dy, int dz) {
        if (source == null) {
            return null;
        }
        long x = (long) source.getX() + dx;
        long y = (long) source.getY() + dy;
        long z = (long) source.getZ() + dz;
        if (x < Integer.MIN_VALUE || x > Integer.MAX_VALUE
            || y < Integer.MIN_VALUE || y > Integer.MAX_VALUE
            || z < Integer.MIN_VALUE || z > Integer.MAX_VALUE) {
            return null;
        }
        return new BlockPos((int) x, (int) y, (int) z);
    }

    /**
     * Snaps a transformed cell-center point back to BLOCK_POS; rejects non-finite input.
     */
    public static @Nullable BlockPos trySnapCellCenter(Vector3d continuousPoint) {
        if (continuousPoint == null
            || !Double.isFinite(continuousPoint.x)
            || !Double.isFinite(continuousPoint.y)
            || !Double.isFinite(continuousPoint.z)) {
            return null;
        }
        return BlockSpace.snapCellCenter(continuousPoint);
    }

    /**
     * Preflight check for BLOCK_LIST size. Returns an error message when over limit, else null.
     */
    public static @Nullable String validateBlockListSize(@Nullable BlockPosList list) {
        if (list == null) {
            return "Missing block position list";
        }
        if (list.size() > GenerationLimits.MAX_LIST_ELEMENTS) {
            return "Block position list exceeds MAX_LIST_ELEMENTS";
        }
        return null;
    }
}

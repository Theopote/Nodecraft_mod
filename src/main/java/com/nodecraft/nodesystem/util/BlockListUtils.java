package com.nodecraft.nodesystem.util;

import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashSet;

/**
 * Strict BLOCK_LIST payload resolution for set-based block operations.
 * <p>
 * Unlike {@link PointUtils#resolveStrictPointList}, an empty collection is a valid empty set
 * (not null). Duplicate positions are ignored (set semantics).
 */
public final class BlockListUtils {

    private BlockListUtils() {
    }

    /**
     * Resolves a BLOCK_LIST port value to a unique block set.
     *
     * @return {@code null} when the payload is invalid (wrong type, null member, non-BlockPos member);
     *         empty {@link LinkedHashSet} for valid empty input;
     *         non-empty set in encounter order with duplicates removed
     */
    public static @Nullable LinkedHashSet<BlockPos> resolveStrictBlockSet(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BlockPosList blockPosList) {
            return resolveFromIterable(blockPosList);
        }
        if (value instanceof Collection<?> collection) {
            return resolveFromIterable(collection);
        }
        return null;
    }

    private static @Nullable LinkedHashSet<BlockPos> resolveFromIterable(Iterable<?> values) {
        LinkedHashSet<BlockPos> blocks = new LinkedHashSet<>();
        for (Object entry : values) {
            if (!(entry instanceof BlockPos pos)) {
                return null;
            }
            blocks.add(pos.toImmutable());
        }
        return blocks;
    }
}

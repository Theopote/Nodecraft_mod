package com.nodecraft.nodesystem.util;

import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Strict BLOCK_LIST payload resolution for set-based and ordered block operations.
 * <p>
 * Unlike {@link PointUtils#resolveStrictPointList}, an empty collection is a valid empty set
 * (not null) for {@link #resolveStrictBlockSet}. Duplicate positions are ignored (set semantics).
 * {@link #resolveStrictBlockList} preserves encounter order including duplicates.
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
        return switch (value) {
            case BlockPosList blockPosList -> resolveSetFromIterable(blockPosList);
            case Collection<?> collection -> resolveSetFromIterable(collection);
            case null, default -> null;
        };
    }

    /**
     * Resolves a BLOCK_LIST port value to an ordered list (duplicates preserved).
     *
     * @return {@code null} when the payload is invalid;
     *         empty list for valid empty input;
     *         otherwise encounter-order list of immutable {@link BlockPos}
     */
    public static @Nullable List<BlockPos> resolveStrictBlockList(@Nullable Object value) {
        return switch (value) {
            case BlockPosList blockPosList -> resolveListFromIterable(blockPosList);
            case Collection<?> collection -> resolveListFromIterable(collection);
            case null, default -> null;
        };
    }

    private static @Nullable LinkedHashSet<BlockPos> resolveSetFromIterable(Iterable<?> values) {
        LinkedHashSet<BlockPos> blocks = new LinkedHashSet<>();
        for (Object entry : values) {
            if (!(entry instanceof BlockPos pos)) {
                return null;
            }
            blocks.add(pos.toImmutable());
        }
        return blocks;
    }

    private static @Nullable List<BlockPos> resolveListFromIterable(Iterable<?> values) {
        List<BlockPos> blocks = new ArrayList<>();
        for (Object entry : values) {
            if (!(entry instanceof BlockPos pos)) {
                return null;
            }
            blocks.add(pos.toImmutable());
        }
        return blocks;
    }
}

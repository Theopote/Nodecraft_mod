package com.nodecraft.nodesystem.util;

import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Shared last-write-wins collapse by {@link BlockPos}: later entries replace earlier ones;
 * first-seen key order is preserved (LinkedHashMap insertion order).
 */
public final class LastWriteWinsByPos {

    private LastWriteWinsByPos() {
    }

    /**
     * @param items      source sequence (null/empty → empty list)
     * @param posOf      coordinate extractor; null pos skips the item
     * @param accept     extra keep predicate (e.g. non-null payload)
     * @param remapAtKey builds the stored value at an immutable key
     */
    public static <T> List<T> collapse(
            @Nullable List<T> items,
            Function<? super T, BlockPos> posOf,
            java.util.function.Predicate<? super T> accept,
            BiFunction<BlockPos, T, T> remapAtKey) {
        Objects.requireNonNull(posOf, "posOf");
        Objects.requireNonNull(accept, "accept");
        Objects.requireNonNull(remapAtKey, "remapAtKey");
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<BlockPos, T> byPos = new LinkedHashMap<>();
        for (T item : items) {
            if (item == null || !accept.test(item)) {
                continue;
            }
            BlockPos pos = posOf.apply(item);
            if (pos == null) {
                continue;
            }
            BlockPos key = pos.toImmutable();
            byPos.put(key, remapAtKey.apply(key, item));
        }
        return List.copyOf(byPos.values());
    }
}

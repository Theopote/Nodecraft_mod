package com.nodecraft.nodesystem.util;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Canonical Material palette payload: ordered weighted block entries.
 * <p>
 * Prefer this over raw {@code List<String>} on palette ports.
 */
public final class BlockPaletteData {

    private final List<BlockPaletteEntry> entries;

    public BlockPaletteData(List<BlockPaletteEntry> entries) {
        List<BlockPaletteEntry> copy = new ArrayList<>();
        if (entries != null) {
            for (BlockPaletteEntry entry : entries) {
                if (entry != null && entry.isUsable()) {
                    copy.add(entry);
                }
            }
        }
        this.entries = List.copyOf(copy);
    }

    public static BlockPaletteData empty() {
        return new BlockPaletteData(List.of());
    }

    public static BlockPaletteData ofBlockIds(List<String> blockIds) {
        if (blockIds == null || blockIds.isEmpty()) {
            return empty();
        }
        List<BlockPaletteEntry> entries = new ArrayList<>(blockIds.size());
        for (String blockId : blockIds) {
            if (blockId != null && !blockId.isBlank()) {
                entries.add(new BlockPaletteEntry(blockId));
            }
        }
        return new BlockPaletteData(entries);
    }

    public static BlockPaletteData ofBlockIdsAndWeights(List<String> blockIds, @Nullable List<Double> weights) {
        if (blockIds == null || blockIds.isEmpty()) {
            return empty();
        }
        List<BlockPaletteEntry> entries = new ArrayList<>(blockIds.size());
        for (int i = 0; i < blockIds.size(); i++) {
            String blockId = blockIds.get(i);
            if (blockId == null || blockId.isBlank()) {
                continue;
            }
            double weight = 1.0d;
            if (weights != null && i < weights.size() && weights.get(i) != null) {
                weight = weights.get(i);
            }
            entries.add(new BlockPaletteEntry(blockId, weight));
        }
        return new BlockPaletteData(entries);
    }

    /**
     * Legacy coercion: {@code List}/{@code String} → {@link BlockPaletteData}.
     * <p>
     * Do <em>not</em> call from typed palette consumers. Use
     * {@link LegacyBlockPaletteAdapter} / Create Block Palette instead.
     */
    public static BlockPaletteData fromLegacyObject(@Nullable Object value) {
        if (value instanceof BlockPaletteData palette) {
            return palette;
        }
        if (value instanceof String blockId) {
            return blockId.isBlank() ? empty() : ofBlockIds(List.of(blockId));
        }
        if (value instanceof List<?> list) {
            List<BlockPaletteEntry> entries = new ArrayList<>();
            for (Object entry : list) {
                if (entry instanceof BlockPaletteEntry paletteEntry && paletteEntry.isUsable()) {
                    entries.add(paletteEntry);
                } else if (entry instanceof String blockId && !blockId.isBlank()) {
                    entries.add(new BlockPaletteEntry(blockId));
                }
            }
            return new BlockPaletteData(entries);
        }
        return empty();
    }

    /**
     * Typed-port helper: only {@link BlockPaletteData} is accepted; otherwise empty.
     */
    public static BlockPaletteData requireTyped(@Nullable Object value) {
        return value instanceof BlockPaletteData palette ? palette : empty();
    }

    public List<BlockPaletteEntry> entries() {
        return entries;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int size() {
        return entries.size();
    }

    public List<String> blockIds() {
        if (entries.isEmpty()) {
            return List.of();
        }
        List<String> ids = new ArrayList<>(entries.size());
        for (BlockPaletteEntry entry : entries) {
            ids.add(entry.blockId());
        }
        return Collections.unmodifiableList(ids);
    }

    public List<Double> weights() {
        if (entries.isEmpty()) {
            return List.of();
        }
        List<Double> weights = new ArrayList<>(entries.size());
        for (BlockPaletteEntry entry : entries) {
            weights.add(entry.weight());
        }
        return Collections.unmodifiableList(weights);
    }

    public BlockPaletteData withFallback(String fallbackBlockId) {
        if (!isEmpty()) {
            return this;
        }
        if (fallbackBlockId == null || fallbackBlockId.isBlank()) {
            return empty();
        }
        return ofBlockIds(List.of(fallbackBlockId));
    }

    public String blockIdAt(int index, String fallback) {
        if (entries.isEmpty()) {
            return fallback;
        }
        BlockPaletteEntry entry = entries.get(Math.floorMod(index, entries.size()));
        String blockId = entry.blockId();
        return blockId == null || blockId.isBlank() ? fallback : blockId;
    }
}

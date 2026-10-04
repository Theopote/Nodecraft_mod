package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.nodes.material.basic_assignment.BasicAssignmentUtils;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Canonical Material palette payload: ordered weighted block entries (blockId + weight only).
 * <p>
 * Prefer this over raw {@code List<String>} on palette ports.
 */
public record BlockPaletteData(List<BlockPaletteEntry> entries) {

    public BlockPaletteData {
        if (entries == null) {
            throw new IllegalArgumentException("Palette entries required");
        }
        List<BlockPaletteEntry> copy = new ArrayList<>(entries.size());
        for (BlockPaletteEntry entry : entries) {
            if (entry == null) {
                throw new IllegalArgumentException("Palette entry must not be null");
            }
            copy.add(entry);
        }
        entries = List.copyOf(copy);
    }

    public static BlockPaletteData empty() {
        return new BlockPaletteData(List.of());
    }

    /**
     * Graph-facing constructor: {@code null} when entries are missing or illegal.
     */
    public static @Nullable BlockPaletteData canonical(@Nullable List<BlockPaletteEntry> entries) {
        try {
            if (entries == null) {
                return null;
            }
            return new BlockPaletteData(entries);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static BlockPaletteData ofBlockIds(List<String> blockIds) {
        if (blockIds == null || blockIds.isEmpty()) {
            return empty();
        }
        List<BlockPaletteEntry> entries = new ArrayList<>(blockIds.size());
        for (String blockId : blockIds) {
            String canonical = BasicAssignmentUtils.canonicalizePaletteBlockId(blockId);
            if (canonical != null) {
                entries.add(new BlockPaletteEntry(canonical));
            }
        }
        return new BlockPaletteData(entries);
    }

    /**
     * @deprecated Use {@link BasicAssignmentUtils#buildPalette}
     * with validated weights instead.
     */
    @Deprecated
    public static BlockPaletteData ofBlockIdsAndWeights(List<String> blockIds, @Nullable List<Double> weights) {
        if (blockIds == null || blockIds.isEmpty()) {
            return empty();
        }
        List<BlockPaletteEntry> entries = new ArrayList<>(blockIds.size());
        for (int i = 0; i < blockIds.size(); i++) {
            String canonical = BasicAssignmentUtils.canonicalizePaletteBlockId(blockIds.get(i));
            if (canonical == null) {
                continue;
            }
            double weight = weights != null && i < weights.size() && weights.get(i) != null
                    ? weights.get(i)
                    : 1.0d;
            if (!Double.isFinite(weight) || weight < 0.0d) {
                continue;
            }
            entries.add(new BlockPaletteEntry(canonical, weight));
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
            String canonical = BasicAssignmentUtils.canonicalizePaletteBlockId(blockId);
            return canonical == null ? empty() : ofBlockIds(List.of(canonical));
        }
        if (value instanceof List<?> list) {
            List<BlockPaletteEntry> entries = new ArrayList<>();
            for (Object entry : list) {
                if (entry instanceof BlockPaletteEntry paletteEntry && paletteEntry.isUsable()) {
                    entries.add(paletteEntry);
                } else if (entry instanceof String blockId) {
                    String canonical = BasicAssignmentUtils.canonicalizePaletteBlockId(blockId);
                    if (canonical != null) {
                        entries.add(new BlockPaletteEntry(canonical));
                    }
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

    public String blockIdAt(int index, String fallback) {
        if (entries.isEmpty()) {
            return fallback;
        }
        BlockPaletteEntry entry = entries.get(Math.floorMod(index, entries.size()));
        String blockId = entry.blockId();
        return blockId == null || blockId.isBlank() ? fallback : blockId;
    }
}

package com.nodecraft.nodesystem.util;

import net.minecraft.util.Identifier;

import java.util.Locale;

/**
 * One entry in a {@link BlockPaletteData}: block id and weight (material only; no stateData).
 */
public record BlockPaletteEntry(String blockId, double weight) {

    public BlockPaletteEntry(String blockId) {
        this(blockId, 1.0d);
    }

    public BlockPaletteEntry {
        if (blockId == null || blockId.isBlank()) {
            throw new IllegalArgumentException("Palette block id required");
        }
        String normalized = blockId.trim().toLowerCase(Locale.ROOT);
        if (!normalized.contains(":")) {
            normalized = "minecraft:" + normalized;
        }
        if (Identifier.tryParse(normalized) == null) {
            throw new IllegalArgumentException("Invalid palette block id: " + blockId);
        }
        if (!Double.isFinite(weight) || weight < 0.0d) {
            throw new IllegalArgumentException("Palette weight must be finite and >= 0");
        }
        blockId = normalized;
    }

    public boolean isUsable() {
        return blockId != null && !blockId.isBlank();
    }
}

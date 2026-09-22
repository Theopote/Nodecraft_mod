package com.nodecraft.nodesystem.util;

import org.jetbrains.annotations.Nullable;

/**
 * One entry in a {@link BlockPaletteData}: block id, optional weight, optional state overrides.
 */
public record BlockPaletteEntry(String blockId, double weight, @Nullable BlockStateData stateData) {

    public BlockPaletteEntry(String blockId) {
        this(blockId, 1.0d, null);
    }

    public BlockPaletteEntry(String blockId, double weight) {
        this(blockId, weight, null);
    }

    public BlockPaletteEntry {
        blockId = blockId != null ? blockId : "";
        weight = Double.isFinite(weight) ? Math.max(0.0d, weight) : 0.0d;
        stateData = stateData != null ? stateData.copy() : null;
    }

    public boolean isUsable() {
        return blockId != null && !blockId.isBlank();
    }

    @Override
    public BlockStateData stateData() {
        return stateData != null ? stateData.copy() : null;
    }
}

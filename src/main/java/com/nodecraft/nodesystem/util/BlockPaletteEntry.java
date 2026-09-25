package com.nodecraft.nodesystem.util;

/**
 * One entry in a {@link BlockPaletteData}: block id and weight (material only; no stateData).
 */
public record BlockPaletteEntry(String blockId, double weight) {

    public BlockPaletteEntry(String blockId) {
        this(blockId, 1.0d);
    }

    public BlockPaletteEntry {
        blockId = blockId != null ? blockId : "";
    }

    public boolean isUsable() {
        return blockId != null && !blockId.isBlank();
    }
}

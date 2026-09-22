package com.nodecraft.nodesystem.util;

import org.jetbrains.annotations.Nullable;

/**
 * Legacy palette coercion for migration / Create Block Palette only.
 * <p>
 * Typed {@link com.nodecraft.nodesystem.api.NodeDataType#BLOCK_PALETTE} ports accept
 * only {@link BlockPaletteData}. Raw {@code List}/{@code String} must enter through
 * {@code material.basic_assignment.create_block_palette} (or this adapter during load).
 */
public final class LegacyBlockPaletteAdapter {

    private LegacyBlockPaletteAdapter() {
    }

    /**
     * Coerce legacy palette payloads into {@link BlockPaletteData}.
     * Prefer constructing via {@link BlockPaletteData#ofBlockIds(java.util.List)} in new code.
     */
    public static BlockPaletteData fromLegacy(@Nullable Object value) {
        return BlockPaletteData.fromLegacyObject(value);
    }
}

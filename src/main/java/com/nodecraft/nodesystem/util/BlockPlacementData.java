package com.nodecraft.nodesystem.util;

import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable placement data for a single block position, block id, and optional state overrides.
 * <p>
 * Canonical Material → Build payload element. Material mapping remaps {@code blockId} only;
 * Block State mapping remaps {@code stateData} only.
 */
public record BlockPlacementData(BlockPos pos, String blockId, BlockStateData stateData) {

    public BlockPlacementData(BlockPos pos, String blockId) {
        this(pos, blockId, null);
    }

    public BlockPlacementData {
        pos = pos != null ? pos.toImmutable() : null;
        stateData = stateData != null ? stateData.copy() : null;
    }

    /**
     * Material remap: new block id, same position and stateData.
     */
    public BlockPlacementData withBlockId(String newBlockId) {
        return new BlockPlacementData(pos, newBlockId, stateData);
    }

    /**
     * Block-state remap: same position and block id, new state overrides.
     */
    public BlockPlacementData withStateData(@Nullable BlockStateData newStateData) {
        return new BlockPlacementData(pos, blockId, newStateData);
    }

    @Override
    public BlockPos pos() {
        return pos != null ? pos.toImmutable() : null;
    }

    @Override
    public BlockStateData stateData() {
        return stateData != null ? stateData.copy() : null;
    }
}

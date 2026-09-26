package com.nodecraft.nodesystem.nodes.world.write;

import net.minecraft.block.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Strict BLOCK_INFO_LIST resolution for world.write per-position materials.
 */
final class BlockInfoListUtils {

    private BlockInfoListUtils() {
    }

    /**
     * @return {@code null} when invalid or any member fails to resolve; empty list for valid empty input
     */
    static @Nullable List<BlockState> resolveStrictOrderedBlockInfoList(@Nullable Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return null;
        }
        List<BlockState> states = new ArrayList<>(collection.size());
        for (Object entry : collection) {
            BlockState state = WorldWriteUtils.resolveBlockState(entry);
            if (state == null) {
                return null;
            }
            states.add(state);
        }
        return states;
    }
}

package com.nodecraft.nodesystem.interaction;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.nodesystem.util.BlockStateData;
import net.minecraft.block.BlockState;

final class BlockStateDataMapper {

    private BlockStateDataMapper() {
    }

    static BlockStateData fromBlockState(BlockState blockState) {
        try {
            return com.nodecraft.nodesystem.util.BlockStateResolver.toStateData(blockState);
        } catch (Exception e) {
            NodeCraft.LOGGER.error("创建BlockStateData时出错", e);
            return new BlockStateData();
        }
    }
}

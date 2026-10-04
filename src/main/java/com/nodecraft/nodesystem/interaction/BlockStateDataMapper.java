package com.nodecraft.nodesystem.interaction;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.nodesystem.util.BlockStateData;
import net.minecraft.block.BlockState;

final class BlockStateDataMapper {

    private BlockStateDataMapper() {
    }

    static BlockStateData fromBlockState(BlockState blockState) {
        BlockStateData stateData = new BlockStateData();
        if (blockState == null) {
            return stateData;
        }

        try {
            for (var property : blockState.getProperties()) {
                String propertyName = property.getName();
                Comparable<?> propertyValue = blockState.get(property);
                stateData = stateData.withProperty(propertyName, propertyValue.toString());
            }
        } catch (Exception e) {
            NodeCraft.LOGGER.error("创建BlockStateData时出错", e);
        }

        return stateData;
    }
}

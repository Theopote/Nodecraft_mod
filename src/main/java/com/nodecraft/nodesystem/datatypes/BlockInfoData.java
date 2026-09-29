package com.nodecraft.nodesystem.datatypes;

import com.nodecraft.nodesystem.util.BlockStateData;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Immutable BLOCK_INFO snapshot: block position, registry id, state properties, and air flag.
 * Prefer this over live {@link BlockState} on graph wires.
 */
public record BlockInfoData(
    BlockPos position,
    String blockId,
    BlockStateData stateProperties,
    boolean isAir
) {

    public BlockInfoData {
        Objects.requireNonNull(position, "position");
        position = position.toImmutable();
        blockId = blockId == null ? "" : blockId;
        stateProperties = stateProperties == null
            ? new BlockStateData()
            : new BlockStateData(stateProperties);
    }

    /**
     * Builds a snapshot from a world block query. Returns {@code null} when {@code state} is null.
     */
    public static @Nullable BlockInfoData fromBlockState(@Nullable BlockPos pos, @Nullable BlockState state) {
        if (pos == null || state == null) {
            return null;
        }
        String id = Registries.BLOCK.getId(state.getBlock()).toString();
        BlockStateData properties = new BlockStateData();
        try {
            state.getProperties().forEach(property -> {
                Comparable<?> propertyValue = state.get(property);
                properties.put(property.getName(), propertyValue.toString());
            });
        } catch (RuntimeException ignored) {
            // Keep empty properties on unexpected state shape.
        }
        return new BlockInfoData(pos.toImmutable(), id, properties, state.isAir());
    }
}

package com.nodecraft.nodesystem.util;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Shared {@code blockId + BlockStateData → BlockState} resolution for Preview, Apply Changes, and exports.
 * <p>
 * Unknown properties on the target block are ignored so Material remap may safely preserve
 * incoming {@link BlockStateData} when the new block id does not support every property.
 */
public final class BlockStateResolver {

    private BlockStateResolver() {
    }

    /**
     * Resolves a block id to its default state, then applies compatible state overrides.
     */
    public static @Nullable BlockState resolve(@Nullable String blockId, @Nullable BlockStateData stateData) {
        BlockState base = resolveDefault(blockId);
        if (base == null) {
            return null;
        }
        return applyStateData(base, stateData);
    }

    /**
     * Resolves a block id to its default {@link BlockState}, or {@code null} when unknown.
     */
    public static @Nullable BlockState resolveDefault(@Nullable String blockId) {
        if (blockId == null || blockId.isBlank()) {
            return null;
        }
        try {
            Identifier id = Identifier.of(blockId);
            Block block = Registries.BLOCK.get(id);
            return block != null ? block.getDefaultState() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Applies only properties that exist on {@code baseState}; incompatible keys are skipped.
     */
    public static BlockState applyStateData(BlockState baseState, @Nullable BlockStateData stateData) {
        if (baseState == null || stateData == null || stateData.isEmpty()) {
            return baseState;
        }

        BlockState resolved = baseState;
        for (Map.Entry<String, String> entry : stateData.entrySet()) {
            if ("blockId".equals(entry.getKey()) || "id".equals(entry.getKey())) {
                continue;
            }
            Property<?> property = resolved.getProperties().stream()
                .filter(candidate -> candidate.getName().equals(entry.getKey()))
                .findFirst()
                .orElse(null);
            if (property == null) {
                continue;
            }
            resolved = applyPropertyValue(resolved, property, entry.getValue());
        }
        return resolved;
    }

    private static <T extends Comparable<T>> BlockState applyPropertyValue(
        BlockState state,
        Property<T> property,
        String rawValue
    ) {
        return property.parse(rawValue)
            .map(parsed -> state.with(property, parsed))
            .orElse(state);
    }
}

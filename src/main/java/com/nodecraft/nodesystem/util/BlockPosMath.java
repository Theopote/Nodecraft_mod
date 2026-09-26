package com.nodecraft.nodesystem.util;

import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Overflow-safe block position arithmetic for world query nodes.
 */
public final class BlockPosMath {

    private BlockPosMath() {
    }

    public static Optional<BlockPos> tryOffset(BlockPos origin, int dx, int dy, int dz) {
        if (origin == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new BlockPos(
                    Math.addExact(origin.getX(), dx),
                    Math.addExact(origin.getY(), dy),
                    Math.addExact(origin.getZ(), dz)
            ));
        } catch (ArithmeticException ignored) {
            return Optional.empty();
        }
    }
}

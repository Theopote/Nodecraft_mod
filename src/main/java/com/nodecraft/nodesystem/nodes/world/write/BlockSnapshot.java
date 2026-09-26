package com.nodecraft.nodesystem.nodes.world.write;

import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * One cell snapshot for world.write undo/redo (state + optional block-entity NBT).
 */
public final class BlockSnapshot {

    private final BlockPos pos;
    private final BlockState state;
    private final @Nullable NbtCompound blockEntityNbt;

    public BlockSnapshot(BlockPos pos, BlockState state, @Nullable NbtCompound blockEntityNbt) {
        this.pos = pos.toImmutable();
        this.state = state;
        this.blockEntityNbt = blockEntityNbt == null ? null : blockEntityNbt.copy();
    }

    public BlockPos pos() {
        return pos;
    }

    public BlockState state() {
        return state;
    }

    public @Nullable NbtCompound blockEntityNbt() {
        return blockEntityNbt == null ? null : blockEntityNbt.copy();
    }
}

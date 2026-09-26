package com.nodecraft.nodesystem.nodes.world.write;

import com.mojang.brigadier.StringReader;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

final class WorldWriteNbtUtils {

    static final String INPUT_NBT_ID = "input_nbt";
    static final String INPUT_NBT_STRING_ID = "input_nbt_string";
    static final String INPUT_MERGE_NBT_ID = "input_merge_nbt";

    /** Result of NBT preflight: ok with compound, none, or fail with error. */
    record NbtResolveResult(@Nullable NbtCompound nbt, @Nullable String error) {
        static NbtResolveResult none() {
            return new NbtResolveResult(null, null);
        }

        static NbtResolveResult ok(NbtCompound nbt) {
            return new NbtResolveResult(nbt, null);
        }

        static NbtResolveResult fail(String error) {
            return new NbtResolveResult(null, error);
        }

        boolean failed() {
            return error != null;
        }
    }

    private WorldWriteNbtUtils() {
    }

    /**
     * Connection-aware NBT resolution: connected NBT Compound owns the source (invalid → fail);
     * otherwise optional SNBT with hard length cap.
     */
    static NbtResolveResult resolveIncomingNbt(BaseNode node) {
        boolean nbtConnected = OptionalPortDrive.isConnected(node, INPUT_NBT_ID);
        if (nbtConnected) {
            Object value = node.getInput(INPUT_NBT_ID);
            if (!(value instanceof NbtCompound nbt)) {
                return NbtResolveResult.fail("NBT Compound is connected but null or invalid.");
            }
            return NbtResolveResult.ok(nbt.copy());
        }

        Object localNbt = node.getInput(INPUT_NBT_ID);
        if (localNbt instanceof NbtCompound nbt) {
            return NbtResolveResult.ok(nbt.copy());
        }

        boolean snbtConnected = OptionalPortDrive.isConnected(node, INPUT_NBT_STRING_ID);
        Object snbtRaw = node.getInput(INPUT_NBT_STRING_ID);
        if (snbtConnected) {
            if (!(snbtRaw instanceof String text) || text.isBlank()) {
                return NbtResolveResult.fail("NBT String is connected but null or blank.");
            }
            if (text.length() > GenerationLimits.MAX_WORLD_WRITE_SNBT_CHARS) {
                return NbtResolveResult.fail("NBT String exceeds MAX_WORLD_WRITE_SNBT_CHARS ("
                    + GenerationLimits.MAX_WORLD_WRITE_SNBT_CHARS + ").");
            }
            NbtCompound parsed = parseSnbt(text);
            if (parsed == null) {
                return NbtResolveResult.fail("NBT String SNBT parse failed.");
            }
            return NbtResolveResult.ok(parsed);
        }

        if (snbtRaw instanceof String text && !text.isBlank()) {
            if (text.length() > GenerationLimits.MAX_WORLD_WRITE_SNBT_CHARS) {
                return NbtResolveResult.fail("NBT String exceeds MAX_WORLD_WRITE_SNBT_CHARS ("
                    + GenerationLimits.MAX_WORLD_WRITE_SNBT_CHARS + ").");
            }
            NbtCompound parsed = parseSnbt(text);
            if (parsed == null) {
                return NbtResolveResult.fail("NBT String SNBT parse failed.");
            }
            return NbtResolveResult.ok(parsed);
        }

        return NbtResolveResult.none();
    }

    static @Nullable Boolean resolveMergeNbt(BaseNode node, boolean propertyFallback) {
        return OptionalPortDrive.resolveOptionalBoolean(node, INPUT_MERGE_NBT_ID, propertyFallback);
    }

    static @Nullable NbtCompound parseSnbt(String text) {
        try {
            Method readCompound = StringNbtReader.class.getMethod("readCompound", String.class);
            Object result = readCompound.invoke(null, text);
            if (result instanceof NbtCompound nbt) {
                return nbt;
            }
        } catch (Exception ignored) {
        }

        try {
            Method readCompoundAsArgument = StringNbtReader.class.getMethod("readCompoundAsArgument", StringReader.class);
            Object result = readCompoundAsArgument.invoke(null, new StringReader(text));
            if (result instanceof NbtCompound nbt) {
                return nbt;
            }
        } catch (Exception ignored) {
        }

        try {
            Method parse = StringNbtReader.class.getMethod("parse", String.class);
            Object result = parse.invoke(null, text);
            if (result instanceof NbtCompound nbt) {
                return nbt;
            }
        } catch (Exception ignored) {
        }

        try {
            Constructor<StringNbtReader> ctor = StringNbtReader.class.getConstructor(StringReader.class);
            StringNbtReader reader = ctor.newInstance(new StringReader(text));
            Method parseCompound = StringNbtReader.class.getMethod("parseCompound");
            Object result = parseCompound.invoke(reader);
            if (result instanceof NbtCompound nbt) {
                return nbt;
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    static NbtCompound mergeNbt(NbtCompound base, NbtCompound incoming) {
        for (String key : incoming.getKeys()) {
            NbtElement value = incoming.get(key);
            if (value != null) {
                base.put(key, value.copy());
            }
        }
        return base;
    }

    static boolean applyToBlockEntity(ExecutionContext context,
                                      BlockPos pos,
                                      NbtCompound incoming,
                                      boolean merge,
                                      boolean notify) {
        if (context == null || context.getWorld() == null || pos == null || incoming == null) {
            return false;
        }

        BlockEntity blockEntity = context.getWorld().getBlockEntity(pos);
        if (blockEntity == null) {
            return false;
        }

        NbtCompound current = extractBlockEntityNbt(blockEntity, context);
        NbtCompound target = merge && current != null ? mergeNbt(current.copy(), incoming) : incoming.copy();
        target.putInt("x", pos.getX());
        target.putInt("y", pos.getY());
        target.putInt("z", pos.getZ());

        boolean success = applyBlockEntityNbt(blockEntity, target, context);
        if (success) {
            blockEntity.markDirty();
            if (notify) {
                context.getWorld().updateListeners(pos, context.getWorld().getBlockState(pos), context.getWorld().getBlockState(pos), 3);
            }
        }
        return success;
    }

    static @Nullable NbtCompound extractBlockEntityNbt(BlockEntity blockEntity, @Nullable ExecutionContext context) {
        Object lookup = context != null && context.getWorld() != null
            ? context.getWorld().getRegistryManager()
            : null;
        Method[] methods = blockEntity.getClass().getMethods();
        for (Method method : methods) {
            if (!method.getName().startsWith("createNbt")) {
                continue;
            }
            try {
                if (method.getParameterCount() == 0) {
                    Object result = method.invoke(blockEntity);
                    if (result instanceof NbtCompound nbt) return nbt;
                } else if (method.getParameterCount() == 1 && lookup != null) {
                    Object result = method.invoke(blockEntity, lookup);
                    if (result instanceof NbtCompound nbt) return nbt;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    static boolean applyBlockEntityNbt(BlockEntity blockEntity, NbtCompound nbt, @Nullable ExecutionContext context) {
        Object lookup = context != null && context.getWorld() != null
            ? context.getWorld().getRegistryManager()
            : null;
        Method[] methods = blockEntity.getClass().getMethods();
        for (Method method : methods) {
            String name = method.getName();
            if (!"read".equals(name) && !"readNbt".equals(name)) {
                continue;
            }
            try {
                if (method.getParameterCount() == 1 && method.getParameterTypes()[0] == NbtCompound.class) {
                    method.invoke(blockEntity, nbt);
                    return true;
                }
                if (method.getParameterCount() == 2
                    && method.getParameterTypes()[0] == NbtCompound.class
                    && lookup != null) {
                    method.invoke(blockEntity, nbt, lookup);
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }
}

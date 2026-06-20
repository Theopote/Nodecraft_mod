package com.nodecraft.nodesystem.nodes.world.write;

import com.mojang.brigadier.StringReader;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;

final class WorldWriteNbtUtils {

    static final String INPUT_NBT_ID = "input_nbt";
    static final String INPUT_NBT_STRING_ID = "input_nbt_string";
    static final String INPUT_MERGE_NBT_ID = "input_merge_nbt";

    private WorldWriteNbtUtils() {
    }

    static @Nullable NbtCompound resolveIncomingNbt(Map<String, Object> inputValues) {
        if (inputValues.get(INPUT_NBT_ID) instanceof NbtCompound nbt) {
            return nbt.copy();
        }
        if (inputValues.get(INPUT_NBT_STRING_ID) instanceof String text && !text.isBlank()) {
            return parseSnbt(text);
        }
        return null;
    }

    static boolean hasNbtInput(Map<String, Object> inputValues) {
        Object nbt = inputValues.get(INPUT_NBT_ID);
        Object text = inputValues.get(INPUT_NBT_STRING_ID);
        return nbt instanceof NbtCompound || text instanceof String stringValue && !stringValue.isBlank();
    }

    static boolean mergeRequested(Map<String, Object> inputValues) {
        return inputValues.get(INPUT_MERGE_NBT_ID) instanceof Boolean value && value;
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

    static @Nullable NbtCompound extractBlockEntityNbt(BlockEntity blockEntity, ExecutionContext context) {
        Object lookup = context.getWorld() != null ? context.getWorld().getRegistryManager() : null;
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

    static boolean applyBlockEntityNbt(BlockEntity blockEntity, NbtCompound nbt, ExecutionContext context) {
        Object lookup = context.getWorld() != null ? context.getWorld().getRegistryManager() : null;
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

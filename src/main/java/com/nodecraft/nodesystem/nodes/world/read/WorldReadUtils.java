package com.nodecraft.nodesystem.nodes.world.read;

import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.Vector3;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

final class WorldReadUtils {

    static final int DEFAULT_MAX_BLOCKS = 100_000;
    static final int DEFAULT_MAX_COLUMNS = 65_536;
    static final int DEFAULT_MAX_NBT_STRING_LENGTH = 4096;
    static final int MAX_NBT_STRING_LENGTH = 65_536;

    private WorldReadUtils() {
    }

    static @Nullable BlockPos resolveBlockPos(Object value) {
        if (value instanceof BlockPos pos) {
            return pos.toImmutable();
        }
        if (value instanceof Vector3d vector) {
            return BlockPos.ofFloored(vector.x, vector.y, vector.z);
        }
        if (value instanceof Vector3 vector) {
            return BlockPos.ofFloored(vector.getX(), vector.getY(), vector.getZ());
        }
        return null;
    }

    static long volume(@Nullable RegionData region) {
        if (region == null || !region.isComplete()) {
            return 0L;
        }
        BlockPos min = region.getMinCorner();
        BlockPos max = region.getMaxCorner();
        if (min == null || max == null) {
            return 0L;
        }
        return (long) (max.getX() - min.getX() + 1)
            * (long) (max.getY() - min.getY() + 1)
            * (long) (max.getZ() - min.getZ() + 1);
    }

    static long columnCount(@Nullable RegionData region) {
        if (region == null || !region.isComplete()) {
            return 0L;
        }
        BlockPos min = region.getMinCorner();
        BlockPos max = region.getMaxCorner();
        if (min == null || max == null) {
            return 0L;
        }
        return (long) (max.getX() - min.getX() + 1) * (long) (max.getZ() - min.getZ() + 1);
    }

    static String blockId(BlockState state) {
        return Registries.BLOCK.getId(state.getBlock()).toString();
    }

    static @Nullable String resolveBlockId(Object target) {
        if (target instanceof BlockState state) {
            return blockId(state);
        }
        if (target instanceof String idString && !idString.isBlank()) {
            try {
                Identifier id = Identifier.of(idString.trim());
                if (Registries.BLOCK.containsId(id)) {
                    return id.toString();
                }
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    static boolean matchesBlock(BlockState state, Object target, boolean exactState) {
        if (target instanceof BlockState targetState) {
            return exactState ? state.equals(targetState) : state.isOf(targetState.getBlock());
        }
        String targetId = resolveBlockId(target);
        return targetId != null && blockId(state).equals(targetId);
    }

    static int resolveMaxListElements(@Nullable Object value) {
        int requested = value instanceof Number number ? Math.max(0, number.intValue()) : 0;
        if (requested <= 0) {
            return GenerationLimits.MAX_LIST_ELEMENTS;
        }
        return GenerationLimits.clampPositiveCount(requested);
    }

    static int resolveMaxStringLength(@Nullable Object value) {
        int requested = value instanceof Number number ? Math.max(0, number.intValue()) : 0;
        if (requested <= 0) {
            return DEFAULT_MAX_NBT_STRING_LENGTH;
        }
        return Math.min(requested, MAX_NBT_STRING_LENGTH);
    }

    static String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        int limit = maxLength <= 0 ? DEFAULT_MAX_NBT_STRING_LENGTH : Math.min(maxLength, MAX_NBT_STRING_LENGTH);
        if (value.length() <= limit) {
            return value;
        }
        return value.substring(0, limit) + "...";
    }
}

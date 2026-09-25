package com.nodecraft.nodesystem.nodes.world.write;

import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.BlockStateData;
import com.nodecraft.nodesystem.util.BlockStateResolver;
import com.nodecraft.nodesystem.util.Coordinate;
import com.nodecraft.nodesystem.util.Vector3;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.Map;
import java.util.Optional;

final class WorldWriteUtils {

    static final String INPUT_TRIGGER_ID = "input_trigger";
    static final String OUTPUT_VALID_ID = "output_valid";
    static final String OUTPUT_ERROR_ID = "output_error";

    private WorldWriteUtils() {
    }

    static boolean shouldRun(Map<String, Object> inputValues) {
        Object trigger = inputValues.get(INPUT_TRIGGER_ID);
        return !(trigger instanceof Boolean) || (Boolean) trigger;
    }

    static int resolveLimit(Object value, int fallback) {
        if (value instanceof Number number) {
            int resolved = number.intValue();
            return resolved <= 0 ? Integer.MAX_VALUE : resolved;
        }
        return Math.max(1, fallback);
    }

    static int flags(boolean notify) {
        return notify ? Block.NOTIFY_ALL : Block.FORCE_STATE;
    }

    static @Nullable BlockPos resolveBlockPos(Object value) {
        if (value instanceof BlockPos pos) {
            return pos.toImmutable();
        }
        if (value instanceof Coordinate coordinate) {
            return new BlockPos(coordinate.getX(), coordinate.getY(), coordinate.getZ());
        }
        if (value instanceof PointData pointData) {
            Vector3d position = pointData.getPosition();
            return BlockPos.ofFloored(position.x, position.y, position.z);
        }
        if (value instanceof Vector3d vector) {
            return BlockPos.ofFloored(vector.x, vector.y, vector.z);
        }
        if (value instanceof Vec3d vector) {
            return BlockPos.ofFloored(vector.x, vector.y, vector.z);
        }
        if (value instanceof Vector3 vector) {
            return BlockPos.ofFloored(vector.getX(), vector.getY(), vector.getZ());
        }
        return null;
    }

    static @Nullable BlockState resolveBlockState(Object value) {
        if (value instanceof BlockState blockState) {
            return blockState;
        }
        if (value instanceof String blockId && !blockId.isBlank()) {
            try {
                Identifier id = Identifier.of(blockId);
                Block block = Registries.BLOCK.get(id);
                return block.getDefaultState();
            } catch (Exception ignored) {
                return null;
            }
        }
        if (value instanceof BlockStateData stateData) {
            // Legacy graphs may still carry blockId inside state until V35 migration strips it.
            String blockId = stateData.get("blockId");
            if (blockId == null || blockId.isBlank()) {
                blockId = stateData.get("id");
            }
            if (blockId == null || blockId.isBlank()) {
                return null;
            }
            return BlockStateResolver.resolve(blockId, stateData);
        }
        return null;
    }

    static @Nullable BlockState resolveBlockState(@Nullable String blockId, @Nullable BlockStateData stateData) {
        return BlockStateResolver.resolve(blockId, stateData);
    }

    static boolean matches(BlockState current, BlockState target, boolean exactMatch) {
        if (current == null || target == null) {
            return false;
        }
        return exactMatch ? current.equals(target) : current.getBlock() == target.getBlock();
    }

    static long volume(RegionData region) {
        if (region == null || !region.isComplete()) {
            return 0L;
        }
        return volume(region.getMinCorner(), region.getMaxCorner());
    }

    static long volume(@Nullable BlockPos minCorner, @Nullable BlockPos maxCorner) {
        if (minCorner == null || maxCorner == null) {
            return 0L;
        }
        long width = (long) maxCorner.getX() - minCorner.getX() + 1L;
        long height = (long) maxCorner.getY() - minCorner.getY() + 1L;
        long depth = (long) maxCorner.getZ() - minCorner.getZ() + 1L;
        if (width <= 0L || height <= 0L || depth <= 0L) {
            return 0L;
        }
        return width * height * depth;
    }

    static BlockPosList dedupe(BlockPosList positions) {
        BlockPosList deduped = new BlockPosList();
        java.util.LinkedHashSet<BlockPos> seen = new java.util.LinkedHashSet<>();
        for (BlockPos pos : positions) {
            if (pos != null && seen.add(pos.toImmutable())) {
                deduped.add(pos.toImmutable());
            }
        }
        return deduped;
    }

    private static <T extends Comparable<T>> BlockState withProperty(BlockState state, String name, String rawValue) {
        Property<T> property = findProperty(state, name);
        if (property == null || rawValue == null) {
            return state;
        }
        Optional<T> parsed = property.parse(rawValue);
        return parsed.map(value -> state.with(property, value)).orElse(state);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> @Nullable Property<T> findProperty(BlockState state, String name) {
        for (Property<?> property : state.getProperties()) {
            if (property.getName().equals(name)) {
                return (Property<T>) property;
            }
        }
        return null;
    }
}

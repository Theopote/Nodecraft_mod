package com.nodecraft.nodesystem.nodes.world.write;

import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.datatypes.BlockInfoData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockListUtils;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.BlockStateData;
import com.nodecraft.nodesystem.util.BlockStateResolver;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import com.nodecraft.nodesystem.util.StrictIntegerUtils;
import com.nodecraft.nodesystem.world.WorldQueryAccess;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * World Write v1 shared input / budget / trigger helpers (strict typed, fail-closed).
 */
final class WorldWriteUtils {

    static final String INPUT_TRIGGER_ID = "input_trigger";
    static final String OUTPUT_VALID_ID = "output_valid";
    static final String OUTPUT_ERROR_ID = "output_error";
    static final String OUTPUT_COMPLETE_ID = "output_complete";
    static final String OUTPUT_HIT_LIMIT_ID = "output_hit_limit";
    static final String UNLOADED_CHUNK_ERROR = "Target chunk is not loaded";

    enum TriggerResult {
        RUN,
        SKIP,
        FAIL
    }

    private WorldWriteUtils() {
    }

    /**
     * Trigger contract: unconnected → property; connected true/false → that;
     * connected null/invalid → {@link TriggerResult#FAIL} (no world mutation).
     */
    static TriggerResult resolveWriteTrigger(BaseNode node, boolean propertyDefault) {
        if (!OptionalPortDrive.isConnected(node, INPUT_TRIGGER_ID)) {
            return propertyDefault ? TriggerResult.RUN : TriggerResult.SKIP;
        }
        Object value = node.getInput(INPUT_TRIGGER_ID);
        if (!(value instanceof Boolean bool)) {
            return TriggerResult.FAIL;
        }
        return bool ? TriggerResult.RUN : TriggerResult.SKIP;
    }

    /**
     * User budget exact INTEGER in {@code [1, hardCeiling]}. Connected invalid / out of range → null.
     */
    static @Nullable Integer resolveUserBudgetExactInteger(
        BaseNode node,
        String portId,
        int propertyFallback,
        int hardCeiling
    ) {
        Integer resolved;
        if (OptionalPortDrive.isConnected(node, portId)) {
            resolved = StrictIntegerUtils.requireExactInteger(node.getInput(portId));
        } else {
            Object raw = node.getInput(portId);
            if (raw == null) {
                resolved = propertyFallback;
            } else {
                resolved = StrictIntegerUtils.requireExactInteger(raw);
                if (resolved == null) {
                    return null;
                }
            }
        }
        if (resolved == null || resolved < 1 || resolved > hardCeiling) {
            return null;
        }
        return resolved;
    }

    static @Nullable Boolean resolveOptionalBoolean(BaseNode node, String portId, boolean propertyFallback) {
        return OptionalPortDrive.resolveOptionalBoolean(node, portId, propertyFallback);
    }

    static @Nullable Double resolveOptionalFiniteDouble(BaseNode node, String portId, double propertyFallback) {
        Double resolved = OptionalPortDrive.resolveOptionalDouble(node, portId, propertyFallback);
        if (resolved == null) {
            return OptionalPortDrive.isConnected(node, portId) ? null : (Double.isFinite(propertyFallback) ? propertyFallback : null);
        }
        return Double.isFinite(resolved) ? resolved : null;
    }

    /** Strict BLOCK_POS — rejects Point / Vector floor coercion. */
    static @Nullable BlockPos requireBlockPos(@Nullable Object value) {
        if (value instanceof BlockPos pos) {
            return pos.toImmutable();
        }
        return null;
    }

    static @Nullable List<BlockPos> requireBlockList(@Nullable Object value) {
        return BlockListUtils.resolveStrictBlockList(value);
    }

    static @Nullable BlockState resolveBlockState(Object value) {
        if (value instanceof BlockInfoData info) {
            if (info.blockId().isBlank()) {
                return null;
            }
            return BlockStateResolver.resolve(info.blockId(), info.stateProperties());
        }
        if (value instanceof BlockState blockState) {
            return blockState;
        }
        if (value instanceof String blockId && !blockId.isBlank()) {
            try {
                Identifier id = Identifier.of(blockId);
                if (!Registries.BLOCK.containsId(id)) {
                    return null;
                }
                Block block = Registries.BLOCK.get(id);
                return block.getDefaultState();
            } catch (Exception ignored) {
                return null;
            }
        }
        if (value instanceof BlockStateData stateData) {
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

    static int flags(boolean notify) {
        return notify ? Block.NOTIFY_ALL : Block.FORCE_STATE;
    }

    /**
     * Inclusive region volume. Returns {@code -1} on overflow / non-positive extent.
     */
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
            return -1L;
        }
        try {
            return Math.multiplyExact(Math.multiplyExact(width, height), depth);
        } catch (ArithmeticException e) {
            return -1L;
        }
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

    static BlockPosList toBlockPosList(@Nullable List<BlockPos> positions) {
        BlockPosList list = new BlockPosList();
        if (positions == null) {
            return list;
        }
        for (BlockPos pos : positions) {
            if (pos != null) {
                list.add(pos.toImmutable());
            }
        }
        return list;
    }

    static String worldKey(@Nullable World world) {
        if (world == null || world.getRegistryKey() == null || world.getRegistryKey().getValue() == null) {
            return "unknown";
        }
        return world.getRegistryKey().getValue().toString();
    }

    static boolean exceedsWriteBlockCap(long count) {
        return count < 0L || count > GenerationLimits.MAX_WORLD_WRITE_BLOCKS;
    }

    static boolean isChunkLoaded(@Nullable ExecutionContext context, @Nullable BlockPos pos) {
        if (context == null || context.getWorld() == null || pos == null) {
            return false;
        }
        return new WorldQueryAccess(context.getWorld()).isLoaded(pos);
    }
}

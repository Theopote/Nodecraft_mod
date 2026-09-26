package com.nodecraft.nodesystem.nodes.world.read;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import com.nodecraft.nodesystem.util.StrictIntegerUtils;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import org.jetbrains.annotations.Nullable;

final class WorldReadUtils {

    static final int DEFAULT_MAX_BLOCKS = 100_000;
    static final int DEFAULT_MAX_COLUMNS = 65_536;
    static final int DEFAULT_MAX_NBT_STRING_LENGTH = 4096;
    static final int MAX_NBT_STRING_LENGTH = 65_536;

    /** Sentinel: region span overflowed long arithmetic. */
    static final long OVERFLOW = -1L;

    private WorldReadUtils() {
    }

    /** Strict BLOCK_POS only — no Vector/POINT floor coercion. */
    static @Nullable BlockPos requireBlockPos(@Nullable Object value) {
        return value instanceof BlockPos pos ? pos.toImmutable() : null;
    }

    /**
     * Inclusive XYZ volume. Returns {@link #OVERFLOW} when any axis span overflows
     * or the product overflows {@code long}. Incomplete region → {@code 0}.
     */
    static long volume(@Nullable RegionData region) {
        if (region == null || !region.isComplete()) {
            return 0L;
        }
        BlockPos min = region.getMinCorner();
        BlockPos max = region.getMaxCorner();
        if (min == null || max == null) {
            return 0L;
        }
        long sizeX = axisSpan(min.getX(), max.getX());
        long sizeY = axisSpan(min.getY(), max.getY());
        long sizeZ = axisSpan(min.getZ(), max.getZ());
        if (sizeX < 0 || sizeY < 0 || sizeZ < 0) {
            return OVERFLOW;
        }
        return multiplyExactOrOverflow(sizeX, sizeY, sizeZ);
    }

    /**
     * Inclusive XZ column count. Returns {@link #OVERFLOW} on span/product overflow.
     */
    static long columnCount(@Nullable RegionData region) {
        if (region == null || !region.isComplete()) {
            return 0L;
        }
        BlockPos min = region.getMinCorner();
        BlockPos max = region.getMaxCorner();
        if (min == null || max == null) {
            return 0L;
        }
        long sizeX = axisSpan(min.getX(), max.getX());
        long sizeZ = axisSpan(min.getZ(), max.getZ());
        if (sizeX < 0 || sizeZ < 0) {
            return OVERFLOW;
        }
        return multiplyExactOrOverflow(sizeX, sizeZ);
    }

    static long axisSpan(int min, int max) {
        long span = (long) max - (long) min + 1L;
        return span <= 0L ? OVERFLOW : span;
    }

    private static long multiplyExactOrOverflow(long a, long b) {
        try {
            return Math.multiplyExact(a, b);
        } catch (ArithmeticException ignored) {
            return OVERFLOW;
        }
    }

    private static long multiplyExactOrOverflow(long a, long b, long c) {
        long ab = multiplyExactOrOverflow(a, b);
        if (ab == OVERFLOW) {
            return OVERFLOW;
        }
        return multiplyExactOrOverflow(ab, c);
    }

    /**
     * Next axis coordinate after {@code current + step}. Returns null when the
     * addition would overflow {@code int} or step past {@code maxInclusive}.
     */
    static @Nullable Integer nextAxisCoordinate(long current, int step, int maxInclusive) {
        if (step < 1) {
            return null;
        }
        long next = current + (long) step;
        if (next > maxInclusive || next > Integer.MAX_VALUE || next < Integer.MIN_VALUE) {
            return null;
        }
        return (int) next;
    }

    /**
     * Exact positive INTEGER budget: unconnected → {@code defaultWhenUnconnected};
     * connected exact Integer in {@code [1, hardCap]} → value;
     * otherwise null (fail closed). Does not clamp.
     */
    static @Nullable Integer resolveBoundedWorldReadCount(
            BaseNode node,
            String portId,
            int hardCap,
            int defaultWhenUnconnected
    ) {
        boolean connected = OptionalPortDrive.isConnected(node, portId);
        Object raw = node.getInput(portId);
        if (!connected) {
            if (raw == null) {
                return defaultWhenUnconnected <= hardCap ? defaultWhenUnconnected : null;
            }
            Integer exact = StrictIntegerUtils.requireExactInteger(raw);
            if (exact == null || exact < 1 || exact > hardCap) {
                return null;
            }
            return exact;
        }
        Integer exact = StrictIntegerUtils.requireExactInteger(raw);
        if (exact == null || exact < 1 || exact > hardCap) {
            return null;
        }
        return exact;
    }

    /**
     * Exact INTEGER ≥ 1 for step-like ports. Unconnected null → default;
     * connected invalid → null.
     */
    static @Nullable Integer resolveExactStep(
            BaseNode node,
            String portId,
            int defaultWhenUnconnected
    ) {
        boolean connected = OptionalPortDrive.isConnected(node, portId);
        Object raw = node.getInput(portId);
        if (!connected) {
            if (raw == null) {
                return defaultWhenUnconnected >= 1 ? defaultWhenUnconnected : null;
            }
            Integer exact = StrictIntegerUtils.requireExactInteger(raw);
            return exact != null && exact >= 1 ? exact : null;
        }
        Integer exact = StrictIntegerUtils.requireExactInteger(raw);
        return exact != null && exact >= 1 ? exact : null;
    }

    /**
     * Max SNBT string length: unconnected/null → default 4096;
     * connected exact Integer in {@code [1, MAX_NBT_STRING_LENGTH]} → value;
     * otherwise null (fail closed).
     */
    static @Nullable Integer resolveMaxStringLength(BaseNode node, String portId) {
        boolean connected = OptionalPortDrive.isConnected(node, portId);
        Object raw = node.getInput(portId);
        if (!connected) {
            if (raw == null) {
                return DEFAULT_MAX_NBT_STRING_LENGTH;
            }
            Integer exact = StrictIntegerUtils.requireExactInteger(raw);
            if (exact == null || exact < 1 || exact > MAX_NBT_STRING_LENGTH) {
                return null;
            }
            return exact;
        }
        Integer exact = StrictIntegerUtils.requireExactInteger(raw);
        if (exact == null || exact < 1 || exact > MAX_NBT_STRING_LENGTH) {
            return null;
        }
        return exact;
    }

    /**
     * Heightmap type: unconnected → property fallback; connected valid enum → override;
     * connected invalid → null (fail closed).
     */
    static @Nullable Heightmap.Type resolveHeightmapType(
            BaseNode node,
            String portId,
            @Nullable String propertyFallback
    ) {
        boolean connected = OptionalPortDrive.isConnected(node, portId);
        Object raw = node.getInput(portId);
        if (!connected) {
            String text = raw instanceof String s && !s.isBlank() ? s : propertyFallback;
            return parseHeightmapType(text);
        }
        if (!(raw instanceof String text) || text.isBlank()) {
            return null;
        }
        return parseHeightmapType(text);
    }

    static @Nullable Heightmap.Type parseHeightmapType(@Nullable String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Heightmap.Type.valueOf(text.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
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

    static boolean isConnected(@Nullable INode node, @Nullable String portId) {
        return OptionalPortDrive.isConnected(node, portId);
    }
}

package com.nodecraft.nodesystem.nodes.world.terrain;

import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import com.nodecraft.nodesystem.util.StrictIntegerUtils;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Shared Terrain Field v1 resolvers (connection-aware, exact integers, documented domains).
 */
final class TerrainNodeUtils {

    /** Local raster default: 64×64 columns, sample Y = 64. */
    static final int DEFAULT_MIN_X = -32;
    static final int DEFAULT_MAX_X = 31;
    static final int DEFAULT_MIN_Z = -32;
    static final int DEFAULT_MAX_Z = 31;
    static final int DEFAULT_MIN_Y = -64;
    static final int DEFAULT_MAX_Y = 320;
    static final int DEFAULT_BASE_Y = 64;

    /** Continental soft domain used when Region is unconnected on large-scale field nodes. */
    static final int CONTINENTAL_MIN_XZ = -4096;
    static final int CONTINENTAL_MAX_XZ = 4096;

    static final int DEFAULT_MAX_SAMPLES = 10_000;
    static final int DEFAULT_MAX_COLUMNS = 10_000;
    static final int DEFAULT_MAX_PLACEMENTS = 100_000;
    static final int DEFAULT_FILL_DEPTH = 8;

    private TerrainNodeUtils() {
    }

    /**
     * Optional exact INTEGER. Connected+invalid → null (fail closed).
     */
    static @Nullable Integer resolveOptionalExactInteger(
        BaseNode node,
        String portId,
        int propertyFallback
    ) {
        return OptionalPortDrive.resolveOptionalInteger(node, portId, propertyFallback);
    }

    /**
     * Exact INTEGER in {@code [min, max]} inclusive. Connected+invalid or out of range → null.
     */
    static @Nullable Integer resolveBoundedExactInteger(
        BaseNode node,
        String portId,
        int propertyFallback,
        int minInclusive,
        int maxInclusive
    ) {
        Integer resolved = resolveOptionalExactInteger(node, portId, propertyFallback);
        if (resolved == null) {
            return null;
        }
        if (resolved < minInclusive || resolved > maxInclusive) {
            return null;
        }
        return resolved;
    }

    /**
     * User budget capped by {@link GenerationLimits} hard safety ceiling.
     * Values above the hard ceiling → null (fail closed, never silent clamp).
     */
    static @Nullable Integer resolveUserBudgetExactInteger(
        BaseNode node,
        String portId,
        int propertyFallback,
        int hardCeiling
    ) {
        Integer resolved = resolveOptionalExactInteger(node, portId, propertyFallback);
        if (resolved == null) {
            return null;
        }
        if (resolved < 1 || resolved > hardCeiling) {
            return null;
        }
        return resolved;
    }

    static @Nullable Double resolveOptionalFiniteDouble(
        BaseNode node,
        String portId,
        double propertyFallback
    ) {
        return OptionalPortDrive.resolveOptionalDouble(node, portId, propertyFallback);
    }

    static @Nullable Boolean resolveOptionalBoolean(
        BaseNode node,
        String portId,
        boolean propertyFallback
    ) {
        return OptionalPortDrive.resolveOptionalBoolean(node, portId, propertyFallback);
    }

    static @Nullable String resolveOptionalString(
        BaseNode node,
        String portId,
        @Nullable String propertyFallback
    ) {
        return OptionalPortDrive.resolveOptionalString(node, portId, propertyFallback);
    }

    /**
     * Region resolution:
     * <ul>
     *   <li>unconnected → {@code null} (caller applies documented default domain)</li>
     *   <li>connected + complete → region</li>
     *   <li>connected + invalid/incomplete → sentinel fail ({@link #INVALID_REGION})</li>
     * </ul>
     */
    static final RegionData INVALID_REGION = new RegionData(null, null);

    static @Nullable RegionData resolveOptionalRegion(BaseNode node, String portId) {
        if (!OptionalPortDrive.isConnected(node, portId)) {
            Object value = node.getInput(portId);
            if (value == null) {
                return null;
            }
            // Unconnected with a stale value: treat as property/default path only if complete.
            if (value instanceof RegionData region && region.isComplete()) {
                return region;
            }
            return null;
        }
        Object value = node.getInput(portId);
        if (!(value instanceof RegionData region) || !region.isComplete()) {
            return INVALID_REGION;
        }
        return region;
    }

    static boolean isInvalidRegionMarker(@Nullable RegionData region) {
        return region == INVALID_REGION;
    }

    static TerrainGridDomain localDomainFromRegion(@Nullable RegionData region) {
        if (region == null || !region.isComplete()) {
            return TerrainGridDomain.localDefault();
        }
        BlockPos min = region.getMinCorner();
        BlockPos max = region.getMaxCorner();
        if (min == null || max == null) {
            return TerrainGridDomain.localDefault();
        }
        return TerrainGridDomain.of(min.getX(), max.getX(), min.getZ(), max.getZ(), DEFAULT_BASE_Y);
    }

    static TerrainGridDomain continentalDomainFromRegion(@Nullable RegionData region, int sampleY) {
        if (region == null || !region.isComplete()) {
            return TerrainGridDomain.of(
                CONTINENTAL_MIN_XZ,
                CONTINENTAL_MAX_XZ,
                CONTINENTAL_MIN_XZ,
                CONTINENTAL_MAX_XZ,
                sampleY
            );
        }
        BlockPos min = region.getMinCorner();
        BlockPos max = region.getMaxCorner();
        if (min == null || max == null) {
            return TerrainGridDomain.of(
                CONTINENTAL_MIN_XZ,
                CONTINENTAL_MAX_XZ,
                CONTINENTAL_MIN_XZ,
                CONTINENTAL_MAX_XZ,
                sampleY
            );
        }
        return TerrainGridDomain.of(min.getX(), max.getX(), min.getZ(), max.getZ(), sampleY);
    }

    static double clamp(double value, double min, double max) {
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }

    static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }

    /** Clamp normalized height field outputs to [-1, 1]. */
    static double clampNormalizedHeight(double value) {
        if (!Double.isFinite(value)) {
            return Double.NaN;
        }
        return clamp(value, -1.0d, 1.0d);
    }

    static boolean isFinite(double value) {
        return Double.isFinite(value);
    }

    /**
     * Exact integer from raw port value (no connection awareness). Prefer
     * {@link #resolveOptionalExactInteger} for optional drives.
     */
    static @Nullable Integer requireExactInteger(@Nullable Object value) {
        return StrictIntegerUtils.requireExactInteger(value);
    }

    static long sampledCount(int minA, int maxA, int minB, int maxB, int step) {
        int stride = Math.max(1, step);
        long countA = ((long) maxA - minA) / stride + 1L;
        long countB = ((long) maxB - minB) / stride + 1L;
        if (countA < 0L || countB < 0L) {
            return -1L;
        }
        try {
            return Math.multiplyExact(countA, countB);
        } catch (ArithmeticException e) {
            return -1L;
        }
    }
}

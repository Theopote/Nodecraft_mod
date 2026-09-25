package com.nodecraft.nodesystem.nodes.material.surface_aging;

import com.nodecraft.nodesystem.math.RandomOps;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.MaterialMappingSupport;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared helpers for topology-based surface aging (Surface Aging v1).
 */
public final class SurfaceAgingUtils {

    public record Validation(boolean valid, String message) {
        public static Validation ok() {
            return new Validation(true, "");
        }

        public static Validation fail(String message) {
            return new Validation(false, message == null ? "" : message);
        }
    }

    public record OriginResult(boolean valid, BlockPos origin, String error) {
        public static OriginResult ok(BlockPos origin) {
            return new OriginResult(true, origin != null ? origin : WORLD_ORIGIN, "");
        }

        public static OriginResult fail(String error) {
            return new OriginResult(false, WORLD_ORIGIN, error == null ? "" : error);
        }
    }

    public record SampleResult(boolean valid, double sample, String error) {
        public static SampleResult ok(double sample) {
            return new SampleResult(true, sample, "");
        }

        public static SampleResult fail(String error) {
            return new SampleResult(false, Double.NaN, error == null ? "" : error);
        }
    }

    private static final BlockPos WORLD_ORIGIN = new BlockPos(0, 0, 0);

    private SurfaceAgingUtils() {
    }

    public static @Nullable String optionalRole(@Nullable Object value) {
        return MaterialMappingSupport.optionalBlockType(value);
    }

    public static String pickRole(@Nullable String mapped, @Nullable String sourceBlockId) {
        return MaterialMappingSupport.resolveMaterialTarget(mapped, sourceBlockId);
    }

    public static OriginResult resolveAgingOrigin(@Nullable Object value) {
        if (value == null) {
            return OriginResult.ok(WORLD_ORIGIN);
        }
        if (value instanceof BlockPos pos) {
            return OriginResult.ok(pos);
        }
        return OriginResult.fail("Aging Origin must be BLOCK_POS");
    }

    public static Validation requireAmount01(double amount) {
        if (!Double.isFinite(amount)) {
            return Validation.fail("Amount must be finite");
        }
        if (amount < 0.0d || amount > 1.0d) {
            return Validation.fail("Amount must be within [0, 1]");
        }
        return Validation.ok();
    }

    public static Set<BlockPos> buildOccupancy(List<BlockPlacementData> sources) {
        Set<BlockPos> set = new HashSet<>();
        if (sources == null) {
            return set;
        }
        for (BlockPlacementData source : sources) {
            if (source != null && source.pos() != null) {
                set.add(source.pos().toImmutable());
            }
        }
        return set;
    }

    /** True when any of the six axis neighbors is missing from the occupancy set. */
    public static boolean isSurface(BlockPos pos, Set<BlockPos> occupancy) {
        return !occupancy.contains(pos.up())
            || !occupancy.contains(pos.down())
            || !occupancy.contains(pos.north())
            || !occupancy.contains(pos.south())
            || !occupancy.contains(pos.east())
            || !occupancy.contains(pos.west());
    }

    /** True when the voxel above is missing from the occupancy set. */
    public static boolean isTopExposed(BlockPos pos, Set<BlockPos> occupancy) {
        return !occupancy.contains(pos.up());
    }

    /**
     * Deterministic aging sample in {@code [0,1]} from {@link RandomOps#valueNoise3}.
     * Non-finite noise → fail.
     */
    public static SampleResult agingSample(int dx, int dy, int dz, int seed) {
        double noise = RandomOps.valueNoise3(dx, dy, dz, seed);
        if (!Double.isFinite(noise)) {
            return SampleResult.fail("Aging sample produced a non-finite value");
        }
        double sample = (noise + 1.0d) * 0.5d;
        if (!Double.isFinite(sample)) {
            return SampleResult.fail("Aging sample produced a non-finite value");
        }
        return SampleResult.ok(Math.max(0.0d, Math.min(1.0d, sample)));
    }

    public static boolean shouldAge(double sample, double amount) {
        if (amount <= 0.0d) {
            return false;
        }
        if (amount >= 1.0d) {
            return true;
        }
        return sample < amount;
    }

    public static int relativeX(BlockPos pos, BlockPos origin) {
        return pos.getX() - origin.getX();
    }

    public static int relativeY(BlockPos pos, BlockPos origin) {
        return pos.getY() - origin.getY();
    }

    public static int relativeZ(BlockPos pos, BlockPos origin) {
        return pos.getZ() - origin.getZ();
    }

    public static boolean hasNonPlacementSource(
            @Nullable Object coordinates,
            @Nullable Object geometry,
            @Nullable Object box,
            @Nullable Object cylinder,
            @Nullable Object sphere,
            @Nullable Object torus
    ) {
        return coordinates != null
            || geometry != null
            || box != null
            || cylinder != null
            || sphere != null
            || torus != null;
    }

    public static Map<String, Object> failResult(String error) {
        Map<String, Object> out = new HashMap<>();
        out.put("output_placements", List.of());
        out.put("output_valid", false);
        out.put("output_error", error == null ? "" : error);
        out.put("output_affected_count", 0);
        return out;
    }

    public static Map<String, Object> okResult(List<BlockPlacementData> placements, int affectedCount) {
        Map<String, Object> out = new HashMap<>();
        out.put("output_placements", placements != null ? placements : List.of());
        out.put("output_valid", true);
        out.put("output_error", "");
        out.put("output_affected_count", affectedCount);
        return out;
    }
}

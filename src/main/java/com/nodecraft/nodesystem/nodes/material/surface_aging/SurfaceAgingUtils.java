package com.nodecraft.nodesystem.nodes.material.surface_aging;

import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.math.RandomOps;
import com.nodecraft.nodesystem.nodes.material.gradient_mapping.GradientMaterialUtils;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.MaterialMappingSupport;
import com.nodecraft.nodesystem.util.MaterialSourceResolver;
import com.nodecraft.nodesystem.util.MaterialSpatialUtils;
import com.nodecraft.nodesystem.util.RandomInputResolver;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared helpers for topology-based surface aging (Surface Aging v2).
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

    /** Mix into user Seed so Weathering / Moss / Cracks do not share one noise field. */
    public static final int WEATHERING_NOISE_SALT = 0x57454154;
    public static final int MOSS_NOISE_SALT = 0x4D4F5353;
    public static final int CRACK_NOISE_SALT = 0x4352414B;

    private SurfaceAgingUtils() {
    }

    public static String pickRole(@Nullable String mapped, @Nullable String sourceBlockId) {
        return MaterialMappingSupport.resolveMaterialTarget(mapped, sourceBlockId);
    }

    public static RandomInputResolver.IntegerResolveResult resolveSeed(BaseNode node, String portId) {
        return RandomInputResolver.resolveSeed(
            node.getInput(portId),
            MaterialSourceResolver.isDriven(node, portId)
        );
    }

    /**
     * Connection-aware Aging Origin: undriven → (0,0,0); driven + BlockPos → use;
     * driven + null / wrong type → fail.
     */
    public static OriginResult resolveAgingOrigin(BaseNode node, String portId) {
        if (!MaterialSourceResolver.isDriven(node, portId)) {
            return OriginResult.ok(WORLD_ORIGIN);
        }
        Object value = node.getInput(portId);
        if (value instanceof BlockPos pos) {
            return OriginResult.ok(pos);
        }
        return OriginResult.fail("Aging Origin must be BLOCK_POS");
    }

    public static GradientMaterialUtils.OptionalDoubleResult resolveAmount(
            BaseNode node,
            String portId,
            double fallback,
            String label
    ) {
        return GradientMaterialUtils.resolveOptionalStrictDouble(node, portId, fallback, label);
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

    public static Validation requireUniquePositions(List<BlockPlacementData> sources) {
        if (sources == null || sources.isEmpty()) {
            return Validation.ok();
        }
        Set<BlockPos> seen = new HashSet<>();
        for (BlockPlacementData source : sources) {
            if (source == null || source.pos() == null) {
                continue;
            }
            BlockPos key = source.pos().toImmutable();
            if (!seen.add(key)) {
                return Validation.fail("Duplicate block position: " + key.getX()
                    + "," + key.getY() + "," + key.getZ());
            }
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
        long x = pos.getX();
        long y = pos.getY();
        long z = pos.getZ();
        return !occupancyContains(occupancy, x, y + 1L, z)
            || !occupancyContains(occupancy, x, y - 1L, z)
            || !occupancyContains(occupancy, x, y, z - 1L)
            || !occupancyContains(occupancy, x, y, z + 1L)
            || !occupancyContains(occupancy, x + 1L, y, z)
            || !occupancyContains(occupancy, x - 1L, y, z);
    }

    /** True when the voxel above is missing from the occupancy set. */
    public static boolean isTopExposed(BlockPos pos, Set<BlockPos> occupancy) {
        return !occupancyContains(occupancy, pos.getX(), (long) pos.getY() + 1L, pos.getZ());
    }

    /**
     * Neighbor lookup in long so {@code Integer.MAX_VALUE + 1} is not wrapped through
     * {@link BlockPos#offset}. Coordinates outside {@code int} are treated as missing (exposed).
     */
    static boolean occupancyContains(Set<BlockPos> occupancy, long x, long y, long z) {
        if (x < Integer.MIN_VALUE || x > Integer.MAX_VALUE
            || y < Integer.MIN_VALUE || y > Integer.MAX_VALUE
            || z < Integer.MIN_VALUE || z > Integer.MAX_VALUE) {
            return false;
        }
        return occupancy.contains(new BlockPos((int) x, (int) y, (int) z));
    }

    /**
     * Deterministic aging sample in {@code [0,1]} from {@link RandomOps#valueNoise3}.
     * Non-finite noise → fail. {@code nodeSalt} decorrelates process families; user seed still
     * fully determines repeatability.
     */
    public static SampleResult agingSample(double dx, double dy, double dz, int seed, int nodeSalt) {
        double noise = RandomOps.valueNoise3(dx, dy, dz, seed ^ nodeSalt);
        if (!Double.isFinite(noise)) {
            return SampleResult.fail("Aging sample produced a non-finite value");
        }
        double sample = (noise + 1.0d) * 0.5d;
        if (!Double.isFinite(sample)) {
            return SampleResult.fail("Aging sample produced a non-finite value");
        }
        return SampleResult.ok(Math.max(0.0d, Math.min(1.0d, sample)));
    }

    public static SampleResult agingSample(MaterialSpatialUtils.Relative relative, int seed, int nodeSalt) {
        return agingSample(
            (double) relative.dx(),
            (double) relative.dy(),
            (double) relative.dz(),
            seed,
            nodeSalt
        );
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

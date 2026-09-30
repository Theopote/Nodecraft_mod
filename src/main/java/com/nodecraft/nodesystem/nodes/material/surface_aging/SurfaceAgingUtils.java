package com.nodecraft.nodesystem.nodes.material.surface_aging;

import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.math.RandomOps;
import com.nodecraft.nodesystem.nodes.material.gradient_mapping.GradientMaterialUtils;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.MaterialMappingSupport;
import com.nodecraft.nodesystem.util.MaterialSourceResolver;
import com.nodecraft.nodesystem.util.MaterialSpatialUtils;
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

    private SurfaceAgingUtils() {
    }

    public static @Nullable String optionalRole(@Nullable Object value) {
        return MaterialMappingSupport.optionalBlockType(value);
    }

    public static String pickRole(@Nullable String mapped, @Nullable String sourceBlockId) {
        return MaterialMappingSupport.resolveMaterialTarget(mapped, sourceBlockId);
    }

    /**
     * Type-only origin resolve (legacy). Prefer {@link #resolveAgingOrigin(BaseNode, String)}.
     */
    public static OriginResult resolveAgingOrigin(@Nullable Object value) {
        if (value == null) {
            return OriginResult.ok(WORLD_ORIGIN);
        }
        if (value instanceof BlockPos pos) {
            return OriginResult.ok(pos);
        }
        return OriginResult.fail("Aging Origin must be BLOCK_POS");
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
    public static SampleResult agingSample(double dx, double dy, double dz, int seed) {
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

    public static SampleResult agingSample(MaterialSpatialUtils.Relative relative, int seed) {
        return agingSample(
            (double) relative.dx(),
            (double) relative.dy(),
            (double) relative.dz(),
            seed
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

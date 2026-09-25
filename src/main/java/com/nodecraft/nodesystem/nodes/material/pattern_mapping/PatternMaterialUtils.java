package com.nodecraft.nodesystem.nodes.material.pattern_mapping;

import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.MaterialMappingSupport;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared helpers for role-based pattern material mapping (Pattern Mapping v1).
 */
public final class PatternMaterialUtils {

    public record Validation(boolean valid, String message) {
        public static Validation ok() {
            return new Validation(true, "");
        }

        public static Validation fail(String message) {
            return new Validation(false, message == null ? "" : message);
        }
    }

    public record Relative(int dx, int dy, int dz) {
    }

    private static final BlockPos WORLD_ORIGIN = new BlockPos(0, 0, 0);

    /**
     * Result of resolving Pattern Origin: missing → world origin; {@link BlockPos} → use it;
     * any other runtime type → invalid.
     */
    public record OriginResult(boolean valid, BlockPos origin, String error) {
        public static OriginResult ok(BlockPos origin) {
            return new OriginResult(true, origin != null ? origin : WORLD_ORIGIN, "");
        }

        public static OriginResult fail(String error) {
            return new OriginResult(false, WORLD_ORIGIN, error == null ? "" : error);
        }
    }

    private PatternMaterialUtils() {
    }

    public static @Nullable String optionalRole(@Nullable Object value) {
        return MaterialMappingSupport.optionalBlockType(value);
    }

    public static String pickRole(@Nullable String mapped, @Nullable String sourceBlockId) {
        return MaterialMappingSupport.resolveMaterialTarget(mapped, sourceBlockId);
    }

    /**
     * Resolves Pattern Origin.
     * <ul>
     *   <li>{@code null} (missing) → {@code (0,0,0)}, valid</li>
     *   <li>{@link BlockPos} → that position, valid</li>
     *   <li>any other type → invalid ({@code BLOCK_POS} only)</li>
     * </ul>
     */
    public static OriginResult resolveOrigin(@Nullable Object value) {
        if (value == null) {
            return OriginResult.ok(WORLD_ORIGIN);
        }
        if (value instanceof BlockPos pos) {
            return OriginResult.ok(pos);
        }
        return OriginResult.fail("Pattern Origin must be BLOCK_POS");
    }

    public static Relative relative(BlockPos pos, BlockPos origin) {
        BlockPos o = origin != null ? origin : WORLD_ORIGIN;
        return new Relative(
            pos.getX() - o.getX(),
            pos.getY() - o.getY(),
            pos.getZ() - o.getZ()
        );
    }

    public static Validation requirePositiveInt(int value, String name) {
        if (value < 1) {
            return Validation.fail(name + " must be at least 1");
        }
        return Validation.ok();
    }

    public static Validation requireLineWidth(int lineWidth, int gridSize) {
        Validation sizeOk = requirePositiveInt(gridSize, "Grid Size");
        if (!sizeOk.valid()) {
            return sizeOk;
        }
        if (lineWidth < 1 || lineWidth > gridSize) {
            return Validation.fail("Line Width must satisfy 1 ≤ Line Width ≤ Grid Size");
        }
        return Validation.ok();
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
        return out;
    }

    public static Map<String, Object> okResult(List<BlockPlacementData> placements) {
        Map<String, Object> out = new HashMap<>();
        out.put("output_placements", placements != null ? placements : List.of());
        out.put("output_valid", true);
        out.put("output_error", "");
        return out;
    }
}

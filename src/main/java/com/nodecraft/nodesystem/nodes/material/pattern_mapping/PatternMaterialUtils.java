package com.nodecraft.nodesystem.nodes.material.pattern_mapping;

import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.MaterialMappingSupport;
import com.nodecraft.nodesystem.util.MaterialSourceResolver;
import com.nodecraft.nodesystem.util.MaterialSpatialUtils;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared helpers for role-based pattern material mapping (Pattern Mapping v2).
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

    public record Relative(long dx, long dy, long dz) {
    }

    private static final BlockPos WORLD_ORIGIN = new BlockPos(0, 0, 0);

    /**
     * Result of resolving Pattern Origin: undriven → world origin; {@link BlockPos} → use it;
     * driven but invalid → fail.
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

    public static MaterialMappingSupport.MappedBlockType resolveRole(BaseNode node, String portId) {
        return MaterialMappingSupport.requireKnownBlockType(
            node.getInput(portId),
            MaterialSourceResolver.isDriven(node, portId)
        );
    }

    public static String pickRole(@Nullable String mapped, @Nullable String sourceBlockId) {
        return MaterialMappingSupport.resolveMaterialTarget(mapped, sourceBlockId);
    }

    /**
     * Connection-aware Pattern Origin: undriven → (0,0,0); driven + BlockPos → use;
     * driven + null / wrong type → fail.
     */
    public static OriginResult resolveOrigin(BaseNode node, String portId) {
        if (!MaterialSourceResolver.isDriven(node, portId)) {
            return OriginResult.ok(WORLD_ORIGIN);
        }
        Object value = node.getInput(portId);
        if (value instanceof BlockPos pos) {
            return OriginResult.ok(pos);
        }
        return OriginResult.fail("Pattern Origin must be BLOCK_POS");
    }

    public static Relative relative(BlockPos pos, BlockPos origin) {
        MaterialSpatialUtils.Relative rel = MaterialSpatialUtils.relative(pos, origin);
        return new Relative(rel.dx(), rel.dy(), rel.dz());
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

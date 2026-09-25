package com.nodecraft.nodesystem.nodes.material.gradient_mapping;

import com.nodecraft.nodesystem.util.BlockPaletteData;
import com.nodecraft.nodesystem.util.BlockPaletteEntry;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.MaterialMappingSupport;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared helpers for scalar-driven material mapping (Gradient Mapping v1).
 */
public final class GradientMaterialUtils {

    public record Validation(boolean valid, String message) {
        public static Validation ok() {
            return new Validation(true, "");
        }

        public static Validation fail(String message) {
            return new Validation(false, message == null ? "" : message);
        }
    }

    private GradientMaterialUtils() {
    }

    public static BlockPaletteData resolvePalette(@Nullable Object value) {
        return BlockPaletteData.requireTyped(value);
    }

    /**
     * Maps normalized {@code t} in {@code [0,1]} to a palette block id; blank/empty → preserve source.
     */
    public static String pickByNormalized(
            BlockPaletteData palette,
            double t,
            @Nullable String sourceBlockId
    ) {
        String preserved = MaterialMappingSupport.resolveMaterialTarget(null, sourceBlockId);
        if (palette == null || palette.isEmpty()) {
            return preserved;
        }
        double clamped = clamp01(t);
        int size = palette.size();
        int index = Math.min(size - 1, Math.max(0, (int) Math.floor(clamped * size)));
        BlockPaletteEntry entry = palette.entries().get(index);
        String blockId = entry != null ? entry.blockId() : null;
        if (blockId == null || blockId.isBlank()) {
            return preserved;
        }
        return blockId;
    }

    public static Validation requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            return Validation.fail(name + " must be finite");
        }
        return Validation.ok();
    }

    public static Validation requirePositiveWidth(double min, double max) {
        Validation minOk = requireFinite(min, "Min");
        if (!minOk.valid()) {
            return minOk;
        }
        Validation maxOk = requireFinite(max, "Max");
        if (!maxOk.valid()) {
            return maxOk;
        }
        if (!(min < max)) {
            return Validation.fail("Domain width must be positive");
        }
        return Validation.ok();
    }

    public static Validation requirePositive(double value, String name) {
        Validation finite = requireFinite(value, name);
        if (!finite.valid()) {
            return finite;
        }
        if (!(value > 0.0d)) {
            return Validation.fail(name + " must be greater than 0");
        }
        return Validation.ok();
    }

    public static double clamp01(double value) {
        if (!Double.isFinite(value)) {
            return Double.NaN;
        }
        return Math.max(0.0d, Math.min(1.0d, value));
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

    public static Map<String, Object> failResult(String error, String... diagnosticKeys) {
        Map<String, Object> out = new HashMap<>();
        out.put("output_placements", List.of());
        out.put("output_valid", false);
        out.put("output_error", error == null ? "" : error);
        if (diagnosticKeys != null) {
            for (String key : diagnosticKeys) {
                out.put(key, List.of());
            }
        }
        return out;
    }

    public static Map<String, Object> okResult(List<BlockPlacementData> placements) {
        Map<String, Object> out = new HashMap<>();
        out.put("output_placements", placements != null ? placements : List.of());
        out.put("output_valid", true);
        out.put("output_error", "");
        return out;
    }

    public static List<BlockPlacementData> remapAll(
            List<BlockPlacementData> sources,
            java.util.function.Function<BlockPlacementData, String> blockIdFor
    ) {
        List<BlockPlacementData> out = new ArrayList<>(sources.size());
        for (BlockPlacementData source : sources) {
            if (source == null || source.pos() == null) {
                continue;
            }
            String blockId = blockIdFor.apply(source);
            out.add(MaterialMappingSupport.remapBlockId(source, blockId));
        }
        return out;
    }
}

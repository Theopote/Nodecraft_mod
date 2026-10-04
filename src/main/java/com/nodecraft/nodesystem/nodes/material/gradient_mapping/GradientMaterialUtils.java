package com.nodecraft.nodesystem.nodes.material.gradient_mapping;

import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.util.BlockPaletteData;
import com.nodecraft.nodesystem.util.BlockPaletteEntry;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.MaterialMappingSupport;
import com.nodecraft.nodesystem.util.MaterialSourceResolver;
import com.nodecraft.nodesystem.util.StrictDoubleUtils;
import org.jetbrains.annotations.Nullable;

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

    public record OptionalPaletteResult(boolean valid, BlockPaletteData palette, String error) {
        public static OptionalPaletteResult ok(BlockPaletteData palette) {
            return new OptionalPaletteResult(true, palette != null ? palette : BlockPaletteData.empty(), "");
        }

        public static OptionalPaletteResult fail(String error) {
            return new OptionalPaletteResult(false, BlockPaletteData.empty(), error == null ? "" : error);
        }
    }

    /**
     * Driven-aware palette: undriven → empty; connected {@link BlockPaletteData} → use;
     * connected null/wrong type → fail closed.
     */
    public static OptionalPaletteResult resolveOptionalPalette(BaseNode node, String portId) {
        if (!MaterialSourceResolver.isDriven(node, portId)) {
            return OptionalPaletteResult.ok(BlockPaletteData.empty());
        }
        Object value = node.getInput(portId);
        if (value instanceof BlockPaletteData palette) {
            return OptionalPaletteResult.ok(palette);
        }
        return OptionalPaletteResult.fail("Palette must be a BLOCK_PALETTE");
    }

    public record PickResult(boolean valid, @Nullable String blockId, String error) {
        public static PickResult ok(@Nullable String blockId) {
            return new PickResult(true, blockId, "");
        }

        public static PickResult fail(String error) {
            return new PickResult(false, null, error == null ? "" : error);
        }
    }

    /**
     * Maps finite normalized {@code t} in {@code [0,1]} to a palette block id; blank/empty → preserve source.
     * Non-finite {@code t} fails closed (never maps NaN to index 0).
     */
    public static PickResult pickByNormalized(
            BlockPaletteData palette,
            double t,
            @Nullable String sourceBlockId
    ) {
        if (!Double.isFinite(t)) {
            return PickResult.fail("Palette parameter must be finite");
        }
        String preserved = MaterialMappingSupport.resolveMaterialTarget(null, sourceBlockId);
        if (palette == null || palette.isEmpty()) {
            return PickResult.ok(preserved);
        }
        double clamped = clamp01(t);
        if (!Double.isFinite(clamped)) {
            return PickResult.fail("Palette parameter must be finite");
        }
        int size = palette.size();
        int index = Math.min(size - 1, Math.max(0, (int) Math.floor(clamped * size)));
        BlockPaletteEntry entry = palette.entries().get(index);
        String blockId = entry != null ? entry.blockId() : null;
        if (blockId == null || blockId.isBlank()) {
            return PickResult.ok(preserved);
        }
        return PickResult.ok(blockId);
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
        double span = max - min;
        if (!Double.isFinite(span) || span <= 0.0d) {
            return Validation.fail("Domain must have a finite positive width");
        }
        return Validation.ok();
    }

    /**
     * Euclidean distance domain: {@code 0 <= min < max} with a finite span.
     */
    public static Validation requireNonNegativeDistanceDomain(double min, double max) {
        Validation minOk = requireFinite(min, "Min Distance");
        if (!minOk.valid()) {
            return minOk;
        }
        if (min < 0.0d) {
            return Validation.fail("Min Distance must be >= 0");
        }
        Validation maxOk = requireFinite(max, "Max Distance");
        if (!maxOk.valid()) {
            return maxOk;
        }
        if (!(min < max)) {
            return Validation.fail("Min Distance must be less than Max Distance");
        }
        double span = max - min;
        if (!Double.isFinite(span)) {
            return Validation.fail("Distance domain must have a finite positive width");
        }
        return Validation.ok();
    }

    /**
     * Driven-aware optional DOUBLE: undriven → fallback; driven → exact finite {@link Double} or fail.
     */
    public record OptionalDoubleResult(boolean valid, double value, String error) {
        public static OptionalDoubleResult ok(double value) {
            return new OptionalDoubleResult(true, value, "");
        }

        public static OptionalDoubleResult fail(String error) {
            return new OptionalDoubleResult(false, Double.NaN, error == null ? "" : error);
        }
    }

    public static OptionalDoubleResult resolveOptionalStrictDouble(
            BaseNode node,
            String portId,
            double fallback,
            String label
    ) {
        if (!MaterialSourceResolver.isDriven(node, portId)) {
            return OptionalDoubleResult.ok(fallback);
        }
        Double resolved = StrictDoubleUtils.requireExactFiniteDouble(node.getInput(portId));
        if (resolved == null) {
            return OptionalDoubleResult.fail(label + " must be a finite Double");
        }
        return OptionalDoubleResult.ok(resolved);
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

    public static MaterialMappingSupport.RemapListResult remapAll(
            List<BlockPlacementData> sources,
            java.util.function.Function<BlockPlacementData, String> blockIdFor
    ) {
        return MaterialMappingSupport.remapAllValidated(sources, blockIdFor);
    }
}

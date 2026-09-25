package com.nodecraft.nodesystem.nodes.material.basic_assignment;

import com.nodecraft.nodesystem.math.RandomOps;
import com.nodecraft.nodesystem.util.BlockPaletteData;
import com.nodecraft.nodesystem.util.BlockPaletteEntry;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.MaterialMappingSupport;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Shared helpers for Material / Basic Assignment v1.
 */
public final class BasicAssignmentUtils {

    public record Validation(boolean valid, String message) {
        public static Validation ok() {
            return new Validation(true, "");
        }

        public static Validation fail(String message) {
            return new Validation(false, message == null ? "" : message);
        }
    }

    public record ParseResult<T>(boolean valid, List<T> values, String error) {
        public static <T> ParseResult<T> ok(List<T> values) {
            return new ParseResult<>(true, values != null ? values : List.of(), "");
        }

        public static <T> ParseResult<T> fail(String error) {
            return new ParseResult<>(false, List.of(), error == null ? "" : error);
        }
    }

    public record IndexResult(boolean valid, int index, String error) {
        public static IndexResult ok(int index) {
            return new IndexResult(true, index, "");
        }

        public static IndexResult fail(String error) {
            return new IndexResult(false, 0, error == null ? "" : error);
        }
    }

    private BasicAssignmentUtils() {
    }

    public static Validation requireBlockType(@Nullable Object value) {
        String blockType = MaterialMappingSupport.optionalBlockType(value);
        if (blockType == null) {
            return Validation.fail("Block Type required");
        }
        return Validation.ok();
    }

    public static ParseResult<String> parseStringList(@Nullable Object value, String portName) {
        if (value == null) {
            return ParseResult.ok(List.of());
        }
        if (!(value instanceof List<?> list)) {
            return ParseResult.fail(portName + " must be a STRING_LIST");
        }
        List<String> out = new ArrayList<>(list.size());
        for (Object entry : list) {
            if (!(entry instanceof String text)) {
                return ParseResult.fail(portName + " must contain only strings");
            }
            if (!text.isBlank()) {
                out.add(text.trim().toLowerCase(Locale.ROOT));
            }
        }
        return ParseResult.ok(out);
    }

    public static ParseResult<Double> parseDoubleList(@Nullable Object value, String portName) {
        if (value == null) {
            return ParseResult.fail(portName + " must be a DOUBLE_LIST");
        }
        if (!(value instanceof List<?> list)) {
            return ParseResult.fail(portName + " must be a DOUBLE_LIST");
        }
        List<Double> out = new ArrayList<>(list.size());
        for (Object entry : list) {
            if (!(entry instanceof Number number)) {
                return ParseResult.fail(portName + " must contain only numbers");
            }
            double weight = number.doubleValue();
            Validation weightOk = validatePaletteEntryWeight(weight);
            if (!weightOk.valid()) {
                return ParseResult.fail(weightOk.message());
            }
            out.add(weight);
        }
        return ParseResult.ok(out);
    }

    public static Validation validatePaletteEntryWeight(double weight) {
        if (!Double.isFinite(weight)) {
            return Validation.fail("Weight must be finite");
        }
        if (weight < 0.0d) {
            return Validation.fail("Weight must be >= 0");
        }
        return Validation.ok();
    }

    public static Validation validateWeights(List<Double> weights, int blockCount) {
        if (weights.size() != blockCount) {
            return Validation.fail("Weights count must match block count");
        }
        double sum = 0.0d;
        for (double weight : weights) {
            Validation entryOk = validatePaletteEntryWeight(weight);
            if (!entryOk.valid()) {
                return entryOk;
            }
            sum += weight;
        }
        if (sum <= 0.0d) {
            return Validation.fail("Total weight must be > 0");
        }
        return Validation.ok();
    }

    public static Validation validatePaletteWeights(BlockPaletteData palette) {
        if (palette == null || palette.isEmpty()) {
            return Validation.ok();
        }
        double sum = 0.0d;
        for (BlockPaletteEntry entry : palette.entries()) {
            Validation entryOk = validatePaletteEntryWeight(entry.weight());
            if (!entryOk.valid()) {
                return entryOk;
            }
            sum += entry.weight();
        }
        if (sum <= 0.0d) {
            return Validation.fail("Total palette weight must be > 0");
        }
        return Validation.ok();
    }

    public static BlockPaletteData buildPalette(List<String> blockIds, @Nullable List<Double> weights) {
        List<BlockPaletteEntry> entries = new ArrayList<>(blockIds.size());
        for (int i = 0; i < blockIds.size(); i++) {
            String blockId = blockIds.get(i);
            if (blockId == null || blockId.isBlank()) {
                continue;
            }
            double weight = weights != null ? weights.get(i) : 1.0d;
            entries.add(new BlockPaletteEntry(blockId, weight));
        }
        return new BlockPaletteData(entries);
    }

    public static IndexResult resolveStartIndex(@Nullable Object value, int defaultIndex) {
        if (value == null) {
            return IndexResult.ok(defaultIndex);
        }
        if (value instanceof Integer integer) {
            return IndexResult.ok(integer);
        }
        return IndexResult.fail("Start Index must be INTEGER");
    }

    public static int resolveSeed(@Nullable Object value) {
        return RandomOps.resolveSeed(value);
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

    public static boolean hasPlacementSource(@Nullable Object placements) {
        if (!(placements instanceof List<?> list) || list.isEmpty()) {
            return false;
        }
        for (Object entry : list) {
            if (entry instanceof BlockPlacementData placement && placement.pos() != null) {
                return true;
            }
        }
        return false;
    }

    public static String cyclicPaletteBlockId(List<String> palette, int index, @Nullable String preserve) {
        if (palette == null || palette.isEmpty()) {
            return preserve != null ? preserve : "";
        }
        String blockId = palette.get(Math.floorMod(index, palette.size()));
        return blockId == null || blockId.isBlank() ? (preserve != null ? preserve : "") : blockId;
    }

    public static String pickWeightedBlockId(
            BlockPos pos,
            int seed,
            List<String> palette,
            List<Double> weights,
            @Nullable String preserveWhenEmpty
    ) {
        if (palette == null || palette.isEmpty() || weights == null || weights.isEmpty()) {
            return preserveWhenEmpty != null ? preserveWhenEmpty : "";
        }
        double noise = RandomOps.valueNoise3(pos.getX(), pos.getY(), pos.getZ(), seed);
        if (!Double.isFinite(noise)) {
            return preserveWhenEmpty != null ? preserveWhenEmpty : "";
        }
        double sample = (noise + 1.0d) * 0.5d;
        if (!Double.isFinite(sample)) {
            return preserveWhenEmpty != null ? preserveWhenEmpty : "";
        }
        sample = Math.max(0.0d, Math.min(1.0d, sample));

        int index = pickWeightedIndex(sample, weights);
        if (index < 0 || index >= palette.size()) {
            return preserveWhenEmpty != null ? preserveWhenEmpty : "";
        }
        String id = palette.get(index);
        return id == null || id.isBlank() ? (preserveWhenEmpty != null ? preserveWhenEmpty : "") : id;
    }

    /**
     * Maps normalized {@code sample} in {@code [0,1]} to a palette index.
     * Entries with {@code weight <= 0} are never selected.
     *
     * @return index in {@code weights}, or {@code -1} when no positive mass exists
     */
    public static int pickWeightedIndex(double sample, List<Double> weights) {
        if (weights == null || weights.isEmpty()) {
            return -1;
        }
        sample = Math.max(0.0d, Math.min(1.0d, sample));

        double total = 0.0d;
        for (double weight : weights) {
            if (weight > 0.0d) {
                total += weight;
            }
        }
        if (total <= 0.0d) {
            return -1;
        }

        double threshold = sample * total;
        if (threshold >= total) {
            threshold = Math.nextDown(total);
        }

        double cumulative = 0.0d;
        for (int i = 0; i < weights.size(); i++) {
            double weight = weights.get(i);
            if (weight <= 0.0d) {
                continue;
            }
            cumulative += weight;
            if (threshold < cumulative) {
                return i;
            }
        }

        for (int i = weights.size() - 1; i >= 0; i--) {
            if (weights.get(i) > 0.0d) {
                return i;
            }
        }
        return -1;
    }

    public static double totalWeight(List<Double> weights) {
        double sum = 0.0d;
        if (weights == null) {
            return sum;
        }
        for (double weight : weights) {
            if (Double.isFinite(weight)) {
                sum += weight;
            }
        }
        return sum;
    }

    public static Map<String, Object> assignFailResult(String error) {
        Map<String, Object> out = new HashMap<>();
        out.put("output_placements", List.of());
        out.put("output_placements_tree", new com.nodecraft.nodesystem.datatypes.DataTreeData(List.of()));
        out.put("output_valid", false);
        out.put("output_error", error == null ? "" : error);
        return out;
    }

    public static Map<String, Object> assignOkResult(
            List<BlockPlacementData> placements,
            com.nodecraft.nodesystem.datatypes.DataTreeData placementsTree
    ) {
        Map<String, Object> out = new HashMap<>();
        out.put("output_placements", placements != null ? placements : List.of());
        out.put("output_placements_tree", placementsTree != null ? placementsTree
            : new com.nodecraft.nodesystem.datatypes.DataTreeData(List.of()));
        out.put("output_valid", true);
        out.put("output_error", "");
        return out;
    }

    public static Map<String, Object> paletteFailResult(String error) {
        Map<String, Object> out = assignFailResult(error);
        out.put("output_palette_size", 0);
        return out;
    }

    public static Map<String, Object> paletteOkResult(
            List<BlockPlacementData> placements,
            com.nodecraft.nodesystem.datatypes.DataTreeData placementsTree,
            int paletteSize
    ) {
        Map<String, Object> out = assignOkResult(placements, placementsTree);
        out.put("output_palette_size", paletteSize);
        return out;
    }

    public static Map<String, Object> weightedFailResult(String error) {
        Map<String, Object> out = paletteFailResult(error);
        out.put("output_total_weight", 0.0d);
        return out;
    }

    public static Map<String, Object> weightedOkResult(
            List<BlockPlacementData> placements,
            com.nodecraft.nodesystem.datatypes.DataTreeData placementsTree,
            int paletteSize,
            double totalWeight
    ) {
        Map<String, Object> out = paletteOkResult(placements, placementsTree, paletteSize);
        out.put("output_total_weight", totalWeight);
        return out;
    }

    public static Map<String, Object> createPaletteFailResult(String error) {
        Map<String, Object> out = new HashMap<>();
        out.put("output_palette", BlockPaletteData.empty());
        out.put("output_size", 0);
        out.put("output_valid", false);
        out.put("output_error", error == null ? "" : error);
        return out;
    }

    public static Map<String, Object> createPaletteOkResult(BlockPaletteData palette) {
        Map<String, Object> out = new HashMap<>();
        out.put("output_palette", palette != null ? palette : BlockPaletteData.empty());
        out.put("output_size", palette != null ? palette.size() : 0);
        out.put("output_valid", true);
        out.put("output_error", "");
        return out;
    }
}

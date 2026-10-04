package com.nodecraft.nodesystem.util;

import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Shared preflight for structure exporters: strict placements, material resolve,
 * checked bounds/volume, air-at-0 palette, and metadata clamps.
 */
public final class StructureExportPreflight {

    public enum DenseMode {
        /** Sparse NodeCraft structure — placement-count budget only. */
        NONE,
        /** Dense Litematic — volume budget. */
        LITEMATIC,
        /** Dense Sponge/WorldEdit — volume + short-axis budget. */
        WORLD_EDIT
    }

    public record Metadata(String name, String author, String description) {
    }

    public record PaletteEntry(String blockId, @Nullable BlockStateData stateData) {
    }

    public record StructurePalette(List<PaletteEntry> entries, Map<String, Integer> indexByKey) {
        public int size() {
            return entries.size();
        }

        public int indexFor(BlockPlacementData placement) {
            return indexByKey.getOrDefault(keyFor(placement), 0);
        }
    }

    public record PreparedStructureExport(
        List<BlockPlacementData> placements,
        ExportBounds bounds,
        StructurePalette palette,
        Metadata metadata
    ) {
    }

    public record Result(
        @Nullable PreparedStructureExport prepared,
        boolean valid,
        String error
    ) {
        public static Result ok(PreparedStructureExport prepared) {
            return new Result(prepared, true, "");
        }

        public static Result invalid(String error) {
            return new Result(null, false, error == null ? "" : error);
        }
    }

    public enum IndexOrder {
        /** Litematic: {@code x + z * sizeX + y * sizeX * sizeZ}. */
        LITEMATIC,
        /** Sponge: {@code x + y * width + z * width * height}. */
        WORLD_EDIT
    }

    private StructureExportPreflight() {
    }

    public static Result prepare(
        @Nullable Object placementsObj,
        @Nullable Object blocksObj,
        @Nullable String defaultBlockId,
        DenseMode denseMode,
        @Nullable String name,
        @Nullable String author,
        @Nullable String description
    ) {
        Objects.requireNonNull(denseMode, "denseMode");

        PlacementPreflight.ParseResult<List<BlockPlacementData>> source =
            resolveSourcePlacements(placementsObj, blocksObj, defaultBlockId);
        if (!source.valid()) {
            return Result.invalid(source.error());
        }
        List<BlockPlacementData> placements = source.value();
        if (placements == null || placements.isEmpty()) {
            return Result.invalid("empty placements");
        }
        if (placements.size() > GenerationLimits.MAX_EXPORT_PLACEMENTS) {
            return Result.invalid("placements exceed MAX_EXPORT_PLACEMENTS");
        }

        PlacementPreflight.Result material = PlacementPreflight.preflightSources(placements, null);
        if (!material.valid()) {
            return Result.invalid(material.error());
        }

        ExportBounds bounds;
        try {
            bounds = ExportBounds.fromPlacements(placements);
        } catch (IllegalArgumentException e) {
            return Result.invalid(e.getMessage() != null ? e.getMessage() : "invalid_bounds");
        }

        if (denseMode == DenseMode.NONE) {
            String sparseError = bounds.validateSparse();
            if (sparseError != null) {
                return Result.invalid(sparseError);
            }
        } else {
            Integer maxAxis = denseMode == DenseMode.WORLD_EDIT
                ? GenerationLimits.MAX_WORLD_EDIT_AXIS
                : null;
            String denseError = bounds.validateDense(GenerationLimits.MAX_DENSE_EXPORT_VOLUME, maxAxis);
            if (denseError != null) {
                return Result.invalid(denseError);
            }
        }

        StructurePalette palette = buildPaletteWithAir(placements);
        Metadata metadata = new Metadata(
            clamp(name, "nodecraft_export", GenerationLimits.MAX_EXPORT_NAME_CHARS),
            clamp(author, "nodecraft", GenerationLimits.MAX_EXPORT_AUTHOR_CHARS),
            clamp(description, "", GenerationLimits.MAX_EXPORT_DESCRIPTION_CHARS)
        );
        return Result.ok(new PreparedStructureExport(List.copyOf(placements), bounds, palette, metadata));
    }

    /**
     * Dense index array with every cell defaulting to palette index 0 (air).
     */
    public static int[] buildDenseIndices(PreparedStructureExport prepared, IndexOrder order) {
        ExportBounds bounds = prepared.bounds();
        int sizeX = bounds.sizeXInt();
        int sizeY = bounds.sizeYInt();
        int sizeZ = bounds.sizeZInt();
        int volume = Math.toIntExact(bounds.checkedVolume());
        int[] indices = new int[volume]; // zero-filled → air

        StructurePalette palette = prepared.palette();
        for (BlockPlacementData placement : prepared.placements()) {
            BlockPos pos = placement.pos();
            int x = pos.getX() - bounds.minX();
            int y = pos.getY() - bounds.minY();
            int z = pos.getZ() - bounds.minZ();
            int linearIndex = switch (order) {
                case LITEMATIC -> x + z * sizeX + y * sizeX * sizeZ;
                case WORLD_EDIT -> x + y * sizeX + z * sizeX * sizeY;
            };
            indices[linearIndex] = palette.indexFor(placement);
        }
        return indices;
    }

    public static String keyFor(BlockPlacementData placement) {
        StringBuilder builder = new StringBuilder(placement.blockId());
        BlockStateData stateData = placement.stateData();
        if (stateData != null && !stateData.isEmpty()) {
            builder.append('|');
            stateData.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> builder.append(entry.getKey()).append('=').append(entry.getValue()).append(';'));
        }
        return builder.toString();
    }

    /** WorldEdit / Sponge palette key: {@code id[prop=value,...]}. */
    public static String spongeKeyFor(BlockPlacementData placement) {
        BlockStateData stateData = placement.stateData();
        if (stateData == null || stateData.isEmpty()) {
            return placement.blockId();
        }
        StringBuilder builder = new StringBuilder(placement.blockId()).append('[');
        stateData.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEachOrdered(entry -> {
                if (builder.charAt(builder.length() - 1) != '[') {
                    builder.append(',');
                }
                builder.append(entry.getKey()).append('=').append(entry.getValue());
            });
        builder.append(']');
        return builder.toString();
    }

    public static String deriveNameFromPath(@Nullable String rawPath) {
        String candidate = (rawPath == null || rawPath.isBlank()) ? "nodecraft_export" : rawPath.trim();
        java.nio.file.Path path = java.nio.file.Path.of(candidate);
        String fileName = path.getFileName() != null ? path.getFileName().toString() : "nodecraft_export";
        int suffixIndex = fileName.lastIndexOf('.');
        return suffixIndex > 0 ? fileName.substring(0, suffixIndex) : fileName;
    }

    private static PlacementPreflight.ParseResult<List<BlockPlacementData>> resolveSourcePlacements(
        @Nullable Object placementsObj,
        @Nullable Object blocksObj,
        @Nullable String defaultBlockId
    ) {
        boolean hasPlacementsList = placementsObj instanceof List<?> list && !list.isEmpty();
        if (hasPlacementsList) {
            return PlacementPreflight.parsePlacementList(placementsObj);
        }

        if (!(blocksObj instanceof BlockPosList blocks) || blocks.isEmpty()) {
            return PlacementPreflight.ParseResult.ok(List.of());
        }

        String blockId = (defaultBlockId == null || defaultBlockId.isBlank())
            ? "minecraft:stone"
            : defaultBlockId.trim();
        if (BlockStateResolver.resolveDefault(blockId) == null) {
            return PlacementPreflight.ParseResult.invalid(
                PlacementPreflight.ERROR_UNRESOLVABLE_BLOCK + ": " + blockId
            );
        }

        List<BlockPlacementData> fromBlocks = new ArrayList<>(blocks.size());
        for (BlockPos pos : blocks) {
            if (pos == null) {
                return PlacementPreflight.ParseResult.invalid(PlacementPreflight.ERROR_INVALID_ENTRY);
            }
            fromBlocks.add(new BlockPlacementData(pos, blockId));
        }
        return PlacementPreflight.ParseResult.ok(fromBlocks);
    }

    private static StructurePalette buildPaletteWithAir(List<BlockPlacementData> placements) {
        LinkedHashMap<String, Integer> indexByKey = new LinkedHashMap<>();
        List<PaletteEntry> entries = new ArrayList<>();

        // Palette index 0 is always air so dense holes stay empty.
        indexByKey.put(keyFor(new BlockPlacementData(BlockPos.ORIGIN, "minecraft:air")), 0);
        entries.add(new PaletteEntry("minecraft:air", null));

        for (BlockPlacementData placement : placements) {
            String key = keyFor(placement);
            if (indexByKey.containsKey(key)) {
                continue;
            }
            int next = indexByKey.size();
            indexByKey.put(key, next);
            entries.add(new PaletteEntry(placement.blockId(), placement.stateData()));
        }
        return new StructurePalette(List.copyOf(entries), Map.copyOf(indexByKey));
    }

    private static String clamp(@Nullable String value, String fallback, int maxChars) {
        String text = (value == null || value.isBlank()) ? fallback : value;
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars);
    }
}

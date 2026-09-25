package com.nodecraft.nodesystem.util;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Shared Material-layer helpers: {@code BLOCK_PLACEMENT_LIST} is the canonical payload.
 * Material mapping remaps {@code blockId} only and preserves {@link BlockStateData}.
 */
public final class MaterialMappingSupport {

    private MaterialMappingSupport() {
    }

    /**
     * Prefer incoming placements; otherwise voxelize / resolve coordinates into placements
     * with {@code null} state (no fabricated BlockState). Geometry path requires an explicit
     * non-blank {@code fallbackBlockId} — no hidden vanilla defaults.
     */
    public static List<BlockPlacementData> resolveSourcePlacements(
        @Nullable Object placementsObj,
        @Nullable Object coordinatesObj,
        @Nullable Object geometryObj,
        @Nullable Object boxGeometryObj,
        @Nullable Object cylinderGeometryObj,
        @Nullable Object sphereGeometryObj,
        @Nullable Object torusGeometryObj,
        @Nullable String fallbackBlockId
    ) {
        List<BlockPlacementData> fromPlacements = extractPlacements(placementsObj);
        if (!fromPlacements.isEmpty()) {
            return fromPlacements;
        }

        if (fallbackBlockId == null || fallbackBlockId.isBlank()) {
            return List.of();
        }

        BlockPosList positions = GeometryVoxelizer.resolveBlocks(
            coordinatesObj,
            geometryObj,
            boxGeometryObj,
            cylinderGeometryObj,
            sphereGeometryObj,
            torusGeometryObj,
            true
        );
        List<BlockPlacementData> generated = new ArrayList<>(positions.size());
        for (var pos : positions) {
            if (pos != null) {
                generated.add(new BlockPlacementData(pos, fallbackBlockId));
            }
        }
        return generated;
    }

    /**
     * Returns a trimmed block type string when present; never fabricates a default.
     */
    public static @Nullable String optionalBlockType(@Nullable Object value) {
        if (!(value instanceof String text) || text.isBlank()) {
            return null;
        }
        return text.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Uses an explicit mapped block type when provided; otherwise preserves the source block id.
     */
    public static String resolveMaterialTarget(@Nullable String mapped, @Nullable String sourceBlockId) {
        if (mapped != null && !mapped.isBlank()) {
            return mapped;
        }
        return sourceBlockId != null ? sourceBlockId : "";
    }

    /**
     * First non-blank mapped block type from the given candidates.
     */
    public static @Nullable String firstMappedBlockType(@Nullable String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            String mapped = optionalBlockType(candidate);
            if (mapped != null) {
                return mapped;
            }
        }
        return null;
    }

    /**
     * Material remap contract: change block id, keep stateData for Apply/Preview filtering.
     */
    public static BlockPlacementData remapBlockId(BlockPlacementData source, String newBlockId) {
        if (source == null) {
            return null;
        }
        return source.withBlockId(newBlockId);
    }

    public static List<BlockPlacementData> extractPlacements(@Nullable Object placementsObj) {
        if (!(placementsObj instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<BlockPlacementData> resolved = new ArrayList<>(list.size());
        for (Object entry : list) {
            if (entry instanceof BlockPlacementData placement && placement.pos() != null) {
                resolved.add(placement);
            }
        }
        return resolved;
    }
}

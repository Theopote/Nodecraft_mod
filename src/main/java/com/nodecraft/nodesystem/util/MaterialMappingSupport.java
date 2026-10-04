package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.nodes.material.block_state.BlockStateValidationUtils;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

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
     * Registry-validated mapped BLOCK_TYPE for material remappers.
     * Undriven → absent (null id, valid); driven unknown/blank → fail.
     */
    public record MappedBlockType(boolean valid, @Nullable String blockId, String error) {
        public static MappedBlockType absent() {
            return new MappedBlockType(true, null, "");
        }

        public static MappedBlockType known(String blockId) {
            return new MappedBlockType(true, blockId, "");
        }

        public static MappedBlockType fail(String error) {
            return new MappedBlockType(false, null, error == null ? "" : error);
        }
    }

    /**
     * @param driven whether the port is connected or has an injected non-null value
     */
    public static MappedBlockType requireKnownBlockType(@Nullable Object value, boolean driven) {
        if (!driven) {
            return MappedBlockType.absent();
        }
        if (!(value instanceof String text) || text.isBlank()) {
            return MappedBlockType.fail("Block Type must be a non-blank string");
        }
        String normalized = text.trim().toLowerCase(Locale.ROOT);
        if (!normalized.contains(":")) {
            normalized = "minecraft:" + normalized;
        }
        if (!isKnownBlockId(normalized)) {
            return MappedBlockType.fail("Unknown block: " + normalized);
        }
        return MappedBlockType.known(normalized);
    }

    public static boolean isKnownBlockId(@Nullable String blockType) {
        if (blockType == null || blockType.isBlank()) {
            return false;
        }
        Identifier id;
        try {
            id = Identifier.tryParse(blockType);
        } catch (Throwable ignored) {
            return false;
        }
        if (id == null) {
            return false;
        }
        try {
            // Pre-bootstrap / unit tests: empty registry cannot refute membership —
            // accept well-formed ids. Once populated, require containsId.
            if (!isBlockRegistryPopulated()) {
                return true;
            }
            return Registries.BLOCK.containsId(id);
        } catch (Throwable ignored) {
            // Registry unavailable: fail open on syntax only (id already parsed).
            return true;
        }
    }

    /**
     * True when {@link Registries#BLOCK} has at least one id (in-game / bootstrapped tests).
     */
    public static boolean isBlockRegistryPopulated() {
        try {
            return !Registries.BLOCK.getIds().isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
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
     * Structural copy: new block id, same position and stateData. Does not validate compatibility.
     */
    public static BlockPlacementData remapBlockId(BlockPlacementData source, String newBlockId) {
        if (source == null) {
            return null;
        }
        return source.withBlockId(newBlockId);
    }

    public record RemapResult(boolean valid, @Nullable BlockPlacementData placement, String error) {
        public static RemapResult ok(BlockPlacementData placement) {
            return new RemapResult(true, placement, "");
        }

        public static RemapResult fail(String error) {
            return new RemapResult(false, null, error == null ? "" : error);
        }
    }

    /**
     * Remap blockId, preserve stateData, then fail closed if preserved state is incompatible
     * with the new block. Empty state is always compatible. Empty registry cannot refute
     * property membership (same fail-open as {@link #isKnownBlockId}).
     */
    public static RemapResult remapValidated(@Nullable BlockPlacementData source, @Nullable String newBlockId) {
        if (source == null || source.pos() == null) {
            return RemapResult.fail("Placement required for remap");
        }
        String target = newBlockId;
        if (target == null || target.isBlank()) {
            target = source.blockId();
        }
        if (target == null || target.isBlank()) {
            return RemapResult.fail("Block Type must be a non-blank string");
        }
        String normalized = target.trim().toLowerCase(Locale.ROOT);
        if (!normalized.contains(":")) {
            normalized = "minecraft:" + normalized;
        }
        if (!isKnownBlockId(normalized)) {
            return RemapResult.fail("Unknown block: " + normalized);
        }
        BlockPlacementData remapped = source.withBlockId(normalized);
        if (!isBlockRegistryPopulated()) {
            return RemapResult.ok(remapped);
        }
        String error = BlockStateValidationUtils.remapIncompatibility(remapped);
        if (error != null) {
            return RemapResult.fail(error);
        }
        return RemapResult.ok(remapped);
    }

    public record RemapListResult(boolean valid, List<BlockPlacementData> placements, String error) {
        public static RemapListResult ok(List<BlockPlacementData> placements) {
            return new RemapListResult(true, placements != null ? placements : List.of(), "");
        }

        public static RemapListResult fail(String error) {
            return new RemapListResult(false, List.of(), error == null ? "" : error);
        }
    }

    public static RemapListResult remapAllValidated(
            List<BlockPlacementData> sources,
            Function<BlockPlacementData, String> blockIdFor
    ) {
        if (sources == null || sources.isEmpty()) {
            return RemapListResult.ok(List.of());
        }
        List<BlockPlacementData> out = new ArrayList<>(sources.size());
        for (BlockPlacementData source : sources) {
            if (source == null || source.pos() == null) {
                continue;
            }
            String blockId = blockIdFor.apply(source);
            RemapResult remap = remapValidated(source, blockId);
            if (!remap.valid()) {
                return RemapListResult.fail(remap.error());
            }
            out.add(remap.placement());
        }
        return RemapListResult.ok(out);
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

    /**
     * Fail-closed placement list parse for Block State Apply / Stair Shape.
     * Null/absent → valid empty; mixed or incomplete entries → invalid (no silent filter).
     */
    public record PlacementListResult(boolean valid, List<BlockPlacementData> placements, String error) {
        public static PlacementListResult ok(List<BlockPlacementData> placements) {
            return new PlacementListResult(true, placements != null ? placements : List.of(), "");
        }

        public static PlacementListResult fail(String error) {
            return new PlacementListResult(false, List.of(), error == null ? "" : error);
        }
    }

    public static PlacementListResult parsePlacementsStrict(@Nullable Object placementsObj) {
        if (placementsObj == null) {
            return PlacementListResult.ok(List.of());
        }
        if (!(placementsObj instanceof List<?> list)) {
            return PlacementListResult.fail("Block Placements must be a BLOCK_PLACEMENT_LIST");
        }
        List<BlockPlacementData> resolved = new ArrayList<>(list.size());
        for (Object entry : list) {
            if (!(entry instanceof BlockPlacementData placement)
                || placement.pos() == null
                || placement.blockId() == null
                || placement.blockId().isBlank()) {
                return PlacementListResult.fail(
                    "Block Placements must contain only BlockPlacementData with non-null pos and non-blank blockId"
                );
            }
            resolved.add(placement);
        }
        return PlacementListResult.ok(resolved);
    }
}

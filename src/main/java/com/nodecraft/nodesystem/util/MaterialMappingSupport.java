package com.nodecraft.nodesystem.util;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared Material-layer helpers: {@code BLOCK_PLACEMENT_LIST} is the canonical payload.
 * Material mapping remaps {@code blockId} only and preserves {@link BlockStateData}.
 */
public final class MaterialMappingSupport {

    private MaterialMappingSupport() {
    }

    /**
     * Prefer incoming placements; otherwise voxelize / resolve coordinates into placements
     * with {@code null} state (no fabricated BlockState).
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

        String blockId = (fallbackBlockId != null && !fallbackBlockId.isBlank())
            ? fallbackBlockId
            : "minecraft:stone";
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
                generated.add(new BlockPlacementData(pos, blockId));
            }
        }
        return generated;
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

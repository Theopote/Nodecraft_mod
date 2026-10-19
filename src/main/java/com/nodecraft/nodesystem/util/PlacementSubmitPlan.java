package com.nodecraft.nodesystem.util;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Resolves the placement set that {@code ApplyChangesNode} would submit to Bake —
 * without requiring a live world. Preview / Apply acceptance contracts compare against this plan.
 * <p>
 * Priority: non-empty validated {@link PlacementPreflight} placements win; Geometry / Blocks
 * are not additionally voxelized in that case.
 * <p>
 * Duplicate coordinates are collapsed with <strong>last-write-wins</strong> before the plan is
 * returned so Bake OVERWRITE and INCREMENTAL both see one target {@link BlockState} per cell.
 */
public final class PlacementSubmitPlan {

    public enum Source {
        /** Per-position Block Placements / Tree. */
        PLACEMENTS,
        /** Uniform block type applied to a Block List or voxelized geometry. */
        GEOMETRY_VOXELS,
        EMPTY,
        INVALID
    }

    public record Entry(BlockPos pos, BlockState state) {
        public Entry {
            Objects.requireNonNull(pos, "pos");
            Objects.requireNonNull(state, "state");
        }
    }

    /**
     * @param entries                        unique cells after last-write-wins collapse (Bake queue)
     * @param rawEntryCount                  entry count before dedupe (source instruction count)
     * @param geometryIgnoredDueToPlacements true when Geometry/Blocks inputs were present but skipped
     *                                       because Placement data took priority
     */
    public record Plan(
            Source source,
            List<Entry> entries,
            int rawEntryCount,
            boolean valid,
            String error,
            boolean geometryIgnoredDueToPlacements
    ) {
        public Plan {
            entries = entries == null ? List.of() : List.copyOf(entries);
            if (rawEntryCount < entries.size()) {
                rawEntryCount = entries.size();
            }
            error = error == null ? "" : error;
        }

        public static Plan invalid(String error) {
            return new Plan(Source.INVALID, List.of(), 0, false, error == null ? "" : error, false);
        }

        public static Plan empty() {
            return new Plan(Source.EMPTY, List.of(), 0, true, "", false);
        }

        /** Unique cell count submitted to Bake (after dedupe). */
        public int size() {
            return entries.size();
        }

        public int uniqueCount() {
            return entries.size();
        }

        public boolean hadDuplicates() {
            return rawEntryCount > entries.size();
        }

        /** Last-write-wins map (same rule as {@link #dedupeLastWriteWins}). */
        public Map<BlockPos, BlockState> toPositionMap() {
            Map<BlockPos, BlockState> map = new LinkedHashMap<>();
            for (Entry entry : entries) {
                map.put(entry.pos().toImmutable(), entry.state());
            }
            return map;
        }
    }

    private PlacementSubmitPlan() {
    }

    /**
     * Collapses duplicate coordinates: later entries replace earlier state; first-seen key order
     * is preserved (matches {@link com.nodecraft.nodesystem.bake.BakeTask#dedupeLastWriteWins}).
     */
    public static List<Entry> dedupeLastWriteWins(@Nullable List<Entry> entries) {
        return LastWriteWinsByPos.collapse(
                entries,
                Entry::pos,
                e -> e != null && e.state() != null,
                (key, e) -> new Entry(key, e.state()));
    }

    /**
     * Mirrors Apply Changes input resolution order and failure modes.
     */
    public static Plan resolve(
            @Nullable Object placementsObj,
            @Nullable Object placementsTreeObj,
            @Nullable Object blocksObj,
            @Nullable Object geometryObj,
            @Nullable Object boxGeometryObj,
            @Nullable Object cylinderGeometryObj,
            @Nullable Object sphereGeometryObj,
            @Nullable Object torusGeometryObj,
            @Nullable String blockType,
            boolean solidGeometry
    ) {
        boolean hasGeometryInput = blocksObj != null
                || geometryObj != null
                || boxGeometryObj != null
                || cylinderGeometryObj != null
                || sphereGeometryObj != null
                || torusGeometryObj != null;

        PlacementPreflight.Result preflight =
                PlacementPreflight.preflightSources(placementsObj, placementsTreeObj);
        if (!preflight.valid()) {
            return Plan.invalid(preflight.error());
        }
        if (preflight.hasPlacements()) {
            List<Entry> raw = new ArrayList<>(preflight.resolved().size());
            for (PlacementPreflight.ResolvedPlacement placement : preflight.resolved()) {
                raw.add(new Entry(placement.pos().toImmutable(), placement.state()));
            }
            List<Entry> unique = dedupeLastWriteWins(raw);
            return new Plan(Source.PLACEMENTS, unique, raw.size(), true, "", hasGeometryInput);
        }

        if (!hasGeometryInput) {
            return Plan.empty();
        }

        GeometryVoxelizationResult voxelResult = GeometryVoxelizer.resolveBlocksStrict(
                blocksObj,
                geometryObj,
                boxGeometryObj,
                cylinderGeometryObj,
                sphereGeometryObj,
                torusGeometryObj,
                solidGeometry
        );
        if (!voxelResult.success()) {
            String detail = voxelResult.error();
            if (detail == null || detail.isBlank()) {
                detail = "Geometry voxelization failed (" + voxelResult.status() + ")";
            }
            return Plan.invalid(detail);
        }
        BlockPosList blocks = voxelResult.blocks();
        if (blocks == null || blocks.isEmpty()) {
            return Plan.empty();
        }

        String resolvedType = (blockType == null || blockType.isBlank()) ? "minecraft:stone" : blockType;
        BlockState targetState = BlockStateResolver.resolveDefault(resolvedType);
        if (targetState == null) {
            return Plan.invalid("Invalid block type: " + resolvedType);
        }

        List<Entry> raw = new ArrayList<>(blocks.size());
        for (BlockPos pos : blocks) {
            if (pos != null) {
                raw.add(new Entry(pos.toImmutable(), targetState));
            }
        }
        if (raw.isEmpty()) {
            return Plan.empty();
        }
        List<Entry> unique = dedupeLastWriteWins(raw);
        return new Plan(Source.GEOMETRY_VOXELS, unique, raw.size(), true, "", false);
    }
}

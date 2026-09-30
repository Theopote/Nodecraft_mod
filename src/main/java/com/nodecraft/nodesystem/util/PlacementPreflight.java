package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.DataTreeData;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Strict placement validation for world-write nodes (Apply Changes, exports).
 * Any invalid entry or unresolvable {@link BlockState} fails the whole batch.
 */
public final class PlacementPreflight {

    public static final String ERROR_INVALID_INPUT = "invalid_input";
    public static final String ERROR_INVALID_ENTRY = "invalid_placement";
    public static final String ERROR_UNRESOLVABLE_BLOCK = "unresolvable_block_state";

    public record ResolvedPlacement(BlockPos pos, BlockState state) {
    }

    public record Result(List<ResolvedPlacement> resolved, boolean valid, String error) {
        public static Result ok(List<ResolvedPlacement> resolved) {
            return new Result(List.copyOf(resolved), true, "");
        }

        public static Result invalid(String error) {
            return new Result(List.of(), false, error == null ? "" : error);
        }

        public boolean hasPlacements() {
            return !resolved.isEmpty();
        }
    }

    private PlacementPreflight() {
    }

    public static Result preflightSources(@Nullable Object listObj, @Nullable Object treeObj) {
        ParseResult<List<BlockPlacementData>> fromList = parsePlacementList(listObj);
        if (!fromList.valid()) {
            return Result.invalid(fromList.error());
        }
        ParseResult<List<BlockPlacementData>> fromTree = parsePlacementTree(treeObj);
        if (!fromTree.valid()) {
            return Result.invalid(fromTree.error());
        }

        List<BlockPlacementData> combined = new ArrayList<>(fromList.value().size() + fromTree.value().size());
        combined.addAll(fromList.value());
        combined.addAll(fromTree.value());
        if (combined.isEmpty()) {
            return Result.ok(List.of());
        }
        return resolveBlockStates(combined);
    }

    static ParseResult<List<BlockPlacementData>> parsePlacementList(@Nullable Object value) {
        if (value == null) {
            return ParseResult.ok(List.of());
        }
        if (!(value instanceof List<?> list)) {
            return ParseResult.invalid(ERROR_INVALID_INPUT);
        }
        if (list.isEmpty()) {
            return ParseResult.ok(List.of());
        }

        List<BlockPlacementData> parsed = new ArrayList<>(list.size());
        for (Object entry : list) {
            ParseResult<BlockPlacementData> one = parsePlacementEntry(entry);
            if (!one.valid()) {
                return ParseResult.invalid(one.error());
            }
            parsed.add(one.value());
        }
        return ParseResult.ok(parsed);
    }

    static ParseResult<List<BlockPlacementData>> parsePlacementTree(@Nullable Object value) {
        if (value == null) {
            return ParseResult.ok(List.of());
        }
        if (!(value instanceof DataTreeData tree)) {
            return ParseResult.invalid(ERROR_INVALID_INPUT);
        }
        if (tree.getBranchCount() == 0) {
            return ParseResult.ok(List.of());
        }

        List<BlockPlacementData> parsed = new ArrayList<>();
        for (DataTreeData.Branch branch : tree.getBranches()) {
            for (Object entry : branch.items()) {
                ParseResult<BlockPlacementData> one = parsePlacementEntry(entry);
                if (!one.valid()) {
                    return ParseResult.invalid(one.error());
                }
                parsed.add(one.value());
            }
        }
        return ParseResult.ok(parsed);
    }

    private static ParseResult<BlockPlacementData> parsePlacementEntry(@Nullable Object entry) {
        if (!(entry instanceof BlockPlacementData placement)) {
            return ParseResult.invalid(ERROR_INVALID_ENTRY);
        }
        if (placement.pos() == null) {
            return ParseResult.invalid(ERROR_INVALID_ENTRY);
        }
        if (placement.blockId() == null || placement.blockId().isBlank()) {
            return ParseResult.invalid(ERROR_INVALID_ENTRY);
        }
        return ParseResult.ok(placement);
    }

    private static Result resolveBlockStates(List<BlockPlacementData> placements) {
        List<ResolvedPlacement> resolved = new ArrayList<>(placements.size());
        for (BlockPlacementData placement : placements) {
            BlockState state = BlockStateResolver.resolve(placement.blockId(), placement.stateData());
            if (state == null) {
                return Result.invalid(ERROR_UNRESOLVABLE_BLOCK + ": " + placement.blockId());
            }
            resolved.add(new ResolvedPlacement(placement.pos(), state));
        }
        return Result.ok(resolved);
    }

    record ParseResult<T>(@Nullable T value, boolean valid, String error) {
        static <T> ParseResult<T> ok(T value) {
            return new ParseResult<>(value, true, "");
        }

        static <T> ParseResult<T> invalid(String error) {
            return new ParseResult<>(null, false, error == null ? "" : error);
        }
    }
}

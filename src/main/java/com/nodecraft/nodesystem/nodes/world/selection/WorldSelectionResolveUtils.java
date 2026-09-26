package com.nodecraft.nodesystem.nodes.world.selection;

import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.util.BlockSpace;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

/**
 * Strict spatial helpers for world.selection — no polymorphic Point/Vector → BlockPos floor.
 */
final class WorldSelectionResolveUtils {

    enum SnapMode {
        /** Containing cell: {@link BlockSpace#pointToBlockFloor}. */
        CONTAINING_CELL,
        /** Nearest cell center: {@link BlockSpace#nearestCellBlockPos}. */
        NEAREST_CENTER;

        static SnapMode fromLegacyOrName(@Nullable String text) {
            if (text == null || text.isBlank()) {
                return NEAREST_CENTER;
            }
            String key = text.trim().toUpperCase();
            return switch (key) {
                case "CONTAINING_CELL", "FLOOR" -> CONTAINING_CELL;
                case "NEAREST_CENTER", "NEAREST" -> NEAREST_CENTER;
                default -> NEAREST_CENTER; // CEIL and unknowns → NEAREST_CENTER
            };
        }
    }

    private WorldSelectionResolveUtils() {
    }

    /** Strict POINT only. */
    static @Nullable Vector3d requirePoint(@Nullable Object value) {
        if (value instanceof PointData pointData) {
            Vector3d position = pointData.position();
            if (position != null
                    && Double.isFinite(position.x)
                    && Double.isFinite(position.y)
                    && Double.isFinite(position.z)) {
                return new Vector3d(position);
            }
        }
        return null;
    }

    /** Strict BLOCK_POS only — no Point/Vector coercion. */
    static @Nullable BlockPos requireBlockPos(@Nullable Object value) {
        return value instanceof BlockPos pos ? pos.toImmutable() : null;
    }

    static BlockPos snapPointToBlock(Vector3d point, SnapMode mode) {
        if (point == null) {
            return BlockPos.ORIGIN;
        }
        SnapMode resolved = mode == null ? SnapMode.NEAREST_CENTER : mode;
        return switch (resolved) {
            case CONTAINING_CELL -> BlockSpace.pointToBlockFloor(point);
            case NEAREST_CENTER -> BlockSpace.nearestCellBlockPos(point);
        };
    }

    /** Distance from continuous point to the snapped cell center. */
    static double distanceToSnappedCenter(Vector3d point, BlockPos snapped) {
        if (point == null || snapped == null) {
            return Double.NaN;
        }
        Vector3d center = BlockSpace.cellCenter(snapped);
        return point.distance(center);
    }
}

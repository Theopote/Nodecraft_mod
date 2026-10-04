package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Frozen 0/1/N geometry packing: empty/null collection → null, single → raw, many → Composite.
 * Null members are rejected (returns null) — no silent filter / partial Composite.
 */
public final class GeometryOutputUtils {

    private GeometryOutputUtils() {
    }

    public static @Nullable GeometryData packGeometry(@Nullable Collection<? extends GeometryData> pieces) {
        if (pieces == null || pieces.isEmpty()) {
            return null;
        }
        List<GeometryData> list = new ArrayList<>(pieces.size());
        for (GeometryData piece : pieces) {
            if (piece == null) {
                return null;
            }
            list.add(piece);
        }
        if (list.size() == 1) {
            return list.getFirst();
        }
        try {
            return new CompositeGeometryData(list);
        } catch (IllegalArgumentException overflow) {
            return null;
        }
    }

    /**
     * Returns {@code true} when {@code columns * rows} fits in the architectural instance budget.
     * Uses long-first multiply; overflow or over-budget → {@code false}.
     */
    public static boolean fitsArchitecturalInstanceBudget(int columns, int rows) {
        if (columns <= 0 || rows <= 0) {
            return false;
        }
        try {
            long total = Math.multiplyExact((long) columns, (long) rows);
            return total <= GenerationLimits.MAX_ARCHITECTURAL_INSTANCES
                && total <= Integer.MAX_VALUE;
        } catch (ArithmeticException overflow) {
            return false;
        }
    }

    public static int architecturalInstanceCount(int columns, int rows) {
        if (!fitsArchitecturalInstanceBudget(columns, rows)) {
            return -1;
        }
        return columns * rows;
    }
}

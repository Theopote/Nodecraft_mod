package com.nodecraft.nodesystem.datatypes;

import com.nodecraft.nodesystem.util.GenerationLimits;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Composite geometry that groups multiple geometry objects into one value.
 * Structurally flat: nested composites are recursively flattened.
 * Null members are rejected (fail closed). Flattened leaf count cannot exceed
 * {@link GenerationLimits#MAX_COMPOSITE_GEOMETRY_LEAVES}.
 */
public record CompositeGeometryData(List<GeometryData> geometries) implements GeometryData {

    public static final String LEAF_BUDGET_EXCEEDED = "geometry_leaf_budget_exceeded";

    public CompositeGeometryData(List<GeometryData> geometries) {
        List<GeometryData> flattened = flattenLeaves(geometries);
        if (flattened.size() > GenerationLimits.MAX_COMPOSITE_GEOMETRY_LEAVES) {
            throw new IllegalArgumentException(LEAF_BUDGET_EXCEEDED);
        }
        this.geometries = flattened;
    }

    /**
     * Flattens a list of roots into leaf geometries (nested composites expanded).
     * Rejects null members. Throws {@link IllegalArgumentException} when the leaf budget is exceeded.
     */
    public static List<GeometryData> flattenLeaves(List<GeometryData> roots) {
        List<GeometryData> flattened = new ArrayList<>();
        if (roots != null) {
            for (GeometryData geometry : roots) {
                appendLeaves(flattened, geometry);
            }
        }
        return List.copyOf(flattened);
    }

    /**
     * Appends leaf geometries from {@code geometry} into {@code target}, flattening nested composites.
     * Rejects null. Throws {@link IllegalArgumentException} when the leaf budget is exceeded.
     */
    public static void appendLeaves(List<GeometryData> target, GeometryData geometry) {
        Objects.requireNonNull(geometry, "Composite geometry member must not be null");
        if (geometry instanceof CompositeGeometryData(List<GeometryData> geometries1)) {
            for (GeometryData child : geometries1) {
                appendLeaves(target, child);
            }
            return;
        }
        if (target.size() >= GenerationLimits.MAX_COMPOSITE_GEOMETRY_LEAVES) {
            throw new IllegalArgumentException(LEAF_BUDGET_EXCEEDED);
        }
        target.add(geometry);
    }

    public int size() {
        return geometries.size();
    }

    public boolean isEmpty() {
        return geometries.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CompositeGeometryData(List<GeometryData> geometries1))) return false;
        return Objects.equals(geometries, geometries1);
    }

    @Override
    public @NonNull String toString() {
        return "CompositeGeometryData{size=" + geometries.size() + "}";
    }
}

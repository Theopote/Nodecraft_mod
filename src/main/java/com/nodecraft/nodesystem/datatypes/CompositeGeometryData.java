package com.nodecraft.nodesystem.datatypes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Composite geometry that groups multiple geometry objects into one value.
 * Structurally flat: nested composites are recursively flattened.
 * Null members are rejected (fail closed).
 */
public class CompositeGeometryData implements GeometryData {

    private final List<GeometryData> geometries;

    public CompositeGeometryData(List<GeometryData> geometries) {
        this.geometries = Collections.unmodifiableList(flattenLeaves(geometries));
    }

    /**
     * Flattens a list of roots into leaf geometries (nested composites expanded).
     * Rejects null members.
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
     * Rejects null.
     */
    public static void appendLeaves(List<GeometryData> target, GeometryData geometry) {
        Objects.requireNonNull(geometry, "Composite geometry member must not be null");
        if (geometry instanceof CompositeGeometryData composite) {
            for (GeometryData child : composite.getGeometries()) {
                appendLeaves(target, child);
            }
            return;
        }
        target.add(geometry);
    }

    public List<GeometryData> getGeometries() {
        return geometries;
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
        if (!(o instanceof CompositeGeometryData that)) return false;
        return Objects.equals(geometries, that.geometries);
    }

    @Override
    public int hashCode() {
        return Objects.hash(geometries);
    }

    @Override
    public String toString() {
        return "CompositeGeometryData{size=" + geometries.size() + "}";
    }
}

package com.nodecraft.nodesystem.datatypes;

import com.nodecraft.nodesystem.util.PlanarRegionValidator;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Objects;

/**
 * Planar region with an outer boundary and zero or more holes.
 * Historical Graph V91 residue; {@code GraphFormatVersion.CURRENT} is stamp-only 1.
 * <p>
 * Outer and holes are each {@link PolygonProfileData} (simple closed planar loops).
 * PLANAR_REGION is the level above POLYGON_PROFILE for boolean/offset/extrude workflows.
 * Holes must be strictly interior-disjoint from the outer boundary and from each other.
 */
public record PlanarRegionData(
    PolygonProfileData outer,
    List<PolygonProfileData> holes,
    PlaneData plane
) {
    public PlanarRegionData {
        Objects.requireNonNull(outer, "outer");
        Objects.requireNonNull(plane, "plane");
        holes = holes == null ? List.of() : List.copyOf(holes);
        String error = PlanarRegionValidator.validateConstruction(outer, holes, plane);
        if (error != null) {
            throw new IllegalArgumentException(error);
        }
    }

    /** Simple region with no holes. */
    public static PlanarRegionData of(PolygonProfileData profile) {
        Objects.requireNonNull(profile, "profile");
        return new PlanarRegionData(profile, List.of(), profile.plane());
    }

    /**
     * Fail-closed factory. Returns {@code null} when validation fails;
     * writes the reason into {@code errorOut} when non-null.
     */
    public static @Nullable PlanarRegionData tryCreate(
            @Nullable PolygonProfileData outer,
            @Nullable List<PolygonProfileData> holes,
            @Nullable PlaneData plane,
            @Nullable StringBuilder errorOut
    ) {
        String error = PlanarRegionValidator.validateConstruction(outer, holes, plane);
        if (error != null) {
            if (errorOut != null) {
                errorOut.setLength(0);
                errorOut.append(error);
            }
            return null;
        }
        PlaneData resolvedPlane = plane != null ? plane : (outer != null ? outer.plane() : null);
        List<PolygonProfileData> resolvedHoles = holes == null ? List.of() : List.copyOf(holes);
        return new PlanarRegionData(outer, resolvedHoles, resolvedPlane);
    }

    public boolean hasHoles() {
        return !holes.isEmpty();
    }

    public int holeCount() {
        return holes.size();
    }

    public List<PolygonProfileData> holes() {
        return holes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlanarRegionData that)) return false;
        return Objects.equals(outer, that.outer)
            && Objects.equals(holes, that.holes)
            && Objects.equals(plane, that.plane);
    }

    @Override
    public int hashCode() {
        return Objects.hash(outer, holes, plane);
    }

    @Override
    public @NonNull String toString() {
        return "PlanarRegionData{outerEdges=" + outer.getEdgeCount()
            + ", holes=" + holes.size() + "}";
    }
}

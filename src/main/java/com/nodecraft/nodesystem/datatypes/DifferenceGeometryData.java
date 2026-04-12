package com.nodecraft.nodesystem.datatypes;

import java.util.Objects;

/**
 * Geometry value that represents a voxel-evaluated boolean difference.
 * The minuend is evaluated first, then all subtrahend voxels are removed.
 */
public final class DifferenceGeometryData implements GeometryData {

    private final GeometryData minuend;
    private final GeometryData subtrahend;

    public DifferenceGeometryData(GeometryData minuend, GeometryData subtrahend) {
        this.minuend = Objects.requireNonNull(minuend, "minuend");
        this.subtrahend = Objects.requireNonNull(subtrahend, "subtrahend");
    }

    public GeometryData getMinuend() {
        return minuend;
    }

    public GeometryData getSubtrahend() {
        return subtrahend;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DifferenceGeometryData that)) return false;
        return Objects.equals(minuend, that.minuend) && Objects.equals(subtrahend, that.subtrahend);
    }

    @Override
    public int hashCode() {
        return Objects.hash(minuend, subtrahend);
    }

    @Override
    public String toString() {
        return "DifferenceGeometryData{minuend=" + minuend + ", subtrahend=" + subtrahend + "}";
    }
}

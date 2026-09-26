package com.nodecraft.nodesystem.datatypes;

import net.minecraft.util.math.Vec3d;
import org.jspecify.annotations.NonNull;

import java.util.Objects;

/**
 * Represents a line segment defined by a start and end point.
 */
public record LineData(Vec3d start, Vec3d end) {
    public LineData(Vec3d start, Vec3d end) {
        // Use non-null defaults if needed, or throw exception
        this.start = Objects.requireNonNull(start, "Line start point cannot be null");
        this.end = Objects.requireNonNull(end, "Line end point cannot be null");
    }

    public double getLength() {
        return start.distanceTo(end);
    }

    public double getLengthSquared() {
        return start.squaredDistanceTo(end);
    }

    public Vec3d getDirection() {
        return end.subtract(start).normalize(); // Return normalized direction vector
    }

    public Vec3d getVector() {
        return end.subtract(start); // Return the full vector from start to end
    }

    @Override
    public @NonNull String toString() {
        return "Line[" + start + " -> " + end + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LineData lineData = (LineData) o;
        // Consider lines equal if start/end match or if they are reversed
        return (Objects.equals(start, lineData.start) && Objects.equals(end, lineData.end)) ||
                (Objects.equals(start, lineData.end) && Objects.equals(end, lineData.start));
    }

    @Override
    public int hashCode() {
        // Hash code should be consistent for reversed lines too
        // XORing hash codes is a simple way to achieve order-independence
        return Objects.hash(start) ^ Objects.hash(end);
    }
} 
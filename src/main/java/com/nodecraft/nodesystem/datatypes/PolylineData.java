package com.nodecraft.nodesystem.datatypes;

import net.minecraft.util.math.Vec3d;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Represents a polyline defined by a sequence of points.
 */
public record PolylineData(List<Vec3d> points) {
    /**
     * Creates a PolylineData instance.
     *
     * @param points The list of points defining the polyline. The list is copied.
     * @throws NullPointerException     if the points list or any point within it is null.
     * @throws IllegalArgumentException if the list contains fewer than 2 points.
     */
    public PolylineData(List<Vec3d> points) {
        Objects.requireNonNull(points, "Points list cannot be null");
        if (points.size() < 2) {
            throw new IllegalArgumentException("Polyline must have at least 2 points.");
        }
        // Defensive copy and null check elements
        this.points = new ArrayList<>(points.size());
        for (Vec3d point : points) {
            this.points.add(Objects.requireNonNull(point, "Point in polyline cannot be null"));
        }
    }

    /**
     * Gets an unmodifiable view of the points in the polyline.
     *
     * @return Unmodifiable list of points.
     */
    @Override
    public List<Vec3d> points() {
        return Collections.unmodifiableList(points);
    }

    public int getPointCount() {
        return points.size();
    }

    public int getSegmentCount() {
        return points.isEmpty() ? 0 : points.size() - 1;
    }

    /**
     * Gets the total length of the polyline.
     *
     * @return The length.
     */
    public double getLength() {
        double length = 0.0;
        for (int i = 0; i < getSegmentCount(); i++) {
            length += points.get(i).distanceTo(points.get(i + 1));
        }
        return length;
    }

    /**
     * Checks if the polyline is closed (start point equals end point).
     *
     * @return true if closed, false otherwise.
     */
    public boolean isClosed() {
        return points.size() > 1 && points.getFirst().equals(points.getLast());
    }

    @Override
    public @NonNull String toString() {
        return "Polyline[" + points.stream().map(Vec3d::toString).collect(Collectors.joining(", ")) + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PolylineData that = (PolylineData) o;
        return Objects.equals(points, that.points);
    }

}
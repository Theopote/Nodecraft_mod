package com.nodecraft.nodesystem.datatypes;

import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a lightweight planar polygon profile for construct/modeling workflows.
 * The stored point list is ordered and closed: the last point repeats the first point.
 */
public record PolygonProfileData(List<Vector3d> closedPoints, PlaneData plane) {
    public PolygonProfileData(List<Vector3d> closedPoints, PlaneData plane) {
        if (closedPoints == null || closedPoints.size() < 4) {
            throw new IllegalArgumentException("Polygon profile requires at least 3 unique points plus closure");
        }
        if (plane == null) {
            throw new IllegalArgumentException("Polygon profile requires a plane");
        }

        List<Vector3d> copiedPoints = new ArrayList<>(closedPoints.size());
        for (Vector3d point : closedPoints) {
            copiedPoints.add(new Vector3d(Objects.requireNonNull(point, "Polygon point cannot be null")));
        }

        Vector3d first = copiedPoints.getFirst();
        Vector3d last = copiedPoints.getLast();
        if (first.distance(last) > 1.0e-6d) {
            throw new IllegalArgumentException("Polygon profile points must be closed");
        }

        this.closedPoints = List.copyOf(copiedPoints);
        this.plane = plane;
    }

    @Override
    public List<Vector3d> closedPoints() {
        List<Vector3d> copied = new ArrayList<>(closedPoints.size());
        for (Vector3d point : closedPoints) {
            copied.add(new Vector3d(point));
        }
        return List.copyOf(copied);
    }

    public List<Vector3d> getUniquePoints() {
        List<Vector3d> unique = new ArrayList<>(Math.max(0, closedPoints.size() - 1));
        for (int i = 0; i < closedPoints.size() - 1; i++) {
            unique.add(new Vector3d(closedPoints.get(i)));
        }
        return List.copyOf(unique);
    }

    public PolylineData getBoundary() {
        List<Vec3d> points = new ArrayList<>(closedPoints.size());
        for (Vector3d point : closedPoints) {
            points.add(new Vec3d(point.x, point.y, point.z));
        }
        return new PolylineData(points);
    }

    public int getEdgeCount() {
        return Math.max(0, closedPoints.size() - 1);
    }

    public Vector3d getCenter() {
        List<Vector3d> unique = getUniquePoints();
        Vector3d center = new Vector3d();
        for (Vector3d point : unique) {
            center.add(point);
        }
        return unique.isEmpty() ? center : center.div(unique.size());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PolygonProfileData that)) return false;
        return Objects.equals(closedPoints, that.closedPoints) && Objects.equals(plane, that.plane);
    }

    @Override
    public @NonNull String toString() {
        return "PolygonProfileData{edges=" + getEdgeCount() + "}";
    }
}

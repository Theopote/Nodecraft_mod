package com.nodecraft.nodesystem.datatypes;

import com.nodecraft.nodesystem.util.PointUtils;
import com.nodecraft.nodesystem.util.PolygonProfileValidator;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a lightweight planar polygon profile for construct/modeling workflows.
 * <p>
 * Canonical internal representation: ordered closed point list with an exact
 * repeated first vertex at the end ({@code [p0, p1, ..., pn-1, p0]}).
 * POLYGON_PROFILE is a simple planar loop without holes.
 * Vertex order is preserved (no winding rewrite); signed area vs the plane normal
 * is orientation. Historical Graph V73 residue.
 */
public record PolygonProfileData(List<Vector3d> closedPoints, PlaneData plane) {
    public PolygonProfileData(List<Vector3d> closedPoints, PlaneData plane) {
        if (closedPoints == null || closedPoints.size() < 4) {
            throw new IllegalArgumentException("Polygon profile requires at least 3 unique points plus closure");
        }
        if (plane == null) {
            throw new IllegalArgumentException("Polygon profile requires a plane");
        }

        List<Vector3d> canonical = PolygonProfileValidator.canonicalizeClosedPoints(closedPoints);
        String validationError = PolygonProfileValidator.validateConstruction(canonical, plane);
        if (validationError != null) {
            throw new IllegalArgumentException(validationError);
        }

        this.closedPoints = canonical;
        this.plane = plane.normalized() != null ? plane.normalized() : plane;
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

    public PathData getBoundaryPath() {
        return PathData.fromPolyline(getBoundary());
    }

    public int getEdgeCount() {
        return Math.max(0, closedPoints.size() - 1);
    }

    public Vector3d getCenter() {
        Vector3d center = PointUtils.safeListCenter(getUniquePoints());
        if (center == null) {
            throw new IllegalArgumentException("Polygon profile center is non-finite");
        }
        return center;
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

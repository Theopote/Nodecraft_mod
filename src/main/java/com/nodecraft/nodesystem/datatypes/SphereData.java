package com.nodecraft.nodesystem.datatypes;

import org.joml.Vector3d;
import org.jspecify.annotations.NonNull;

import java.util.Objects;

public record SphereData(Vector3d center, double radius) implements GeometryData {
    public SphereData(Vector3d center, double radius) {
        if (radius < 0) {
            throw new IllegalArgumentException("Radius cannot be negative");
        }
        this.center = new Vector3d(center); // Defensive copy
        this.radius = radius;
    }

    @Override
    public Vector3d center() {
        return new Vector3d(center); // Return defensive copy
    }

    @Override
    public @NonNull String toString() {
        return "Sphere[center=" + center + ", radius=" + radius + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SphereData that = (SphereData) o;
        return Double.compare(that.radius, radius) == 0 && Objects.equals(center, that.center);
    }

}

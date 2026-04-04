package com.nodecraft.nodesystem.datatypes;

import org.joml.Vector3d;

import java.util.Objects;

public class SphereData implements GeometryData {
    private final Vector3d center;
    private final double radius;

    public SphereData(Vector3d center, double radius) {
        if (radius < 0) {
            throw new IllegalArgumentException("Radius cannot be negative");
        }
        this.center = new Vector3d(center); // Defensive copy
        this.radius = radius;
    }

    public Vector3d getCenter() {
        return new Vector3d(center); // Return defensive copy
    }

    public double getRadius() {
        return radius;
    }

    @Override
    public String toString() {
        return "Sphere[center=" + center + ", radius=" + radius + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SphereData that = (SphereData) o;
        return Double.compare(that.radius, radius) == 0 && Objects.equals(center, that.center);
    }

    @Override
    public int hashCode() {
        return Objects.hash(center, radius);
    }
} 

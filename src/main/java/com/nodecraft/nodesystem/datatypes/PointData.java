package com.nodecraft.nodesystem.datatypes;

import org.joml.Vector3d;
import org.jspecify.annotations.NonNull;

import java.util.Objects;

public record PointData(Vector3d position) {
    public PointData(Vector3d position) {
        this.position = new Vector3d(position); // Defensive copy
    }

    public PointData(double x, double y, double z) {
        this(new Vector3d(x, y, z));
    }

    @Override
    public Vector3d position() {
        return new Vector3d(position); // Return defensive copy
    }

    public double getX() {
        return position.x;
    }

    public double getY() {
        return position.y;
    }

    public double getZ() {
        return position.z;
    }

    @Override
    public @NonNull String toString() {
        return "Point[" + position.x + ", " + position.y + ", " + position.z + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PointData pointData = (PointData) o;
        return Objects.equals(position, pointData.position);
    }

}
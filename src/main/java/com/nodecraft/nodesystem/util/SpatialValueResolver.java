package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.PointData;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Normalizes common spatial value representations into JOML vectors so geometry nodes can
 * consistently consume world positions, centers, and point-like values.
 * <p>
 * Graph language uses {@code POINT} / {@code POINT_LIST}; internal algorithms typically consume
 * {@link Vector3d}. Prefer {@link #resolvePointList(Object)} at list-input boundaries so
 * {@link PointData} and legacy {@link Vector3d} entries both work.
 */
public final class SpatialValueResolver {
    private SpatialValueResolver() {
    }

    /** Resolves a graph {@code POINT} or location-like value to a continuous position. */
    public static @Nullable Vector3d resolvePoint(@Nullable Object value) {
        return resolveVector3d(value);
    }

    /** Resolves a graph {@code VECTOR} or direction/displacement value. */
    public static @Nullable Vector3d resolveVector(@Nullable Object value) {
        return resolveVector3d(value);
    }

    public static @Nullable Vector3d resolveVector3d(@Nullable Object value) {
        if (value instanceof PointData pointData) {
            return pointData.getPosition();
        }
        if (value instanceof Coordinate coordinate) {
            return new Vector3d(coordinate.getX(), coordinate.getY(), coordinate.getZ());
        }
        if (value instanceof Vector3 vector) {
            return new Vector3d(vector.getX(), vector.getY(), vector.getZ());
        }
        if (value instanceof Vector3d vector) {
            return new Vector3d(vector);
        }
        if (value instanceof Vec3d vec3d) {
            return new Vector3d(vec3d.x, vec3d.y, vec3d.z);
        }
        if (value instanceof BlockPos blockPos) {
            return new Vector3d(blockPos.getX(), blockPos.getY(), blockPos.getZ());
        }
        return null;
    }

    /**
     * Resolves a collection of point-like entries into continuous locations for algorithms.
     * Accepts {@link PointData}, {@link Vector3d}, legacy position/vector wrappers, and
     * {@link BlockPos} (integer corner as continuous xyz). Unknown entries are skipped.
     */
    public static List<Vector3d> resolvePointList(@Nullable Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return List.of();
        }
        List<Vector3d> points = new ArrayList<>(collection.size());
        for (Object entry : collection) {
            Vector3d resolved = resolveVector3d(entry);
            if (resolved != null
                    && Double.isFinite(resolved.x)
                    && Double.isFinite(resolved.y)
                    && Double.isFinite(resolved.z)) {
                points.add(resolved);
            }
        }
        return points;
    }

    /** Converts continuous locations to graph-facing {@link PointData} values. */
    public static List<PointData> toPointDataList(Collection<Vector3d> points) {
        if (points == null || points.isEmpty()) {
            return List.of();
        }
        List<PointData> out = new ArrayList<>(points.size());
        for (Vector3d point : points) {
            if (point != null) {
                out.add(new PointData(point));
            }
        }
        return List.copyOf(out);
    }

    public static @Nullable BlockPos resolveBlockPos(@Nullable Object value) {
        if (value instanceof BlockPos blockPos) {
            return blockPos;
        }
        Vector3d resolved = resolveVector3d(value);
        if (resolved == null) {
            return null;
        }
        return BlockPos.ofFloored(resolved.x, resolved.y, resolved.z);
    }
}

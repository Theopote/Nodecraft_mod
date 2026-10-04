package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.VectorData;
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
 * {@link Vector3d}. Graph-facing {@code POINT_LIST} ingest must use
 * {@link PointUtils#resolveStrictPointListBounded}; {@link #resolvePointList(Object)} is a
 * legacy helper that silently skips invalid entries.
 * <p>
 * Direction ports use {@link #resolveVector(Object)} (strict): points and block positions are
 * not treated as vectors.
 * <p>
 * <b>Spatial Convention v1:</b> {@link BlockPos} / {@link Coordinate} resolve to the block
 * <em>cell center</em> ({@code +0.5}). See {@link BlockSpace}. Cell-corner continuous xyz is only
 * via {@link BlockSpace#cellMinCorner(BlockPos)} for AABB/render — never as a POINT role.
 */
public final class SpatialValueResolver {
    private SpatialValueResolver() {
    }

    /** Resolves a graph {@code POINT} or location-like value to a continuous position. */
    public static @Nullable Vector3d resolvePoint(@Nullable Object value) {
        if (value instanceof PointData pointData) {
            return pointData.position();
        }
        if (value instanceof Coordinate coordinate) {
            // Coordinate is a block-grid alias → canonical Point is cell center.
            return BlockSpace.cellCenter(coordinate.x(), coordinate.y(), coordinate.z());
        }
        if (value instanceof Vector3 vector) {
            return new Vector3d(vector.x(), vector.y(), vector.z());
        }
        if (value instanceof Vector3d vector) {
            return new Vector3d(vector);
        }
        if (value instanceof Vec3d vec3d) {
            return new Vector3d(vec3d.x, vec3d.y, vec3d.z);
        }
        if (value instanceof BlockPos blockPos) {
            // Canonical BlockPos → Point = cell center (never the min corner).
            return BlockSpace.cellCenter(blockPos);
        }
        return null;
    }

    /**
     * Resolves a graph {@code VECTOR} port. Accepts {@link VectorData} only
     * (not JOML, {@link Vec3d}, points, or block positions).
     */
    public static @Nullable Vector3d resolveVector(@Nullable Object value) {
        return VectorUtils.toStrictVectorPortValue(value);
    }

    /**
     * Legacy shared resolver for point-like values. Prefer {@link #resolvePoint} or
     * {@link #resolveVector} by role on new call sites.
     */
    public static @Nullable Vector3d resolveVector3d(@Nullable Object value) {
        return resolvePoint(value);
    }

    /**
     * Legacy compatibility helper: resolves a collection of point-like entries into continuous
     * locations, <strong>silently skipping</strong> unknown or non-finite entries.
     * Do not use for new graph-facing {@code POINT_LIST} ingest — use
     * {@link PointUtils#resolveStrictPointListBounded} so one invalid item fails the whole list.
     */
    public static List<Vector3d> resolvePointList(@Nullable Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return List.of();
        }
        List<Vector3d> points = new ArrayList<>(collection.size());
        for (Object entry : collection) {
            Vector3d resolved = resolvePoint(entry);
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

    /**
     * Resolves a collection of direction/displacement values for {@code VECTOR_LIST} boundaries.
     * Strict: skips points and block positions.
     */
    public static List<Vector3d> resolveVectorList(@Nullable Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return List.of();
        }
        List<Vector3d> vectors = new ArrayList<>(collection.size());
        for (Object entry : collection) {
            Vector3d resolved = resolveVector(entry);
            if (resolved != null
                    && Double.isFinite(resolved.x)
                    && Double.isFinite(resolved.y)
                    && Double.isFinite(resolved.z)) {
                vectors.add(resolved);
            }
        }
        return vectors;
    }

    public static @Nullable BlockPos resolveBlockPos(@Nullable Object value) {
        if (value instanceof BlockPos blockPos) {
            return blockPos;
        }
        if (value instanceof Coordinate coordinate) {
            return new BlockPos(coordinate.x(), coordinate.y(), coordinate.z());
        }
        Vector3d resolved = resolvePoint(value);
        if (resolved == null) {
            return null;
        }
        // Point → BlockPos is floor snap (cell containing the point).
        return BlockSpace.pointToBlockFloor(resolved);
    }
}

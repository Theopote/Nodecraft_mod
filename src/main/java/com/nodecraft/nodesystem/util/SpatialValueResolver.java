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
 * <p>
 * Direction ports use {@link #resolveVector(Object)} (strict): points and block positions are
 * not treated as vectors.
 * <p>
 * {@link BlockPos} resolves to the cell <em>min corner</em> as continuous xyz (no +0.5).
 * For a geometric center on a picked block, use {@link BlockSpace#cellCenter(BlockPos)}
 * or the Block To Point node with center offset — see {@link BlockSpace}.
 */
public final class SpatialValueResolver {
    private SpatialValueResolver() {
    }

    /** Resolves a graph {@code POINT} or location-like value to a continuous position. */
    public static @Nullable Vector3d resolvePoint(@Nullable Object value) {
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
            // Cell min corner — not block center. See BlockSpace.
            return BlockSpace.cellMinCorner(blockPos);
        }
        return null;
    }

    /**
     * Resolves a graph {@code VECTOR} or direction/displacement value.
     * Strict: accepts only vector-like values, not {@link PointData} or {@link BlockPos}.
     */
    public static @Nullable Vector3d resolveVector(@Nullable Object value) {
        if (value instanceof Vector3d vector) {
            return new Vector3d(vector);
        }
        if (value instanceof Vec3d vec3d) {
            return new Vector3d(vec3d.x, vec3d.y, vec3d.z);
        }
        if (value instanceof Vector3 vector) {
            return new Vector3d(vector.getX(), vector.getY(), vector.getZ());
        }
        return null;
    }

    /**
     * Legacy shared resolver for point-like values. Prefer {@link #resolvePoint} or
     * {@link #resolveVector} by role on new call sites.
     */
    public static @Nullable Vector3d resolveVector3d(@Nullable Object value) {
        return resolvePoint(value);
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
        Vector3d resolved = resolvePoint(value);
        if (resolved == null) {
            return null;
        }
        return BlockPos.ofFloored(resolved.x, resolved.y, resolved.z);
    }
}

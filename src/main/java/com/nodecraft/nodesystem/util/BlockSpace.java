package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.RegionData;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

/**
 * NodeCraft Spatial Convention v1 — frozen coordinate contract.
 * <p>
 * <b>POINT</b> — continuous world geometry coordinates, e.g. {@code (10.5, 64.5, 20.5)}.
 * Used for sphere/box centers, pivots, frame origins.
 * <p>
 * <b>BLOCK_POS</b> — Minecraft voxel <em>cell index</em> {@code (10, 64, 20)}, meaning the unit cell
 * {@code [10,11) × [64,65) × [20,21)}. Not a geometric point.
 * <p>
 * <b>Canonical BlockPos → Point</b> — always the cell center:
 * {@code POINT = BLOCK_POS + (0.5, 0.5, 0.5)}. Never map to the cell corner as a POINT.
 * <p>
 * <b>Point → BlockPos</b> — explicit snap only. Placement / block-grid transforms use
 * {@link #pointToBlockFloor} / {@link #snapCellCenter} (containing cell). Do <em>not</em> use
 * {@link #pointToBlockNearest} for Placement — it is half-up integer rounding, not “nearest cell
 * center”, and breaks {@code cellCenter} round-trips ({@code (0.5,0.5,0.5) → (1,1,1)}).
 * Also available: {@link #pointToBlockCeil}.
 * <p>
 * <b>PreviewBlock / Apply</b> — store cell indices (min corner integers). Ghost draws
 * {@code [n, n+1]} without adding 0.5.
 * <p>
 * <b>Voxelization</b> — a cell is included when {@link #voxelSamplePoint(BlockPos)}
 * (the cell center) lies inside the continuous solid.
 */
public final class BlockSpace {

    public static final double CELL_CENTER_OFFSET = 0.5d;
    private static final double BOUNDARY_EPS = 1.0e-9d;

    private BlockSpace() {
    }

    // --- Cell geometry -------------------------------------------------------

    /** Alias of {@link #cellCenter(BlockPos)} — Spatial Convention v1 name. */
    public static Vector3d blockCenter(BlockPos cell) {
        return cellCenter(cell);
    }

    /** Continuous center of the block cell at {@code cell}. */
    public static Vector3d cellCenter(BlockPos cell) {
        return new Vector3d(
            cell.getX() + CELL_CENTER_OFFSET,
            cell.getY() + CELL_CENTER_OFFSET,
            cell.getZ() + CELL_CENTER_OFFSET
        );
    }

    public static Vector3d cellCenter(int x, int y, int z) {
        return new Vector3d(x + CELL_CENTER_OFFSET, y + CELL_CENTER_OFFSET, z + CELL_CENTER_OFFSET);
    }

    /** Nearest block cell index for a continuous axis coordinate. */
    public static int nearestCellIndex(double axis) {
        return (int) Math.round(axis - CELL_CENTER_OFFSET);
    }

    /** Nearest block cell index for a continuous axis coordinate. */
    public static BlockPos nearestCellBlockPos(Vector3d point) {
        if (point == null) {
            return BlockPos.ORIGIN;
        }
        return new BlockPos(
            nearestCellIndex(point.x),
            nearestCellIndex(point.y),
            nearestCellIndex(point.z)
        );
    }

    /** Continuous center of the nearest block cell to {@code point}. */
    public static Vector3d nearestCellCenter(Vector3d point) {
        return cellCenter(nearestCellBlockPos(point));
    }

    /** Whether {@code point} lies on the block cell-center lattice within {@code tolerance}. */
    public static boolean isCellCenter(Vector3d point, double tolerance) {
        if (point == null) {
            return false;
        }
        Vector3d nearest = nearestCellCenter(point);
        return point.distance(nearest) <= Math.max(0.0d, tolerance);
    }

    /** Offset from the nearest block cell center to {@code point}. */
    public static Vector3d offsetFromNearestCellCenter(Vector3d point) {
        if (point == null) {
            return new Vector3d();
        }
        Vector3d nearest = nearestCellCenter(point);
        return new Vector3d(point.x - nearest.x, point.y - nearest.y, point.z - nearest.z);
    }

    /**
     * Sample point used by voxelizers for membership tests — the cell center.
     * Same as {@link #cellCenter(BlockPos)}.
     */
    public static Vector3d voxelSamplePoint(BlockPos cell) {
        return cellCenter(cell);
    }

    public static Vector3d voxelSamplePoint(int x, int y, int z) {
        return cellCenter(x, y, z);
    }

    /** Alias of {@link #cellMinCorner(BlockPos)}. */
    public static Vector3d blockCellMin(BlockPos cell) {
        return cellMinCorner(cell);
    }

    /** Cell min corner as continuous xyz (no +0.5). For AABB/render only — not a POINT role. */
    public static Vector3d cellMinCorner(BlockPos cell) {
        return new Vector3d(cell.getX(), cell.getY(), cell.getZ());
    }

    /** Exclusive max corner of the cell AABB: {@code (x+1, y+1, z+1)}. */
    public static Vector3d blockCellMax(BlockPos cell) {
        return new Vector3d(cell.getX() + 1.0d, cell.getY() + 1.0d, cell.getZ() + 1.0d);
    }

    // --- Point → Block snap --------------------------------------------------

    /** Snap a continuous point to the cell that contains it ({@code floor}). */
    public static BlockPos cellContaining(double x, double y, double z) {
        return BlockPos.ofFloored(x, y, z);
    }

    public static BlockPos cellContaining(Vector3d point) {
        return cellContaining(point.x, point.y, point.z);
    }

    public static BlockPos pointToBlockFloor(Vector3d point) {
        return cellContaining(point);
    }

    /**
     * Snap a continuous point that represents a transformed <em>cell center</em> back to a
     * {@code BLOCK_POS}. Alias of {@link #pointToBlockFloor} — Placement v1 canonical rule.
     * <p>
     * Pipeline: {@code BLOCK_POS → cellCenter → continuous transform → snapCellCenter → BLOCK_POS}.
     */
    public static BlockPos snapCellCenter(Vector3d continuousPoint) {
        return pointToBlockFloor(continuousPoint);
    }

    /**
     * Half-up integer rounding per axis. <b>Not</b> “nearest cell center”.
     * {@code Math.round(0.5) = 1}, so {@code cellCenter(0,0,0)} does not round-trip.
     * Prefer {@link #snapCellCenter} / {@link #pointToBlockFloor} for Placement.
     */
    public static BlockPos pointToBlockNearest(Vector3d point) {
        return new BlockPos(
            (int) Math.round(point.x),
            (int) Math.round(point.y),
            (int) Math.round(point.z)
        );
    }

    public static BlockPos pointToBlockCeil(Vector3d point) {
        return new BlockPos(
            (int) Math.ceil(point.x - BOUNDARY_EPS),
            (int) Math.ceil(point.y - BOUNDARY_EPS),
            (int) Math.ceil(point.z - BOUNDARY_EPS)
        );
    }

    // --- Continuous AABB → inclusive cells -----------------------------------

    /**
     * Inclusive block region covering every cell whose center lies in the closed continuous AABB
     * {@code [minX, maxX] × [minY, maxY] × [minZ, maxZ]}.
     * <p>
     * Example: center {@code (10.5, 64.5, 10.5)}, half extents {@code 2.5} → continuous
     * {@code [8, 13]} → cells {@code 8..12} (5 blocks, odd size on block centers).
     */
    public static @Nullable RegionData inclusiveRegionFromClosedAabb(
        double minX, double minY, double minZ,
        double maxX, double maxY, double maxZ
    ) {
        if (!Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(minZ)
            || !Double.isFinite(maxX) || !Double.isFinite(maxY) || !Double.isFinite(maxZ)) {
            return new RegionData(null, null);
        }
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            return new RegionData(null, null);
        }

        BlockPos minCorner = BlockPos.ofFloored(minX, minY, minZ);
        BlockPos maxCorner = BlockPos.ofFloored(
            maxX - BOUNDARY_EPS,
            maxY - BOUNDARY_EPS,
            maxZ - BOUNDARY_EPS
        );
        if (minCorner.getX() > maxCorner.getX()
            || minCorner.getY() > maxCorner.getY()
            || minCorner.getZ() > maxCorner.getZ()) {
            return new RegionData(null, null);
        }
        return new RegionData(minCorner, maxCorner);
    }

    /**
     * Inclusive region for an axis-aligned continuous box defined by center + half extents.
     */
    public static RegionData inclusiveRegionFromCenterHalfExtents(Vector3d center, Vector3d halfExtents) {
        return inclusiveRegionFromClosedAabb(
            center.x - halfExtents.x,
            center.y - halfExtents.y,
            center.z - halfExtents.z,
            center.x + halfExtents.x,
            center.y + halfExtents.y,
            center.z + halfExtents.z
        );
    }

    /**
     * Whether the center of cell {@code (x,y,z)} lies inside an axis-aligned closed box.
     */
    public static boolean cellCenterInsideAxisAlignedBox(
        int x, int y, int z,
        Vector3d center,
        Vector3d halfExtents
    ) {
        Vector3d sample = voxelSamplePoint(x, y, z);
        return Math.abs(sample.x - center.x) <= halfExtents.x + BOUNDARY_EPS
            && Math.abs(sample.y - center.y) <= halfExtents.y + BOUNDARY_EPS
            && Math.abs(sample.z - center.z) <= halfExtents.z + BOUNDARY_EPS;
    }
}

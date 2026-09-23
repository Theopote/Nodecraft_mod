package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.RegionData;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

/**
 * Frozen coordinate contract between continuous geometry and Minecraft block cells.
 * <p>
 * <b>Block cell</b> — integer {@link BlockPos} {@code (n)}; world AABB {@code [n, n+1)}.
 * <p>
 * <b>Block center</b> — continuous point {@code (n + 0.5, n + 0.5, n + 0.5)}.
 * Prefer this when a picked block is used as the center of a box/sphere/etc.
 * <p>
 * <b>Continuous geometry</b> — doubles in world space. Snap only when entering
 * {@code BLOCK_LIST} / Apply / preview block payloads.
 * <p>
 * <b>PreviewBlock / placement</b> — store <em>cell min corner</em> (integer), matching Ghost draw
 * of {@code [p, p+1]}.
 * <p>
 * <b>Voxelization</b> — a cell is selected when its <em>center</em> lies inside the continuous solid
 * (closed bounds). For an axis-aligned solid AABB {@code [min, max]} this is equivalent to
 * inclusive cells {@code [floor(min), floor(max - eps)]}.
 */
public final class BlockSpace {

    public static final double CELL_CENTER_OFFSET = 0.5d;
    private static final double BOUNDARY_EPS = 1.0e-9d;

    private BlockSpace() {
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

    /** Cell min corner as continuous xyz (no +0.5). */
    public static Vector3d cellMinCorner(BlockPos cell) {
        return new Vector3d(cell.getX(), cell.getY(), cell.getZ());
    }

    /** Snap a continuous point to the cell that contains it ({@code floor}). */
    public static BlockPos cellContaining(double x, double y, double z) {
        return BlockPos.ofFloored(x, y, z);
    }

    public static BlockPos cellContaining(Vector3d point) {
        return cellContaining(point.x, point.y, point.z);
    }

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
        double cx = x + CELL_CENTER_OFFSET;
        double cy = y + CELL_CENTER_OFFSET;
        double cz = z + CELL_CENTER_OFFSET;
        return Math.abs(cx - center.x) <= halfExtents.x + BOUNDARY_EPS
            && Math.abs(cy - center.y) <= halfExtents.y + BOUNDARY_EPS
            && Math.abs(cz - center.z) <= halfExtents.z + BOUNDARY_EPS;
    }
}

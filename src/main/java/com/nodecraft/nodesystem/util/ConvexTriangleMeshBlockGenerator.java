package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.RegionData;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3d;

/**
 * Fills voxels inside a convex polyhedron given as triangle soup (e.g. Platonic solids from three.js-style data).
 */
public final class ConvexTriangleMeshBlockGenerator {

    private ConvexTriangleMeshBlockGenerator() {
    }

    public static RegionData createBoundingRegion(Vector3d center, Vector3d[] localVertices) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        for (Vector3d lv : localVertices) {
            double wx = center.x + lv.x;
            double wy = center.y + lv.y;
            double wz = center.z + lv.z;
            minX = Math.min(minX, wx);
            minY = Math.min(minY, wy);
            minZ = Math.min(minZ, wz);
            maxX = Math.max(maxX, wx);
            maxY = Math.max(maxY, wy);
            maxZ = Math.max(maxZ, wz);
        }

        return new RegionData(
            BlockPos.ofFloored(minX - 1.0d, minY - 1.0d, minZ - 1.0d),
            BlockPos.ofFloored(maxX + 1.0d, maxY + 1.0d, maxZ + 1.0d)
        );
    }

    /**
     * @param center     world-space center offset for {@code localVertices}
     * @param localVertices vertex positions relative to {@code center}
     * @param triangleIndices length multiple of 3; each triple is one triangular face (CCW arbitrary)
     */
    public static void populateConvexHull(BlockPosList blocks,
                                          RegionData region,
                                          Vector3d center,
                                          Vector3d[] localVertices,
                                          int[] triangleIndices) {
        if (blocks == null || region == null || !region.isComplete() || localVertices == null || triangleIndices == null) {
            return;
        }

        BlockPos minCorner = region.getMinCorner();
        BlockPos maxCorner = region.getMaxCorner();
        if (minCorner == null || maxCorner == null) {
            return;
        }

        int faceCount = triangleIndices.length / 3;
        double[][] normals = new double[faceCount][3];
        double[] dValues = new double[faceCount];

        for (int f = 0; f < faceCount; f++) {
            int ia = triangleIndices[f * 3];
            int ib = triangleIndices[f * 3 + 1];
            int ic = triangleIndices[f * 3 + 2];
            Vector3d a = localVertices[ia];
            Vector3d b = localVertices[ib];
            Vector3d c = localVertices[ic];

            double abx = b.x - a.x;
            double aby = b.y - a.y;
            double abz = b.z - a.z;
            double acx = c.x - a.x;
            double acy = c.y - a.y;
            double acz = c.z - a.z;

            normals[f][0] = aby * acz - abz * acy;
            normals[f][1] = abz * acx - abx * acz;
            normals[f][2] = abx * acy - aby * acx;

            dValues[f] = normals[f][0] * a.x + normals[f][1] * a.y + normals[f][2] * a.z;

            if (-dValues[f] < 0.0d) {
                normals[f][0] = -normals[f][0];
                normals[f][1] = -normals[f][1];
                normals[f][2] = -normals[f][2];
                dValues[f] = -dValues[f];
            }
        }

        for (int x = minCorner.getX(); x <= maxCorner.getX(); x++) {
            for (int y = minCorner.getY(); y <= maxCorner.getY(); y++) {
                for (int z = minCorner.getZ(); z <= maxCorner.getZ(); z++) {
                    double dx = x - center.x;
                    double dy = y - center.y;
                    double dz = z - center.z;

                    boolean inside = true;
                    for (int f = 0; f < faceCount; f++) {
                        double dot = normals[f][0] * dx + normals[f][1] * dy + normals[f][2] * dz;
                        if (dot > dValues[f] + 0.5d) {
                            inside = false;
                            break;
                        }
                    }
                    if (inside) {
                        blocks.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
    }
}

package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.datatypes.TetrahedronGeometryData;
import net.minecraft.util.math.BlockPos;
import org.joml.Matrix3d;
import org.joml.Vector3d;
import org.jspecify.annotations.NonNull;

public final class TetrahedronBlockGenerator {

    private TetrahedronBlockGenerator() {
    }

    public static RegionData createBoundingRegion(TetrahedronGeometryData geometry) {
        Vector3d center = geometry.getCenter();
        double[][] offsets = getRotatedLocalOffsets(geometry);
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (double[] o : offsets) {
            double wx = center.x + o[0];
            double wy = center.y + o[1];
            double wz = center.z + o[2];
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

    public static void populateTetrahedron(BlockPosList blocks,
                                           RegionData region,
                                           TetrahedronGeometryData geometry) {
        if (blocks == null || region == null || !region.isComplete() || geometry == null) {
            return;
        }

        BlockPos minCorner = region.getMinCorner();
        BlockPos maxCorner = region.getMaxCorner();
        if (minCorner == null || maxCorner == null) {
            return;
        }

        Vector3d center = geometry.getCenter();
        double[][] vertices = getRotatedLocalOffsets(geometry);

        int[][] faces = {{1, 2, 3}, {0, 3, 2}, {0, 1, 3}, {0, 2, 1}};
        double[][] normals = new double[4][3];
        double[] dValues = new double[4];

        for (int f = 0; f < 4; f++) {
            double[] a = vertices[faces[f][0]];
            double[] b = vertices[faces[f][1]];
            double[] c = vertices[faces[f][2]];

            double[] ab = {b[0] - a[0], b[1] - a[1], b[2] - a[2]};
            double[] ac = {c[0] - a[0], c[1] - a[1], c[2] - a[2]};

            normals[f][0] = ab[1] * ac[2] - ab[2] * ac[1];
            normals[f][1] = ab[2] * ac[0] - ab[0] * ac[2];
            normals[f][2] = ab[0] * ac[1] - ab[1] * ac[0];
            dValues[f] = normals[f][0] * a[0] + normals[f][1] * a[1] + normals[f][2] * a[2];

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
                    for (int f = 0; f < 4; f++) {
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

    private static double[] @NonNull [] getRotatedLocalOffsets(TetrahedronGeometryData geometry) {
        double[][] canon = getCanonicalLocalDoubles(geometry.getEdgeLength());
        Matrix3d r = geometry.getOrientationMatrix();
        Vector3d tmp = new Vector3d();
        double[][] out = new double[4][3];
        for (int i = 0; i < 4; i++) {
            tmp.set(canon[i][0], canon[i][1], canon[i][2]);
            r.transform(tmp);
            out[i][0] = tmp.x;
            out[i][1] = tmp.y;
            out[i][2] = tmp.z;
        }
        return out;
    }

    private static double[] @NonNull [] getCanonicalLocalDoubles(double edgeLength) {
        double circumR = edgeLength * Math.sqrt(6.0d) / 4.0d;

        double h = circumR;
        double hBottom = circumR / 3.0d;
        double frontZ = 2.0d * circumR * Math.sqrt(2.0d) / 3.0d;
        double backZ = -circumR * Math.sqrt(2.0d) / 3.0d;
        double sideX = circumR * Math.sqrt(6.0d) / 3.0d;

        return new double[][]{
            {0.0d, h, 0.0d},
            {0.0d, -hBottom, frontZ},
            {-sideX, -hBottom, backZ},
            {sideX, -hBottom, backZ}
        };
    }
}

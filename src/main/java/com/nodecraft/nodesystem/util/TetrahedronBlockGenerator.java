package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.datatypes.TetrahedronGeometryData;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3d;

public final class TetrahedronBlockGenerator {

    private TetrahedronBlockGenerator() {
    }

    public static RegionData createBoundingRegion(TetrahedronGeometryData geometry) {
        double circumR = geometry.getCircumradius();
        Vector3d center = geometry.getCenter();
        return new RegionData(
            BlockPos.ofFloored(center.x - circumR - 1.0d, center.y - circumR - 1.0d, center.z - circumR - 1.0d),
            BlockPos.ofFloored(center.x + circumR + 1.0d, center.y + circumR + 1.0d, center.z + circumR + 1.0d)
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
        double edgeLength = geometry.getEdgeLength();
        double circumR = edgeLength * Math.sqrt(6.0d) / 4.0d;

        double h = circumR;
        double hBottom = circumR / 3.0d;
        double frontZ = 2.0d * circumR * Math.sqrt(2.0d) / 3.0d;
        double backZ = -circumR * Math.sqrt(2.0d) / 3.0d;
        double sideX = circumR * Math.sqrt(6.0d) / 3.0d;

        double[][] vertices = {
            {0.0d, h, 0.0d},
            {0.0d, -hBottom, frontZ},
            {-sideX, -hBottom, backZ},
            {sideX, -hBottom, backZ}
        };

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
}

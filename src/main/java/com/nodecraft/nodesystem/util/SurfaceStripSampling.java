package com.nodecraft.nodesystem.util;

import org.joml.Vector3d;

import java.util.List;
import java.util.Random;

/**
 * Surface strip topology validation and area-weighted quad sampling.
 */
public final class SurfaceStripSampling {

    private SurfaceStripSampling() {
    }

    public static boolean hasValidTopology(List<List<Vector3d>> sections) {
        if (sections == null || sections.size() < 2) {
            return false;
        }
        int expected = sections.getFirst().size();
        if (expected < 2) {
            return false;
        }
        for (List<Vector3d> section : sections) {
            if (section == null || section.size() != expected) {
                return false;
            }
        }
        return true;
    }

    /**
     * Samples a random point on a strip quad using two triangles (abc) and (bdc).
     */
    public static Vector3d samplePointOnStrip(List<List<Vector3d>> sections, Random random) {
        int sectionCount = sections.size();
        int pointsPerSection = sections.getFirst().size();

        int s = random.nextInt(sectionCount - 1);
        int i = random.nextInt(pointsPerSection);
        int nextI = (i + 1) % pointsPerSection;

        Vector3d a = sections.get(s).get(i);
        Vector3d b = sections.get(s).get(nextI);
        Vector3d c = sections.get(s + 1).get(i);
        Vector3d d = sections.get(s + 1).get(nextI);

        double trianglePick = random.nextDouble();
        double areaAbc = triangleArea(a, b, c);
        double areaBdc = triangleArea(b, d, c);
        double totalArea = areaAbc + areaBdc;
        if (totalArea <= 1.0e-12d) {
            return new Vector3d(a);
        }

        if (trianglePick * totalArea < areaAbc) {
            return sampleTriangle(a, b, c, random);
        }
        return sampleTriangle(b, d, c, random);
    }

    private static Vector3d sampleTriangle(Vector3d a, Vector3d b, Vector3d c, Random random) {
        double u = random.nextDouble();
        double v = random.nextDouble();
        if (u + v > 1.0d) {
            u = 1.0d - u;
            v = 1.0d - v;
        }
        return new Vector3d(a)
            .add(new Vector3d(b).sub(a).mul(u))
            .add(new Vector3d(c).sub(a).mul(v));
    }

    private static double triangleArea(Vector3d a, Vector3d b, Vector3d c) {
        Vector3d ab = new Vector3d(b).sub(a);
        Vector3d ac = new Vector3d(c).sub(a);
        return ab.cross(ac).length() * 0.5d;
    }
}

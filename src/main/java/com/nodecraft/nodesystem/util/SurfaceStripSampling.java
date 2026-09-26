package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.SurfaceStripData;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Surface strip topology validation and globally area-weighted quad sampling.
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
     * Returns the number of strip quads used for globally area-weighted sampling.
     */
    public static int countQuads(SurfaceStripData strip) {
        return QuadCatalog.from(strip).size();
    }

    /**
     * Samples a random point on a strip using globally area-weighted quad selection.
     */
    public static Vector3d samplePointOnStrip(SurfaceStripData strip, Random random) {
        return QuadCatalog.from(strip).sample(random);
    }

    /**
     * Precomputed quad catalog for repeated sampling on the same strip.
     */
    public static final class QuadCatalog {
        private final List<StripQuad> quads;
        private final double[] cumulativeAreas;

        private QuadCatalog(List<StripQuad> quads, double[] cumulativeAreas) {
            this.quads = quads;
            this.cumulativeAreas = cumulativeAreas;
        }

        public static QuadCatalog from(SurfaceStripData strip) {
            List<List<Vector3d>> sections = strip.sections();
            boolean closed = strip.areAllSectionsClosed();
            int pointsPerSection = sections.getFirst().size();
            int segmentCount = closed ? pointsPerSection : pointsPerSection - 1;
            if (segmentCount <= 0) {
                return new QuadCatalog(List.of(), new double[0]);
            }

            List<StripQuad> quads = new ArrayList<>((sections.size() - 1) * segmentCount);
            for (int s = 0; s < sections.size() - 1; s++) {
                for (int i = 0; i < segmentCount; i++) {
                    int nextI = closed ? (i + 1) % pointsPerSection : i + 1;
                    Vector3d a = sections.get(s).get(i);
                    Vector3d b = sections.get(s).get(nextI);
                    Vector3d c = sections.get(s + 1).get(i);
                    Vector3d d = sections.get(s + 1).get(nextI);
                    double area = triangleArea(a, b, c) + triangleArea(b, d, c);
                    if (area > 1.0e-12d) {
                        quads.add(new StripQuad(a, b, c, d, area));
                    }
                }
            }

            double[] cumulativeAreas = new double[quads.size()];
            double acc = 0.0d;
            for (int i = 0; i < quads.size(); i++) {
                acc += quads.get(i).totalArea();
                cumulativeAreas[i] = acc;
            }
            return new QuadCatalog(List.copyOf(quads), cumulativeAreas);
        }

        public int size() {
            return quads.size();
        }

        public Vector3d sample(Random random) {
            if (quads.isEmpty()) {
                return new Vector3d();
            }

            StripQuad quad = pickQuad(random);
            double areaAbc = triangleArea(quad.a(), quad.b(), quad.c());
            double areaBdc = triangleArea(quad.b(), quad.d(), quad.c());
            double total = areaAbc + areaBdc;
            if (total <= 1.0e-12d) {
                return new Vector3d(quad.a());
            }
            if (random.nextDouble() * total < areaAbc) {
                return sampleTriangle(quad.a(), quad.b(), quad.c(), random);
            }
            return sampleTriangle(quad.b(), quad.d(), quad.c(), random);
        }

        private StripQuad pickQuad(Random random) {
            double total = cumulativeAreas[cumulativeAreas.length - 1];
            double target = random.nextDouble() * total;
            int low = 0;
            int high = cumulativeAreas.length - 1;
            while (low < high) {
                int mid = (low + high) >>> 1;
                if (target <= cumulativeAreas[mid]) {
                    high = mid;
                } else {
                    low = mid + 1;
                }
            }
            return quads.get(low);
        }
    }

    private record StripQuad(Vector3d a, Vector3d b, Vector3d c, Vector3d d, double totalArea) {
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

package com.nodecraft.nodesystem.util;

import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Unit-sphere direction sampling shared by sphere surface nodes.
 */
public final class SphereSurfaceSampling {

    public enum Mode {
        FIBONACCI_UNIFORM,
        RANDOM_UNIFORM,
        LAT_LONG_GRID
    }

    private SphereSurfaceSampling() {
    }

    public static List<Vector3d> sampleUnitNormals(Mode mode, int count, int seed) {
        if (count <= 0) {
            return List.of();
        }
        return switch (mode) {
            case RANDOM_UNIFORM -> sampleRandomUniform(count, seed);
            case LAT_LONG_GRID -> sampleLatLongGrid(count);
            case FIBONACCI_UNIFORM -> sampleFibonacci(count);
        };
    }

    public static Vector3d sampleRandomUnitNormal(Random random) {
        double u = random.nextDouble();
        double v = random.nextDouble();
        double theta = 2.0d * Math.PI * u;
        double z = 1.0d - 2.0d * v;
        double radial = Math.sqrt(Math.max(0.0d, 1.0d - (z * z)));
        return normalizeOrUp(new Vector3d(
            Math.cos(theta) * radial,
            z,
            Math.sin(theta) * radial
        ));
    }

    public static Vector3d normalizeOrUp(Vector3d value) {
        Vector3d normalized = new Vector3d(value);
        if (normalized.lengthSquared() < 1.0e-12d) {
            return new Vector3d(0.0d, 1.0d, 0.0d);
        }
        return normalized.normalize();
    }

    private static List<Vector3d> sampleFibonacci(int count) {
        List<Vector3d> normals = new ArrayList<>(count);
        double goldenAngle = Math.PI * (3.0d - Math.sqrt(5.0d));

        for (int i = 0; i < count; i++) {
            double y = 1.0d - (2.0d * (i + 0.5d) / count);
            double radial = Math.sqrt(Math.max(0.0d, 1.0d - (y * y)));
            double theta = goldenAngle * i;
            normals.add(new Vector3d(
                Math.cos(theta) * radial,
                y,
                Math.sin(theta) * radial
            ));
        }

        return normals;
    }

    private static List<Vector3d> sampleRandomUniform(int count, int seed) {
        List<Vector3d> normals = new ArrayList<>(count);
        Random random = new Random(seed);

        for (int i = 0; i < count; i++) {
            normals.add(sampleRandomUnitNormal(random));
        }

        return normals;
    }

    private static List<Vector3d> sampleLatLongGrid(int count) {
        int latSteps = Math.max(2, (int) Math.round(Math.sqrt(count / 2.0d)));
        int lonSteps = Math.max(4, (int) Math.ceil((double) count / latSteps));
        List<Vector3d> normals = new ArrayList<>(latSteps * lonSteps);

        for (int latIndex = 0; latIndex < latSteps; latIndex++) {
            double v = latSteps == 1 ? 0.5d : (double) latIndex / (latSteps - 1);
            double phi = Math.PI * v;
            double y = Math.cos(phi);
            double radial = Math.sin(phi);

            for (int lonIndex = 0; lonIndex < lonSteps; lonIndex++) {
                double u = (double) lonIndex / lonSteps;
                double theta = u * Math.PI * 2.0d;
                normals.add(new Vector3d(
                    Math.cos(theta) * radial,
                    y,
                    Math.sin(theta) * radial
                ));
                if (normals.size() >= count) {
                    return normals;
                }
            }
        }

        return normals;
    }
}

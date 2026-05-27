package com.nodecraft.nodesystem.datatypes;

import org.joml.Vector3d;

/**
 * Applies domain warp (coordinate-space noise warp) before sampling an input SDF.
 */
public class DomainWarpedSdfData implements SignedDistanceFieldData {
    private final SignedDistanceFieldData source;
    private final double warpAmplitude;
    private final double warpFrequency;
    private final int seed;
    private final Vector3d offset;

    public DomainWarpedSdfData(SignedDistanceFieldData source,
                               double warpAmplitude,
                               double warpFrequency,
                               int seed,
                               Vector3d offset) {
        this.source = source;
        this.warpAmplitude = Math.max(0.0d, warpAmplitude);
        this.warpFrequency = Math.max(1.0e-6d, warpFrequency);
        this.seed = seed;
        this.offset = new Vector3d(offset);
    }

    public SignedDistanceFieldData getSource() {
        return source;
    }

    public double getWarpAmplitude() {
        return warpAmplitude;
    }

    @Override
    public double sampleDistance(Vector3d point) {
        double px = point.x + offset.x;
        double py = point.y + offset.y;
        double pz = point.z + offset.z;

        double wx = noise(px, py, pz, warpFrequency, seed ^ 0x45d9f3b);
        double wy = noise(px, py, pz, warpFrequency, seed ^ 0x9e3779b9);
        double wz = noise(px, py, pz, warpFrequency, seed ^ 0x7f4a7c15);

        Vector3d warped = new Vector3d(
            point.x + wx * warpAmplitude,
            point.y + wy * warpAmplitude,
            point.z + wz * warpAmplitude
        );
        return source.sampleDistance(warped);
    }

    private static double noise(double x, double y, double z, double freq, int seedValue) {
        double nx = x * freq;
        double ny = y * freq;
        double nz = z * freq;
        double s = Math.sin(nx * 12.9898d + ny * 78.233d + nz * 37.719d + seedValue * 0.12345d) * 43758.5453d;
        return (s - Math.floor(s)) * 2.0d - 1.0d;
    }
}

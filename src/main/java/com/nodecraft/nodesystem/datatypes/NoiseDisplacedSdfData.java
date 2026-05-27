package com.nodecraft.nodesystem.datatypes;

import org.joml.Vector3d;

/**
 * Applies deterministic pseudo-noise displacement to an input SDF.
 */
public class NoiseDisplacedSdfData implements SignedDistanceFieldData {
    private final SignedDistanceFieldData source;
    private final double amplitude;
    private final double frequency;
    private final int seed;
    private final Vector3d offset;

    public NoiseDisplacedSdfData(SignedDistanceFieldData source, double amplitude, double frequency, int seed, Vector3d offset) {
        this.source = source;
        this.amplitude = Math.max(0.0d, amplitude);
        this.frequency = Math.max(1.0e-6d, frequency);
        this.seed = seed;
        this.offset = new Vector3d(offset);
    }

    public SignedDistanceFieldData getSource() {
        return source;
    }

    public double getAmplitude() {
        return amplitude;
    }

    @Override
    public double sampleDistance(Vector3d point) {
        double base = source.sampleDistance(point);
        double px = point.x + offset.x;
        double py = point.y + offset.y;
        double pz = point.z + offset.z;
        double n = noise(px, py, pz, frequency, seed);
        return base + n * amplitude;
    }

    private static double noise(double x, double y, double z, double freq, int seedValue) {
        double px = x * freq;
        double py = y * freq;
        double pz = z * freq;
        double s = Math.sin(px * 12.9898d + py * 78.233d + pz * 37.719d + seedValue * 0.12345d) * 43758.5453d;
        return (s - Math.floor(s)) * 2.0d - 1.0d;
    }
}

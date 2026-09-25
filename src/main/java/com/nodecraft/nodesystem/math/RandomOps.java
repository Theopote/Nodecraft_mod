package com.nodecraft.nodesystem.math;

import com.nodecraft.nodesystem.util.GenerationLimits;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.Random;

/**
 * Shared deterministic sampling and coherent noise for Random v1.
 * <p>
 * Missing seed ≡ {@code 0}. Same inputs + same seed ⇒ same outputs.
 * INTEGER Count/Seed accept {@link Integer} only (no Number coercion).
 */
public final class RandomOps {

    private static final Vector3d DEFAULT_MIN = new Vector3d(0.0d, 0.0d, 0.0d);
    private static final Vector3d DEFAULT_MAX = new Vector3d(1.0d, 1.0d, 1.0d);

    private RandomOps() {
    }

    /**
     * Resolves Seed: {@link Integer} → value; otherwise {@code 0} (including missing).
     */
    public static int resolveSeed(@Nullable Object seed) {
        return seed instanceof Integer i ? i : 0;
    }

    /**
     * Resolves Count: {@link Integer} → clamped non-negative; otherwise {@code defaultCount} then clamp.
     */
    public static int resolveCount(@Nullable Object count, int defaultCount) {
        int raw = count instanceof Integer i ? i : defaultCount;
        return GenerationLimits.clampNonNegativeCount(raw);
    }

    /**
     * Always-seeded RNG (seed {@code 0} is a valid deterministic seed).
     */
    public static Random rng(int seed) {
        return new Random(seed);
    }

    /**
     * Samples a finite double in {@code [min, max]} (order-insensitive).
     * Non-finite bounds or overflowed span → {@link Double#NaN}.
     */
    public static double sampleDouble(double min, double max, Random random) {
        if (random == null || !Double.isFinite(min) || !Double.isFinite(max)) {
            return Double.NaN;
        }
        if (min == max) {
            return min;
        }
        double lo = Math.min(min, max);
        double hi = Math.max(min, max);
        double span = hi - lo;
        if (!Double.isFinite(span)) {
            return Double.NaN;
        }
        double result = lo + random.nextDouble() * span;
        return Double.isFinite(result) ? result : Double.NaN;
    }

    /**
     * Resolves a VECTOR input to JOML {@link Vector3d}.
     * Accepts {@link Vector3d} and legacy Minecraft {@link Vec3d}; otherwise {@code defaultValue} copy.
     */
    public static Vector3d resolveVector(@Nullable Object value, Vector3d defaultValue) {
        if (value instanceof Vector3d v) {
            return new Vector3d(v);
        }
        if (value instanceof Vec3d v) {
            return new Vector3d(v.x, v.y, v.z);
        }
        Vector3d fallback = defaultValue != null ? defaultValue : DEFAULT_MIN;
        return new Vector3d(fallback);
    }

    public static Vector3d defaultMinCorner() {
        return new Vector3d(DEFAULT_MIN);
    }

    public static Vector3d defaultMaxCorner() {
        return new Vector3d(DEFAULT_MAX);
    }

    /**
     * Axis-wise sample between min and max corners. Emits a new JOML vector.
     * Non-finite axis sample → that component is NaN (vector may be partially NaN).
     */
    public static Vector3d sampleVector(Vector3d min, Vector3d max, Random random) {
        Vector3d lo = min != null ? min : DEFAULT_MIN;
        Vector3d hi = max != null ? max : DEFAULT_MAX;
        return new Vector3d(
                sampleDouble(lo.x, hi.x, random),
                sampleDouble(lo.y, hi.y, random),
                sampleDouble(lo.z, hi.z, random)
        );
    }

    /**
     * Deterministic 3D value noise with smooth interpolation.
     * Output roughly in {@code [-1, 1]}. Non-finite position → {@link Double#NaN}.
     */
    public static double valueNoise3(double x, double y, double z, int seed) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            return Double.NaN;
        }

        int x0 = floor(x);
        int y0 = floor(y);
        int z0 = floor(z);
        double fx = x - x0;
        double fy = y - y0;
        double fz = z - z0;

        double u = fade(fx);
        double v = fade(fy);
        double w = fade(fz);

        double n000 = latticeValue(x0, y0, z0, seed);
        double n100 = latticeValue(x0 + 1, y0, z0, seed);
        double n010 = latticeValue(x0, y0 + 1, z0, seed);
        double n110 = latticeValue(x0 + 1, y0 + 1, z0, seed);
        double n001 = latticeValue(x0, y0, z0 + 1, seed);
        double n101 = latticeValue(x0 + 1, y0, z0 + 1, seed);
        double n011 = latticeValue(x0, y0 + 1, z0 + 1, seed);
        double n111 = latticeValue(x0 + 1, y0 + 1, z0 + 1, seed);

        double nx00 = lerp(n000, n100, u);
        double nx10 = lerp(n010, n110, u);
        double nx01 = lerp(n001, n101, u);
        double nx11 = lerp(n011, n111, u);

        double nxy0 = lerp(nx00, nx10, v);
        double nxy1 = lerp(nx01, nx11, v);

        return lerp(nxy0, nxy1, w);
    }

    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    /** Perlin quintic fade: 6t^5 - 15t^4 + 10t^3 */
    private static double fade(double t) {
        return t * t * t * (t * (t * 6.0d - 15.0d) + 10.0d);
    }

    private static double lerp(double a, double b, double t) {
        return a + t * (b - a);
    }

    /** Stable lattice hash mapped to roughly [-1, 1]. */
    private static double latticeValue(int x, int y, int z, int seed) {
        int h = hash3(x, y, z, seed);
        // Use upper 24 bits for uniform [0,1) then map to [-1,1]
        int bits = (h >>> 8) & 0xFFFFFF;
        return (bits / (double) 0xFFFFFF) * 2.0d - 1.0d;
    }

    private static int hash3(int x, int y, int z, int seed) {
        int h = seed;
        h ^= x * 0x27d4eb2d;
        h = Integer.rotateLeft(h, 13);
        h *= 0x165667b1;
        h ^= y * 0x85ebca6b;
        h = Integer.rotateLeft(h, 17);
        h *= 0xc2b2ae35;
        h ^= z * 0x27d4eb2d;
        h ^= h >>> 16;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        h *= 0xc2b2ae35;
        h ^= h >>> 16;
        return h;
    }
}

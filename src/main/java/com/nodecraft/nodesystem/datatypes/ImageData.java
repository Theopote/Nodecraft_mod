package com.nodecraft.nodesystem.datatypes;

import java.util.List;
import java.util.Objects;

/**
 * Immutable sampled image payload: source dimensions, sample grid, and row-major colors.
 * <p>
 * Metadata-only reads should emit a null {@code ImageData} on the node output rather than
 * an empty colors list; every {@code ImageData} instance always has {@code colors.size()
 * == sampleWidth * sampleHeight}.
 */
public record ImageData(
    int sourceWidth,
    int sourceHeight,
    int sampleWidth,
    int sampleHeight,
    int sampleStep,
    List<ColorData> colors
) {
    public ImageData {
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            throw new IllegalArgumentException("sourceWidth and sourceHeight must be > 0");
        }
        if (sampleWidth <= 0 || sampleHeight <= 0) {
            throw new IllegalArgumentException("sampleWidth and sampleHeight must be > 0");
        }
        if (sampleStep < 1) {
            throw new IllegalArgumentException("sampleStep must be >= 1");
        }
        Objects.requireNonNull(colors, "colors");
        long expected;
        try {
            expected = Math.multiplyExact((long) sampleWidth, (long) sampleHeight);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("sampleWidth * sampleHeight overflows", ex);
        }
        if (colors.size() != expected) {
            throw new IllegalArgumentException(
                "colors size " + colors.size() + " does not match sample grid " + expected
            );
        }
        colors = List.copyOf(colors);
    }

    public static ImageData create(
        int sourceWidth,
        int sourceHeight,
        int sampleWidth,
        int sampleHeight,
        int sampleStep,
        List<ColorData> colors
    ) {
        return new ImageData(sourceWidth, sourceHeight, sampleWidth, sampleHeight, sampleStep, colors);
    }

    /**
     * Stable content fingerprint over dimensions and every sample color.
     * Used by custom UI texture caching — sparse probes are not sufficient.
     */
    public long contentIdentity() {
        long h = 0xcbf29ce484222325L;
        h = fnv(h, sourceWidth);
        h = fnv(h, sourceHeight);
        h = fnv(h, sampleWidth);
        h = fnv(h, sampleHeight);
        h = fnv(h, sampleStep);
        h = fnv(h, colors.size());
        for (ColorData color : colors) {
            h = fnv(h, Float.floatToIntBits(color.r()));
            h = fnv(h, Float.floatToIntBits(color.g()));
            h = fnv(h, Float.floatToIntBits(color.b()));
            h = fnv(h, Float.floatToIntBits(color.a()));
        }
        return h;
    }

    private static long fnv(long hash, int value) {
        hash ^= value;
        hash *= 0x100000001b3L;
        return hash;
    }
}

package com.nodecraft.nodesystem.nodes.utilities.fileio;

import com.nodecraft.nodesystem.datatypes.ColorData;
import com.nodecraft.nodesystem.datatypes.ImageData;
import com.nodecraft.nodesystem.nodes.utilities.fileio.ImageSamplerNode.FilterMode;
import com.nodecraft.nodesystem.nodes.utilities.fileio.ImageSamplerNode.OriginMode;
import com.nodecraft.nodesystem.nodes.utilities.fileio.ImageSamplerNode.WrapMode;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Shared coordinate / wrap / filter math for {@link ImageSamplerNode} execution and UI crosshair mapping.
 */
public final class ImageSamplerMath {

    public record SamplePoint(double x, double y) {
    }

    private ImageSamplerMath() {
    }

    /**
     * Maps UV (0..1 inclusive endpoints) or pixel coordinates into continuous sample space,
     * applying origin flip when needed. Wrap is applied later via {@link #resolveCoordinate}.
     */
    public static SamplePoint toSampleSpace(
            double coordX,
            double coordY,
            boolean uvMode,
            int sampleWidth,
            int sampleHeight,
            OriginMode originMode
    ) {
        double x;
        double y;
        if (uvMode) {
            x = coordX * Math.max(0, sampleWidth - 1);
            y = coordY * Math.max(0, sampleHeight - 1);
        } else {
            x = coordX;
            y = coordY;
        }
        if (originMode == OriginMode.BOTTOM_LEFT) {
            y = Math.max(0, sampleHeight - 1) - y;
        }
        return new SamplePoint(x, y);
    }

    public static double resolveCoordinate(double value, int size, WrapMode wrapMode) {
        if (size <= 1) {
            return 0.0d;
        }
        double max = size - 1.0d;
        WrapMode mode = wrapMode == null ? WrapMode.CLAMP : wrapMode;
        return switch (mode) {
            case CLAMP -> Math.max(0.0d, Math.min(max, value));
            case REPEAT -> positiveModulo(value, size);
            case MIRROR -> mirror(value, size);
        };
    }

    public static @Nullable ColorData sample(
            ImageData image,
            double x,
            double y,
            FilterMode filterMode,
            WrapMode wrapMode
    ) {
        FilterMode filter = filterMode == null ? FilterMode.BILINEAR : filterMode;
        return filter == FilterMode.NEAREST
            ? sampleNearest(image, x, y, wrapMode)
            : sampleBilinear(image, x, y, wrapMode);
    }

    public static @Nullable ColorData sampleNearest(ImageData image, double x, double y, WrapMode wrapMode) {
        int px = (int) Math.round(resolveCoordinate(x, image.sampleWidth(), wrapMode));
        int py = (int) Math.round(resolveCoordinate(y, image.sampleHeight(), wrapMode));
        return readColor(image, px, py, wrapMode);
    }

    public static @Nullable ColorData sampleBilinear(ImageData image, double x, double y, WrapMode wrapMode) {
        double rx = resolveCoordinate(x, image.sampleWidth(), wrapMode);
        double ry = resolveCoordinate(y, image.sampleHeight(), wrapMode);
        int x0 = (int) Math.floor(rx);
        int y0 = (int) Math.floor(ry);
        int x1 = x0 + 1;
        int y1 = y0 + 1;
        double tx = rx - x0;
        double ty = ry - y0;

        ColorData c00 = readColor(image, x0, y0, wrapMode);
        ColorData c10 = readColor(image, x1, y0, wrapMode);
        ColorData c01 = readColor(image, x0, y1, wrapMode);
        ColorData c11 = readColor(image, x1, y1, wrapMode);
        if (c00 == null || c10 == null || c01 == null || c11 == null) {
            return null;
        }

        float r = (float) bilerp(c00.r(), c10.r(), c01.r(), c11.r(), tx, ty);
        float g = (float) bilerp(c00.g(), c10.g(), c01.g(), c11.g(), tx, ty);
        float b = (float) bilerp(c00.b(), c10.b(), c01.b(), c11.b(), tx, ty);
        float a = (float) bilerp(c00.a(), c10.a(), c01.a(), c11.a(), tx, ty);
        return new ColorData(r, g, b, a);
    }

    public static @Nullable ColorData readColor(ImageData image, int x, int y, WrapMode wrapMode) {
        int px = (int) resolveCoordinate(x, image.sampleWidth(), wrapMode);
        int py = (int) resolveCoordinate(y, image.sampleHeight(), wrapMode);
        int index = py * image.sampleWidth() + px;
        List<ColorData> colors = image.colors();
        if (index < 0 || index >= colors.size()) {
            return null;
        }
        return colors.get(index);
    }

    public static double grayscale(ColorData color) {
        return color.r() * 0.299d + color.g() * 0.587d + color.b() * 0.114d;
    }

    /**
     * Maps resolved sample-space coordinates into thumbnail pixel offsets for crosshair drawing.
     */
    public static SamplePoint toThumbnailPixel(
            double sampleX,
            double sampleY,
            int sampleWidth,
            int sampleHeight,
            float thumbWidth,
            float thumbHeight
    ) {
        float u = sampleWidth <= 1 ? 0.5f : (float) (sampleX / (sampleWidth - 1.0d));
        float v = sampleHeight <= 1 ? 0.5f : (float) (sampleY / (sampleHeight - 1.0d));
        u = Math.max(0.0f, Math.min(1.0f, u));
        v = Math.max(0.0f, Math.min(1.0f, v));
        return new SamplePoint(u * Math.max(0.0f, thumbWidth - 1.0f), v * Math.max(0.0f, thumbHeight - 1.0f));
    }

    private static double positiveModulo(double value, int size) {
        double result = value % size;
        return result < 0.0d ? result + size : result;
    }

    private static double mirror(double value, int size) {
        if (size <= 1) {
            return 0.0d;
        }
        double period = (size - 1.0d) * 2.0d;
        double wrapped = value % period;
        if (wrapped < 0.0d) {
            wrapped += period;
        }
        double max = size - 1.0d;
        return wrapped <= max ? wrapped : period - wrapped;
    }

    private static double bilerp(double c00, double c10, double c01, double c11, double tx, double ty) {
        double top = lerp(c00, c10, tx);
        double bottom = lerp(c01, c11, tx);
        return lerp(top, bottom, ty);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}

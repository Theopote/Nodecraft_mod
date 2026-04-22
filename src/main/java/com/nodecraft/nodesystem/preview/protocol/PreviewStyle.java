package com.nodecraft.nodesystem.preview.protocol;

import com.nodecraft.nodesystem.preview.PreviewOptions;
import org.joml.Vector3f;

/**
 * Style layer for previews: what to draw is {@link PreviewPayload}; how it looks is {@link PreviewStyle}.
 */
public final class PreviewStyle {
    private final float red;
    private final float green;
    private final float blue;
    private final float opacity;
    private final boolean showOutline;
    private final String textureMode;
    private final float lineWidth;
    private final float pointSize;
    private final int durationTicks;

    public PreviewStyle(
        float red,
        float green,
        float blue,
        float opacity,
        boolean showOutline,
        String textureMode,
        float lineWidth,
        float pointSize,
        int durationTicks
    ) {
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.opacity = Math.max(0.0f, Math.min(1.0f, opacity));
        this.showOutline = showOutline;
        this.textureMode = textureMode;
        this.lineWidth = lineWidth;
        this.pointSize = pointSize;
        this.durationTicks = Math.max(0, durationTicks);
    }

    public static PreviewStyle forGhostBlocks(
        float red,
        float green,
        float blue,
        float opacity,
        boolean showOutline,
        String textureMode,
        float lineWidth,
        float pointSize,
        int durationTicks
    ) {
        return new PreviewStyle(red, green, blue, opacity, showOutline, textureMode, lineWidth, pointSize, durationTicks);
    }

    /**
     * Best-effort mapping from legacy {@link PreviewOptions} used by transitional APIs.
     */
    public static PreviewStyle fromLegacyGhostOptions(PreviewOptions options, float fallbackOpacity) {
        float r = 1.0f;
        float g = 1.0f;
        float b = 1.0f;
        if (options.color != null) {
            Vector3f c = options.color;
            r = c.x;
            g = c.y;
            b = c.z;
        } else if (options.tintColor != null) {
            Vector3f c = options.tintColor;
            r = c.x;
            g = c.y;
            b = c.z;
        }
        float opacity = options.opacity != null ? options.opacity : fallbackOpacity;
        boolean outline = Boolean.TRUE.equals(options.showOutline);
        String tex = options.textureMode;
        float lw = options.lineWidth != null ? options.lineWidth : 2.0f;
        float ps = options.pointSize != null ? options.pointSize : 0.1f;
        int ticks = 0;
        if (options.duration != null && options.duration > 0) {
            ticks = options.duration * 20;
        }
        return new PreviewStyle(r, g, b, opacity, outline, tex, lw, ps, ticks);
    }

    public static PreviewStyle fromLegacyPointOptions(PreviewOptions options) {
        float r = 1.0f;
        float g = 0.0f;
        float b = 0.0f;
        if (options.color != null) {
            Vector3f c = options.color;
            r = c.x;
            g = c.y;
            b = c.z;
        }
        float opacity = options.opacity != null ? options.opacity : 0.9f;
        float lw = options.lineWidth != null ? options.lineWidth : 2.0f;
        float ps = options.pointSize != null ? options.pointSize : 0.1f;
        int ticks = 0;
        if (options.duration != null && options.duration > 0) {
            ticks = options.duration * 20;
        }
        return new PreviewStyle(r, g, b, opacity, false, null, lw, ps, ticks);
    }

    /**
     * Maps vector-specific {@link PreviewOptions} into the generic {@link PreviewStyle} slots:
     * {@code lengthScale}→lineWidth, {@code arrowSize}→pointSize, {@code showArrows}→showOutline.
     */
    public static PreviewStyle fromLegacyVectorOptions(PreviewOptions options) {
        float r = 0.0f;
        float g = 1.0f;
        float b = 0.0f;
        if (options.color != null) {
            Vector3f c = options.color;
            r = c.x;
            g = c.y;
            b = c.z;
        }
        float opacity = options.opacity != null ? options.opacity : 0.8f;
        float lengthScale = options.lengthScale != null ? options.lengthScale : 1.0f;
        float arrowSize = options.arrowSize != null ? options.arrowSize : 0.2f;
        boolean showArrows = !Boolean.FALSE.equals(options.showArrows);
        int ticks = 0;
        if (options.duration != null && options.duration > 0) {
            ticks = options.duration * 20;
        }
        return new PreviewStyle(r, g, b, opacity, showArrows, null, lengthScale, arrowSize, ticks);
    }

    /** Prefer {@link #toPreviewOptions(PreviewKind)}; this overload assumes block ghost styling. */
    @Deprecated
    public PreviewOptions toPreviewOptions() {
        return toPreviewOptions(PreviewKind.BLOCKS);
    }

    public PreviewOptions toPreviewOptions(PreviewKind kind) {
        return switch (kind) {
            case BLOCKS -> toGhostBlockPreviewOptions();
            case POINTS -> toPointsPreviewOptions();
            case VECTORS -> toVectorsPreviewOptions();
            default -> new PreviewOptions().setColor(red, green, blue).setOpacity(opacity);
        };
    }

    private PreviewOptions toGhostBlockPreviewOptions() {
        PreviewOptions o = new PreviewOptions().ghostBlockMode().setOpacity(opacity);
        o.setColor(red, green, blue);
        if (textureMode != null && !textureMode.isEmpty()) {
            o.textureMode = textureMode;
            if ("solid_color".equals(textureMode) || "wireframe".equals(textureMode)) {
                o.useOriginalTexture = false;
            }
        }
        if (durationTicks > 0) {
            o.setDuration(Math.max(1, (durationTicks + 19) / 20));
        }
        o.setShowOutline(showOutline);
        o.setLineWidth(lineWidth);
        if (pointSize > 0.0f) {
            o.pointSize = pointSize;
        }
        return o;
    }

    private PreviewOptions toPointsPreviewOptions() {
        PreviewOptions o = PreviewOptions.createPoints();
        o.setColor(red, green, blue);
        o.setOpacity(opacity);
        if (durationTicks > 0) {
            o.setDuration(Math.max(1, (durationTicks + 19) / 20));
        }
        if (pointSize > 0.0f) {
            o.pointSize = pointSize;
        }
        if (lineWidth > 0.0f) {
            o.setLineWidth(lineWidth);
        }
        return o;
    }

    private PreviewOptions toVectorsPreviewOptions() {
        PreviewOptions o = PreviewOptions.createVectorArrows();
        o.setColor(red, green, blue);
        o.setOpacity(opacity);
        if (durationTicks > 0) {
            o.setDuration(Math.max(1, (durationTicks + 19) / 20));
        }
        o.lengthScale = lineWidth > 0.0f ? lineWidth : 1.0f;
        o.arrowSize = pointSize > 0.0f ? pointSize : 0.2f;
        o.showArrows = showOutline;
        return o;
    }

    public float red() {
        return red;
    }

    public float green() {
        return green;
    }

    public float blue() {
        return blue;
    }

    public float opacity() {
        return opacity;
    }

    public boolean showOutline() {
        return showOutline;
    }

    public String textureMode() {
        return textureMode;
    }

    public float lineWidth() {
        return lineWidth;
    }

    public float pointSize() {
        return pointSize;
    }

    public int durationTicks() {
        return durationTicks;
    }
}

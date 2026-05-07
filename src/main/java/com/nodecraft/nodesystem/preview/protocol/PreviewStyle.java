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
    private final float fillRed;
    private final float fillGreen;
    private final float fillBlue;
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
        float fillRed,
        float fillGreen,
        float fillBlue,
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
        this.fillRed = fillRed;
        this.fillGreen = fillGreen;
        this.fillBlue = fillBlue;
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
        return new PreviewStyle(red, green, blue, red, green, blue, opacity, showOutline, textureMode, lineWidth, pointSize, durationTicks);
    }

    public static PreviewStyle forGhostBlocksWithOutline(
        float fillRed,
        float fillGreen,
        float fillBlue,
        float outlineRed,
        float outlineGreen,
        float outlineBlue,
        float opacity,
        boolean showOutline,
        String textureMode,
        float lineWidth,
        float pointSize,
        int durationTicks
    ) {
        return new PreviewStyle(fillRed, fillGreen, fillBlue, outlineRed, outlineGreen, outlineBlue, opacity, showOutline, textureMode, lineWidth, pointSize, durationTicks);
    }

    /**
     * Best-effort mapping from legacy {@link PreviewOptions} used by transitional APIs.
     */
    public static PreviewStyle from(PreviewOptions options, PreviewKind kind) {
        PreviewOptions safeOptions = options != null ? options : new PreviewOptions();
        return switch (kind) {
            case BLOCKS -> fromLegacyGhostOptions(safeOptions, 0.7f);
            case POINTS -> fromLegacyPointOptions(safeOptions);
            case VECTORS -> fromLegacyVectorOptions(safeOptions);
            case REGIONS -> fromLegacyRegionOptions(safeOptions);
            case CURVES -> fromLegacyPathOptions(safeOptions);
            case GEOMETRY -> fromLegacyGeometryOptions(safeOptions);
            case PLANE -> fromLegacyPlaneGridOptions(safeOptions);
            case FRAME -> fromLegacyFrameAxesOptions(safeOptions);
            case LABELS -> fromLegacyLabelsOptions(safeOptions);
            case SURFACE_STRIP -> fromLegacyGeometryOptions(safeOptions);
        };
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
        return new PreviewStyle(r, g, b, r, g, b, opacity, outline, tex, lw, ps, ticks);
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
        return new PreviewStyle(r, g, b, r, g, b, opacity, false, null, lw, ps, ticks);
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
        return new PreviewStyle(r, g, b, r, g, b, opacity, showArrows, null, lengthScale, arrowSize, ticks);
    }

    /**
     * Region box legacy options: pulse encoded in {@code textureMode} ({@code region_pulse} / {@code region_still}),
     * fill on/off in {@code pointSize} ({@code >= 0.5f} = fill).
     */
    public static PreviewStyle fromLegacyRegionOptions(PreviewOptions options) {
        float r = 0.2f;
        float g = 0.7f;
        float b = 1.0f;
        if (options.color != null) {
            Vector3f c = options.color;
            r = c.x;
            g = c.y;
            b = c.z;
        }
        float fr = r;
        float fg = g;
        float fb = b;
        if (options.tintColor != null) {
            Vector3f t = options.tintColor;
            fr = t.x;
            fg = t.y;
            fb = t.z;
        }
        float opacity = options.opacity != null ? options.opacity : 0.3f;
        boolean outline = options.showOutline == null || options.showOutline;
        boolean pulse = Boolean.TRUE.equals(options.pulseAnimation);
        String tex = pulse ? "region_pulse" : "region_still";
        float lw = options.lineWidth != null ? options.lineWidth : 1.5f;
        float ps = Boolean.TRUE.equals(options.showFill) ? 1.0f : 0.0f;
        int ticks = 0;
        if (options.duration != null && options.duration > 0) {
            ticks = options.duration * 20;
        }
        return new PreviewStyle(r, g, b, fr, fg, fb, opacity, outline, tex, lw, ps, ticks);
    }

    /**
     * Path / curve preview: {@code smoothCurves} → {@code textureMode} {@code curve_smooth} / {@code curve_linear};
     * {@code showArrows} → {@code showOutline}.
     */
    public static PreviewStyle fromLegacyPathOptions(PreviewOptions options) {
        float r = 1.0f;
        float g = 0.85f;
        float b = 0.2f;
        if (options.color != null) {
            Vector3f c = options.color;
            r = c.x;
            g = c.y;
            b = c.z;
        }
        float opacity = options.opacity != null ? options.opacity : 1.0f;
        boolean showDir = !Boolean.FALSE.equals(options.showArrows);
        String tex = Boolean.TRUE.equals(options.smoothCurves) ? "curve_smooth" : "curve_linear";
        float lw = options.lineWidth != null ? options.lineWidth : 1.5f;
        float ps = options.arrowSize != null ? options.arrowSize : 0.25f;
        int ticks = 0;
        if (options.duration != null && options.duration > 0) {
            ticks = options.duration * 20;
        }
        return new PreviewStyle(r, g, b, r, g, b, opacity, showDir, tex, lw, ps, ticks);
    }

    /**
     * Analytic geometry surface preview. Fill RGB in {@code red/green/blue}, line RGB in {@code fill*}.
     * Mesh quality is encoded in {@code textureMode} as {@code geom_quality:} plus an integer (clamped 8–64).
     * {@code pointSize ≥ 0.5} means show fill.
     */
    public static PreviewStyle fromLegacyGeometryOptions(PreviewOptions options) {
        float r = 0.30f;
        float g = 0.84f;
        float b = 1.0f;
        if (options.color != null) {
            Vector3f c = options.color;
            r = c.x;
            g = c.y;
            b = c.z;
        }
        float fr = Math.max(0.0f, r * 0.25f);
        float fg = Math.max(0.0f, g * 0.25f);
        float fb = Math.max(0.0f, b * 0.25f);
        if (options.tintColor != null) {
            Vector3f t = options.tintColor;
            fr = t.x;
            fg = t.y;
            fb = t.z;
        }
        float opacity = options.opacity != null ? options.opacity : 1.0f;
        boolean outline = options.showOutline == null || options.showOutline;
        boolean fillOn = options.showFill == null || options.showFill;
        int q = options.particleDensity != null ? options.particleDensity : 20;
        String tex = "geom_quality:" + q;
        float lw = options.lineWidth != null ? options.lineWidth : 1.6f;
        float ps = fillOn ? 1.0f : 0.0f;
        int ticks = 0;
        if (options.duration != null && options.duration > 0) {
            ticks = options.duration * 20;
        }
        return new PreviewStyle(r, g, b, fr, fg, fb, opacity, outline, tex, lw, ps, ticks);
    }

    public static PreviewStyle fromLegacyPlaneGridOptions(PreviewOptions options) {
        float r = 0.35f;
        float g = 0.75f;
        float b = 1.0f;
        if (options.color != null) {
            Vector3f c = options.color;
            r = c.x;
            g = c.y;
            b = c.z;
        }
        float opacity = options.opacity != null ? options.opacity : 1.0f;
        boolean pulse = Boolean.TRUE.equals(options.pulseAnimation);
        String tex = pulse ? "plane_pulse" : "plane_still";
        float lw = options.lineWidth != null ? options.lineWidth : 1.5f;
        int ticks = 0;
        if (options.duration != null && options.duration > 0) {
            ticks = options.duration * 20;
        }
        return new PreviewStyle(r, g, b, r, g, b, opacity, true, tex, lw, 0.0f, ticks);
    }

    public static PreviewStyle fromLegacyFrameAxesOptions(PreviewOptions options) {
        float r = 1.0f;
        float g = 1.0f;
        float b = 1.0f;
        if (options.color != null) {
            Vector3f c = options.color;
            r = c.x;
            g = c.y;
            b = c.z;
        }
        float opacity = options.opacity != null ? options.opacity : 1.0f;
        float lw = options.lineWidth != null ? options.lineWidth : 1.5f;
        int ticks = 0;
        if (options.duration != null && options.duration > 0) {
            ticks = options.duration * 20;
        }
        return new PreviewStyle(r, g, b, r, g, b, opacity, true, "frame", lw, 0.0f, ticks);
    }

    public static PreviewStyle fromLegacyLabelsOptions(PreviewOptions options) {
        float r = 1.0f;
        float g = 1.0f;
        float b = 1.0f;
        if (options.color != null) {
            Vector3f c = options.color;
            r = c.x;
            g = c.y;
            b = c.z;
        }
        float opacity = options.opacity != null ? options.opacity : 1.0f;
        boolean showBg = options.showBackground == null || options.showBackground;
        float fs = options.fontSize != null ? options.fontSize : 0.025f;
        int ticks = 0;
        if (options.duration != null && options.duration > 0) {
            ticks = options.duration * 20;
        }
        return new PreviewStyle(r, g, b, r, g, b, opacity, showBg, "labels", 1.5f, fs, ticks);
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
            case REGIONS -> toRegionPreviewOptions();
            case CURVES -> toCurvesPreviewOptions();
            case GEOMETRY -> toGeometryPreviewOptions();
            case PLANE -> toPlaneGridPreviewOptions();
            case FRAME -> toFrameAxesPreviewOptions();
            case LABELS -> toLabelsPreviewOptions();
            default -> new PreviewOptions().setColor(red, green, blue).setOpacity(opacity);
        };
    }

    private PreviewOptions toGhostBlockPreviewOptions() {
        PreviewOptions o = new PreviewOptions().ghostBlockMode().setOpacity(opacity);
        o.setColor(red, green, blue);
        o.setTintColor(fillRed, fillGreen, fillBlue);
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

    private PreviewOptions toRegionPreviewOptions() {
        PreviewOptions o = PreviewOptions.createRegionBox();
        o.setColor(red, green, blue);
        o.setTintColor(fillRed, fillGreen, fillBlue);
        o.setOpacity(opacity);
        o.setShowOutline(showOutline);
        o.setLineWidth(lineWidth > 0.0f ? lineWidth : 1.5f);
        if ("region_still".equals(textureMode)) {
            o.pulseAnimation = false;
            o.enableAnimation = false;
        }
        o.showFill = pointSize >= 0.5f;
        if (durationTicks > 0) {
            o.setDuration(Math.max(1, (durationTicks + 19) / 20));
        }
        return o;
    }

    private PreviewOptions toCurvesPreviewOptions() {
        PreviewOptions o = new PreviewOptions();
        o.setColor(red, green, blue);
        o.setOpacity(opacity);
        o.setLineWidth(lineWidth > 0.0f ? lineWidth : 1.5f);
        o.showArrows = showOutline;
        o.arrowSize = pointSize > 0.0f ? pointSize : 0.25f;
        o.smoothCurves = "curve_smooth".equals(textureMode);
        if (durationTicks > 0) {
            o.setDuration(Math.max(1, (durationTicks + 19) / 20));
        }
        return o;
    }

    private PreviewOptions toGeometryPreviewOptions() {
        PreviewOptions o = new PreviewOptions();
        o.setColor(red, green, blue);
        o.setTintColor(fillRed, fillGreen, fillBlue);
        o.setOpacity(opacity);
        o.showFill = pointSize >= 0.5f;
        o.setShowOutline(showOutline);
        o.setLineWidth(lineWidth > 0.0f ? lineWidth : 1.6f);
        if (textureMode != null && textureMode.startsWith("geom_quality:")) {
            try {
                int q = Integer.parseInt(textureMode.substring("geom_quality:".length()));
                o.particleDensity = Math.max(8, Math.min(64, q));
            } catch (NumberFormatException ignored) {
                o.particleDensity = 20;
            }
        } else {
            o.particleDensity = 20;
        }
        if (durationTicks > 0) {
            o.setDuration(Math.max(1, (durationTicks + 19) / 20));
        }
        return o;
    }

    private PreviewOptions toPlaneGridPreviewOptions() {
        PreviewOptions o = new PreviewOptions();
        o.setColor(red, green, blue);
        o.setOpacity(opacity);
        o.setLineWidth(lineWidth > 0.0f ? lineWidth : 1.5f);
        if (durationTicks > 0) {
            o.setDuration(Math.max(1, (durationTicks + 19) / 20));
        }
        if ("plane_pulse".equals(textureMode)) {
            o.pulseAnimation = true;
            o.enableAnimation = true;
        } else {
            o.pulseAnimation = false;
            o.enableAnimation = false;
        }
        return o;
    }

    private PreviewOptions toFrameAxesPreviewOptions() {
        PreviewOptions o = new PreviewOptions();
        o.setColor(red, green, blue);
        o.setOpacity(opacity);
        o.setLineWidth(lineWidth > 0.0f ? lineWidth : 1.5f);
        if (durationTicks > 0) {
            o.setDuration(Math.max(1, (durationTicks + 19) / 20));
        }
        return o;
    }

    private PreviewOptions toLabelsPreviewOptions() {
        PreviewOptions o = new PreviewOptions();
        o.setColor(red, green, blue);
        o.setOpacity(opacity);
        o.fontSize = pointSize > 0.0f ? pointSize : 0.025f;
        o.showBackground = showOutline;
        if (durationTicks > 0) {
            o.setDuration(Math.max(1, (durationTicks + 19) / 20));
        }
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

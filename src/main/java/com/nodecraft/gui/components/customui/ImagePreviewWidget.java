package com.nodecraft.gui.components.customui;

import com.nodecraft.nodesystem.datatypes.ColorData;
import com.nodecraft.nodesystem.datatypes.ImageData;
import com.nodecraft.nodesystem.nodes.utilities.fileio.ImageSamplerMath;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Shared image thumbnail + optional sample crosshair for Read Image / Image Sampler custom UI.
 */
public final class ImagePreviewWidget {

    public static final float LOGICAL_THUMB_HEIGHT = 72.0f;

    private ImagePreviewWidget() {
    }

    public static float preferredHeight(boolean hasThumbnail, boolean showMetadataLines) {
        float line = CustomUIMetricsSafe.layoutTextLineHeight();
        float gap = CustomUIMetricsSafe.layoutItemSpacingY();
        float height = 0.0f;
        if (hasThumbnail) {
            height += LOGICAL_THUMB_HEIGHT + gap;
        } else {
            height += line + gap;
        }
        if (showMetadataLines) {
            height += line * 3.0f + gap;
        }
        return height + gap;
    }

    /**
     * Draws a thumbnail (or placeholder) and optional crosshair at resolved sample coordinates.
     *
     * @param sampleX sample-space X (same units as Image Sampler outputs); ignored when {@code drawCrosshair} is false
     * @param sampleY sample-space Y
     */
    public static void renderThumbnail(
            @Nullable UUID ownerNodeId,
            @Nullable ImageData image,
            float widthPixels,
            float heightPixels,
            float zoom,
            boolean drawCrosshair,
            double sampleX,
            double sampleY
    ) {
        ImVec2 origin = ImGui.getCursorScreenPos();
        ImDrawList drawList = ImGui.getWindowDrawList();
        float x0 = origin.x;
        float y0 = origin.y;
        float x1 = x0 + widthPixels;
        float y1 = y0 + heightPixels;

        drawList.addRectFilled(x0, y0, x1, y1, 0xFF2A2A32);
        drawList.addRect(x0, y0, x1, y1, 0xFF505058, 0.0f, 0, Math.max(1.0f, zoom));

        if (image == null) {
            ImGui.setCursorScreenPos(x0 + 6.0f * zoom, y0 + heightPixels * 0.5f - 6.0f * zoom);
            ImGui.textDisabled("No preview");
        } else {
            int textureId = CustomUIImageTextureCache.getInstance().getOrUpload(ownerNodeId, image);
            if (textureId > 0) {
                drawList.addImage(textureId, x0, y0, x1, y1);
            } else {
                ImGui.setCursorScreenPos(x0 + 6.0f * zoom, y0 + heightPixels * 0.5f - 6.0f * zoom);
                ImGui.textDisabled("Loading…");
            }
            if (drawCrosshair) {
                renderCrosshair(drawList, sampleX, sampleY, image.sampleWidth(), image.sampleHeight(),
                    widthPixels, heightPixels, x0, y0, zoom);
            }
        }

        ImGui.setCursorScreenPos(x0, y1 + CustomUIMetricsSafe.scaleLogical(2.0f, zoom));
        ImGui.dummy(widthPixels, 0.1f);
    }

    public static void renderCrosshair(
            ImDrawList drawList,
            double sampleX,
            double sampleY,
            int sampleWidth,
            int sampleHeight,
            float thumbWidth,
            float thumbHeight,
            float originX,
            float originY,
            float zoom
    ) {
        ImageSamplerMath.SamplePoint pixel = ImageSamplerMath.toThumbnailPixel(
            sampleX, sampleY, sampleWidth, sampleHeight, thumbWidth, thumbHeight);
        float cx = originX + (float) pixel.x();
        float cy = originY + (float) pixel.y();
        float arm = Math.max(4.0f, 6.0f * zoom);
        int color = 0xFFFFFFFF;
        float thickness = Math.max(1.0f, zoom);
        drawList.addLine(cx - arm, cy, cx + arm, cy, color, thickness);
        drawList.addLine(cx, cy - arm, cx, cy + arm, color, thickness);
    }

    public static void renderSampleSwatch(@Nullable ColorData color, float sizePixels) {
        ImVec2 origin = ImGui.getCursorScreenPos();
        ImDrawList drawList = ImGui.getWindowDrawList();
        int rgba = color == null
            ? 0xFF808080
            : packRgba(color);
        drawList.addRectFilled(origin.x, origin.y, origin.x + sizePixels, origin.y + sizePixels, rgba);
        drawList.addRect(origin.x, origin.y, origin.x + sizePixels, origin.y + sizePixels, 0xFF404040, 0.0f, 0, 1.0f);
        ImGui.dummy(sizePixels, sizePixels);
    }

    private static int packRgba(ColorData color) {
        int r = Math.round(color.r() * 255.0f) & 0xFF;
        int g = Math.round(color.g() * 255.0f) & 0xFF;
        int b = Math.round(color.b() * 255.0f) & 0xFF;
        int a = Math.round(color.a() * 255.0f) & 0xFF;
        // ImGui ABGR packing
        return (a << 24) | (b << 16) | (g << 8) | r;
    }
}

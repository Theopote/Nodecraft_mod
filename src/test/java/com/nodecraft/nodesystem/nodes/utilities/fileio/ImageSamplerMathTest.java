package com.nodecraft.nodesystem.nodes.utilities.fileio;

import com.nodecraft.nodesystem.datatypes.ColorData;
import com.nodecraft.nodesystem.datatypes.ImageData;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ImageSamplerMathTest {

    @Test
    void uvEndpointsMapToInclusivePixelCorners() {
        ImageSamplerMath.SamplePoint p0 = ImageSamplerMath.toSampleSpace(
            0.0d, 0.0d, true, 4, 4, ImageSamplerNode.OriginMode.TOP_LEFT);
        assertEquals(0.0d, p0.x(), 1e-9);
        assertEquals(0.0d, p0.y(), 1e-9);

        ImageSamplerMath.SamplePoint p1 = ImageSamplerMath.toSampleSpace(
            1.0d, 1.0d, true, 4, 4, ImageSamplerNode.OriginMode.TOP_LEFT);
        assertEquals(3.0d, p1.x(), 1e-9);
        assertEquals(3.0d, p1.y(), 1e-9);
    }

    @Test
    void bottomLeftOriginFlipsY() {
        ImageSamplerMath.SamplePoint point = ImageSamplerMath.toSampleSpace(
            0.0d, 0.0d, true, 5, 5, ImageSamplerNode.OriginMode.BOTTOM_LEFT);
        assertEquals(0.0d, point.x(), 1e-9);
        assertEquals(4.0d, point.y(), 1e-9);
    }

    @Test
    void nearestSampleMatchesCornerColor() {
        List<ColorData> colors = new ArrayList<>();
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 2; x++) {
                colors.add(new ColorData(x, y, 0.0f, 1.0f));
            }
        }
        ImageData image = ImageData.create(2, 2, 2, 2, 1, colors);
        ImageSamplerMath.SamplePoint space = ImageSamplerMath.toSampleSpace(
            1.0d, 0.0d, true, 2, 2, ImageSamplerNode.OriginMode.TOP_LEFT);
        ColorData color = ImageSamplerMath.sample(
            image, space.x(), space.y(),
            ImageSamplerNode.FilterMode.NEAREST,
            ImageSamplerNode.WrapMode.CLAMP);
        assertNotNull(color);
        assertEquals(1.0f, color.r(), 1e-5f);
        assertEquals(0.0f, color.g(), 1e-5f);
    }

    @Test
    void thumbnailMappingKeepsEndpoints() {
        ImageSamplerMath.SamplePoint pixel = ImageSamplerMath.toThumbnailPixel(
            3.0d, 0.0d, 4, 4, 100.0f, 50.0f);
        assertEquals(99.0d, pixel.x(), 1e-6);
        assertEquals(0.0d, pixel.y(), 1e-6);
    }
}

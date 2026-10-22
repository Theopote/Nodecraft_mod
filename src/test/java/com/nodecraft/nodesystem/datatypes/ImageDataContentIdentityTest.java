package com.nodecraft.nodesystem.datatypes;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ImageDataContentIdentityTest {

    @Test
    void identicalImagesShareIdentity() {
        ImageData a = ImageData.create(2, 2, 2, 2, 1, List.of(
            ColorData.RED, ColorData.GREEN, ColorData.BLUE, ColorData.WHITE));
        ImageData b = ImageData.create(2, 2, 2, 2, 1, List.of(
            ColorData.RED, ColorData.GREEN, ColorData.BLUE, ColorData.WHITE));
        assertEquals(a.contentIdentity(), b.contentIdentity());
    }

    @Test
    void interiorPixelChangeChangesIdentity() {
        List<ColorData> base = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            base.add(ColorData.BLACK);
        }
        List<ColorData> altered = new ArrayList<>(base);
        altered.set(4, ColorData.RED);
        ImageData a = ImageData.create(3, 3, 3, 3, 1, base);
        ImageData b = ImageData.create(3, 3, 3, 3, 1, altered);
        assertNotEquals(a.contentIdentity(), b.contentIdentity());
    }
}

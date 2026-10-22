package com.nodecraft.gui.components.customui;

import com.nodecraft.nodesystem.datatypes.ColorData;
import com.nodecraft.nodesystem.datatypes.ImageData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomUIImageTextureCacheTest {

    @Test
    void headlessUploadReturnsZeroWithoutImGuiContext() {
        CustomUIImageTextureCache cache = CustomUIImageTextureCache.getInstance();
        cache.clearEntriesHeadless();
        ImageData image = ImageData.create(2, 2, 2, 2, 1, List.of(
            ColorData.RED, ColorData.GREEN, ColorData.BLUE, ColorData.WHITE));
        int textureId = cache.getOrUpload(UUID.randomUUID(), image);
        assertEquals(0, textureId);
        assertEquals(0, cache.cachedTextureCount());
    }

    @Test
    void cacheKeyDiffersForDifferentContent() {
        ImageData a = ImageData.create(1, 1, 1, 1, 1, List.of(ColorData.RED));
        ImageData b = ImageData.create(1, 1, 1, 1, 1, List.of(ColorData.BLUE));
        assertTrue(!CustomUIImageTextureCache.cacheKey(a).equals(CustomUIImageTextureCache.cacheKey(b)));
    }
}

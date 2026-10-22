package com.nodecraft.gui.components.customui;

import com.nodecraft.nodesystem.datatypes.ColorData;
import com.nodecraft.nodesystem.datatypes.ImageData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomUIImageTextureCacheTest {

    @BeforeEach
    void clearCache() {
        CustomUIImageTextureCache.getInstance().clearEntriesHeadless();
    }

    @Test
    void headlessUploadReturnsZeroWithoutImGuiContext() {
        CustomUIImageTextureCache cache = CustomUIImageTextureCache.getInstance();
        ImageData image = ImageData.create(2, 2, 2, 2, 1, List.of(
            ColorData.RED, ColorData.GREEN, ColorData.BLUE, ColorData.WHITE));
        int textureId = cache.getOrUpload(UUID.randomUUID(), image);
        assertEquals(0, textureId);
        assertEquals(0, cache.cachedTextureCount());
    }

    @Test
    void cacheKeyDiffersWhenInteriorPixelsDifferButCornersMatch() {
        // Same dims; first, middle, and last match — old sparse fingerprint would collide.
        ColorData corner = ColorData.RED;
        ColorData mid = ColorData.GREEN;
        List<ColorData> a = new ArrayList<>();
        List<ColorData> b = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            a.add(i == 0 || i == 8 ? corner : (i == 4 ? mid : ColorData.BLUE));
            b.add(i == 0 || i == 8 ? corner : (i == 4 ? mid : ColorData.WHITE));
        }
        ImageData imageA = ImageData.create(3, 3, 3, 3, 1, a);
        ImageData imageB = ImageData.create(3, 3, 3, 3, 1, b);

        assertEquals(imageA.colors().getFirst(), imageB.colors().getFirst());
        assertEquals(imageA.colors().get(4), imageB.colors().get(4));
        assertEquals(imageA.colors().get(8), imageB.colors().get(8));
        assertNotEquals(imageA.contentIdentity(), imageB.contentIdentity());
        assertNotEquals(
            CustomUIImageTextureCache.computeCacheKey(imageA),
            CustomUIImageTextureCache.computeCacheKey(imageB));
    }

    @Test
    void ownerSwitchReleasesPreviousKeyWhenUnreferenced() {
        CustomUIImageTextureCache cache = CustomUIImageTextureCache.getInstance();
        UUID owner = UUID.randomUUID();
        ImageData first = ImageData.create(1, 1, 1, 1, 1, List.of(ColorData.RED));
        ImageData second = ImageData.create(1, 1, 1, 1, 1, List.of(ColorData.BLUE));

        cache.simulateBindHeadless(owner, first);
        String keyA = CustomUIImageTextureCache.computeCacheKey(first);
        assertEquals(1, cache.refCount(keyA));
        assertEquals(1, cache.cachedTextureCount());

        cache.simulateBindHeadless(owner, second);
        String keyB = CustomUIImageTextureCache.computeCacheKey(second);
        assertEquals(0, cache.refCount(keyA));
        assertEquals(1, cache.refCount(keyB));
        // Previous unreferenced texture removed
        assertEquals(1, cache.cachedTextureCount());
    }

    @Test
    void invalidateClearsOwnerAndUnreferencedTexture() {
        CustomUIImageTextureCache cache = CustomUIImageTextureCache.getInstance();
        UUID owner = UUID.randomUUID();
        ImageData image = ImageData.create(1, 1, 1, 1, 1, List.of(ColorData.GREEN));
        cache.simulateBindHeadless(owner, image);
        assertEquals(1, cache.ownerCount());
        cache.invalidate(owner);
        assertEquals(0, cache.ownerCount());
        assertEquals(0, cache.cachedTextureCount());
    }

    @Test
    void sharedOwnersKeepTextureUntilLastRelease() {
        CustomUIImageTextureCache cache = CustomUIImageTextureCache.getInstance();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        ImageData image = ImageData.create(1, 1, 1, 1, 1, List.of(ColorData.WHITE));
        cache.simulateBindHeadless(a, image);
        cache.simulateBindHeadless(b, image);
        String key = CustomUIImageTextureCache.computeCacheKey(image);
        assertEquals(2, cache.refCount(key));
        cache.invalidate(a);
        assertEquals(1, cache.refCount(key));
        assertEquals(1, cache.cachedTextureCount());
        cache.invalidate(b);
        assertEquals(0, cache.cachedTextureCount());
    }

    @Test
    void nullImageReleasesOwner() {
        CustomUIImageTextureCache cache = CustomUIImageTextureCache.getInstance();
        UUID owner = UUID.randomUUID();
        ImageData image = ImageData.create(1, 1, 1, 1, 1, List.of(ColorData.RED));
        cache.simulateBindHeadless(owner, image);
        assertEquals(0, cache.getOrUpload(owner, null));
        assertEquals(0, cache.ownerCount());
        assertEquals(0, cache.cachedTextureCount());
    }

    @Test
    void hardCapRefusesNewTextureWhenAllSlotsReferenced() {
        CustomUIImageTextureCache cache = CustomUIImageTextureCache.getInstance();
        int cap = CustomUIImageTextureCache.MAX_CACHED_TEXTURES;
        for (int i = 0; i < cap; i++) {
            float channel = (i % 256) / 255.0f;
            ImageData image = ImageData.create(1, 1, 1, 1, 1,
                List.of(new ColorData(channel, (1.0f - channel), 0.25f, 1.0f)));
            assertTrue(cache.simulateBindHeadless(UUID.randomUUID(), image));
        }
        assertEquals(cap, cache.cachedTextureCount());

        ImageData overflow = ImageData.create(1, 1, 1, 1, 1, List.of(ColorData.WHITE));
        assertEquals(false, cache.simulateBindHeadless(UUID.randomUUID(), overflow));
        assertEquals(cap, cache.cachedTextureCount());
    }

    @Test
    void hardCapAllowsExclusiveOwnerReplacement() {
        CustomUIImageTextureCache cache = CustomUIImageTextureCache.getInstance();
        int cap = CustomUIImageTextureCache.MAX_CACHED_TEXTURES;
        UUID stickyOwner = UUID.randomUUID();
        ImageData first = ImageData.create(1, 1, 1, 1, 1, List.of(ColorData.RED));
        assertTrue(cache.simulateBindHeadless(stickyOwner, first));

        for (int i = 1; i < cap; i++) {
            float channel = (i % 256) / 255.0f;
            ImageData image = ImageData.create(1, 1, 1, 1, 1,
                List.of(new ColorData(channel, 0.5f, 0.75f, 1.0f)));
            assertTrue(cache.simulateBindHeadless(UUID.randomUUID(), image));
        }
        assertEquals(cap, cache.cachedTextureCount());

        ImageData replacement = ImageData.create(1, 1, 1, 1, 1, List.of(ColorData.BLUE));
        assertTrue(cache.simulateBindHeadless(stickyOwner, replacement));
        assertEquals(cap, cache.cachedTextureCount());
        assertEquals(1, cache.refCount(CustomUIImageTextureCache.computeCacheKey(replacement)));
        assertEquals(0, cache.refCount(CustomUIImageTextureCache.computeCacheKey(first)));
    }
}

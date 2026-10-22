package com.nodecraft.gui.components.customui;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.nodesystem.datatypes.ColorData;
import com.nodecraft.nodesystem.datatypes.ImageData;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Lazy GPU texture cache for custom UI image thumbnails.
 * <p>
 * Cache keys use a full content identity from {@link ImageData#contentIdentity()} (all pixels),
 * not sparse first/middle/last probes. Owners are reference-counted; switching or clearing an
 * owner's image releases unreferenced textures. OpenGL calls stay on the render thread.
 */
public final class CustomUIImageTextureCache {

    public static final int MAX_THUMB_EDGE = 128;
    public static final int MAX_UPLOADS_PER_FRAME = 2;
    /**
     * Hard cap on retained GPU thumbnails. Unreferenced entries are evicted first;
     * when every slot is still referenced, new uploads are refused (caller shows a placeholder)
     * unless the requesting owner can free an exclusive previous texture as a safe replacement.
     */
    public static final int MAX_CACHED_TEXTURES = 48;

    private static final CustomUIImageTextureCache INSTANCE = new CustomUIImageTextureCache();

    private final Map<String, Integer> textures = new LinkedHashMap<>(64, 0.75f, true);
    private final Map<String, Integer> refCounts = new HashMap<>();
    private final Map<UUID, String> ownerKeys = new HashMap<>();
    /** Memoize expensive content keys while the ImageData instance is reachable. */
    private final Map<ImageData, String> keyMemo = new WeakHashMap<>();
    private int uploadsThisFrame;

    private CustomUIImageTextureCache() {
    }

    public static CustomUIImageTextureCache getInstance() {
        return INSTANCE;
    }

    public void beginFrame() {
        uploadsThisFrame = 0;
    }

    /**
     * @return OpenGL texture id, or 0 if unavailable / budget exhausted
     */
    public int getOrUpload(@Nullable UUID ownerNodeId, @Nullable ImageData image) {
        if (image == null) {
            releaseOwner(ownerNodeId);
            return 0;
        }
        if (!CustomUIMetricsSafe.hasImGuiContext()) {
            return 0;
        }

        String key = cacheKey(image);
        Integer cached = textures.get(key);
        if (cached != null && cached > 0) {
            bindOwner(ownerNodeId, key);
            // Touch LRU order
            textures.get(key);
            return cached;
        }
        if (uploadsThisFrame >= MAX_UPLOADS_PER_FRAME) {
            return 0;
        }
        if (!admitNewTextureSlot(ownerNodeId)) {
            return 0;
        }
        int textureId = uploadThumbnail(image);
        if (textureId <= 0) {
            return 0;
        }
        uploadsThisFrame++;
        textures.put(key, textureId);
        refCounts.put(key, 0);
        bindOwner(ownerNodeId, key);
        return textureId;
    }

    public void invalidate(@Nullable UUID ownerNodeId) {
        releaseOwner(ownerNodeId);
    }

    public void disposeAll() {
        if (!CustomUIMetricsSafe.isOnRenderThread()) {
            // Still clear bookkeeping so next bind starts clean.
            textures.clear();
            refCounts.clear();
            ownerKeys.clear();
            keyMemo.clear();
            return;
        }
        for (Integer id : textures.values()) {
            if (id != null && id > 0) {
                try {
                    GL11.glDeleteTextures(id);
                } catch (Throwable ignored) {
                }
            }
        }
        textures.clear();
        refCounts.clear();
        ownerKeys.clear();
        keyMemo.clear();
    }

    /**
     * Content-stable key: dimensions + full {@link ImageData#contentIdentity()}.
     * Two images that differ only in interior pixels must not collide.
     */
    static String cacheKey(ImageData image) {
        return INSTANCE.keyMemo.computeIfAbsent(image, CustomUIImageTextureCache::computeCacheKey);
    }

    static String computeCacheKey(ImageData image) {
        return image.sampleWidth() + "x" + image.sampleHeight()
            + ":src" + image.sourceWidth() + "x" + image.sourceHeight()
            + ":step" + image.sampleStep()
            + ":" + Long.toHexString(image.contentIdentity());
    }

    private void bindOwner(@Nullable UUID ownerNodeId, String key) {
        if (ownerNodeId == null) {
            return;
        }
        String previous = ownerKeys.get(ownerNodeId);
        if (key.equals(previous)) {
            return;
        }
        if (previous != null) {
            decrementRef(previous);
        }
        ownerKeys.put(ownerNodeId, key);
        refCounts.merge(key, 1, Integer::sum);
    }

    private void releaseOwner(@Nullable UUID ownerNodeId) {
        if (ownerNodeId == null) {
            return;
        }
        String key = ownerKeys.remove(ownerNodeId);
        if (key != null) {
            decrementRef(key);
        }
    }

    private void decrementRef(String key) {
        int next = refCounts.merge(key, -1, Integer::sum);
        if (next <= 0) {
            refCounts.remove(key);
            deleteTexture(key);
        }
    }

    /**
     * Ensures there is room for one new texture key. Evicts unreferenced entries first;
     * if still full, frees the caller's exclusive previous texture (safe replacement).
     * Returns false when the hard cap is still saturated by live references.
     */
    private boolean admitNewTextureSlot(@Nullable UUID ownerNodeId) {
        evictIfNeeded();
        if (textures.size() < MAX_CACHED_TEXTURES) {
            return true;
        }
        // Safe replacement: this owner alone holds a slot — free it so the new image can take its place.
        if (ownerNodeId != null) {
            String previous = ownerKeys.get(ownerNodeId);
            if (previous != null && refCounts.getOrDefault(previous, 0) <= 1) {
                releaseOwner(ownerNodeId);
                evictIfNeeded();
            }
        }
        return textures.size() < MAX_CACHED_TEXTURES;
    }

    private void evictIfNeeded() {
        while (textures.size() >= MAX_CACHED_TEXTURES) {
            String victim = findUnreferencedVictim();
            if (victim == null) {
                break;
            }
            deleteTexture(victim);
            refCounts.remove(victim);
        }
    }

    private @Nullable String findUnreferencedVictim() {
        for (String key : textures.keySet()) {
            int refs = refCounts.getOrDefault(key, 0);
            if (refs <= 0) {
                return key;
            }
        }
        return null;
    }

    private int uploadThumbnail(ImageData image) {
        try {
            int srcW = image.sampleWidth();
            int srcH = image.sampleHeight();
            float scale = Math.min(1.0f, (float) MAX_THUMB_EDGE / Math.max(srcW, srcH));
            int thumbW = Math.max(1, Math.round(srcW * scale));
            int thumbH = Math.max(1, Math.round(srcH * scale));

            ByteBuffer buffer = BufferUtils.createByteBuffer(thumbW * thumbH * 4);
            for (int ty = 0; ty < thumbH; ty++) {
                int sy = Math.min(srcH - 1, Math.round(ty * (srcH - 1) / (float) Math.max(1, thumbH - 1)));
                for (int tx = 0; tx < thumbW; tx++) {
                    int sx = Math.min(srcW - 1, Math.round(tx * (srcW - 1) / (float) Math.max(1, thumbW - 1)));
                    ColorData color = image.colors().get(sy * srcW + sx);
                    buffer.put((byte) Math.round(color.r() * 255.0f));
                    buffer.put((byte) Math.round(color.g() * 255.0f));
                    buffer.put((byte) Math.round(color.b() * 255.0f));
                    buffer.put((byte) Math.round(color.a() * 255.0f));
                }
            }
            buffer.flip();

            int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            int previousAlignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
            int textureId = GL11.glGenTextures();
            if (textureId == 0) {
                return 0;
            }
            try {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
                GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexImage2D(
                    GL11.GL_TEXTURE_2D,
                    0,
                    GL11.GL_RGBA,
                    thumbW,
                    thumbH,
                    0,
                    GL11.GL_RGBA,
                    GL11.GL_UNSIGNED_BYTE,
                    buffer
                );
                return textureId;
            } finally {
                GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, previousAlignment);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            }
        } catch (Throwable t) {
            NodeCraft.LOGGER.debug("Custom UI image upload failed: {}", t.toString());
            return 0;
        }
    }

    private void deleteTexture(String key) {
        Integer id = textures.remove(key);
        if (id == null || id <= 0) {
            return;
        }
        if (!CustomUIMetricsSafe.isOnRenderThread()) {
            // Drop bookkeeping; GPU id may leak only if called off-thread (should not happen).
            return;
        }
        try {
            GL11.glDeleteTextures(id);
        } catch (Throwable ignored) {
        }
    }

    int cachedTextureCount() {
        return textures.size();
    }

    int ownerCount() {
        return ownerKeys.size();
    }

    int refCount(String key) {
        return refCounts.getOrDefault(key, 0);
    }

    void clearEntriesHeadless() {
        textures.clear();
        refCounts.clear();
        ownerKeys.clear();
        keyMemo.clear();
    }

    /**
     * Headless ownership transition without GL (tests).
     *
     * @return {@code true} when the owner is bound to the image key; {@code false} when the hard
     *         capacity budget refused admitting a new texture
     */
    boolean simulateBindHeadless(UUID owner, ImageData image) {
        String key = cacheKey(image);
        if (!textures.containsKey(key)) {
            if (!admitNewTextureSlot(owner)) {
                return false;
            }
            textures.put(key, -1); // sentinel non-GL id for bookkeeping tests
            refCounts.put(key, 0);
        }
        bindOwner(owner, key);
        return true;
    }
}

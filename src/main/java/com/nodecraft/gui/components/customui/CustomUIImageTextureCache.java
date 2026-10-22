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
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Lazy GPU texture cache for custom UI image thumbnails.
 * Uploads only on the render thread with a live ImGui context; never every UI frame for unchanged images.
 */
public final class CustomUIImageTextureCache {

    public static final int MAX_THUMB_EDGE = 128;
    public static final int MAX_UPLOADS_PER_FRAME = 2;

    private static final CustomUIImageTextureCache INSTANCE = new CustomUIImageTextureCache();

    private final Map<String, Integer> textures = new HashMap<>();
    private final Map<UUID, String> ownerKeys = new HashMap<>();
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
        if (image == null || !CustomUIMetricsSafe.hasImGuiContext()) {
            return 0;
        }
        String key = cacheKey(image);
        Integer cached = textures.get(key);
        if (cached != null && cached > 0) {
            if (ownerNodeId != null) {
                ownerKeys.put(ownerNodeId, key);
            }
            return cached;
        }
        if (uploadsThisFrame >= MAX_UPLOADS_PER_FRAME) {
            return 0;
        }
        int textureId = uploadThumbnail(image);
        if (textureId <= 0) {
            return 0;
        }
        uploadsThisFrame++;
        textures.put(key, textureId);
        if (ownerNodeId != null) {
            String previous = ownerKeys.put(ownerNodeId, key);
            if (previous != null && !previous.equals(key)) {
                // keep previous texture — may still be referenced by another owner key
            }
        }
        return textureId;
    }

    public void invalidate(@Nullable UUID ownerNodeId) {
        if (ownerNodeId == null) {
            return;
        }
        String key = ownerKeys.remove(ownerNodeId);
        if (key == null) {
            return;
        }
        boolean stillOwned = ownerKeys.containsValue(key);
        if (!stillOwned) {
            deleteTexture(key);
        }
    }

    public void disposeAll() {
        if (!CustomUIMetricsSafe.isOnRenderThread()) {
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
        ownerKeys.clear();
    }

    static String cacheKey(ImageData image) {
        int hash = Objects.hash(
            image.sourceWidth(),
            image.sourceHeight(),
            image.sampleWidth(),
            image.sampleHeight(),
            image.sampleStep(),
            image.colors().size()
        );
        // Include a few sample pixels so identical dims with different content don't collide forever
        ListSample probe = probeColors(image);
        return image.sampleWidth() + "x" + image.sampleHeight() + ":" + image.sampleStep()
            + ":" + Integer.toHexString(hash) + ":" + probe.fingerprint();
    }

    private record ListSample(int fingerprint) {
    }

    private static ListSample probeColors(ImageData image) {
        var colors = image.colors();
        int fp = colors.size();
        if (!colors.isEmpty()) {
            ColorData first = colors.getFirst();
            ColorData last = colors.get(colors.size() - 1);
            fp = 31 * fp + Float.hashCode(first.r());
            fp = 31 * fp + Float.hashCode(first.g());
            fp = 31 * fp + Float.hashCode(last.b());
            fp = 31 * fp + Float.hashCode(last.a());
            int mid = colors.size() / 2;
            ColorData midColor = colors.get(mid);
            fp = 31 * fp + Float.hashCode(midColor.r() + midColor.g() + midColor.b());
        }
        return new ListSample(fp);
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
        if (id == null || id <= 0 || !CustomUIMetricsSafe.isOnRenderThread()) {
            return;
        }
        try {
            GL11.glDeleteTextures(id);
        } catch (Throwable ignored) {
        }
    }

    /** Test helper: number of cached textures. */
    int cachedTextureCount() {
        return textures.size();
    }

    /** Test helper: drop entries without GL (headless). */
    void clearEntriesHeadless() {
        textures.clear();
        ownerKeys.clear();
    }
}

package com.nodecraft.gui.utils;

import com.mojang.blaze3d.systems.RenderSystem;
import com.nodecraft.core.NodeCraft;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

/**
 * Node-library icon textures for ImGui.
 * <p>
 * Runtime path: prefer build-time PNG → decode/upload under a per-frame budget.
 * SVG parse/rasterize is a last-resort fallback and never runs unbounded during a draw.
 * <p>
 * Texture ownership lives here for the client lifetime (and resource reloads), not in
 * individual panels — see {@link #cleanup()} / {@link #onResourcesReloaded()}.
 */
public class NodeIconManager {

    private static NodeIconManager instance;

    public static final int ICON_RENDER_SIZE = SvgIconRasterizer.DEFAULT_SIZE;
    /** Soft budget: new GPU uploads attempted per UI frame. */
    public static final int MAX_UPLOADS_PER_FRAME = 4;
    /** Log an individual icon when decode+upload exceeds this (ms). */
    private static final long SLOW_ICON_MS = 25L;
    /** Log a frame summary when this many new icons were requested in one frame. */
    private static final int BURST_REQUEST_LOG_THRESHOLD = 8;

    private final Map<String, Integer> textureCache = new HashMap<>();
    private final Map<String, Integer> categoryColors = new HashMap<>();
    /** Permanent failure cache (missing / corrupt / upload failed). */
    private final Set<String> failedResources = new HashSet<>();
    /** Logical node key → winning texture-cache key. */
    private final Map<String, String> resolvedTextureKeys = new HashMap<>();

    private final Queue<PendingLoad> pendingQueue = new ArrayDeque<>();
    private final Set<String> pendingKeys = new HashSet<>();

    private int placeholderTextureId;
    private int uploadsThisFrame;
    private int frameRequestCount;
    private long frameRequestStartedNs;
    private String frameRequestCategoryHint;

    private NodeIconManager() {
        initCategoryColors();
    }

    public static NodeIconManager getInstance() {
        if (instance == null) {
            instance = new NodeIconManager();
        }
        return instance;
    }

    public void initialize() {
        NodeCraft.LOGGER.info(
                "Node icon manager ready (cachedTextures={}, pending={}, failed={})",
                textureCache.size(),
                pendingQueue.size(),
                failedResources.size()
        );
    }

    /**
     * Resets the per-frame upload budget and drains previously queued icons.
     * Call once at the start of each ImGui frame.
     */
    public void beginFrame() {
        if (!isOnRenderThread("begin node icon frame")) {
            return;
        }
        uploadsThisFrame = 0;
        processPendingUploads(MAX_UPLOADS_PER_FRAME);
        maybeLogBurstSummary();
    }

    /**
     * Releases all GPU textures. Call only from client shutdown or explicit full reset —
     * not from panel {@code cleanup()}.
     */
    public void cleanup() {
        if (!isOnRenderThread("cleanup node icon textures")) {
            return;
        }
        deleteAllTextures();
        pendingQueue.clear();
        pendingKeys.clear();
        failedResources.clear();
        resolvedTextureKeys.clear();
        placeholderTextureId = 0;
        uploadsThisFrame = 0;
        NodeCraft.LOGGER.info("Node icon manager cleaned up");
    }

    /**
     * Resource pack / reload: drop GPU textures and resolution memo so icons re-resolve.
     */
    public void onResourcesReloaded() {
        if (!isOnRenderThread("reload node icon textures")) {
            return;
        }
        deleteAllTextures();
        pendingQueue.clear();
        pendingKeys.clear();
        failedResources.clear();
        resolvedTextureKeys.clear();
        placeholderTextureId = 0;
        uploadsThisFrame = 0;
        NodeCraft.LOGGER.info("Node icon manager cleared after resource reload");
    }

    /**
     * Loads (or requests) the icon for a node.
     * <p>
     * Cache hits return immediately. Misses consume the per-frame upload budget when
     * available; otherwise they enqueue and return a shared placeholder so ImGui drawing
     * never blocks on bulk SVG/PNG work.
     */
    public int loadNodeIcon(String nodeId, String category, String explicitIcon) {
        if (!isOnRenderThread("load node icon")) {
            return 0;
        }

        String logicalKey = NodeIconPathResolver.logicalKey(nodeId, category, explicitIcon);
        String resolvedKey = resolvedTextureKeys.get(logicalKey);
        if (resolvedKey != null) {
            Integer cached = textureCache.get(resolvedKey);
            if (cached != null) {
                return cached;
            }
            resolvedTextureKeys.remove(logicalKey);
        }

        noteRequest(category);

        for (NodeIconPathResolver.Candidate candidate : NodeIconPathResolver.candidates(nodeId, category, explicitIcon)) {
            if (candidate.kind() == NodeIconPathResolver.CandidateKind.FALLBACK) {
                int fallbackId = getFallbackTexture(candidate.fallbackCategory());
                resolvedTextureKeys.put(logicalKey, candidate.cacheKey());
                return fallbackId;
            }

            String cacheKey = candidate.cacheKey();
            Integer cached = textureCache.get(cacheKey);
            if (cached != null) {
                resolvedTextureKeys.put(logicalKey, cacheKey);
                return cached;
            }
            if (failedResources.contains(cacheKey)) {
                continue;
            }
            if (pendingKeys.contains(cacheKey)) {
                return getPlaceholderTexture();
            }

            PendingLoad pending = new PendingLoad(
                    cacheKey,
                    candidate.resourcePath(),
                    logicalKey,
                    nodeId,
                    category,
                    candidate.kind().name()
            );

            if (uploadsThisFrame < MAX_UPLOADS_PER_FRAME) {
                int texId = completeLoad(pending);
                if (texId != 0) {
                    return texId;
                }
                // Failed this candidate — try the next (e.g. PNG miss → SVG).
                continue;
            }

            enqueue(pending);
            return getPlaceholderTexture();
        }

        return getFallbackTexture("unknown");
    }

    public int loadNodeIcon(String nodeId, String category) {
        return loadNodeIcon(nodeId, category, null);
    }

    public int loadIcon(String iconId) {
        if (!isOnRenderThread("load icon")) {
            return 0;
        }
        // Reuse node resolution with an explicit icon token so PNG is preferred over SVG.
        return loadNodeIcon(iconId, "", iconId);
    }

    public String getCategoryIconId(String categoryId) {
        String mainCategory = NodeIconPathResolver.extractMainCategory(categoryId);
        return mainCategory.isEmpty() ? "" : "cat:" + mainCategory;
    }

    /**
     * Processes pending icon loads up to {@code maxUploads} (respecting remaining frame budget).
     */
    public void processPendingUploads() {
        processPendingUploads(MAX_UPLOADS_PER_FRAME - uploadsThisFrame);
    }

    public void processPendingUploads(int maxUploads) {
        if (!isOnRenderThread("process pending icon uploads")) {
            return;
        }
        int budget = Math.min(maxUploads, MAX_UPLOADS_PER_FRAME - uploadsThisFrame);
        if (budget <= 0 || pendingQueue.isEmpty()) {
            return;
        }

        int processed = 0;
        long frameStarted = System.nanoTime();
        while (processed < budget && !pendingQueue.isEmpty()) {
            PendingLoad pending = pendingQueue.poll();
            if (pending == null) {
                break;
            }
            pendingKeys.remove(pending.cacheKey());

            if (textureCache.containsKey(pending.cacheKey()) || failedResources.contains(pending.cacheKey())) {
                continue;
            }

            completeLoad(pending);
            processed++;
        }

        long frameMs = (System.nanoTime() - frameStarted) / 1_000_000L;
        if (processed > 0 && frameMs >= 16L) {
            NodeCraft.LOGGER.info(
                    "Node icon upload frame: processed={} remaining={} took={}ms",
                    processed,
                    pendingQueue.size(),
                    frameMs
            );
        }
    }

    /** Package-visible for tests. */
    int resolvedCacheSize() {
        return resolvedTextureKeys.size();
    }

    /** Package-visible for tests. */
    int textureCacheSize() {
        return textureCache.size();
    }

    /** Package-visible for tests. */
    int pendingCount() {
        return pendingQueue.size();
    }

    /** Package-visible for tests. */
    int failedCount() {
        return failedResources.size();
    }

    /** Package-visible for tests — clears resolution memo without touching GL textures. */
    void clearResolutionCache() {
        resolvedTextureKeys.clear();
    }

    /** Package-visible for tests. */
    boolean isFailed(String cacheKey) {
        return failedResources.contains(cacheKey);
    }

    /** Package-visible for tests. */
    void markFailedForTest(String cacheKey) {
        failedResources.add(cacheKey);
    }

    private int completeLoad(PendingLoad pending) {
        uploadsThisFrame++;
        LoadResult result = loadResourceToTexture(pending);
        if (result.textureId() != 0) {
            textureCache.put(pending.cacheKey(), result.textureId());
            if (pending.logicalKey() != null && !pending.logicalKey().isEmpty()) {
                resolvedTextureKeys.putIfAbsent(pending.logicalKey(), pending.cacheKey());
            }
            if (result.totalMs() >= SLOW_ICON_MS) {
                NodeCraft.LOGGER.info(
                        "Slow node icon: nodeId={} path={} decode={}ms upload={}ms total={}ms",
                        pending.nodeId(),
                        pending.resourcePath(),
                        result.decodeMs(),
                        result.uploadMs(),
                        result.totalMs()
                );
            }
            return result.textureId();
        }

        failedResources.add(pending.cacheKey());
        NodeCraft.LOGGER.warn(
                "Node icon load failed: nodeId={} category={} kind={} path={} stage={} reason={}",
                pending.nodeId(),
                pending.category(),
                pending.kind(),
                pending.resourcePath(),
                result.stage(),
                result.reason()
        );
        return 0;
    }

    private void enqueue(PendingLoad pending) {
        if (!pendingKeys.add(pending.cacheKey())) {
            return;
        }
        pendingQueue.add(pending);
    }

    private void noteRequest(String category) {
        if (frameRequestCount == 0) {
            frameRequestStartedNs = System.nanoTime();
            frameRequestCategoryHint = category;
        }
        frameRequestCount++;
    }

    private void maybeLogBurstSummary() {
        if (frameRequestCount < BURST_REQUEST_LOG_THRESHOLD) {
            frameRequestCount = 0;
            return;
        }
        long ms = (System.nanoTime() - frameRequestStartedNs) / 1_000_000L;
        NodeCraft.LOGGER.info(
                "Node icon burst request: count={} categoryHint={} queueDepth={} uploadsThisFrame={} windowMs={}",
                frameRequestCount,
                frameRequestCategoryHint,
                pendingQueue.size(),
                uploadsThisFrame,
                ms
        );
        frameRequestCount = 0;
        frameRequestCategoryHint = null;
    }

    private LoadResult loadResourceToTexture(PendingLoad pending) {
        String resourcePath = pending.resourcePath();
        if (resourcePath == null || resourcePath.isBlank()) {
            return LoadResult.failed("validate", "blank resource path");
        }

        Identifier id = Identifier.tryParse(NodeIconPathResolver.ICON_NAMESPACE + ":" + resourcePath);
        if (id == null) {
            return LoadResult.failed("validate", "invalid identifier");
        }

        Optional<Resource> resource;
        try {
            resource = MinecraftClient.getInstance().getResourceManager().getResource(id);
        } catch (RuntimeException e) {
            return LoadResult.failed("resource", e.toString());
        }
        if (resource.isEmpty()) {
            return LoadResult.failed("resource", "missing");
        }

        long decodeStarted = System.nanoTime();
        BufferedImage image;
        String stage = NodeIconPathResolver.isPngPath(resourcePath) ? "png_decode" : "svg_rasterize";
        try (InputStream stream = resource.get().getInputStream()) {
            if (NodeIconPathResolver.isPngPath(resourcePath)) {
                BufferedImage decoded = ImageIO.read(stream);
                if (decoded == null) {
                    return LoadResult.failed(stage, "ImageIO returned null");
                }
                image = SvgIconRasterizer.ensureArgb(decoded);
            } else if (NodeIconPathResolver.isSvgPath(resourcePath)) {
                image = SvgIconRasterizer.rasterize(stream, ICON_RENDER_SIZE, ICON_RENDER_SIZE);
            } else {
                return LoadResult.failed("validate", "unsupported extension");
            }
        } catch (Exception e) {
            return LoadResult.failed(stage, e.toString());
        }
        long decodeMs = (System.nanoTime() - decodeStarted) / 1_000_000L;

        long uploadStarted = System.nanoTime();
        int textureId;
        try {
            textureId = uploadBufferedImage(image);
        } catch (RuntimeException e) {
            return LoadResult.failed("gpu_upload", e.toString());
        }
        long uploadMs = (System.nanoTime() - uploadStarted) / 1_000_000L;

        if (textureId == 0) {
            return LoadResult.failed("gpu_upload", "gl error or gen failed");
        }
        return LoadResult.ok(textureId, decodeMs, uploadMs);
    }

    private int uploadBufferedImage(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);

        for (int argb : pixels) {
            buffer.put((byte) ((argb >> 16) & 0xFF));
            buffer.put((byte) ((argb >> 8) & 0xFF));
            buffer.put((byte) (argb & 0xFF));
            buffer.put((byte) ((argb >> 24) & 0xFF));
        }
        buffer.flip();

        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int previousAlignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        int textureId = 0;
        try {
            clearGlErrors();
            textureId = GL11.glGenTextures();
            if (textureId == 0) {
                return 0;
            }
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexImage2D(
                    GL11.GL_TEXTURE_2D,
                    0,
                    GL11.GL_RGBA8,
                    width,
                    height,
                    0,
                    GL11.GL_RGBA,
                    GL11.GL_UNSIGNED_BYTE,
                    buffer
            );
            int error = GL11.glGetError();
            if (error != GL11.GL_NO_ERROR) {
                NodeCraft.LOGGER.warn("glTexImage2D failed for node icon (glError=0x{})", Integer.toHexString(error));
                GL11.glDeleteTextures(textureId);
                return 0;
            }
            return textureId;
        } finally {
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, previousAlignment);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
        }
    }

    private int getFallbackTexture(String mainCategory) {
        String category = mainCategory == null || mainCategory.isBlank() ? "unknown" : mainCategory;
        String key = "fallback:" + category;
        Integer cached = textureCache.get(key);
        if (cached != null) {
            return cached;
        }

        int color = categoryColors.getOrDefault(category, 0xFF607D8B);
        int textureId = createColorTexture(color);
        if (textureId != 0) {
            textureCache.put(key, textureId);
        }
        return textureId;
    }

    private int getPlaceholderTexture() {
        if (placeholderTextureId != 0) {
            return placeholderTextureId;
        }
        placeholderTextureId = createColorTexture(0xFF9CA3AF);
        if (placeholderTextureId != 0) {
            textureCache.put("placeholder:loading", placeholderTextureId);
        }
        return placeholderTextureId;
    }

    private int createColorTexture(int argb) {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                image.setRGB(x, y, argb);
            }
        }
        return uploadBufferedImage(image);
    }

    private void deleteAllTextures() {
        for (int id : textureCache.values()) {
            if (id != 0) {
                GL11.glDeleteTextures(id);
            }
        }
        textureCache.clear();
    }

    private static void clearGlErrors() {
        while (GL11.glGetError() != GL11.GL_NO_ERROR) {
            // drain
        }
    }

    private boolean isOnRenderThread(String operation) {
        try {
            RenderSystem.assertOnRenderThread();
            return true;
        } catch (IllegalStateException e) {
            NodeCraft.LOGGER.warn("Skipped {} outside the render thread.", operation);
            return false;
        }
    }

    private void initCategoryColors() {
        categoryColors.put("flow", 0xFFF59E0B);
        categoryColors.put("geometry", 0xFF3B82F6);
        categoryColors.put("input", 0xFF10B981);
        categoryColors.put("material", 0xFF8B5CF6);
        categoryColors.put("math", 0xFFEF4444);
        categoryColors.put("output", 0xFF06B6D4);
        categoryColors.put("pattern", 0xFFEAB308);
        categoryColors.put("reference", 0xFF6366F1);
        categoryColors.put("transform", 0xFFEC4899);
        categoryColors.put("utilities", 0xFF64748B);
        categoryColors.put("variable", 0xFFF97316);
        categoryColors.put("world", 0xFF14B8A6);
    }

    private record PendingLoad(
            String cacheKey,
            String resourcePath,
            String logicalKey,
            String nodeId,
            String category,
            String kind
    ) {
    }

    private record LoadResult(int textureId, String stage, String reason, long decodeMs, long uploadMs) {
        static LoadResult ok(int textureId, long decodeMs, long uploadMs) {
            return new LoadResult(textureId, "ok", "", decodeMs, uploadMs);
        }

        static LoadResult failed(String stage, String reason) {
            return new LoadResult(0, stage, reason == null ? "" : reason, 0L, 0L);
        }

        long totalMs() {
            return decodeMs + uploadMs;
        }
    }
}

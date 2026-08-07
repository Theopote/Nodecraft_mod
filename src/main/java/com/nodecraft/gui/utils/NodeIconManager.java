package com.nodecraft.gui.utils;

import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.attributes.ViewBox;
import com.github.weisj.jsvg.parser.LoaderContext;
import com.github.weisj.jsvg.parser.SVGLoader;
import com.mojang.blaze3d.systems.RenderSystem;
import com.nodecraft.core.NodeCraft;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.awt.AlphaComposite;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Loads node-library SVG icons from Minecraft resources and rasterizes them to
 * OpenGL textures for ImGui.
 * <p>
 * Caches both GPU textures and the winning resolution key so subsequent frames
 * skip the multi-candidate path walk. See {@code docs/architecture/node-library-display-cache.md}.
 */
public class NodeIconManager {

    private static NodeIconManager instance;

    private static final int ICON_RENDER_SIZE = 64;

    private final Map<String, Integer> textureCache = new HashMap<>();
    private final Map<String, Integer> categoryColors = new HashMap<>();
    /** Negative cache: resources known missing. */
    private final Set<String> missingResources = new HashSet<>();
    /** Logical node key → winning texture-cache key. */
    private final Map<String, String> resolvedTextureKeys = new HashMap<>();

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
        NodeCraft.LOGGER.info("Node icon manager ready, cached {} textures", textureCache.size());
    }

    public void cleanup() {
        if (!isOnRenderThread("cleanup node icon textures")) {
            return;
        }
        for (int id : textureCache.values()) {
            GL11.glDeleteTextures(id);
        }
        textureCache.clear();
        missingResources.clear();
        resolvedTextureKeys.clear();
        NodeCraft.LOGGER.info("Node icon manager cleaned up");
    }

    /**
     * Loads the icon for a node.
     * <p>
     * Lookup order is defined by {@link NodeIconPathResolver#candidates}.
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
            // Stale resolution (e.g. after partial clear) — re-resolve.
            resolvedTextureKeys.remove(logicalKey);
        }

        for (NodeIconPathResolver.Candidate candidate : NodeIconPathResolver.candidates(nodeId, category, explicitIcon)) {
            if (candidate.kind() == NodeIconPathResolver.CandidateKind.FALLBACK) {
                int fallbackId = getFallbackTexture(candidate.fallbackCategory());
                resolvedTextureKeys.put(logicalKey, candidate.cacheKey());
                return fallbackId;
            }

            int texId = loadOrGet(candidate.cacheKey(), candidate.resourcePath());
            if (texId != 0) {
                resolvedTextureKeys.put(logicalKey, candidate.cacheKey());
                return texId;
            }
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

        String resourcePath = NodeIconPathResolver.normalizeIconPath(iconId);
        if (resourcePath == null) {
            return 0;
        }
        return loadOrGet("icon:" + resourcePath, resourcePath);
    }

    public String getCategoryIconId(String categoryId) {
        String mainCategory = NodeIconPathResolver.extractMainCategory(categoryId);
        return mainCategory.isEmpty() ? "" : "cat:" + mainCategory;
    }

    /** Package-visible for tests. */
    int resolvedCacheSize() {
        return resolvedTextureKeys.size();
    }

    /** Package-visible for tests — clears resolution memo without touching GL textures. */
    void clearResolutionCache() {
        resolvedTextureKeys.clear();
    }

    private int loadOrGet(String cacheKey, String resourcePath) {
        Integer cached = textureCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        if (missingResources.contains(cacheKey)) {
            return 0;
        }

        int texId = loadSvgFromResource(resourcePath);
        if (texId != 0) {
            textureCache.put(cacheKey, texId);
        } else {
            missingResources.add(cacheKey);
            NodeCraft.LOGGER.debug("Added to negative cache: {}", cacheKey);
        }
        return texId;
    }

    private int loadSvgFromResource(String resourcePath) {
        try {
            Identifier id = Identifier.tryParse(NodeIconPathResolver.ICON_NAMESPACE + ":" + resourcePath);
            if (id == null) {
                return 0;
            }

            Optional<Resource> resource = MinecraftClient.getInstance().getResourceManager().getResource(id);
            if (resource.isEmpty()) {
                return 0;
            }

            try (InputStream stream = resource.get().getInputStream()) {
                SVGDocument document = new SVGLoader().load(stream, null, LoaderContext.createDefault());
                if (document == null) {
                    return 0;
                }

                BufferedImage image = rasterizeSvg(document, ICON_RENDER_SIZE, ICON_RENDER_SIZE);
                int textureId = uploadBufferedImage(image);
                NodeCraft.LOGGER.debug("Loaded SVG node icon: {} (texId={})", resourcePath, textureId);
                return textureId;
            }
        } catch (Exception e) {
            NodeCraft.LOGGER.debug("Failed to load SVG node icon {}: {}", resourcePath, e.getMessage());
            return 0;
        }
    }

    private BufferedImage rasterizeSvg(SVGDocument document, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setComposite(AlphaComposite.Clear);
            graphics.fillRect(0, 0, width, height);
            graphics.setComposite(AlphaComposite.SrcOver);
            document.render((Component) null, graphics, new ViewBox(0, 0, width, height));
        } finally {
            graphics.dispose();
        }
        return image;
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

        int textureId = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
        return textureId;
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
        textureCache.put(key, textureId);
        return textureId;
    }

    private int createColorTexture(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        int a = (argb >> 24) & 0xFF;

        ByteBuffer buffer = BufferUtils.createByteBuffer(16 * 16 * 4);
        for (int i = 0; i < 16 * 16; i++) {
            buffer.put((byte) r).put((byte) g).put((byte) b).put((byte) a);
        }
        buffer.flip();

        int textureId = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, 16, 16, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
        return textureId;
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
}

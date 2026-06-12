package com.nodecraft.gui.utils;

import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.attributes.ViewBox;
import com.github.weisj.jsvg.parser.SVGLoader;
import com.nodecraft.core.NodeCraft;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Loads node-library SVG icons from Minecraft resources and rasterizes them to
 * OpenGL textures for ImGui.
 */
public class NodeIconManager {

    private static NodeIconManager instance;

    private static final String ICON_NAMESPACE = "nodecraft";
    private static final String ICON_BASE_PATH = "textures/icons/nodes/";
    private static final String SVG_EXTENSION = ".svg";
    private static final int ICON_RENDER_SIZE = 64;

    private static final SVGLoader SVG_LOADER = new SVGLoader();

    private final Map<String, Integer> textureCache = new HashMap<>();
    private final Map<String, Integer> categoryColors = new HashMap<>();

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
        for (int id : textureCache.values()) {
            GL11.glDeleteTextures(id);
        }
        textureCache.clear();
        NodeCraft.LOGGER.info("Node icon manager cleaned up");
    }

    /**
     * Loads the icon for a node.
     * <p>
     * Lookup order:
     * 1. Explicit node metadata icon.
     * 2. Node id path, for example geometry.boolean.union ->
     *    textures/icons/nodes/geometry/boolean/union.svg.
     * 3. Subcategory icon, for example geometry.boolean ->
     *    textures/icons/nodes/geometry/boolean.svg.
     * 4. Main category icon, for example geometry ->
     *    textures/icons/nodes/geometry/geometry.svg.
     * 5. Category-colored fallback texture.
     */
    public int loadNodeIcon(String nodeId, String category, String explicitIcon) {
        String normalizedCategory = normalizeId(category);

        String explicitPath = normalizeIconPath(explicitIcon);
        if (explicitPath != null) {
            int texId = loadOrGet("explicit:" + explicitPath, explicitPath);
            if (texId != 0) {
                return texId;
            }
        }

        String normalizedNodeId = normalizeId(nodeId);
        if (!normalizedNodeId.isEmpty()) {
            int texId = loadOrGet("node:" + normalizedNodeId, buildNodePath(normalizedNodeId));
            if (texId != 0) {
                return texId;
            }
        }

        if (!normalizedCategory.isEmpty()) {
            int texId = loadOrGet("subcat:" + normalizedCategory, buildCategoryPath(normalizedCategory));
            if (texId != 0) {
                return texId;
            }
        }

        String mainCategory = extractMainCategory(normalizedCategory);
        if (!mainCategory.isEmpty()) {
            int texId = loadOrGet("cat:" + mainCategory, ICON_BASE_PATH + mainCategory + "/" + mainCategory + SVG_EXTENSION);
            if (texId != 0) {
                return texId;
            }
        }

        return getFallbackTexture(mainCategory);
    }

    public int loadNodeIcon(String nodeId, String category) {
        return loadNodeIcon(nodeId, category, null);
    }

    public int loadIcon(String iconId) {
        String resourcePath = normalizeIconPath(iconId);
        if (resourcePath == null) {
            return 0;
        }
        return loadOrGet("icon:" + resourcePath, resourcePath);
    }

    public String getCategoryIconId(String categoryId) {
        String mainCategory = extractMainCategory(normalizeId(categoryId));
        return mainCategory.isEmpty() ? "" : "cat:" + mainCategory;
    }

    private String buildNodePath(String nodeId) {
        String[] parts = nodeId.split("\\.");
        String fileName = parts[parts.length - 1];

        if (parts.length == 1) {
            return ICON_BASE_PATH + fileName + "/" + fileName + SVG_EXTENSION;
        }

        StringBuilder path = new StringBuilder(ICON_BASE_PATH);
        for (int i = 0; i < parts.length - 1; i++) {
            if (!parts[i].isBlank()) {
                path.append(parts[i]).append('/');
            }
        }
        path.append(fileName).append(SVG_EXTENSION);
        return path.toString();
    }

    private String buildCategoryPath(String category) {
        String[] parts = category.split("\\.");
        if (parts.length == 1) {
            return ICON_BASE_PATH + parts[0] + "/" + parts[0] + SVG_EXTENSION;
        }

        StringBuilder path = new StringBuilder(ICON_BASE_PATH);
        path.append(parts[0]).append('/');
        for (int i = 1; i < parts.length; i++) {
            path.append(parts[i]);
            if (i < parts.length - 1) {
                path.append('/');
            }
        }
        path.append(SVG_EXTENSION);
        return path.toString();
    }

    private String normalizeIconPath(String icon) {
        if (icon == null || icon.isBlank()) {
            return null;
        }

        String path = icon.trim().replace('\\', '/');
        if (path.startsWith("cat:")) {
            String mainCategory = normalizeId(path.substring("cat:".length()));
            return mainCategory.isEmpty() ? null : ICON_BASE_PATH + mainCategory + "/" + mainCategory + SVG_EXTENSION;
        }

        int namespaceSeparator = path.indexOf(':');
        if (namespaceSeparator >= 0) {
            String namespace = path.substring(0, namespaceSeparator);
            if (!ICON_NAMESPACE.equals(namespace)) {
                return null;
            }
            path = path.substring(namespaceSeparator + 1);
        }

        while (path.startsWith("/")) {
            path = path.substring(1);
        }

        if (!path.startsWith(ICON_BASE_PATH)) {
            if (!path.contains("/") && path.contains(".")) {
                path = path.replace('.', '/');
            }
            path = ICON_BASE_PATH + path;
        }
        if (!path.endsWith(SVG_EXTENSION)) {
            path += SVG_EXTENSION;
        }
        return path;
    }

    private String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase().replace('/', '.').replace('\\', '.');
    }

    private String extractMainCategory(String category) {
        if (category == null || category.isBlank()) {
            return "";
        }
        int dot = category.indexOf('.');
        return dot >= 0 ? category.substring(0, dot) : category;
    }

    private int loadOrGet(String cacheKey, String resourcePath) {
        Integer cached = textureCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        int texId = loadSvgFromResource(resourcePath);
        if (texId != 0) {
            textureCache.put(cacheKey, texId);
        }
        return texId;
    }

    private int loadSvgFromResource(String resourcePath) {
        try {
            Identifier id = Identifier.tryParse(ICON_NAMESPACE + ":" + resourcePath);
            if (id == null) {
                return 0;
            }

            Optional<Resource> resource = MinecraftClient.getInstance().getResourceManager().getResource(id);
            if (resource.isEmpty()) {
                return 0;
            }

            try (InputStream stream = resource.get().getInputStream()) {
                SVGDocument document = SVG_LOADER.load(stream);
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
            document.render(null, graphics, new ViewBox(0, 0, width, height));
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

package com.nodecraft.gui.utils;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pure icon resource-path resolution for the node library.
 * <p>
 * No OpenGL / Minecraft dependencies — safe for unit tests.
 */
public final class NodeIconPathResolver {

    public static final String ICON_NAMESPACE = "nodecraft";
    public static final String ICON_BASE_PATH = "textures/icons/nodes/";
    public static final String SVG_EXTENSION = ".svg";

    public enum CandidateKind {
        EXPLICIT,
        NODE_ID,
        SUBCATEGORY,
        MAIN_CATEGORY,
        FALLBACK
    }

    /**
     * One lookup candidate in resolution order.
     *
     * @param cacheKey     stable key for texture / negative caches
     * @param resourcePath Minecraft resource path under the nodecraft namespace, or {@code null} for synthetic fallback
     * @param kind         candidate kind
     * @param fallbackCategory main category used when {@code kind == FALLBACK}
     */
    public record Candidate(
            String cacheKey,
            @Nullable String resourcePath,
            CandidateKind kind,
            String fallbackCategory
    ) {
    }

    private NodeIconPathResolver() {
    }

    /**
     * Builds ordered lookup candidates for a node icon.
     */
    public static List<Candidate> candidates(
            @Nullable String nodeId,
            @Nullable String category,
            @Nullable String explicitIcon
    ) {
        List<Candidate> result = new ArrayList<>(5);
        String normalizedCategory = normalizeId(category);

        String explicitPath = normalizeIconPath(explicitIcon);
        if (explicitPath != null) {
            result.add(new Candidate("explicit:" + explicitPath, explicitPath, CandidateKind.EXPLICIT, ""));
        }

        String normalizedNodeId = normalizeId(nodeId);
        if (!normalizedNodeId.isEmpty()) {
            String path = buildNodePath(normalizedNodeId);
            result.add(new Candidate("node:" + normalizedNodeId, path, CandidateKind.NODE_ID, ""));
        }

        if (!normalizedCategory.isEmpty()) {
            String path = buildCategoryPath(normalizedCategory);
            result.add(new Candidate("subcat:" + normalizedCategory, path, CandidateKind.SUBCATEGORY, ""));
        }

        String mainCategory = extractMainCategory(normalizedCategory);
        if (!mainCategory.isEmpty()) {
            String path = ICON_BASE_PATH + mainCategory + "/" + mainCategory + SVG_EXTENSION;
            result.add(new Candidate("cat:" + mainCategory, path, CandidateKind.MAIN_CATEGORY, ""));
        }

        String fallbackCategory = mainCategory.isEmpty() ? "unknown" : mainCategory;
        result.add(new Candidate(
                "fallback:" + fallbackCategory,
                null,
                CandidateKind.FALLBACK,
                fallbackCategory
        ));
        return List.copyOf(result);
    }

    public static String logicalKey(
            @Nullable String nodeId,
            @Nullable String category,
            @Nullable String explicitIcon
    ) {
        return normalizeId(nodeId) + "|" + normalizeId(category) + "|" + normalizeIconToken(explicitIcon);
    }

    public static String normalizeId(@Nullable String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('/', '.').replace('\\', '.');
    }

    public static String extractMainCategory(@Nullable String category) {
        if (category == null || category.isBlank()) {
            return "";
        }
        String normalized = normalizeId(category);
        int dot = normalized.indexOf('.');
        return dot >= 0 ? normalized.substring(0, dot) : normalized;
    }

    public static @Nullable String normalizeIconPath(@Nullable String icon) {
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

    public static String buildNodePath(String nodeId) {
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

    public static String buildCategoryPath(String category) {
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

    private static String normalizeIconToken(@Nullable String icon) {
        if (icon == null || icon.isBlank()) {
            return "";
        }
        return icon.trim().toLowerCase(Locale.ROOT);
    }
}

package com.nodecraft.gui.components.search;

import com.nodecraft.gui.node.NodeInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Shared node/category text matching for the library panel and canvas search popup.
 * Tokens are whitespace-split; every token must match at least one searchable field.
 */
public final class NodeSearchMatcher {

    private NodeSearchMatcher() {
    }

    public static boolean matchesNode(NodeInfo node, String searchTerm) {
        if (node == null) {
            return false;
        }
        return matches(
                node.getId(),
                node.getDisplayName(),
                node.getDescription(),
                node.getCategoryId(),
                searchTerm);
    }

    public static boolean matches(
            String nodeId,
            String displayName,
            String description,
            String categoryId,
            String searchTerm) {
        List<String> tokens = tokenize(searchTerm);
        if (tokens.isEmpty()) {
            return true;
        }

        String id = lower(nodeId);
        String name = lower(displayName);
        String desc = lower(description);
        String category = lower(categoryId);

        for (String token : tokens) {
            if (!(id.contains(token)
                    || name.contains(token)
                    || desc.contains(token)
                    || category.contains(token))) {
                return false;
            }
        }
        return true;
    }

    public static boolean matchesCategory(String categoryId, String displayName, String searchTerm) {
        List<String> tokens = tokenize(searchTerm);
        if (tokens.isEmpty()) {
            return true;
        }

        String id = lower(categoryId);
        String name = lower(displayName);
        for (String token : tokens) {
            if (!(id.contains(token) || name.contains(token))) {
                return false;
            }
        }
        return true;
    }

    public static List<String> tokenize(String searchTerm) {
        if (searchTerm == null || searchTerm.isBlank()) {
            return List.of();
        }
        String[] raw = searchTerm.toLowerCase(Locale.ROOT).trim().split("\\s+");
        List<String> tokens = new ArrayList<>(raw.length);
        for (String part : raw) {
            if (!part.isBlank()) {
                tokens.add(part);
            }
        }
        return tokens;
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}

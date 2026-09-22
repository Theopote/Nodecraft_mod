package com.nodecraft.gui.utils;

import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.registry.NodeRegistry;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Persisted favorite node ids for library / canvas search UX.
 */
public final class NodeFavoritesStore {

    static final String PREF_KEY = "node_library.favorites";
    private static final int MAX_FAVORITES = 64;
    private static final Object LOCK = new Object();

    private static LinkedHashSet<String> cachedIds;

    private NodeFavoritesStore() {
    }

    public static List<String> getFavoriteIds() {
        synchronized (LOCK) {
            return List.copyOf(loadUnlocked());
        }
    }

    public static boolean isFavorite(String nodeId) {
        String normalized = normalize(nodeId);
        if (normalized.isEmpty()) {
            return false;
        }
        synchronized (LOCK) {
            return loadUnlocked().contains(normalized);
        }
    }

    public static boolean toggle(String nodeId) {
        String normalized = normalize(nodeId);
        if (normalized.isEmpty()) {
            return false;
        }
        synchronized (LOCK) {
            LinkedHashSet<String> ids = loadUnlocked();
            boolean nowFavorite;
            if (ids.contains(normalized)) {
                ids.remove(normalized);
                nowFavorite = false;
            } else {
                ids.add(normalized);
                trimToMax(ids);
                nowFavorite = true;
            }
            persistUnlocked(ids);
            return nowFavorite;
        }
    }

    public static void add(String nodeId) {
        String normalized = normalize(nodeId);
        if (normalized.isEmpty()) {
            return;
        }
        synchronized (LOCK) {
            LinkedHashSet<String> ids = loadUnlocked();
            if (ids.add(normalized)) {
                trimToMax(ids);
                persistUnlocked(ids);
            }
        }
    }

    public static void remove(String nodeId) {
        String normalized = normalize(nodeId);
        if (normalized.isEmpty()) {
            return;
        }
        synchronized (LOCK) {
            LinkedHashSet<String> ids = loadUnlocked();
            if (ids.remove(normalized)) {
                persistUnlocked(ids);
            }
        }
    }

    public static void clear() {
        synchronized (LOCK) {
            cachedIds = new LinkedHashSet<>();
            UserPreferences.remove(PREF_KEY);
        }
    }

    /**
     * Resolves favorite ids against the registry, preserving favorite order and skipping unknowns.
     */
    public static List<NodeInfo> resolveFavorites(NodeRegistry registry) {
        if (registry == null) {
            return List.of();
        }
        List<NodeInfo> resolved = new ArrayList<>();
        for (String id : getFavoriteIds()) {
            NodeInfo info = registry.getNodeInfo(id);
            if (info != null) {
                resolved.add(info);
            }
        }
        return resolved;
    }

    /** Test hook: replace in-memory + prefs state. */
    static void replaceAllForTests(List<String> nodeIds) {
        synchronized (LOCK) {
            LinkedHashSet<String> ids = new LinkedHashSet<>();
            if (nodeIds != null) {
                for (String id : nodeIds) {
                    String normalized = normalize(id);
                    if (!normalized.isEmpty()) {
                        ids.add(normalized);
                    }
                }
            }
            trimToMax(ids);
            persistUnlocked(ids);
        }
    }

    private static LinkedHashSet<String> loadUnlocked() {
        if (cachedIds != null) {
            return cachedIds;
        }
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        String raw = UserPreferences.getString(PREF_KEY, "");
        if (raw != null && !raw.isBlank()) {
            for (String part : raw.split("[,\\n\\r]+")) {
                String normalized = normalize(part);
                if (!normalized.isEmpty()) {
                    ids.add(normalized);
                }
            }
        }
        trimToMax(ids);
        cachedIds = ids;
        return cachedIds;
    }

    private static void persistUnlocked(LinkedHashSet<String> ids) {
        cachedIds = ids;
        if (ids.isEmpty()) {
            UserPreferences.remove(PREF_KEY);
            return;
        }
        UserPreferences.setString(PREF_KEY, String.join(",", ids));
    }

    private static void trimToMax(LinkedHashSet<String> ids) {
        if (ids.size() <= MAX_FAVORITES) {
            return;
        }
        List<String> keep = new ArrayList<>(ids);
        ids.clear();
        ids.addAll(keep.subList(keep.size() - MAX_FAVORITES, keep.size()));
    }

    private static String normalize(String nodeId) {
        return nodeId == null ? "" : nodeId.trim().toLowerCase(Locale.ROOT);
    }
}

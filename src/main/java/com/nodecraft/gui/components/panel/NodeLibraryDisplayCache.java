package com.nodecraft.gui.components.panel;

import com.nodecraft.gui.node.NodeInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Caches sorted node-library display lists keyed by registry introspection epoch.
 */
public final class NodeLibraryDisplayCache {

    private long boundEpoch = Long.MIN_VALUE;
    private final Map<String, List<NodeInfo>> sortedNodesByKey = new HashMap<>();

    /**
     * Returns a stable sorted view of {@code source} for the given epoch/key.
     * Clears all entries when the registry epoch changes.
     */
    public synchronized List<NodeInfo> getSortedNodes(long registryEpoch, String cacheKey, List<NodeInfo> source) {
        if (registryEpoch != boundEpoch) {
            sortedNodesByKey.clear();
            boundEpoch = registryEpoch;
        }
        if (cacheKey == null || cacheKey.isBlank()) {
            return sortCopy(source);
        }
        return sortedNodesByKey.computeIfAbsent(cacheKey, ignored -> sortCopy(source));
    }

    public synchronized void clear() {
        sortedNodesByKey.clear();
        boundEpoch = Long.MIN_VALUE;
    }

    public synchronized int size() {
        return sortedNodesByKey.size();
    }

    public synchronized long boundEpoch() {
        return boundEpoch;
    }

    private static List<NodeInfo> sortCopy(List<NodeInfo> source) {
        List<NodeInfo> sorted = source == null ? new ArrayList<>() : new ArrayList<>(source);
        sorted.sort(Comparator
                .comparingInt(NodeInfo::getOrder)
                .thenComparing(NodeInfo::getDisplayName, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(sorted);
    }
}

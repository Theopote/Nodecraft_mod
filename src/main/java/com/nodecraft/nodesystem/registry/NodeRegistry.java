package com.nodecraft.nodesystem.registry;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.core.exception.NodeExecutionException;
import com.nodecraft.core.exception.NodeValidationException;
import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.spi.INodeProvider;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry for node metadata, categories, and instantiation.
 * Supports discovery through {@link INodeProvider} implementations loaded via SPI.
 */
public class NodeRegistry {

    private static NodeRegistry instance;

    private final Map<String, NodeInfo> nodeInfoMap = new ConcurrentHashMap<>();
    private final Map<String, NodeCategory> categoryMap = new ConcurrentHashMap<>();
    private volatile boolean initialized = false;
    private volatile List<NodeCategory> sortedCategoriesCache = null;
    private volatile long introspectionEpoch = 0L;
    private final Map<String, Map<String, Object>> defaultNodeStateCache = new ConcurrentHashMap<>();

    private NodeRegistry() {
        // Singleton.
    }

    /**
     * Returns the singleton registry instance.
     *
     * @return registry instance
     */
    public static synchronized NodeRegistry getInstance() {
        if (instance == null) {
            instance = new NodeRegistry();
        }
        return instance;
    }

    // addMovedNodeAlias was a no-op wrapper — callers now use addAlias directly.

    private String normalizeNodeId(String nodeId) {
        if (nodeId == null) {
            return null;
        }
        return nodeId.toLowerCase();
    }

    public String resolveCanonicalNodeId(String nodeId) {
        return normalizeNodeId(nodeId);
    }

    private String remapCategory(String normalizedNodeId, String categoryId) {
        return categoryId == null ? "" : categoryId.toLowerCase();
    }

    private String remapDescription(String targetCategoryId, String description) {
        return description == null ? "" : description;
    }

    /**
     * Reinitializes the registry by clearing current state and reloading all {@link INodeProvider}s.
     */
    public synchronized void initialize() {
        if (initialized) {
            NodeCraft.LOGGER.warn("NodeRegistry is already initialized. Call clear() before reloading providers.");
            return;
        }

        NodeCraft.LOGGER.debug("Initializing NodeRegistry...");
        this.clearInternal(); // Ensure a clean registry state before loading providers.

        // Load all node providers through SPI.
        ServiceLoader<INodeProvider> loader = ServiceLoader.load(INodeProvider.class);
        int providerCount = 0;
        int initialNodes;
        int initialCategories;

        for (INodeProvider provider : loader) {
            try {
                NodeCraft.LOGGER.debug("Loading node provider: {}", provider.getClass().getName());
                initialNodes = nodeInfoMap.size();
                initialCategories = categoryMap.size();

                provider.registerNodes(this);
                providerCount++;

                NodeCraft.LOGGER.debug("Provider {} registered {} nodes and {} categories",
                        provider.getClass().getSimpleName(),
                        nodeInfoMap.size() - initialNodes,
                        categoryMap.size() - initialCategories);

            } catch (Exception e) {
                NodeCraft.LOGGER.error("Failed to load node provider {}: {}",
                        provider.getClass().getName(), e.getMessage(), e);
            }
        }

        if (providerCount == 0) {
            NodeCraft.LOGGER.error("No node providers were found. Check META-INF/services/com.nodecraft.nodesystem.spi.INodeProvider.");
        }

        // Sort nodes in all categories once, after all providers have registered.
        for (NodeCategory category : categoryMap.values()) {
            category.sealNodes();
        }
        initialized = true;
        introspectionEpoch++;
        NodeCraft.LOGGER.info("NodeRegistry initialized. Loaded {} providers, {} categories, and {} nodes.",
                providerCount, categoryMap.size(), nodeInfoMap.size());
    }

    /**
     * Registers a node category.
     *
     * @param category category metadata
     */
    public synchronized void registerCategory(NodeCategory category) {
        if (category == null || category.getId() == null) {
            NodeCraft.LOGGER.warn("Attempted to register an invalid node category (null category or null ID).");
            return;
        }

        String normalizedId = category.getId().toLowerCase();
        String displayName = category.getDisplayName();
        if (displayName == null || displayName.trim().isEmpty()) {
            displayName = formatCategoryName(normalizedId);
        }

        ensureCategoryChain(normalizedId);

        NodeCategory existingCategory = categoryMap.get(normalizedId);
        if (existingCategory != null) {
            NodeCraft.LOGGER.debug("Node category {} already exists. Skipping duplicate registration.", normalizedId);
            return;
        }

        categoryMap.put(normalizedId, new NodeCategory(normalizedId, displayName));
        invalidateCategoryCache();
        NodeCraft.LOGGER.debug("Registered node category: {} (ID: {})", displayName, normalizedId);
    }

    /**
     * Registers a node definition.
     * <p>
     * If the target category does not exist yet, it is created automatically.
     *
     * @param nodeInfo node metadata
     * @return true if registration succeeds; false when the node is invalid or already registered
     */
    public synchronized boolean registerNode(NodeInfo nodeInfo) {
        if (nodeInfo == null || nodeInfo.getId() == null || nodeInfo.getNodeClass() == null) {
            NodeCraft.LOGGER.warn("Attempted to register invalid node metadata (null, null ID, or null node class).");
            return false;
        }

        // Normalize the node ID before writing to the registry.
        String normalizedId = nodeInfo.getId().toLowerCase();
        String categoryId = remapCategory(normalizedId, nodeInfo.getCategoryId());
        String description = remapDescription(categoryId, nodeInfo.getDescription());
        NodeInfo normalizedNodeInfo = new NodeInfo(
                normalizedId,
                nodeInfo.getDisplayName(),
                description,
                categoryId,
                nodeInfo.getOrder(),
                nodeInfo.getNodeClass());
        normalizedNodeInfo.setIcon(nodeInfo.getIcon());

        if (shouldSkipRegistration(normalizedNodeInfo)) {
            NodeCraft.LOGGER.debug("Skipped registration for deprecated node: {} (ID: {})",
                normalizedNodeInfo.getDisplayName(), normalizedId);
            return false;
        }

        if (nodeInfoMap.containsKey(normalizedId)) {
            NodeCraft.LOGGER.warn("Duplicate node registration attempted for ID: {} (title: {}). Skipping.",
                    normalizedId, nodeInfo.getDisplayName());
            return false;
        }

        nodeInfoMap.put(normalizedId, normalizedNodeInfo);

        // Ensure the category exists, including its parent chain.
        if (!categoryMap.containsKey(categoryId)) {
            NodeCategory newCategoryForNode = new NodeCategory(categoryId, formatCategoryName(categoryId));
            registerCategory(newCategoryForNode);
        }

        NodeCategory category = categoryMap.get(categoryId);
        if (category != null) {
            category.addNode(normalizedNodeInfo);
            if (initialized) {
                category.sealNodes();
            }
            NodeCraft.LOGGER.debug("Registered node: {} (ID: {}) in category [{}]",
                normalizedNodeInfo.getDisplayName(), normalizedId, category.getDisplayName());
            return true;
        } else {
            NodeCraft.LOGGER.error("Failed to register node {} (ID: {}): could not resolve category {}",
                    normalizedNodeInfo.getDisplayName(), normalizedId, categoryId);
            nodeInfoMap.remove(normalizedId);
            return false;
        }
    }

    private boolean shouldSkipRegistration(NodeInfo nodeInfo) {
        if (nodeInfo == null || nodeInfo.getNodeClass() == null) {
            return false;
        }
        return nodeInfo.getNodeClass().isAnnotationPresent(Deprecated.class);
    }

    /**
     * Creates a new node instance for the given node type ID.
     *
     * @param nodeId node type ID to instantiate
     * @return new {@link INode} instance
     * @throws IllegalArgumentException if the node ID is unknown or has no implementation class
     * @throws RuntimeException if instantiation fails
     */
    public INode createNodeInstance(String nodeId) {
        String resolvedNodeId = normalizeNodeId(nodeId);
        NodeInfo nodeInfo = nodeInfoMap.get(resolvedNodeId);
        if (nodeInfo == null) {
            throw new NodeValidationException("Unregistered node type ID: " + nodeId);
        }

        Class<? extends INode> nodeClass = nodeInfo.getNodeClass();
        if (nodeClass == null) {
            throw new NodeValidationException("Node type '" + nodeId + "' (" + nodeInfo.getDisplayName() + ") has no implementation class.");
        }

        try {
            return nodeClass.getDeclaredConstructor().newInstance();
        } catch (NoSuchMethodException e) {
            NodeCraft.LOGGER.error("Node class {} is missing a no-argument constructor.", nodeClass.getName(), e);
            throw new NodeExecutionException("Node class " + nodeClass.getName() + " is missing a no-argument constructor.", e);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            NodeCraft.LOGGER.error("Failed to instantiate node {} ({}).", nodeId, nodeClass.getName(), e);
            throw new NodeExecutionException("Failed to instantiate node " + nodeId + ": " + e.getMessage(), e);
        } catch (Exception e) {
            NodeCraft.LOGGER.error("Unexpected error while instantiating node {} ({}).", nodeId, nodeClass.getName(), e);
            throw new NodeExecutionException("Unexpected error while instantiating node " + nodeId + ": " + e.getMessage(), e);
        }
    }

    /**
     * Returns an immutable snapshot of a freshly constructed node's default {@link INode#getNodeState()}.
     * Results are cached per type until the registry is cleared or re-initialized.
     */
    public Map<String, Object> getDefaultNodeState(String nodeId) {
        String resolvedNodeId = normalizeNodeId(nodeId);
        if (resolvedNodeId == null) {
            return Map.of();
        }
        Map<String, Object> cached = defaultNodeStateCache.computeIfAbsent(resolvedNodeId, this::buildDefaultNodeStateSnapshot);
        return cached.isEmpty() ? Map.of() : Map.copyOf(cached);
    }

    /**
     * Monotonic counter bumped whenever registry metadata caches must be rebuilt.
     */
    public long getIntrospectionEpoch() {
        return introspectionEpoch;
    }

    private Map<String, Object> buildDefaultNodeStateSnapshot(String resolvedNodeId) {
        try {
            INode node = createNodeInstance(resolvedNodeId);
            return snapshotNodeState(node == null ? null : node.getNodeState());
        } catch (Exception e) {
            NodeCraft.LOGGER.debug("Unable to capture default node state for {}: {}", resolvedNodeId, e.getMessage());
            return Map.of();
        }
    }

    private static Map<String, Object> snapshotNodeState(Object state) {
        if (!(state instanceof Map<?, ?> rawMap) || rawMap.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> normalized = new HashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (entry.getKey() instanceof String key && !key.isBlank()) {
                normalized.put(key, entry.getValue());
            }
        }
        return normalized.isEmpty() ? Map.of() : Map.copyOf(normalized);
    }

    /**
     * Returns the category for the given category ID.
     *
     * @param categoryId category ID
     * @return matching {@link NodeCategory}, or null when not found
     */
    public NodeCategory getCategory(String categoryId) {
        return categoryMap.get(categoryId);
    }

    /**
     * Returns all registered categories sorted by display name.
     *
     * @return sorted immutable category list
     */
    public List<NodeCategory> getAllCategories() {
        List<NodeCategory> cached = sortedCategoriesCache;
        if (cached != null) {
            return cached;
        }
        List<NodeCategory> sortedCategories = new ArrayList<>(categoryMap.values());
        sortedCategories.sort((c1, c2) -> c1.getDisplayName().compareToIgnoreCase(c2.getDisplayName()));
        List<NodeCategory> result = Collections.unmodifiableList(sortedCategories);
        sortedCategoriesCache = result;
        return result;
    }

    /** Invalidates the sorted-categories cache. Must be called whenever categoryMap changes. */
    private void invalidateCategoryCache() {
        sortedCategoriesCache = null;
    }

    /**
     * Returns all registered node IDs.
     *
     * @return immutable node ID list
     */
    public List<String> getAllNodeIds() {
        return List.copyOf(nodeInfoMap.keySet());
    }

    /**
     * Returns node metadata for the given node ID.
     *
     * @param nodeId node ID
     * @return matching {@link NodeInfo}, or null when not found
     */
    public NodeInfo getNodeInfo(String nodeId) {
        return nodeInfoMap.get(normalizeNodeId(nodeId));
    }

    /**
     * Returns the number of registered categories.
     *
     * @return category count
     */
    public int getCategoryCount() {
        return categoryMap.size();
    }

    /**
     * Returns the number of registered nodes.
     *
     * @return node count
     */
    public int getNodeCount() {
        return nodeInfoMap.size();
    }

    /**
     * Returns whether the registry has been initialized.
     *
     * @return true after {@link #initialize()} and before {@link #clear()}
     */
    public boolean isInitialized() {
        return initialized;
    }

    private void clearInternal() {
        nodeInfoMap.clear();
        categoryMap.clear();
        defaultNodeStateCache.clear();
        introspectionEpoch++;
        invalidateCategoryCache();
        // The initialized flag is managed by initialize() and clear().
    }

    /**
     * Clears the registry and resets initialization state.
     */
    public synchronized void clear() {
        clearInternal();
        initialized = false;
        NodeCraft.LOGGER.info("NodeRegistry cleared and reset.");
    }

    private void ensureCategoryChain(String categoryId) {
        for (String parentId : buildParentChain(categoryId)) {
            if (!categoryMap.containsKey(parentId)) {
                String displayName = formatCategoryName(parentId);
                categoryMap.put(parentId, new NodeCategory(parentId, displayName));
                invalidateCategoryCache();
                NodeCraft.LOGGER.debug("Registered node category: {} (ID: {})", displayName, parentId);
            }
        }
    }

    private List<String> buildParentChain(String categoryId) {
        List<String> chain = new ArrayList<>();
        String current = categoryId;
        while (current != null && current.contains(".") && !current.endsWith(".")) {
            current = current.substring(0, current.lastIndexOf('.'));
            chain.addFirst(current);
        }
        return chain;
    }

    /**
     * Represents a node category and its registered nodes.
     */
    public static class NodeCategory {
        private static final Comparator<NodeInfo> NODE_ORDER =
                Comparator.comparingInt(NodeInfo::getOrder)
                        .thenComparing(NodeInfo::getDisplayName, String.CASE_INSENSITIVE_ORDER);

        private final String id;
        private final String displayName;
        private final List<NodeInfo> nodes = new ArrayList<>();
        private volatile List<NodeInfo> sealedNodes = List.of();

        public NodeCategory(String id, String displayName) {
            this.id = Objects.requireNonNull(id, "Category ID must not be null");
            this.displayName = Objects.requireNonNull(displayName, "Category display name must not be null");
        }

        public String getId() {
            return id;
        }

        public String getDisplayName() {
            return displayName;
        }

        void addNode(NodeInfo nodeInfo) {
            if (nodeInfo != null && !nodes.contains(nodeInfo)) {
                nodes.add(nodeInfo);
                sealedNodes = List.copyOf(nodes);
            }
        }

        /**
         * Sorts registered nodes by explicit order, then display name.
         * Call once after all nodes are added to avoid repeated full re-sorts during startup registration.
         */
        void sealNodes() {
            nodes.sort(NODE_ORDER);
            sealedNodes = List.copyOf(nodes);
        }

        public List<NodeInfo> getNodes() {
            return sealedNodes;
        }

        public boolean isEmpty() {
            return nodes.isEmpty();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            NodeCategory that = (NodeCategory) o;
            return id.equals(that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }

        @Override
        public String toString() {
            return "NodeCategory{id='" + id + "', displayName='" + displayName + "', nodeCount=" + nodes.size() + "}";
        }
    }

    /**
     * Formats a dotted category ID into a display label.
     * Example: {@code inputs.basic -> Inputs / Basic}.
     *
     * @param categoryId category ID
     * @return formatted display name
     */
    private String formatCategoryName(String categoryId) {
        if (categoryId == null || categoryId.isEmpty()) {
            return "General";
        }
        String[] parts = categoryId.split("\\.");
        StringBuilder formatted = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                if (!formatted.isEmpty()) {
                    formatted.append(" / ");
                }
                // Title-case each category segment.
                formatted.append(part.substring(0, 1).toUpperCase()).append(part.substring(1));
            }
        }
        return formatted.toString();
    }
}

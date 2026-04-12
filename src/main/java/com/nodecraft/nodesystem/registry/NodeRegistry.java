package com.nodecraft.nodesystem.registry;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.spi.INodeProvider;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
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
    private static final Map<String, String> NODE_ID_ALIASES = createNodeIdAliases();
    private static final Map<String, String> NODE_CATEGORY_OVERRIDES = createNodeCategoryOverrides();

    private final Map<String, NodeInfo> nodeInfoMap = new ConcurrentHashMap<>();
    private final Map<String, NodeCategory> categoryMap = new ConcurrentHashMap<>();
    private volatile boolean initialized = false;

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

    private static Map<String, String> createNodeIdAliases() {
        Map<String, String> aliases = new HashMap<>();
        addMovedNodeAlias(aliases, "visualization.preview.geometry_viewer", "output.preview.geometry_viewer");
        addMovedNodeAlias(aliases, "visualization.preview.preview_geometry", "output.preview.preview_geometry");
        addMovedNodeAlias(aliases, "visualization.preview.preview_blocks", "output.preview.preview_blocks");
        addMovedNodeAlias(aliases, "visualization.preview.preview_points", "output.preview.preview_points");
        addMovedNodeAlias(aliases, "visualization.preview.preview_vectors", "output.preview.preview_vectors");
        addMovedNodeAlias(aliases, "visualization.preview.preview_plane", "output.preview.preview_plane");
        addMovedNodeAlias(aliases, "visualization.preview.preview_frame", "output.preview.preview_frame");
        addMovedNodeAlias(aliases, "visualization.preview.preview_paths", "output.preview.preview_curves");
        addMovedNodeAlias(aliases, "visualization.preview.preview_regions", "output.preview.preview_regions");
        addMovedNodeAlias(aliases, "visualization.preview.preview_labels", "output.preview.preview_labels");
        addMovedNodeAlias(aliases, "visualization.preview.preview_surface_strip", "output.preview.preview_surface_strip");
        addMovedNodeAlias(aliases, "visualization.preview.preview_polygon_profiles", "output.preview.preview_profiles");
        addMovedNodeAlias(aliases, "visualization.preview.clear_all_previews", "output.execute.clear_preview");
        addMovedNodeAlias(aliases, "visualization.execute.apply_changes", "output.execute.apply_changes");
        addMovedNodeAlias(aliases, "visualization.execute.export_schematic", "output.export.export_schematic");
        addMovedNodeAlias(aliases, "visualization.debugging.value_monitor", "output.debug.value_monitor");
        addMovedNodeAlias(aliases, "visualization.debugging.print_to_chat", "output.debug.print_to_chat");
        addMovedNodeAlias(aliases, "visualization.debugging.execution_timer", "output.debug.execution_timer");
        addMovedNodeAlias(aliases, "visualization.debugging.panel", "output.debug.data_inspector");

        addMovedNodeAlias(aliases, "world.query.get_block", "world.read.get_block");
        addMovedNodeAlias(aliases, "world.query.get_blocks_in_region", "world.read.get_blocks_in_region");
        addMovedNodeAlias(aliases, "world.query.find_blocks", "world.read.find_blocks");
        addMovedNodeAlias(aliases, "world.query.get_biome", "world.read.get_biome");
        addMovedNodeAlias(aliases, "world.modification.set_block", "world.write.set_block");
        addMovedNodeAlias(aliases, "world.modification.set_blocks", "world.write.set_blocks");
        addMovedNodeAlias(aliases, "world.modification.fill_region", "world.write.fill_region");
        addMovedNodeAlias(aliases, "world.modification.replace_blocks", "world.write.replace_blocks");
        addMovedNodeAlias(aliases, "world.modification.clone_region", "world.write.clone_region");
        addMovedNodeAlias(aliases, "world.modification.remove_blocks", "world.write.clear_region");
        addMovedNodeAlias(aliases, "world.modification.material_mapper", "material.gradient_mapping.height_gradient_map");
        addMovedNodeAlias(aliases, "material.basic_assignment.replace_material", "material.gradient_mapping.height_gradient_map");
        addMovedNodeAlias(aliases, "inputs.basic.integer_input", "input.numeric.integer");
        addMovedNodeAlias(aliases, "inputs.basic.float_input", "input.numeric.float");
        addMovedNodeAlias(aliases, "inputs.basic.integer_slider", "input.numeric.integer_slider");
        addMovedNodeAlias(aliases, "inputs.basic.float_slider", "input.numeric.float_slider");
        addMovedNodeAlias(aliases, "inputs.basic.angle_slider", "input.numeric.angle");
        addMovedNodeAlias(aliases, "inputs.basic.circular_angle", "input.numeric.angle_picker");
        addMovedNodeAlias(aliases, "inputs.basic.boolean_toggle", "input.numeric.boolean_toggle");
        addMovedNodeAlias(aliases, "inputs.basic.vector_input", "reference.vectors.vector");
        addMovedNodeAlias(aliases, "inputs.basic.coordinate_input", "reference.points.point_from_coordinates");
        addMovedNodeAlias(aliases, "inputs.basic.plane_selector", "reference.planes.world_plane");
        addMovedNodeAlias(aliases, "inputs.minecraft.player_position", "input.context.player_position");
        addMovedNodeAlias(aliases, "inputs.minecraft.player_look_at", "input.context.player_look_direction");
        addMovedNodeAlias(aliases, "inputs.minecraft.selected_block", "world.selection.selected_block");
        addMovedNodeAlias(aliases, "inputs.minecraft.selected_region", "world.selection.selected_region");
        addMovedNodeAlias(aliases, "inputs.minecraft.biome_at_player", "world.read.biome_at_player");
        addMovedNodeAlias(aliases, "inputs.minecraft.current_time", "input.context.current_time");
        addMovedNodeAlias(aliases, "inputs.minecraft.dimension_info", "input.context.dimension_info");
        addMovedNodeAlias(aliases, "inputs.selectors.block_type_selector", "input.type_selectors.block_type_selector");
        addMovedNodeAlias(aliases, "inputs.sources.create_list", "math.list_sequence.create_list");
        addMovedNodeAlias(aliases, "math.basic.range", "math.list_sequence.range");
        addMovedNodeAlias(aliases, "math.basic.absolute", "math.scalar_math.absolute");
        addMovedNodeAlias(aliases, "math.basic.addition", "math.scalar_math.addition");
        addMovedNodeAlias(aliases, "math.basic.ceiling", "math.scalar_math.ceiling");
        addMovedNodeAlias(aliases, "math.basic.clamp", "math.scalar_math.clamp");
        addMovedNodeAlias(aliases, "math.basic.division", "math.scalar_math.division");
        addMovedNodeAlias(aliases, "math.basic.floor", "math.scalar_math.floor");
        addMovedNodeAlias(aliases, "math.basic.logarithm", "math.scalar_math.logarithm");
        addMovedNodeAlias(aliases, "math.basic.max", "math.scalar_math.max");
        addMovedNodeAlias(aliases, "math.basic.min", "math.scalar_math.min");
        addMovedNodeAlias(aliases, "math.basic.modulus", "math.scalar_math.modulus");
        addMovedNodeAlias(aliases, "math.basic.multiplication", "math.scalar_math.multiplication");
        addMovedNodeAlias(aliases, "math.basic.power", "math.scalar_math.power");
        addMovedNodeAlias(aliases, "math.basic.remap", "math.scalar_math.remap");
        addMovedNodeAlias(aliases, "math.basic.round", "math.scalar_math.round");
        addMovedNodeAlias(aliases, "math.basic.subtraction", "math.scalar_math.subtraction");
        addMovedNodeAlias(aliases, "math.randomness.random_number", "math.random.random_number");
        addMovedNodeAlias(aliases, "math.randomness.random_list_item", "math.random.random_list_item");
        addMovedNodeAlias(aliases, "math.randomness.random_vector", "math.random.random_vector");
        addMovedNodeAlias(aliases, "math.randomness.noise", "math.random.noise");
        addMovedNodeAlias(aliases, "math.logic.equals", "math.compare.equals");
        addMovedNodeAlias(aliases, "math.logic.not_equals", "math.compare.not_equals");
        addMovedNodeAlias(aliases, "math.logic.less_than", "math.compare.less_than");
        addMovedNodeAlias(aliases, "math.logic.less_than_or_equal", "math.compare.less_than_or_equal");
        addMovedNodeAlias(aliases, "math.logic.greater_than", "math.compare.greater_than");
        addMovedNodeAlias(aliases, "math.logic.greater_than_or_equal", "math.compare.greater_than_or_equal");
        addMovedNodeAlias(aliases, "logic.if", "math.logic.if");
        addMovedNodeAlias(aliases, "logic.select_item", "math.logic.switch");
        addMovedNodeAlias(aliases, "math.trigonometry.sine", "math.trigonometry.sin");
        addMovedNodeAlias(aliases, "math.trigonometry.cosine", "math.trigonometry.cos");
        addMovedNodeAlias(aliases, "math.trigonometry.tangent", "math.trigonometry.tan");
        addMovedNodeAlias(aliases, "math.trigonometry.arcsin", "math.trigonometry.asin");
        addMovedNodeAlias(aliases, "math.trigonometry.arccos", "math.trigonometry.acos");
        addMovedNodeAlias(aliases, "math.trigonometry.arctan", "math.trigonometry.atan");
        addMovedNodeAlias(aliases, "math.trigonometry.degrees_to_radians", "math.trigonometry.deg_to_rad");
        addMovedNodeAlias(aliases, "math.trigonometry.deg2rad", "math.trigonometry.deg_to_rad");
        addMovedNodeAlias(aliases, "math.trigonometry.radians_to_degrees", "math.trigonometry.rad_to_deg");
        addMovedNodeAlias(aliases, "math.trigonometry.rad2deg", "math.trigonometry.rad_to_deg");
        addMovedNodeAlias(aliases, "math.vector.construct", "reference.vectors.vector");
        addMovedNodeAlias(aliases, "math.vector.cross_product", "reference.vectors.cross_product");
        addMovedNodeAlias(aliases, "math.vector.dot_product", "reference.vectors.dot_product");
        addMovedNodeAlias(aliases, "math.vector.normalize", "reference.vectors.normalize_vector");
        addMovedNodeAlias(aliases, "math.vector.normalize_vector", "reference.vectors.normalize_vector");
        addMovedNodeAlias(aliases, "math.vector.length", "reference.vectors.vector_length");
        addMovedNodeAlias(aliases, "math.vector.addition", "reference.vectors.vector_addition");
        addMovedNodeAlias(aliases, "math.vector.subtraction", "reference.vectors.vector_subtraction");
        addMovedNodeAlias(aliases, "math.vector.scalar_multiply", "reference.vectors.vector_scalar_multiply");
        addMovedNodeAlias(aliases, "math.vector.scalar_divide", "reference.vectors.vector_scalar_divide");
        addMovedNodeAlias(aliases, "math.vector.deconstruct", "reference.vectors.deconstruct_vector");
        addMovedNodeAlias(aliases, "math.vector.construct_coordinate", "reference.points.point_from_coordinates");
        addMovedNodeAlias(aliases, "math.vector.deconstruct_coordinate", "reference.points.deconstruct_point");
        addMovedNodeAlias(aliases, "math.vector.midpoint", "reference.points.mid_point");
        addMovedNodeAlias(aliases, "math.vector.distance", "reference.points.distance_between_points");
        addMovedNodeAlias(aliases, "math.vector.construct_plane", "reference.planes.construct_plane");
        addMovedNodeAlias(aliases, "math.vector.construct_plane_from_points", "reference.planes.plane_from_points");
        addMovedNodeAlias(aliases, "math.vector.rotate", "transform.orientation.rotate_vector");
        addMovedNodeAlias(aliases, "math.vector.rotate_vector", "transform.orientation.rotate_vector");
        return Collections.unmodifiableMap(aliases);
    }

    private static void addAlias(Map<String, String> aliases, String legacyId, String canonicalId) {
        aliases.put(legacyId, canonicalId);
    }

    private static void addMovedNodeAlias(Map<String, String> aliases, String legacyId, String canonicalId) {
        addAlias(aliases, legacyId, canonicalId);
    }

    private static Map<String, String> createNodeCategoryOverrides() {
        return Map.of();
    }

    private String normalizeNodeId(String nodeId) {
        if (nodeId == null) {
            return null;
        }
        String normalizedId = nodeId.toLowerCase();
        String aliasedId = NODE_ID_ALIASES.getOrDefault(normalizedId, normalizedId);
        return remapMigratedNodePrefixes(aliasedId);
    }

    private String remapMigratedNodePrefixes(String nodeId) {
        if (nodeId.startsWith("inputs.basic.")) {
            return "input.basic." + nodeId.substring("inputs.basic.".length());
        }
        return switch (nodeId) {
            case "inputs.minecraft.selected_entity" -> "world.selection.selected_entity";
            case "inputs.minecraft.selected_block_sequence" -> "world.selection.selected_block_sequence";
            default -> nodeId;
        };
    }

    public String resolveCanonicalNodeId(String nodeId) {
        return normalizeNodeId(nodeId);
    }

    private String remapCategory(String normalizedNodeId, String categoryId) {
        String normalizedCategoryId = categoryId == null ? "" : categoryId.toLowerCase();
        String explicitOverride = NODE_CATEGORY_OVERRIDES.get(normalizedNodeId);
        if (explicitOverride != null) {
            return explicitOverride;
        }
        if (isListSequenceMigrationTarget(normalizedNodeId, normalizedCategoryId)) {
            return "math.list_sequence";
        }
        return normalizedCategoryId;
    }

    private boolean isListSequenceMigrationTarget(String normalizedNodeId, String normalizedCategoryId) {
        return false;
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

        initialized = true;
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

        // Normalize the category ID and display name.
        String normalizedId = category.getId().toLowerCase();
        String displayName = category.getDisplayName();
        if (displayName == null || displayName.trim().isEmpty()) {
            displayName = formatCategoryName(normalizedId);
        }

        // Register the parent category first so the hierarchy remains valid.
        if (normalizedId.contains(".") && !normalizedId.endsWith(".")) {
            String parentId = normalizedId.substring(0, normalizedId.lastIndexOf('.'));

            if (!categoryMap.containsKey(parentId)) {
                NodeCategory parentCategory = new NodeCategory(parentId, formatCategoryName(parentId));
                registerCategory(parentCategory);
            }
        }

        if (categoryMap.containsKey(normalizedId)) {
            NodeCraft.LOGGER.debug("Node category {} already exists. Skipping duplicate registration.", normalizedId);
            return;
        }

        NodeCategory newCategory = new NodeCategory(normalizedId, displayName);
        categoryMap.put(normalizedId, newCategory);
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
                nodeInfo.getNodeClass());
        normalizedNodeInfo.setIcon(nodeInfo.getIcon());

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
            throw new IllegalArgumentException("Unregistered node type ID: " + nodeId);
        }

        Class<? extends INode> nodeClass = nodeInfo.getNodeClass();
        if (nodeClass == null) {
            throw new IllegalArgumentException("Node type '" + nodeId + "' (" + nodeInfo.getDisplayName() + ") has no implementation class.");
        }

        try {
            return nodeClass.getDeclaredConstructor().newInstance();
        } catch (NoSuchMethodException e) {
            NodeCraft.LOGGER.error("Node class {} is missing a no-argument constructor.", nodeClass.getName(), e);
            throw new RuntimeException("Node class " + nodeClass.getName() + " is missing a no-argument constructor.", e);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            NodeCraft.LOGGER.error("Failed to instantiate node {} ({}).", nodeId, nodeClass.getName(), e);
            throw new RuntimeException("Failed to instantiate node " + nodeId + ": " + e.getMessage(), e);
        } catch (Exception e) {
            NodeCraft.LOGGER.error("Unexpected error while instantiating node {} ({}).", nodeId, nodeClass.getName(), e);
            throw new RuntimeException("Unexpected error while instantiating node " + nodeId + ": " + e.getMessage(), e);
        }
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
        List<NodeCategory> sortedCategories = new ArrayList<>(categoryMap.values());
        // Sort by display name for stable UI presentation.
        sortedCategories.sort((c1, c2) -> c1.getDisplayName().compareToIgnoreCase(c2.getDisplayName()));
        return Collections.unmodifiableList(sortedCategories);
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

    /**
     * Represents a node category and its registered nodes.
     */
    public static class NodeCategory {
        private final String id;
        private final String displayName;
        private final List<NodeInfo> nodes = new ArrayList<>();

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
                // Keep nodes sorted by display name for stable rendering.
                nodes.sort((n1, n2) -> n1.getDisplayName().compareToIgnoreCase(n2.getDisplayName()));
            }
        }

        public List<NodeInfo> getNodes() {
            return Collections.unmodifiableList(nodes);
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

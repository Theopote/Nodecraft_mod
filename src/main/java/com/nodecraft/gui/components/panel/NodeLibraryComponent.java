package com.nodecraft.gui.components.panel;

import com.nodecraft.gui.components.EditorComponent;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.HashSet;
import java.util.Set;
import java.util.Comparator;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.registry.NodeRegistry.NodeCategory;
import com.nodecraft.gui.components.node.NodeCategoryPresentationMapper;
import com.nodecraft.gui.components.node.NodeCategoryPresentationMapper.CategoryPresentation;
import com.nodecraft.gui.utils.NodeIconManager;
import com.nodecraft.gui.utils.UserPreferences;
import com.nodecraft.gui.components.search.NodeSearchManager;
import com.nodecraft.gui.editor.impl.ImGuiNodeEditor;
import com.nodecraft.gui.recommendation.NodeRecommendation;
import com.nodecraft.gui.recommendation.NodeRecommendationContext;
import com.nodecraft.gui.recommendation.NodeRecommendations;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.graph.NodeGraph;
import org.lwjgl.opengl.GL11;

import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiSelectableFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.flag.ImGuiDragDropFlags;
import imgui.ImDrawList;
import imgui.ImVec2;

/**
 * Node library panel used by the editor sidebar.
 * Handles category grouping, search, filtering, and drag/drop entry points.
 */
public class NodeLibraryComponent implements EditorComponent {

    private static final NodeCategoryPresentationMapper PRESENTATION_MAPPER = new NodeCategoryPresentationMapper();
    private static final boolean SHOW_DEPRECATED_NODES = false;

    // Inner record for display purposes
    private record DisplayCategory(CategoryPresentation presentation, List<NodeInfo> displayNodes) {
        String getDisplayName() { return presentation.displayName(); }
        String getId() { return presentation.displayCategoryId(); }
        String getParentId() { return presentation.parentDisplayId(); }
        String getColorKey() { return presentation.colorKey(); }
        List<NodeInfo> getNodes() { return displayNodes; }
    }

    // Internal UI constants.
    private static class NodeLibraryConstants {
        static final String PREF_DISPLAY_MODE_KEY = "node_library.display_mode";
        static final String PREF_GRID_TILE_SCALE_KEY = "node_library.grid_tile_scale";
        static final float CHILD_WINDOW_MIN_WIDTH = 50;
        static final float CHILD_WINDOW_MIN_HEIGHT = 50;
        static final float CATEGORY_INDENT = 10f;
        static final float CATEGORY_SPACING_EXPANDED = 3f; // Tighter spacing below expanded categories.
        static final float CATEGORY_SPACING_COLLAPSED = 2f;
        static final float CATEGORY_ITEM_SPACING = 2f; // Spacing between category items.
        static final float GRID_TILE_SIZE_SCALE = 1.5f;
        static final float GRID_TILE_SIZE_SCALE_MIN = 1.0f;
        static final float GRID_TILE_SIZE_SCALE_MAX = 2.0f;
        static final String DRAG_DROP_PAYLOAD_TYPE = "DND_NODE_FROM_LIBRARY";

        static final Map<String, float[]> CATEGORY_COLORS_FLOAT = buildGradientCategoryColors();
        static final ConcurrentMap<String, Integer> CATEGORY_COLORS_INT = buildPackedCategoryColors();
        static final float[] DEFAULT_CATEGORY_COLOR_FLOAT = new float[]{0.75f, 0.75f, 0.75f, 1.0f};
        static final int DEFAULT_CATEGORY_COLOR_INT = ImGui.colorConvertFloat4ToU32(DEFAULT_CATEGORY_COLOR_FLOAT[0], DEFAULT_CATEGORY_COLOR_FLOAT[1], DEFAULT_CATEGORY_COLOR_FLOAT[2], DEFAULT_CATEGORY_COLOR_FLOAT[3]);
        static final List<String> TOP_LEVEL_CATEGORY_ORDER = List.of(
                "input",
                "reference",
                "geometry",
                "transform",
                "pattern",
                "material",
                "world",
                "output",
                "math",
                "utilities"
        );
        private static final float[] GRADIENT_START_COLOR = new float[]{0.25f, 0.55f, 0.96f, 1.0f};
        private static final float[] GRADIENT_END_COLOR = new float[]{0.95f, 0.64f, 0.24f, 1.0f};

        private static Map<String, float[]> buildGradientCategoryColors() {
            Map<String, float[]> colors = new HashMap<>();
            if (TOP_LEVEL_CATEGORY_ORDER != null) {
                for (int i = 0; i < TOP_LEVEL_CATEGORY_ORDER.size(); i++) {
                    String topLevelId = TOP_LEVEL_CATEGORY_ORDER.get(i);
                    float t = TOP_LEVEL_CATEGORY_ORDER.size() <= 1 ? 0.0f : (float) i / (TOP_LEVEL_CATEGORY_ORDER.size() - 1);
                    colors.put(topLevelId, lerpColor(t));
                }
            }
            return Map.copyOf(colors);
        }

        private static ConcurrentMap<String, Integer> buildPackedCategoryColors() {
            ConcurrentMap<String, Integer> packedColors = new ConcurrentHashMap<>();
            for (Map.Entry<String, float[]> entry : NodeLibraryConstants.CATEGORY_COLORS_FLOAT.entrySet()) {
                float[] c = entry.getValue();
                packedColors.put(entry.getKey(), ImGui.colorConvertFloat4ToU32(c[0], c[1], c[2], c[3]));
            }
            return packedColors;
        }

        private static float[] lerpColor(float t) {
            float[] color = new float[4];
            for (int i = 0; i < 4; i++) {
                color[i] = NodeLibraryConstants.GRADIENT_START_COLOR[i] + (NodeLibraryConstants.GRADIENT_END_COLOR[i] - NodeLibraryConstants.GRADIENT_START_COLOR[i]) * t;
            }
            return color;
        }

        private static float[] lightenColor(float[] color, float amount) {
            float[] result = Arrays.copyOf(color, color.length);
            result[0] = result[0] + (1.0f - result[0]) * amount;
            result[1] = result[1] + (1.0f - result[1]) * amount;
            result[2] = result[2] + (1.0f - result[2]) * amount;
            result[3] = 1.0f;
            return result;
        }

        /**
         * Resolves the best-matching packed color for a category identifier.
         * <p>
         * Lookup order:
         * <ol>
         *   <li>Exact lowercase match (all canonical IDs are already lowercase).</li>
         *   <li>Top-level prefix: {@code "math.scalar_math"} falls back to {@code "math"}.</li>
         *   <li>Global default.</li>
         * </ol>
         */
        static int getPackedColor(String categoryName) {
            if (categoryName == null) {
                return DEFAULT_CATEGORY_COLOR_INT;
            }
            // 1. Exact lowercase match — canonical IDs are always lowercase.
            String lower = categoryName.toLowerCase(Locale.ROOT);
            Integer exact = CATEGORY_COLORS_INT.get(lower);
            if (exact != null) {
                return exact;
            }
            String topLevel = getTopLevelCategoryId(lower);
            Integer parentColor = CATEGORY_COLORS_INT.get(topLevel);
            if (parentColor != null) {
                int depth = getCategoryDepth(lower);
                if (depth <= 0) {
                    return parentColor;
                }

                imgui.ImVec4 colorVec = new imgui.ImVec4();
                ImGui.colorConvertU32ToFloat4(parentColor, colorVec);
                float lightenAmount = Math.min(0.32f, 0.12f * depth);
                float[] derived = lightenColor(new float[]{colorVec.x, colorVec.y, colorVec.z, colorVec.w}, lightenAmount);
                return CATEGORY_COLORS_INT.computeIfAbsent(lower,
                        ignored -> ImGui.colorConvertFloat4ToU32(derived[0], derived[1], derived[2], derived[3]));
            }
            // 2. Walk up the dot-separated prefix chain: a.b.c -> a.b -> a
            int dot = lower.lastIndexOf('.');
            while (dot > 0) {
                String prefix = lower.substring(0, dot);
                Integer prefixColor = CATEGORY_COLORS_INT.get(prefix);
                if (prefixColor != null) {
                    return prefixColor;
                }
                dot = prefix.lastIndexOf('.');
            }
            // 3. Global default.
            return DEFAULT_CATEGORY_COLOR_INT;
        }

        static int compareTopLevelCategoryIds(String left, String right) {
            int leftIndex = getTopLevelCategoryOrderIndex(left);
            int rightIndex = getTopLevelCategoryOrderIndex(right);
            if (leftIndex != rightIndex) {
                return Integer.compare(leftIndex, rightIndex);
            }
            return left.compareToIgnoreCase(right);
        }

        private static int getTopLevelCategoryOrderIndex(String categoryId) {
            String topLevel = getTopLevelCategoryId(categoryId);
            int index = TOP_LEVEL_CATEGORY_ORDER.indexOf(topLevel);
            return index >= 0 ? index : Integer.MAX_VALUE;
        }

        private static String getTopLevelCategoryId(String categoryId) {
            int dot = categoryId.indexOf('.');
            return dot >= 0 ? categoryId.substring(0, dot) : categoryId;
        }

        private static int getCategoryDepth(String categoryId) {
            int depth = 0;
            for (int i = 0; i < categoryId.length(); i++) {
                if (categoryId.charAt(i) == '.') {
                    depth++;
                }
            }
            return depth;
        }
    }

    // Node library state
    public enum DisplayMode {
        LIST,
        GRID
    }

    private final List<NodeCategory> canonicalCategories; // Registry categories remain the source of truth.
    private List<CategoryPresentation> allCategories; // Current UI-facing presentation categories.
    private final Map<String, Boolean> expandedCategories = new HashMap<>();
    /** Reusable scratch vector for color conversion — avoids one allocation per rendered category header. */
    private final imgui.ImVec4 scratchColorVec = new imgui.ImVec4();
    private List<DisplayCategory> filteredCategories; // Filtered categories for the current search term.
    private List<DisplayCategory> cachedTopLevelCategories = List.of();
    private Map<String, List<DisplayCategory>> cachedChildCategoriesMap = Map.of();
    private boolean categoryHierarchyCacheDirty = true;
    private boolean visible = true;
    private DisplayMode displayMode;
    private float gridTileSizeScale = NodeLibraryConstants.GRID_TILE_SIZE_SCALE;

    // Icon manager.
    private final NodeIconManager iconManager = NodeIconManager.getInstance();

    // Search manager.
    private final NodeSearchManager searchManager = new NodeSearchManager();
    private UUID selectedNodeId;
    private UUID cachedRecommendationNodeId;
    private NodeRecommendationContext cachedRecommendationContext;
    private List<NodeRecommendation> cachedRecommendations = List.of();

    /**
     * Callback used when the user selects a node from the library.
     */
    public interface NodeSelectCallback {
        void onNodeSelected(String nodeId, String nodeTitle);
    }

    private final NodeSelectCallback selectCallback;

    /**
     * Creates the node library component.
     *
     * @param selectCallback node selection callback
     */
    public NodeLibraryComponent(NodeSelectCallback selectCallback) {
        this.selectCallback = selectCallback;
        // Read categories directly from the registry.
        List<NodeCategory> categoriesFromRegistry = NodeRegistry.getInstance().getAllCategories();

        // Validate registry output before building local state.
        if (categoriesFromRegistry == null || categoriesFromRegistry.isEmpty()) {
            NodeCraft.LOGGER.warn("NodeRegistry returned an empty or invalid category list.");
            this.canonicalCategories = new ArrayList<>();
        } else {
            this.canonicalCategories = new ArrayList<>(categoriesFromRegistry);
        }

        // Restore persisted display mode preferences.
        String storedMode = UserPreferences.getString(NodeLibraryConstants.PREF_DISPLAY_MODE_KEY, DisplayMode.LIST.name());
        try {
            this.displayMode = DisplayMode.valueOf(storedMode);
        } catch (IllegalArgumentException e) {
            this.displayMode = DisplayMode.LIST;
        }

        float storedGridScale = UserPreferences.getFloat(
                NodeLibraryConstants.PREF_GRID_TILE_SCALE_KEY,
                NodeLibraryConstants.GRID_TILE_SIZE_SCALE
        );
        setGridTileSizeScale(storedGridScale);

        // 彻底移除 CategoryViewMode 的持久化和恢复逻辑
        rebuildPresentationCategories();
        // Initialize icon resources.
        iconManager.initialize();
    }

    /**
     * Renders the node library panel.
     *
     * @param contentStartY content start Y
     * @param nodePanelWidth panel width
     * @param contentHeight content height
     * @param windowPaddingX horizontal window padding
     */
    public void render(float contentStartY, float nodePanelWidth, float contentHeight, float windowPaddingX) {
        if (!visible) {
            return;
        }

        boolean nodeLibraryChildBegin;

        try {
            // Clamp layout inputs to safe minimums.
            nodePanelWidth = Math.max(NodeLibraryConstants.CHILD_WINDOW_MIN_WIDTH, nodePanelWidth);
            contentHeight = Math.max(NodeLibraryConstants.CHILD_WINDOW_MIN_HEIGHT, contentHeight);

            // Create the child window and disable the native scrollbar.
            int windowFlags = ImGuiWindowFlags.NoScrollbar |
                    ImGuiWindowFlags.NoMove |
                    ImGuiWindowFlags.NoResize |
                    ImGuiWindowFlags.NoCollapse |
                    ImGuiWindowFlags.NoTitleBar;

            // 外层 LayoutRenderer 已提供 child 背景（ChildBg）；这里不再叠加第二层底色，避免文字发灰
            nodeLibraryChildBegin = ImGui.beginChild("nodeLibrary", nodePanelWidth, contentHeight, true, windowFlags);

            if (!nodeLibraryChildBegin) {
                NodeCraft.LOGGER.warn("Failed to begin nodeLibrary child window");
                return;
            }

            try {
                // Search bar.
                renderSearchBar();

                renderSuggestedSection();

                ImGui.separator();
                ImGui.spacing();

                // Node categories.
                renderNodeCategories();
            } finally {
                // Only end the child window after a successful beginChild call.
                ImGui.endChild();
            }
        } catch (Exception e) {
            NodeCraft.LOGGER.error("Failed to render node library: {}", e.getMessage());
            NodeCraft.LOGGER.error("Failed to render node library: {}", e.getMessage(), e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void render(float x, float y, float width, float height, float paddingX, float paddingY) {
        render(y, width, height, paddingX);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void init() {
        // No extra initialization is required here.
        if (NodeCraft.LOGGER.isDebugEnabled()) {
            NodeCraft.LOGGER.debug("Initializing node library component");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cleanup() {
        // Release icon resources.
        iconManager.cleanup();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isVisible() {
        return visible;
    }

    public DisplayMode getDisplayMode() {
        return displayMode;
    }

    public void setDisplayMode(DisplayMode mode) {
        if (mode == null || this.displayMode == mode) {
            return;
        }
        this.displayMode = mode;
        UserPreferences.setString(NodeLibraryConstants.PREF_DISPLAY_MODE_KEY, mode.name());
    }

    public float getGridTileSizeScale() {
        return gridTileSizeScale;
    }

    public void setGridTileSizeScale(float scale) {
        float clamped = Math.max(NodeLibraryConstants.GRID_TILE_SIZE_SCALE_MIN,
                Math.min(NodeLibraryConstants.GRID_TILE_SIZE_SCALE_MAX, scale));
        if (Math.abs(this.gridTileSizeScale - clamped) < 0.001f) {
            return;
        }
        this.gridTileSizeScale = clamped;
        UserPreferences.setFloat(NodeLibraryConstants.PREF_GRID_TILE_SCALE_KEY, clamped);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getComponentId() {
        return "nodeLibrary";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean handleEvent(String eventType, Object data) {
        switch (eventType) {
            case "nodeSelected" -> {
                if (data instanceof UUID nodeId) {
                    selectedNodeId = nodeId;
                    refreshRecommendationCache();
                } else {
                    clearRecommendationCache();
                }
                return true;
            }
            case "nodeSelectionCleared", "graphChanged" -> {
                clearRecommendationCache();
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void clearRecommendationCache() {
        selectedNodeId = null;
        cachedRecommendationNodeId = null;
        cachedRecommendationContext = null;
        cachedRecommendations = List.of();
    }

    private void refreshRecommendationCache() {
        if (selectedNodeId == null) {
            clearRecommendationCache();
            return;
        }
        if (selectedNodeId.equals(cachedRecommendationNodeId) && cachedRecommendationContext != null) {
            return;
        }

        ImGuiNodeEditor editor = ImGuiNodeEditor.getInstance();
        NodeGraph graph = editor != null ? editor.getCurrentGraph() : null;
        if (graph == null) {
            cachedRecommendations = List.of();
            return;
        }

        INode selectedNode = graph.getNode(selectedNodeId);
        if (selectedNode == null) {
            cachedRecommendations = List.of();
            return;
        }

        NodeRecommendations.get().initialize();
        cachedRecommendationContext = NodeRecommendationContext.forSelectedNode(
                selectedNodeId,
                (float) selectedNode.getPositionX(),
                (float) selectedNode.getPositionY(),
                5);
        cachedRecommendations = NodeRecommendations.get().recommend(graph, cachedRecommendationContext);
        cachedRecommendationNodeId = selectedNodeId;
    }

    private void renderSuggestedSection() {
        if (selectedNodeId == null || !searchManager.getSearchTerm().isEmpty()) {
            return;
        }

        if (cachedRecommendations.isEmpty()) {
            return;
        }

        ImGuiNodeEditor editor = ImGuiNodeEditor.getInstance();
        ImGui.textColored(0.55f, 0.85f, 1.0f, 1.0f, "推荐下游");
        for (NodeRecommendation recommendation : cachedRecommendations) {
            if (ImGui.selectable("  " + recommendation.displayName() + "##suggest_" + recommendation.nodeId())) {
                if (editor != null && cachedRecommendationContext != null) {
                    editor.applyRecommendation(cachedRecommendationContext, recommendation);
                }
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(recommendation.reason());
            }
        }
        ImGui.spacing();
    }

    /**
     * Renders the search bar.
     */
    private void renderSearchBar() {
        // Delegate search UI rendering to the search manager.
        boolean searchChanged = searchManager.renderSearchBar(this::updateFilteredCategories);

        if (searchChanged) {
            NodeCraft.LOGGER.debug("Search term changed. Node library will refresh on the next frame.");
        }
    }

    private void rebuildPresentationCategories() {
        this.allCategories = PRESENTATION_MAPPER.mapCategories(
                canonicalCategories
        );

        expandedCategories.clear();
        for (CategoryPresentation category : allCategories) {
            expandedCategories.put(category.displayCategoryId(), category.defaultExpanded());
        }
        updateFilteredCategories(searchManager.getSearchTerm());
    }

    /**
     * Updates the filtered category list for the active search term.
     *
     * @param searchTerm search term
     */
    private void updateFilteredCategories(String searchTerm) {
        NodeCraft.LOGGER.debug("Updating node library search results for term: '{}'", searchTerm);

        // Empty search shows all categories and nodes.
        if (searchTerm == null || searchTerm.isEmpty()) {
            NodeCraft.LOGGER.debug("Search term is empty. Showing all categories.");
            this.filteredCategories = this.allCategories.stream()
                    .map(cat -> new DisplayCategory(cat, getVisibleNodes(cat.sourceCategory().getNodes())))
                    .collect(Collectors.toList());
            this.categoryHierarchyCacheDirty = true;
            NodeCraft.LOGGER.debug("Filtered category count for empty search: {}", this.filteredCategories.size());
            return;
        }

        // Normalize the search term before matching.
        String processedTerm = searchTerm.toLowerCase().trim();
        NodeCraft.LOGGER.debug("Normalized search term: '{}'", processedTerm);

        Map<String, CategoryPresentation> presentationById = buildPresentationCategoryIndex();

        // Scan all categories and nodes directly.
        List<DisplayCategory> searchResults = new ArrayList<>();
        Set<String> categoriesToExpand = new HashSet<>();

        for (CategoryPresentation category : allCategories) {
            String categoryId = category.displayCategoryId();
            String categoryName = category.displayName().toLowerCase();
            boolean categoryMatches = categoryName.contains(processedTerm) || categoryId.toLowerCase().contains(processedTerm);

            // Collect matching nodes in the current category.
            List<NodeInfo> matchingNodes = new ArrayList<>();
            for (NodeInfo node : category.sourceCategory().getNodes()) {
                if (isDeprecatedNode(node)) {
                    continue;
                }
                if (matchesNode(node, processedTerm)) {
                    matchingNodes.add(node);
                    NodeCraft.LOGGER.debug("Node matched search term: {} ({}) in category {}",
                            node.getDisplayName(), node.getId(), categoryId);
                }
            }

            // 1. Category name matched, so keep all nodes in that category.
            if (categoryMatches) {
                searchResults.add(new DisplayCategory(category, getVisibleNodes(category.sourceCategory().getNodes())));
                NodeCraft.LOGGER.debug("Category matched search term '{}': {} ({}), keeping all nodes",
                        processedTerm, category.displayName(), categoryId);

                categoriesToExpand.addAll(collectSelfAndAncestorCategoryIds(categoryId, presentationById));

                continue;
            }

            // 2. Category did not match, but some nodes did.
            if (!matchingNodes.isEmpty()) {
                searchResults.add(new DisplayCategory(category, matchingNodes));
                NodeCraft.LOGGER.debug("Category {} contains {} matching nodes", categoryId, matchingNodes.size());

                categoriesToExpand.addAll(collectSelfAndAncestorCategoryIds(categoryId, presentationById));
            }
        }

        // Expand matched categories and all their ancestor chain.
        for (String categoryId : categoriesToExpand) {
            expandedCategories.put(categoryId, true);
            NodeCraft.LOGGER.debug("Expanded category during search: {}", categoryId);
        }

        NodeCraft.LOGGER.debug("Search '{}' matched {} categories", processedTerm, searchResults.size());

        if (!searchResults.isEmpty()) {
            // Ensure ancestor categories remain visible even when only deep child categories matched.
            // Build a lookup set of already-included IDs to avoid O(n²) containment checks.
            Set<String> includedIds = new HashSet<>();
            for (DisplayCategory dc : searchResults) {
                includedIds.add(dc.getId());
            }
            List<DisplayCategory> completeResults = new ArrayList<>(searchResults);
            for (String categoryId : categoriesToExpand) {
                if (!includedIds.contains(categoryId)) {
                    CategoryPresentation cat = presentationById.get(categoryId);
                    if (cat != null) {
                        completeResults.add(new DisplayCategory(cat, new ArrayList<>()));
                        NodeCraft.LOGGER.debug("Added missing ancestor category {}", categoryId);
                    }
                }
            }
            this.filteredCategories = completeResults;
        } else {
            NodeCraft.LOGGER.debug("No matches found. Showing empty top-level categories.");
            this.filteredCategories = allCategories.stream()
                    .filter(cat -> cat.parentDisplayId() == null)
                    .map(cat -> new DisplayCategory(cat, new ArrayList<>()))
                    .collect(Collectors.toList());
        }

        this.categoryHierarchyCacheDirty = true;
        NodeCraft.LOGGER.debug("Filtered category count: {}", this.filteredCategories.size());
    }

    /**
     * Returns whether a node matches the current search term.
     */
    private boolean matchesNode(NodeInfo node, String searchTerm) {
        if (searchTerm == null || searchTerm.isEmpty() || node == null) {
            return false;
        }

        String displayName = node.getDisplayName() != null ? node.getDisplayName().toLowerCase() : "";
        String nodeId = node.getId() != null ? node.getId().toLowerCase() : "";
        String description = node.getDescription() != null ? node.getDescription().toLowerCase() : "";

        return displayName.contains(searchTerm) ||
                nodeId.contains(searchTerm) ||
                description.contains(searchTerm);
    }

    /**
     * Renders the category list and its visible nodes.
     */
    private void renderNodeCategories() {
        // Use zero height so the child region consumes the remaining vertical space.
        ImGui.beginChild("##nodeListScrollingRegion", 0, 0, false, ImGuiWindowFlags.NoScrollbar);

        // Show empty-state feedback when nothing can be displayed.
        if (filteredCategories.isEmpty() && !searchManager.getSearchTerm().isEmpty()) {
            searchManager.renderNoMatchesMessage();
        } else if (allCategories.isEmpty()) {
            ImGui.text("  No nodes registered.");
            ImGui.spacing();
            ImGui.text("  Please restart the application or check logs.");
        }

        // Control vertical spacing between categories.
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.ItemSpacing, ImGui.getStyle().getItemSpacingX(), NodeLibraryConstants.CATEGORY_ITEM_SPACING);

        ensureCategoryHierarchyCache();
        List<DisplayCategory> topLevelCategories = cachedTopLevelCategories;
        Map<String, List<DisplayCategory>> childCategoriesMap = cachedChildCategoriesMap;

        boolean isSearching = searchManager.getSearchTerm() != null && !searchManager.getSearchTerm().isEmpty();

        // In search mode, force open the categories that contain matching nodes.
        if (isSearching) {
            Map<String, CategoryPresentation> presentationById = buildPresentationCategoryIndex();
            for (DisplayCategory category : filteredCategories) {
                if (!category.getNodes().isEmpty()) {
                    for (String categoryId : collectSelfAndAncestorCategoryIds(category.getId(), presentationById)) {
                        expandedCategories.put(categoryId, true);
                        NodeCraft.LOGGER.debug("Forced category open during search: {}", categoryId);
                    }
                }
            }
        }

        // Render top-level categories first, then any visible children.
        for (DisplayCategory topCategory : topLevelCategories) {
            renderCategory(topCategory, 0, isSearching);

            if (expandedCategories.getOrDefault(topCategory.getId(), true)) {
                List<DisplayCategory> children = childCategoriesMap.get(topCategory.getId());

                if (children != null && !children.isEmpty()) {
                    ImGui.indent(NodeLibraryConstants.CATEGORY_INDENT);

                    for (DisplayCategory childCategory : children) {
                        renderCategory(childCategory, 1, isSearching);
                    }

                    ImGui.unindent(NodeLibraryConstants.CATEGORY_INDENT);
                }
            }
        }

        ImGui.popStyleVar();
        ImGui.endChild();
    }

    private void ensureCategoryHierarchyCache() {
        if (!categoryHierarchyCacheDirty) {
            return;
        }

        Map<String, List<DisplayCategory>> childCategoriesMap = new HashMap<>();
        List<DisplayCategory> topLevelCategories = new ArrayList<>();

        for (DisplayCategory category : filteredCategories) {
            String parentId = category.getParentId();

            if (parentId == null) {
                topLevelCategories.add(category);
            } else {
                childCategoriesMap
                        .computeIfAbsent(parentId, k -> new ArrayList<>())
                        .add(category);
            }
        }

        topLevelCategories.sort((cat1, cat2) -> {
            int orderCompare = NodeLibraryConstants.compareTopLevelCategoryIds(cat1.getId(), cat2.getId());
            if (orderCompare != 0) {
                return orderCompare;
            }
            return cat1.getDisplayName().compareToIgnoreCase(cat2.getDisplayName());
        });
        for (List<DisplayCategory> childList : childCategoriesMap.values()) {
            childList.sort((cat1, cat2) -> cat1.getDisplayName().compareToIgnoreCase(cat2.getDisplayName()));
        }

        List<DisplayCategory> promotedTopLevelCategories = new ArrayList<>();
        List<DisplayCategory> retainedTopLevelCategories = new ArrayList<>();
        for (DisplayCategory topCategory : topLevelCategories) {
            List<DisplayCategory> children = childCategoriesMap.get(topCategory.getId());
            if (topCategory.getNodes().isEmpty() && children != null && children.size() == 1) {
                DisplayCategory promotedChild = children.getFirst();
                if (promotedChild.getId().endsWith(".legacy")) {
                    promotedTopLevelCategories.add(promotedChild);
                    childCategoriesMap.remove(topCategory.getId());
                    NodeCraft.LOGGER.debug("Promoted lone legacy child category {} to top-level display", promotedChild.getId());
                    continue;
                }
            }
            retainedTopLevelCategories.add(topCategory);
        }

        retainedTopLevelCategories.addAll(promotedTopLevelCategories);
        this.cachedTopLevelCategories = List.copyOf(retainedTopLevelCategories);

        Map<String, List<DisplayCategory>> cachedChildren = new HashMap<>();
        for (Map.Entry<String, List<DisplayCategory>> entry : childCategoriesMap.entrySet()) {
            cachedChildren.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        this.cachedChildCategoriesMap = Map.copyOf(cachedChildren);
        this.categoryHierarchyCacheDirty = false;
    }

    private Map<String, CategoryPresentation> buildPresentationCategoryIndex() {
        Map<String, CategoryPresentation> presentationById = new HashMap<>();
        for (CategoryPresentation category : allCategories) {
            presentationById.put(category.displayCategoryId(), category);
        }
        return presentationById;
    }

    private Set<String> collectSelfAndAncestorCategoryIds(String categoryId, Map<String, CategoryPresentation> presentationById) {
        Set<String> result = new HashSet<>();
        if (categoryId == null || categoryId.isBlank() || presentationById == null || presentationById.isEmpty()) {
            return result;
        }

        String currentId = categoryId;
        int guard = 0;
        while (currentId != null && !currentId.isBlank() && guard < 64 && result.add(currentId)) {
            CategoryPresentation current = presentationById.get(currentId);
            currentId = current == null ? null : current.parentDisplayId();
            guard++;
        }
        return result;
    }

    /**
     * Renders a single category header and its visible nodes.
     *
     * @param displayCategory category to render
     * @param level nesting depth, where 0 is top-level
     * @param shouldExpand whether the category should be forced open
     */
    private void renderCategory(DisplayCategory displayCategory, int level, boolean shouldExpand) {
        boolean hasNodes = !displayCategory.getNodes().isEmpty();

        // Matching categories stay expanded in search mode.
        if (shouldExpand && hasNodes) {
            expandedCategories.put(displayCategory.getId(), true);
        }

        String categoryId = displayCategory.getColorKey();

        // Styling setup.
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.ItemSpacing, ImGui.getStyle().getItemSpacingX(), 0);

        // Give top-level categories a little more visual weight.
        if (level == 0) {
            ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.FramePadding, 4, 6);
        }

        // Build the label shown in the collapsing header.
        String displayTitle = getString(displayCategory, level, categoryId);

        int headerFlags = ImGuiSelectableFlags.None;

        if (shouldExpand && hasNodes) {
            ImGui.setNextItemOpen(true);
        }

        boolean headerClicked = ImGui.collapsingHeader(displayTitle + "##header_" + displayCategory.getId(), headerFlags);

        if (level == 0) {
            ImGui.popStyleVar();
        }
        ImGui.popStyleVar();

        // Persist the latest expansion state.
        expandedCategories.put(displayCategory.getId(), headerClicked);

        // Render nodes only while the category is open.
        if (headerClicked) {
            ImGui.indent(NodeLibraryConstants.CATEGORY_INDENT);

            ImGui.dummy(0, 2.0f);

            // Use the already-computed hierarchy cache — O(1) instead of O(n).
            boolean hasSubcategories = cachedChildCategoriesMap.containsKey(displayCategory.getId());

            List<NodeInfo> nodesToRender = getSortedNodesForDisplay(displayCategory);

            NodeCraft.LOGGER.debug("Category {} has {} nodes to render", displayCategory.getDisplayName(), nodesToRender.size());

            // Node-list debug logging omitted: joining all display names per frame is O(n)
            // and clutters logs during active search. Use the Value Monitor node for runtime inspection.

            if (nodesToRender.isEmpty() && searchManager.getSearchTerm().isEmpty() && !hasSubcategories) {
                ImGui.text("  (No nodes in this category)");
            } else if (nodesToRender.isEmpty() && !searchManager.getSearchTerm().isEmpty()) {
                // Category name matched, but there are no visible nodes to draw.
            } else {
                if (displayMode == DisplayMode.GRID) {
                    renderNodesAsGrid(nodesToRender, displayCategory);
                } else {
                    renderNodesAsList(nodesToRender, displayCategory);
                }
            }
            ImGui.unindent(NodeLibraryConstants.CATEGORY_INDENT);
            ImGui.dummy(0, NodeLibraryConstants.CATEGORY_SPACING_EXPANDED);
        } else {
            // Keep a small spacer below collapsed headers for readability.
            ImGui.dummy(0, NodeLibraryConstants.CATEGORY_SPACING_COLLAPSED);
        }
    }

    private void renderNodesAsList(List<NodeInfo> nodesToRender, DisplayCategory displayCategory) {
        for (NodeInfo node : nodesToRender) {
            String nodeCategory = node.getCategoryId();
            float lineHeight = ImGui.getTextLineHeight();
            float iconPadding = 4.0f;
            float availableWidth = ImGui.getContentRegionAvailX();

            boolean selected = ImGui.selectable("##node_selector_" + node.getId(), false,
                    ImGuiSelectableFlags.AllowItemOverlap, availableWidth, lineHeight);

            ImVec2 rectMin = ImGui.getItemRectMin();
            ImDrawList drawList = ImGui.getWindowDrawList();

            renderNode(drawList, rectMin, new ImVec2(lineHeight, lineHeight), node, nodeCategory, lineHeight, iconPadding);
            handleNodeInteraction(node, nodeCategory, displayCategory, lineHeight);

            if (selected && selectCallback != null) {
                selectCallback.onNodeSelected(node.getId(), node.getDisplayName());
                if (NodeCraft.LOGGER.isDebugEnabled()) {
                    NodeCraft.LOGGER.debug("Node selected: {} ({})", node.getDisplayName(), node.getId());
                }
            }
        }
    }

    private void renderNodesAsGrid(List<NodeInfo> nodesToRender, DisplayCategory displayCategory) {
        float listLikeSpacing = ImGui.getStyle().getItemSpacingY();
        // Use frame height as the 1x baseline for grid tiles.
        float tileSide = ImGui.getFrameHeight() * gridTileSizeScale;
        float spacingX = listLikeSpacing;
        float availableWidth = Math.max(tileSide, ImGui.getContentRegionAvailX());
        int columns = Math.max(1, (int) ((availableWidth + spacingX) / (tileSide + spacingX)));

        for (int i = 0; i < nodesToRender.size(); i++) {
            NodeInfo node = nodesToRender.get(i);
            String nodeCategory = node.getCategoryId();

            boolean selected = ImGui.selectable("##node_tile_" + node.getId(), false,
                    ImGuiSelectableFlags.AllowItemOverlap, tileSide, tileSide);

            ImVec2 rectMin = ImGui.getItemRectMin();
            ImDrawList drawList = ImGui.getWindowDrawList();

            drawNodeIcon(drawList, rectMin, node, nodeCategory, tileSide);
            handleNodeInteraction(node, nodeCategory, displayCategory, tileSide);

            if (selected && selectCallback != null) {
                selectCallback.onNodeSelected(node.getId(), node.getDisplayName());
                if (NodeCraft.LOGGER.isDebugEnabled()) {
                    NodeCraft.LOGGER.debug("Node selected: {} ({})", node.getDisplayName(), node.getId());
                }
            }

            boolean isEndOfRow = (i + 1) % columns == 0;
            boolean isLastItem = i == nodesToRender.size() - 1;
            if (!isEndOfRow && !isLastItem) {
                ImGui.sameLine(0.0f, spacingX);
            }
        }
    }

    private void drawGridPlaceholderIcon(ImDrawList drawList, ImVec2 topLeft, float tileSide, String nodeCategory) {
        int categoryColor = NodeLibraryConstants.getPackedColor(nodeCategory);
        imgui.ImVec4 colorVec = scratchColorVec;
        ImGui.colorConvertU32ToFloat4(categoryColor, colorVec);

        int fillColor = ImGui.colorConvertFloat4ToU32(
                colorVec.x * 0.55f,
                colorVec.y * 0.55f,
                colorVec.z * 0.55f,
                0.90f
        );
        int borderColor = ImGui.colorConvertFloat4ToU32(
                Math.min(1.0f, colorVec.x * 1.15f),
                Math.min(1.0f, colorVec.y * 1.15f),
                Math.min(1.0f, colorVec.z * 1.15f),
                1.0f
        );

        drawList.addRectFilled(
                topLeft.x,
                topLeft.y,
                topLeft.x + tileSide,
                topLeft.y + tileSide,
                fillColor,
                0.0f
        );
        drawList.addRect(
                topLeft.x,
                topLeft.y,
                topLeft.x + tileSide,
                topLeft.y + tileSide,
                borderColor,
                0.0f,
                0,
                1.0f
        );
    }

    private void handleNodeInteraction(NodeInfo node, String nodeCategory, DisplayCategory displayCategory, float dragIconSizeRef) {
        if (ImGui.beginDragDropSource(ImGuiDragDropFlags.None)) {
            byte[] payloadBytes = node.getId().getBytes(StandardCharsets.UTF_8);
            ImGui.setDragDropPayload(NodeLibraryConstants.DRAG_DROP_PAYLOAD_TYPE, payloadBytes);

            float dragIconSize = dragIconSizeRef * 0.8f;
            ImGui.dummy(dragIconSize, dragIconSize);
            ImVec2 iconPos = ImGui.getItemRectMin();

            ImDrawList dragDrawList = ImGui.getWindowDrawList();
            drawNodeIcon(dragDrawList, iconPos, node, nodeCategory, dragIconSize);

            ImGui.sameLine(0, 5.0f);
            ImGui.text(node.getDisplayName());
            ImGui.endDragDropSource();
        }

        if (ImGui.isItemHovered()) {
            ImGui.beginTooltip();

            float tooltipIconSize = ImGui.getTextLineHeight() * 1.2f;
            ImVec2 tooltipPos = ImGui.getCursorScreenPos();

            ImDrawList tooltipDrawList = ImGui.getWindowDrawList();
            drawNodeIcon(tooltipDrawList, tooltipPos, node, nodeCategory, tooltipIconSize);

            ImGui.dummy(tooltipIconSize, tooltipIconSize);
            ImGui.sameLine();
            ImGui.textUnformatted(node.getDisplayName());

            ImGui.text(node.getId());

            String description = node.getDescription() != null ? node.getDescription() : "";
            if (!description.isEmpty()) {
                ImGui.separator();
                ImGui.textWrapped(description);
            }

            ImGui.separator();
            ImGui.text("Category: " + displayCategory.getDisplayName());

            ImGui.endTooltip();
        }
    }

    private List<NodeInfo> getSortedNodesForDisplay(DisplayCategory displayCategory) {
        List<NodeInfo> nodes = new ArrayList<>(displayCategory.getNodes());
        nodes.sort(Comparator
                .comparingInt(NodeInfo::getOrder)
                .thenComparing(NodeInfo::getDisplayName, String.CASE_INSENSITIVE_ORDER));
        return nodes;
    }

    private List<NodeInfo> getVisibleNodes(List<NodeInfo> nodes) {
        if (SHOW_DEPRECATED_NODES || nodes == null || nodes.isEmpty()) {
            return nodes == null ? new ArrayList<>() : new ArrayList<>(nodes);
        }
        return nodes.stream()
                .filter(node -> !isDeprecatedNode(node))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static boolean isDeprecatedNode(NodeInfo node) {
        if (SHOW_DEPRECATED_NODES || node == null || node.getNodeClass() == null) {
            return false;
        }
        return node.getNodeClass().isAnnotationPresent(Deprecated.class);
    }

    private static String getString(DisplayCategory displayCategory, int level, String categoryId) {
        String displayTitle;

        if (level > 0 && categoryId.contains(".")) {
            // Build a short subcategory label in the UI layer.
            String subCategoryPart = categoryId.substring(categoryId.lastIndexOf('.') + 1);
            // Title-case the first letter for display.
            subCategoryPart = subCategoryPart.substring(0, 1).toUpperCase() + subCategoryPart.substring(1);
            displayTitle = subCategoryPart;
        } else {
            // Top-level categories use their stored display names directly.
            displayTitle = displayCategory.getDisplayName();
        }
        return displayTitle;
    }

    /**
     * Renders a node row and highlights matching text while searching.
     */
    private void renderNode(ImDrawList drawList, ImVec2 rectMin, ImVec2 textSize, NodeInfo node, String nodeCategory, float iconSize, float iconPadding) {
        float actualLineHeight = ImGui.getTextLineHeight();
        float yOffset = 0;
        if (iconSize < actualLineHeight) {
            yOffset = (actualLineHeight - iconSize) * 0.5f;
        }
        drawNodeIcon(drawList, new ImVec2(rectMin.x, rectMin.y + yOffset), node, nodeCategory, iconSize);

        // Draw text only for the normal row view, not for drag previews or tooltips.
        if (iconPadding > 0) {
            // Compute text placement.
            float textStartX = rectMin.x + iconSize + iconPadding;
            float textPosY = rectMin.y + (actualLineHeight - ImGui.getTextLineHeight()) * 0.5f;

            String searchTerm = searchManager.getSearchTerm();
            String nodeDisplayName = node.getDisplayName();

            // Highlight matching text while searching.
            int textColor = ImGui.getColorU32(ImGuiCol.Text);

            if (searchTerm != null && !searchTerm.isEmpty()) {
                String lowerNodeName = nodeDisplayName.toLowerCase();
                String lowerSearchTerm = searchTerm.toLowerCase();

                if (lowerNodeName.contains(lowerSearchTerm)) {
                    int highlightColor = ImGui.colorConvertFloat4ToU32(1.0f, 0.8f, 0.0f, 1.0f);

                    int matchStart = lowerNodeName.indexOf(lowerSearchTerm);
                    int matchEnd = matchStart + lowerSearchTerm.length();

                    if (matchStart > 0) {
                        String prefix = nodeDisplayName.substring(0, matchStart);
                        drawList.addText(textStartX, textPosY, textColor, prefix);
                        textStartX += ImGui.calcTextSize(prefix).x;
                    }

                    String highlight = nodeDisplayName.substring(matchStart, matchEnd);
                    drawList.addText(textStartX, textPosY, highlightColor, highlight);
                    textStartX += ImGui.calcTextSize(highlight).x;

                    if (matchEnd < nodeDisplayName.length()) {
                        String suffix = nodeDisplayName.substring(matchEnd);
                        drawList.addText(textStartX, textPosY, textColor, suffix);
                    }
                } else {
                    // The node matched by ID or description rather than display name.
                    drawList.addText(textStartX, textPosY, textColor, nodeDisplayName);

                    float indicatorSize = 3.0f;
                    float indicatorX = textStartX - indicatorSize - 2.0f;
                    float indicatorY = textPosY + ImGui.getTextLineHeight() / 2.0f - indicatorSize / 2.0f;

                    int indicatorColor = ImGui.colorConvertFloat4ToU32(1.0f, 0.8f, 0.0f, 1.0f);
                    drawList.addRectFilled(
                            indicatorX, indicatorY,
                            indicatorX + indicatorSize, indicatorY + indicatorSize,
                            indicatorColor
                    );
                }
            } else {
                // Normal mode: draw the text directly.
                drawList.addText(textStartX, textPosY, textColor, nodeDisplayName);
            }
        }
    }

    private void drawNodeIcon(ImDrawList drawList, ImVec2 topLeft, NodeInfo node, String nodeCategory, float iconSize) {
        String nodeId = node.getId();
        int textureId = iconManager.loadNodeIcon(nodeId, nodeCategory, node.getIcon());

        if (textureId > 0) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
            drawList.addImage(
                    textureId,
                    topLeft.x, topLeft.y,
                    topLeft.x + iconSize, topLeft.y + iconSize,
                    0.0f, 0.0f, 1.0f, 1.0f
            );
        } else {
            int bgColor = ImGui.colorConvertFloat4ToU32(0.7f, 0.7f, 0.7f, 0.7f);
            drawList.addRectFilled(
                    topLeft.x, topLeft.y,
                    topLeft.x + iconSize, topLeft.y + iconSize,
                    bgColor, 0.0f
            );
        }
    }

}

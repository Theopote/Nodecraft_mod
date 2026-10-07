package com.nodecraft.gui.recommendation;

import com.nodecraft.gui.editor.impl.ImGuiNodeEditor;
import com.nodecraft.gui.editor.impl.NodePosition;
import com.nodecraft.gui.layout.ImGuiChildScope;
import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.gui.utils.NodeIconManager;
import com.nodecraft.gui.utils.UserPreferences;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiSelectableFlags;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import org.lwjgl.opengl.GL11;

import java.util.List;
import java.util.UUID;

/**
 * Canvas overlay (bottom-left) listing downstream connection suggestions for the
 * primary selected node.
 */
public final class SuggestedConnectionsOverlay {

    private static final String PREF_KEY = "canvas.show_suggested_connections";
    private static final String LEGACY_PREF_KEY = "node_library.show_suggested_connections";

    private static final float PANEL_MIN_WIDTH = 100.0f;
    private static final float PANEL_MAX_WIDTH = 260.0f;
    private static final float MARGIN = 8.0f;
    private static final float ICON_PADDING = 4.0f;
    private static final float ROW_TEXT_SLACK = 6.0f;
    private static final float COMPACT_PAD_X = 8.0f;
    private static final float COMPACT_PAD_Y = 6.0f;
    private static final float COMPACT_ITEM_SPACING_Y = 2.0f;
    private static final int MAX_VISIBLE_ITEMS = 8;
    private static final int RECOMMENDATION_LIMIT = 5;
    private static final String HEADER_TEXT = "Suggested Connections";

    private final NodeIconManager iconManager = NodeIconManager.getInstance();

    private boolean showSuggestedConnections;
    private UUID cachedRecommendationNodeId;
    private NodeRecommendationContext cachedRecommendationContext;
    private List<NodeRecommendation> cachedRecommendations = List.of();
    private float lastPanelX;
    private float lastPanelY;
    private float lastPanelW;
    private float lastPanelH;
    private boolean lastPanelVisible;

    public SuggestedConnectionsOverlay() {
        // New key wins; when unset, fall back to the former node-library preference.
        this.showSuggestedConnections = UserPreferences.getBoolean(
                PREF_KEY,
                UserPreferences.getBoolean(LEGACY_PREF_KEY, true));
    }

    public boolean isShowSuggestedConnections() {
        return showSuggestedConnections;
    }

    public void setShowSuggestedConnections(boolean show) {
        if (this.showSuggestedConnections == show) {
            return;
        }
        this.showSuggestedConnections = show;
        UserPreferences.setBoolean(PREF_KEY, show);
        if (!show) {
            clearCache();
        }
    }

    /** Hit-test using the panel rect from the previous frame (for canvas click routing). */
    public boolean containsScreenPoint(float screenX, float screenY) {
        return lastPanelVisible
                && screenX >= lastPanelX && screenX <= lastPanelX + lastPanelW
                && screenY >= lastPanelY && screenY <= lastPanelY + lastPanelH;
    }

    public void render(ImVec2 canvasPos, float canvasWidth, float canvasHeight, ImGuiNodeEditor editor) {
        lastPanelVisible = false;
        if (!showSuggestedConnections || editor == null || canvasPos == null) {
            return;
        }

        syncCache(editor);

        if (cachedRecommendations.isEmpty() || cachedRecommendationContext == null) {
            return;
        }

        List<NodeRecommendation> visible = cachedRecommendations.size() > MAX_VISIBLE_ITEMS
                ? cachedRecommendations.subList(0, MAX_VISIBLE_ITEMS)
                : cachedRecommendations;

        float lineHeight = ImGui.getTextLineHeight();
        int itemCount = visible.size();
        float maxLabelWidth = ImGui.calcTextSize(HEADER_TEXT).x;
        for (NodeRecommendation recommendation : visible) {
            String label = recommendation.planMarkAscii() + recommendation.displayName();
            maxLabelWidth = Math.max(maxLabelWidth, ImGui.calcTextSize(label).x);
        }

        // Row content: icon + gap + label; header is text-only (no icon).
        float rowContentWidth = lineHeight + ICON_PADDING + maxLabelWidth + ROW_TEXT_SLACK;
        float contentWidth = Math.max(maxLabelWidth, rowContentWidth);
        float panelWidth = contentWidth + COMPACT_PAD_X * 2.0f;
        panelWidth = Math.max(PANEL_MIN_WIDTH, Math.min(PANEL_MAX_WIDTH, panelWidth));
        panelWidth = Math.min(panelWidth, Math.max(PANEL_MIN_WIDTH, canvasWidth - MARGIN * 2.0f));

        // Tight height: header + rows with spacing only between items (not after the last).
        float contentHeight = lineHeight
                + (itemCount > 0 ? COMPACT_ITEM_SPACING_Y : 0.0f)
                + itemCount * lineHeight
                + Math.max(0, itemCount - 1) * COMPACT_ITEM_SPACING_Y;
        float panelHeight = contentHeight + COMPACT_PAD_Y * 2.0f;
        panelHeight = Math.min(panelHeight, Math.max(lineHeight + COMPACT_PAD_Y * 2.0f, canvasHeight - MARGIN * 2.0f));

        float panelX = canvasPos.x + MARGIN;
        float panelY = canvasPos.y + canvasHeight - panelHeight - MARGIN;
        lastPanelX = panelX;
        lastPanelY = panelY;
        lastPanelW = panelWidth;
        lastPanelH = panelHeight;
        lastPanelVisible = true;

        ImGui.setCursorScreenPos(panelX, panelY);

        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, COMPACT_PAD_X, COMPACT_PAD_Y);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, ImGui.getStyle().getItemSpacingX(), COMPACT_ITEM_SPACING_Y);
        ImGui.pushStyleColor(ImGuiCol.ChildBg, 0.08f, 0.10f, 0.14f, 0.92f);
        ImGui.pushStyleColor(ImGuiCol.Border, 0.35f, 0.55f, 0.75f, 0.65f);
        try (ImGuiChildScope scope = new ImGuiChildScope(
                "##suggested_connections_overlay",
                panelWidth,
                panelHeight,
                true,
                ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse | ImGuiWindowFlags.NoMove)) {
            if (!scope.isOpen()) {
                return;
            }

            if (ImGui.isWindowHovered()) {
                ImGui.getIO().setWantCaptureMouse(true);
            }

            ImGui.textColored(0.55f, 0.85f, 1.0f, 1.0f, HEADER_TEXT);

            NodeRegistry registry = NodeRegistry.getInstance();
            for (NodeRecommendation recommendation : visible) {
                float availableWidth = ImGui.getContentRegionAvailX();
                boolean clicked = ImGui.selectable(
                        "##canvas_suggest_" + recommendation.nodeId(),
                        false,
                        ImGuiSelectableFlags.AllowItemOverlap,
                        availableWidth,
                        lineHeight);

                ImVec2 rectMin = ImGui.getItemRectMin();
                ImDrawList drawList = ImGui.getWindowDrawList();
                int textColor = ImGui.getColorU32(ImGuiCol.Text);

                NodeInfo nodeInfo = registry.getNodeInfo(recommendation.nodeId());
                float textStartX = rectMin.x;
                if (nodeInfo != null) {
                    String category = recommendation.categoryId() != null && !recommendation.categoryId().isBlank()
                            ? recommendation.categoryId()
                            : nodeInfo.getCategoryId();
                    drawNodeIcon(drawList, rectMin, nodeInfo, category, lineHeight);
                    textStartX = rectMin.x + lineHeight + ICON_PADDING;
                }

                String label = recommendation.planMarkAscii() + recommendation.displayName();
                drawList.addText(textStartX, rectMin.y, textColor, label);

                if (clicked) {
                    editor.applyRecommendation(cachedRecommendationContext, recommendation);
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(recommendation.reason());
                }
            }
        } finally {
            ImGui.popStyleColor(2);
            ImGui.popStyleVar(2);
        }
    }

    private void syncCache(ImGuiNodeEditor editor) {
        // Downstream suggestions only make sense for a single selected source node.
        if (editor.getSelectedNodeIds() == null || editor.getSelectedNodeIds().size() != 1) {
            clearCache();
            return;
        }

        UUID selectedNodeId = editor.getSelectedNodeId();
        if (selectedNodeId == null || !editor.getSelectedNodeIds().contains(selectedNodeId)) {
            clearCache();
            return;
        }

        if (selectedNodeId.equals(cachedRecommendationNodeId) && cachedRecommendationContext != null) {
            return;
        }

        NodeGraph graph = editor.getCurrentGraph();
        if (graph == null) {
            clearCache();
            return;
        }

        INode selectedNode = graph.getNode(selectedNodeId);
        if (selectedNode == null || !hasConnectableOutput(selectedNode)) {
            clearCache();
            return;
        }

        NodePosition nodePos = editor.getNodePosition(selectedNodeId);
        float placementX = nodePos != null ? nodePos.x : (float) selectedNode.getPositionX();
        float placementY = nodePos != null ? nodePos.y : (float) selectedNode.getPositionY();

        NodeRecommendations.get().initialize();
        cachedRecommendationContext = NodeRecommendationContext.forSelectedNode(
                selectedNodeId,
                placementX,
                placementY,
                RECOMMENDATION_LIMIT);
        cachedRecommendations = NodeRecommendations.get().recommend(graph, cachedRecommendationContext);
        cachedRecommendationNodeId = selectedNodeId;
    }

    /** True when the node has at least one non-exec output that can drive downstream suggestions. */
    private static boolean hasConnectableOutput(INode node) {
        List<IPort> outputs = node.getOutputPorts();
        if (outputs == null || outputs.isEmpty()) {
            return false;
        }
        for (IPort port : outputs) {
            if (port != null && port.getDataType() != null && port.getDataType() != NodeDataType.EXEC) {
                return true;
            }
        }
        return false;
    }

    private void clearCache() {
        cachedRecommendationNodeId = null;
        cachedRecommendationContext = null;
        cachedRecommendations = List.of();
    }

    private void drawNodeIcon(ImDrawList drawList, ImVec2 topLeft, NodeInfo node, String nodeCategory, float iconSize) {
        int textureId = iconManager.loadNodeIcon(node.getId(), nodeCategory, node.getIcon());
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

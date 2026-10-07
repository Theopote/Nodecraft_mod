package com.nodecraft.gui.recommendation;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.editor.impl.ICanvasEditor;
import com.nodecraft.gui.layout.ImGuiChildScope;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.graph.NodeGraph;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared ImGui popup for context-aware node recommendations.
 */
public final class NodeRecommendationPopupRenderer {

    private final ICanvasEditor editor;
    private final NodeRecommendationService recommendationService;

    private boolean open;
    private NodeRecommendationContext context;
    private List<NodeRecommendation> recommendations = List.of();
    private float popupScreenX;
    private float popupScreenY;

    public NodeRecommendationPopupRenderer(ICanvasEditor editor, NodeRecommendationService recommendationService) {
        this.editor = editor;
        this.recommendationService = recommendationService;
    }

    public void open(NodeRecommendationContext context, float screenX, float screenY) {
        if (context == null || editor.getCurrentGraph() == null) {
            return;
        }
        recommendationService.initialize();
        this.context = context;
        this.recommendations = recommendationService.recommend(editor.getCurrentGraph(), context);
        this.popupScreenX = screenX;
        this.popupScreenY = screenY;
        this.open = true;
        ImGui.openPopup("NodeRecommendationPopup");
        NodeCraft.LOGGER.debug("Opened node recommendation popup with {} candidates", recommendations.size());
    }

    public void render() {
        if (!open || context == null) {
            return;
        }

        ImGui.setNextWindowPos(popupScreenX, popupScreenY);
        ImGui.setNextWindowSize(340, 420);

        if (ImGui.beginPopup("NodeRecommendationPopup", ImGuiWindowFlags.AlwaysAutoResize)) {
            try {
                renderHeader();
                ImGui.separator();
                renderRecommendationList();
                ImGui.separator();
                if (ImGui.button("Cancel", 320, 26)) {
                    close();
                }
            } finally {
                ImGui.endPopup();
            }
        }

        if (!ImGui.isPopupOpen("NodeRecommendationPopup")) {
            open = false;
        }
    }

    private void renderHeader() {
        String directionLabel = context.direction() == RecommendationDirection.DOWNSTREAM
                ? "Suggested Connections"
                : "Suggested Upstream";
        ImGui.text(directionLabel);
        if (context.sourceDataType() != null) {
            ImGui.textDisabled("Type: " + context.sourceDataType().getDisplayName());
        }
    }

    private void renderRecommendationList() {
        try (ImGuiChildScope scope = new ImGuiChildScope("RecommendationList", 320, 300, true, 0)) {
        if (!scope.isOpen()) {
            return;
        }
        if (recommendations.isEmpty()) {
            ImGui.textDisabled("No matching suggestions");
            return;
        }

        INode sourceNode = null;
        NodeGraph graph = editor.getCurrentGraph();
        if (graph != null && context.sourceNodeId() != null) {
            sourceNode = graph.getNode(context.sourceNodeId());
        }

        List<NodeRecommendation> visible = new ArrayList<>(recommendations);
        for (NodeRecommendation recommendation : visible) {
            String label = RecommendationUiPresentation.formatLabel(recommendation);
            if (ImGui.selectable(label + "##rec_" + recommendation.nodeId())) {
                applyRecommendation(recommendation);
                close();
            }
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                ImGui.text(RecommendationUiPresentation.formatTooltip(recommendation, sourceNode));
                ImGui.textDisabled("ID: " + recommendation.nodeId());
                ImGui.endTooltip();
            }
        }
        }
    }

    private void applyRecommendation(NodeRecommendation recommendation) {
        NodeGraph graph = editor.getCurrentGraph();
        if (graph == null || context == null) {
            return;
        }
        NodeRecommendationApplyResult result = recommendationService.apply(editor, graph, context, recommendation);
        if (!result.success()) {
            NodeCraft.LOGGER.warn("Failed to apply node recommendation: {}", result.message());
            return;
        }
        NodeCraft.LOGGER.info("Applied node recommendation: {}", result.message());
    }

    public void close() {
        open = false;
        context = null;
        recommendations = List.of();
        ImGui.closeCurrentPopup();
    }

    public boolean isOpen() {
        return open;
    }
}

package com.nodecraft.gui.recommendation;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.editor.impl.ICanvasEditor;
import com.nodecraft.nodesystem.graph.NodeGraph;
import imgui.ImGui;
import imgui.ImVec2;
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
        if (!ImGui.beginChild("RecommendationList", 320, 300, true)) {
            return;
        }

        try {
        if (recommendations.isEmpty()) {
            ImGui.textDisabled("No matching suggestions");
            return;
        }

        List<NodeRecommendation> visible = new ArrayList<>(recommendations);
        for (NodeRecommendation recommendation : visible) {
            String planMark = switch (recommendation.connectionPlan()) {
                case VIA_CONVERSION -> "↻ ";
                case MANUAL -> "· ";
                case DIRECT -> "→ ";
            };
            String label = planMark + recommendation.displayName();
            if (ImGui.selectable(label + "##rec_" + recommendation.nodeId())) {
                applyRecommendation(recommendation);
                close();
            }
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                ImGui.text("ID: " + recommendation.nodeId());
                ImGui.text(recommendation.reason());
                ImGui.textDisabled("Plan: " + recommendation.connectionPlan().name());
                if (recommendation.connectPortId() != null) {
                    ImGui.text("Port: " + recommendation.connectPortId());
                }
                ImGui.endTooltip();
            }
        }
        } finally {
            ImGui.endChild();
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

package com.nodecraft.gui.components;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * AI assistant state holder component.
 *
 * This component owns core AI panel state so UI/container components can stay thinner
 * and communicate through events instead of direct field coupling.
 */
public class AiAssistantComponent implements EditorComponent {

    private static final String COMPONENT_ID = "ai_assistant";

    private boolean visible = true;
    private UUID selectedNodeId = null;

    private final List<AiChatMessage> chatMessages = new ArrayList<>();
    private AiGraphPlan pendingPlan = null;

    public record AiChatMessage(String role, String content, long timestampMs) {
    }

    public record AiPlanNode(String ref, String typeId, float offsetX, float offsetY, Object nodeState) {
    }

    public record AiPlanConnection(String sourceRef, String sourcePortId, String targetRef, String targetPortId) {
    }

    public record AiGraphPlan(String summary, List<AiPlanNode> nodes, List<AiPlanConnection> connections, List<String> validationErrors) {
        boolean isValid() {
            return validationErrors == null || validationErrors.isEmpty();
        }
    }

    @Override
    public void render(float x, float y, float width, float height, float paddingX, float paddingY) {
        // Rendered by the host container (PropertyPanelComponent) for now.
    }

    @Override
    public void init() {
        // No-op.
    }

    @Override
    public void cleanup() {
        chatMessages.clear();
        pendingPlan = null;
        selectedNodeId = null;
    }

    @Override
    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public String getComponentId() {
        return COMPONENT_ID;
    }

    @Override
    public boolean handleEvent(String eventType, Object data) {
        switch (eventType) {
            case "nodeSelected" -> {
                if (data instanceof UUID nodeId) {
                    selectedNodeId = nodeId;
                    return true;
                }
                if (data == null) {
                    selectedNodeId = null;
                    return true;
                }
            }
            case "nodeSelectionCleared", "graphChanged" -> {
                selectedNodeId = null;
                return true;
            }
            default -> {
                // no-op
            }
        }
        return false;
    }

    public UUID getSelectedNodeId() {
        return selectedNodeId;
    }

    public List<AiChatMessage> getChatMessages() {
        return chatMessages;
    }

    public void setChatMessages(List<AiChatMessage> messages) {
        chatMessages.clear();
        if (messages != null && !messages.isEmpty()) {
            chatMessages.addAll(messages);
        }
    }

    public AiGraphPlan getPendingPlan() {
        return pendingPlan;
    }

    public void setPendingPlan(AiGraphPlan pendingPlan) {
        this.pendingPlan = pendingPlan;
    }
}

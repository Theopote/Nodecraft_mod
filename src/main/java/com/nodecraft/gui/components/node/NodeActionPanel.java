package com.nodecraft.gui.components.node;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.ResettableNode;
import com.nodecraft.nodesystem.graph.NodeGraph;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Property-panel action chrome orchestrator: node-specific providers plus
 * universal Reset / Delete controls.
 */
public final class NodeActionPanel {

    private NodeActionPanel() {
    }

    public static void renderAssistNodeControls(INode selectedNode, Supplier<NodeGraph> graphSupplier) {
        NodeActionProviderRegistry.renderAssistChrome(selectedNode, graphSupplier);
    }

    public static void renderActionButtons(
            INode selectedNode,
            Supplier<NodeGraph> graphSupplier,
            Runnable clearCurrentNodeTempValues,
            Consumer<INode> setSelectedNode,
            Consumer<INode> onResetSuccess,
            ResetFeedback resetFeedback
    ) {
        ImGui.separator();

        NodeActionProviderRegistry.renderActionChrome(selectedNode, graphSupplier);

        if (ResettableNode.supportsReset(selectedNode)) {
            ResettableNode resettable = (ResettableNode) selectedNode;
            if (ImGui.button("Reset Properties")) {
                try {
                    resettable.resetProperties();
                    clearCurrentNodeTempValues.run();
                    onResetSuccess.accept(selectedNode);
                    resetFeedback.setSuccess("Properties reset.");
                    NodeCraft.LOGGER.info("Reset node properties for {}", selectedNode.getDisplayName());
                } catch (Exception e) {
                    resetFeedback.setFailure("Reset failed. Parameters unchanged.");
                    NodeCraft.LOGGER.error(
                            "Failed to reset node properties for {}: {}",
                            selectedNode.getDisplayName(),
                            e.getMessage(),
                            e);
                }
            }
            resetFeedback.renderIfPresent();
            ImGui.sameLine();
        }

        ImGui.pushStyleColor(ImGuiCol.Button, 0.8f, 0.2f, 0.2f, 0.6f);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.9f, 0.3f, 0.3f, 0.8f);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, 1.0f, 0.4f, 0.4f, 1.0f);
        if (ImGui.button("Delete Node")) {
            NodeGraph graph = graphSupplier.get();
            if (graph != null) {
                boolean success = graph.removeNode(selectedNode.getId());
                if (success) {
                    NodeCraft.LOGGER.info("Removed node from graph: {}", selectedNode.getDisplayName());
                    setSelectedNode.accept(null);
                } else {
                    NodeCraft.LOGGER.warn("Failed to remove node from graph: {}", selectedNode.getDisplayName());
                }
            }
        }
        ImGui.popStyleColor(3);
    }

    /**
     * One-frame reset status shown beside the Reset button.
     */
    public static final class ResetFeedback {
        private String message;
        private boolean success;

        public void setSuccess(String message) {
            this.message = message;
            this.success = true;
        }

        public void setFailure(String message) {
            this.message = message;
            this.success = false;
        }

        public void clear() {
            message = null;
        }

        public void renderIfPresent() {
            if (message == null) {
                return;
            }
            ImGui.sameLine();
            if (success) {
                ImGui.textColored(0.4f, 0.9f, 0.4f, 1.0f, message);
            } else {
                ImGui.textColored(1.0f, 0.4f, 0.4f, 1.0f, message);
            }
        }
    }
}

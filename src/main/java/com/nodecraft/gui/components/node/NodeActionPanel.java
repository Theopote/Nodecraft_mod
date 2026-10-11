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

    /**
     * Common actions shown near the node title (reset when supported).
     */
    public static void renderQuickActions(
            INode selectedNode,
            Runnable clearCurrentNodeTempValues,
            Consumer<ResettableNode> onReset,
            ResetFeedback resetFeedback
    ) {
        if (!ResettableNode.supportsReset(selectedNode)) {
            return;
        }
        ResettableNode resettable = (ResettableNode) selectedNode;
        if (ImGui.smallButton("Reset")) {
            try {
                onReset.accept(resettable);
                clearCurrentNodeTempValues.run();
                resetFeedback.setSuccess("Properties reset.");
                NodeCraft.LOGGER.info("Reset node properties for {}", selectedNode.getDisplayName());
            } catch (ResetRollbackException e) {
                resetFeedback.setFailure("Reset failed. Previous values restored.");
                NodeCraft.LOGGER.error(
                        "Failed to reset node properties for {}: {}",
                        selectedNode.getDisplayName(),
                        e.getMessage(),
                        e);
            } catch (ResetInconsistentException e) {
                resetFeedback.setFailure("Reset failed. Parameters may be inconsistent.");
                NodeCraft.LOGGER.error(
                        "Reset rollback failed for {}: {}",
                        selectedNode.getDisplayName(),
                        e.getMessage(),
                        e);
            } catch (Exception e) {
                resetFeedback.setFailure("Reset failed. Parameters may be inconsistent.");
                NodeCraft.LOGGER.error(
                        "Failed to reset node properties for {}: {}",
                        selectedNode.getDisplayName(),
                        e.getMessage(),
                        e);
            }
        }
        resetFeedback.renderIfPresent();
    }

    public static final class ResetRollbackException extends RuntimeException {
        public ResetRollbackException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static final class ResetInconsistentException extends RuntimeException {
        public ResetInconsistentException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static void renderProviderActions(INode selectedNode, Supplier<NodeGraph> graphSupplier) {
        NodeActionProviderRegistry.renderActionChrome(selectedNode, graphSupplier);
    }

    /**
     * Destructive actions pinned to the bottom of the property panel.
     */
    public static void renderDangerActions(
            INode selectedNode,
            Supplier<NodeGraph> graphSupplier,
            Consumer<INode> setSelectedNode
    ) {
        ImGui.separator();
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
     * @deprecated Use {@link #renderQuickActions}, {@link #renderProviderActions}, and {@link #renderDangerActions}.
     */
    @Deprecated
    public static void renderActionButtons(
            INode selectedNode,
            Supplier<NodeGraph> graphSupplier,
            Runnable clearCurrentNodeTempValues,
            Consumer<INode> setSelectedNode,
            Consumer<INode> onResetSuccess,
            ResetFeedback resetFeedback
    ) {
        ImGui.separator();
        renderProviderActions(selectedNode, graphSupplier);
        renderQuickActions(selectedNode, clearCurrentNodeTempValues, resettable -> {
            resettable.resetProperties();
            onResetSuccess.accept(selectedNode);
        }, resetFeedback);
        ImGui.sameLine();
        renderDangerActions(selectedNode, graphSupplier, setSelectedNode);
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

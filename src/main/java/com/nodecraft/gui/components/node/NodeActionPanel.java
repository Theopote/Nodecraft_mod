package com.nodecraft.gui.components.node;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.nodes.output.execute.ApplyChangesNode;
import com.nodecraft.nodesystem.nodes.utilities.assist.SignalForkNode;
import com.nodecraft.nodesystem.nodes.utilities.assist.SignalMergeNode;
import com.nodecraft.nodesystem.nodes.utilities.assist.TagRelayNode;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class NodeActionPanel {

    private NodeActionPanel() {
    }

    public static void renderAssistNodeControls(INode selectedNode, Supplier<NodeGraph> graphSupplier) {
        if (selectedNode instanceof SignalForkNode forkNode) {
            renderSignalForkControls(forkNode, graphSupplier);
            ImGui.separator();
        }

        if (selectedNode instanceof SignalMergeNode mergeNode) {
            renderSignalMergeControls(mergeNode, graphSupplier);
            ImGui.separator();
        }

        if (selectedNode instanceof TagRelayNode) {
            renderTagRelayRuleHint();
            ImGui.separator();
        }
    }

    public static void renderActionButtons(
            INode selectedNode,
            Supplier<NodeGraph> graphSupplier,
            Runnable clearCurrentNodeTempValues,
            Consumer<INode> setSelectedNode
    ) {
        ImGui.separator();

        if (selectedNode instanceof ApplyChangesNode applyChangesNode) {
            renderApplyChangesControls(applyChangesNode);
            ImGui.separator();
        }

        if (ImGui.button("Reset Properties")) {
            clearCurrentNodeTempValues.run();
            if (selectedNode instanceof com.nodecraft.nodesystem.core.BaseNode) {
                try {
                    Method resetMethod = selectedNode.getClass().getMethod("resetProperties");
                    resetMethod.invoke(selectedNode);
                    NodeCraft.LOGGER.info("Reset node properties for {}", selectedNode.getDisplayName());
                } catch (NoSuchMethodException e) {
                    NodeCraft.LOGGER.debug("Node {} does not expose resetProperties()", selectedNode.getDisplayName());
                } catch (Exception e) {
                    NodeCraft.LOGGER.error("Failed to reset node properties for {}: {}", selectedNode.getDisplayName(), e.getMessage());
                }
            }
        }

        ImGui.sameLine();
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

    private static void renderApplyChangesControls(ApplyChangesNode applyChangesNode) {
        ImGui.text("Apply Changes");
        ImGui.textDisabled("Triggers the node to execute on the next auto-preview run.");

        boolean canApply = !applyChangesNode.isExecuting();
        if (!canApply) {
            ImGui.beginDisabled();
        }

        if (ImGui.button("Apply Changes")) {
            applyChangesNode.requestApply();
            NodeCraft.LOGGER.info("Triggered Apply Changes for {}", applyChangesNode.getDisplayName());
        }

        if (!canApply) {
            ImGui.endDisabled();
        }
    }

    private static void renderSignalForkControls(SignalForkNode forkNode, Supplier<NodeGraph> graphSupplier) {
        ImGui.text("Branch Controls");
        ImGui.textDisabled("Output branches: " + forkNode.getOutputBranchCount() + " (1-8)");

        boolean canRemove = forkNode.canDecreaseOutputBranch();
        if (!canRemove) {
            ImGui.beginDisabled();
        }
        if (ImGui.button("- Output")) {
            String removedPortId = forkNode.removeLastOutputBranch();
            if (removedPortId != null) {
                removeConnectionsForPort(graphSupplier, forkNode.getId(), removedPortId, false);
            }
        }
        if (!canRemove) {
            ImGui.endDisabled();
        }

        ImGui.sameLine();
        boolean canAdd = forkNode.canIncreaseOutputBranch();
        if (!canAdd) {
            ImGui.beginDisabled();
        }
        if (ImGui.button("+ Output")) {
            forkNode.addOutputBranch();
        }
        if (!canAdd) {
            ImGui.endDisabled();
        }
    }

    private static void renderSignalMergeControls(SignalMergeNode mergeNode, Supplier<NodeGraph> graphSupplier) {
        ImGui.text("Branch Controls");
        ImGui.textDisabled("Input branches: " + mergeNode.getInputBranchCount() + " (2-8)");

        boolean canRemove = mergeNode.canDecreaseInputBranch();
        if (!canRemove) {
            ImGui.beginDisabled();
        }
        if (ImGui.button("- Input")) {
            String removedPortId = mergeNode.removeLastInputBranch();
            if (removedPortId != null) {
                removeConnectionsForPort(graphSupplier, mergeNode.getId(), removedPortId, true);
            }
        }
        if (!canRemove) {
            ImGui.endDisabled();
        }

        ImGui.sameLine();
        boolean canAdd = mergeNode.canIncreaseInputBranch();
        if (!canAdd) {
            ImGui.beginDisabled();
        }
        if (ImGui.button("+ Input")) {
            mergeNode.addInputBranch();
        }
        if (!canAdd) {
            ImGui.endDisabled();
        }
    }

    private static void renderTagRelayRuleHint() {
        ImGui.text("Tag Relay Rules");
        ImGui.textWrapped("Color supports #RRGGBB/#AARRGGBB, named tokens (danger/warn/io/math/flow/debug), or auto by tag keywords.");
        ImGui.textDisabled("Canvas shows short tag label with mapped color.");
    }

    private static void removeConnectionsForPort(Supplier<NodeGraph> graphSupplier, UUID nodeId, String portId, boolean inputPort) {
        NodeGraph graph = graphSupplier.get();
        if (graph == null || portId == null) {
            return;
        }

        for (NodeGraph.Connection connection : graph.getConnections()) {
            boolean matched;
            if (inputPort) {
                matched = connection.targetNode.getId().equals(nodeId)
                        && connection.targetPort.getId().equals(portId);
            } else {
                matched = connection.sourceNode.getId().equals(nodeId)
                        && connection.sourcePort.getId().equals(portId);
            }

            if (matched) {
                graph.removeConnection(connection);
            }
        }
    }
}

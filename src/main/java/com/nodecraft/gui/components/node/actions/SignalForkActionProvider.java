package com.nodecraft.gui.components.node.actions;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.components.node.NodeActionGraphSupport;
import com.nodecraft.gui.components.node.NodeActionProvider;
import com.nodecraft.gui.editor.GraphStructureEditSupport;
import com.nodecraft.gui.editor.StructureEditRollbackException;
import com.nodecraft.gui.editor.impl.ImGuiNodeEditor;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.nodes.utilities.assist.SignalForkNode;
import imgui.ImGui;
import java.util.function.Supplier;

/** Branch add/remove chrome for {@link SignalForkNode}. */
public final class SignalForkActionProvider implements NodeActionProvider {

    public static final SignalForkActionProvider INSTANCE = new SignalForkActionProvider();

    private SignalForkActionProvider() {
    }

    @Override
    public boolean render(INode node, Supplier<NodeGraph> graphSupplier) {
        if (!(node instanceof SignalForkNode forkNode)) {
            return false;
        }

        ImGui.text("Branch Controls");
        ImGui.textDisabled("Output branches: " + forkNode.getOutputBranchCount() + " (1-8)");

        boolean canRemove = forkNode.canDecreaseOutputBranch();
        if (!canRemove) {
            ImGui.beginDisabled();
        }
        if (ImGui.button("- Output")) {
            runStructureEdit("Remove Fork Branch", () -> {
                String removedPortId = forkNode.removeLastOutputBranch();
                if (removedPortId != null) {
                    NodeActionGraphSupport.removeConnectionsForPort(
                            graphSupplier, forkNode.getId(), removedPortId, false);
                }
                return true;
            });
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
            runStructureEdit("Add Fork Branch", () -> {
                forkNode.addOutputBranch();
                return true;
            });
        }
        if (!canAdd) {
            ImGui.endDisabled();
        }
        return true;
    }

    private static void runStructureEdit(String label, GraphStructureEditSupport.Mutation mutation) {
        try {
            ImGuiNodeEditor editor = ImGuiNodeEditor.getInstance();
            GraphStructureEditSupport.run(editor.structureEditHost(), label, mutation);
        } catch (StructureEditRollbackException e) {
            NodeCraft.LOGGER.error("Fork structure edit '{}' rollback failed: {}", label, e.getMessage(), e);
        } catch (Exception e) {
            // Editor unavailable (tests / headless): mutate without history.
            mutation.run();
        }
    }
}

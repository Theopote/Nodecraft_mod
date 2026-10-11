package com.nodecraft.gui.components.node.actions;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.components.node.NodeActionGraphSupport;
import com.nodecraft.gui.components.node.NodeActionProvider;
import com.nodecraft.gui.editor.GraphStructureEditSupport;
import com.nodecraft.gui.editor.StructureEditRollbackException;
import com.nodecraft.gui.editor.impl.ImGuiNodeEditor;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.nodes.utilities.assist.CoalesceNode;
import imgui.ImGui;
import java.util.function.Supplier;

/** Branch add/remove chrome for {@link CoalesceNode} ({@code utilities.assist.coalesce}). */
public final class CoalesceActionProvider implements NodeActionProvider {

    public static final CoalesceActionProvider INSTANCE = new CoalesceActionProvider();

    private CoalesceActionProvider() {
    }

    @Override
    public boolean render(INode node, Supplier<NodeGraph> graphSupplier) {
        if (!(node instanceof CoalesceNode coalesceNode)) {
            return false;
        }

        ImGui.text("Branch Controls");
        ImGui.textDisabled("Input branches: " + coalesceNode.getInputBranchCount() + " (2-8)");

        boolean canRemove = coalesceNode.canDecreaseInputBranch();
        if (!canRemove) {
            ImGui.beginDisabled();
        }
        if (ImGui.button("- Input")) {
            runStructureEdit("Remove Coalesce Branch", () -> {
                String removedPortId = coalesceNode.removeLastInputBranch();
                if (removedPortId != null) {
                    NodeActionGraphSupport.removeConnectionsForPort(
                            graphSupplier, coalesceNode.getId(), removedPortId, true);
                }
                return true;
            });
        }
        if (!canRemove) {
            ImGui.endDisabled();
        }

        ImGui.sameLine();
        boolean canAdd = coalesceNode.canIncreaseInputBranch();
        if (!canAdd) {
            ImGui.beginDisabled();
        }
        if (ImGui.button("+ Input")) {
            runStructureEdit("Add Coalesce Branch", () -> {
                coalesceNode.addInputBranch();
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
            NodeCraft.LOGGER.error("Coalesce structure edit '{}' rollback failed: {}", label, e.getMessage(), e);
        } catch (Exception e) {
            mutation.run();
        }
    }
}

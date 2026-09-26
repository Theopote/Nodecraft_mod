package com.nodecraft.gui.components.node.actions;

import com.nodecraft.gui.components.node.NodeActionGraphSupport;
import com.nodecraft.gui.components.node.NodeActionProvider;
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
            String removedPortId = coalesceNode.removeLastInputBranch();
            if (removedPortId != null) {
                NodeActionGraphSupport.removeConnectionsForPort(
                        graphSupplier, coalesceNode.getId(), removedPortId, true);
            }
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
            coalesceNode.addInputBranch();
        }
        if (!canAdd) {
            ImGui.endDisabled();
        }
        return true;
    }
}

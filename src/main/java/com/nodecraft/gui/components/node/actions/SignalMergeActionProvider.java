package com.nodecraft.gui.components.node.actions;

import com.nodecraft.gui.components.node.NodeActionGraphSupport;
import com.nodecraft.gui.components.node.NodeActionProvider;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.nodes.utilities.assist.SignalMergeNode;
import imgui.ImGui;
import java.util.function.Supplier;

/** Branch add/remove chrome for {@link SignalMergeNode}. */
public final class SignalMergeActionProvider implements NodeActionProvider {

    public static final SignalMergeActionProvider INSTANCE = new SignalMergeActionProvider();

    private SignalMergeActionProvider() {
    }

    @Override
    public boolean render(INode node, Supplier<NodeGraph> graphSupplier) {
        if (!(node instanceof SignalMergeNode mergeNode)) {
            return false;
        }

        ImGui.text("Branch Controls");
        ImGui.textDisabled("Input branches: " + mergeNode.getInputBranchCount() + " (2-8)");

        boolean canRemove = mergeNode.canDecreaseInputBranch();
        if (!canRemove) {
            ImGui.beginDisabled();
        }
        if (ImGui.button("- Input")) {
            String removedPortId = mergeNode.removeLastInputBranch();
            if (removedPortId != null) {
                NodeActionGraphSupport.removeConnectionsForPort(
                        graphSupplier, mergeNode.getId(), removedPortId, true);
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
        return true;
    }
}

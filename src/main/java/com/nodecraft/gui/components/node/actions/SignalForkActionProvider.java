package com.nodecraft.gui.components.node.actions;

import com.nodecraft.gui.components.node.NodeActionGraphSupport;
import com.nodecraft.gui.components.node.NodeActionProvider;
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
            String removedPortId = forkNode.removeLastOutputBranch();
            if (removedPortId != null) {
                NodeActionGraphSupport.removeConnectionsForPort(
                        graphSupplier, forkNode.getId(), removedPortId, false);
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
        return true;
    }
}

package com.nodecraft.gui.components.node.actions;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.components.node.NodeActionProvider;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.nodes.output.execute.ApplyChangesNode;
import imgui.ImGui;
import java.util.function.Supplier;

/** Apply-trigger chrome for {@link ApplyChangesNode}. */
public final class ApplyChangesActionProvider implements NodeActionProvider {

    public static final ApplyChangesActionProvider INSTANCE = new ApplyChangesActionProvider();

    private ApplyChangesActionProvider() {
    }

    @Override
    public boolean render(INode node, Supplier<NodeGraph> graphSupplier) {
        if (!(node instanceof ApplyChangesNode applyChangesNode)) {
            return false;
        }

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
        return true;
    }
}

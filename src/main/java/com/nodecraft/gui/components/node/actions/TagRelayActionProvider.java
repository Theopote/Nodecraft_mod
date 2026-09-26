package com.nodecraft.gui.components.node.actions;

import com.nodecraft.gui.components.node.NodeActionProvider;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.nodes.utilities.assist.RelayNode;
import imgui.ImGui;
import java.util.function.Supplier;

/** Rule hint chrome for {@link RelayNode}. */
public final class TagRelayActionProvider implements NodeActionProvider {

    public static final TagRelayActionProvider INSTANCE = new TagRelayActionProvider();

    private TagRelayActionProvider() {
    }

    @Override
    public boolean render(INode node, Supplier<NodeGraph> graphSupplier) {
        if (!(node instanceof RelayNode)) {
            return false;
        }

        ImGui.text("Relay Rules");
        ImGui.textWrapped(
                "Color supports #RRGGBB/#AARRGGBB, named tokens (danger/warn/io/math/flow/debug), or auto by tag keywords. Empty tag uses compact relay look.");
        ImGui.textDisabled("Canvas shows short tag label with mapped color when tag is set.");
        return true;
    }
}

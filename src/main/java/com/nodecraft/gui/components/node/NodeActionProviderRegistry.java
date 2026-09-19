package com.nodecraft.gui.components.node;

import com.nodecraft.gui.components.node.actions.ApplyChangesActionProvider;
import com.nodecraft.gui.components.node.actions.SignalForkActionProvider;
import com.nodecraft.gui.components.node.actions.SignalMergeActionProvider;
import com.nodecraft.gui.components.node.actions.TagRelayActionProvider;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.graph.NodeGraph;
import imgui.ImGui;
import java.util.List;
import java.util.function.Supplier;

/**
 * Owns registration of node-specific action chrome providers.
 * <p>
 * See {@code docs/architecture/property-panel-breakup.md} (Phase 4).
 */
public final class NodeActionProviderRegistry {

    private static final List<NodeActionProvider> ASSIST_PROVIDERS = List.of(
            SignalForkActionProvider.INSTANCE,
            SignalMergeActionProvider.INSTANCE,
            TagRelayActionProvider.INSTANCE
    );

    private static final List<NodeActionProvider> ACTION_PROVIDERS = List.of(
            ApplyChangesActionProvider.INSTANCE
    );

    private NodeActionProviderRegistry() {
    }

    /** Chrome drawn above the property list (assist branch controls, rule hints). */
    public static void renderAssistChrome(INode node, Supplier<NodeGraph> graphSupplier) {
        renderAll(ASSIST_PROVIDERS, node, graphSupplier);
    }

    /** Node-specific chrome drawn in the action-button strip (before Reset/Delete). */
    public static void renderActionChrome(INode node, Supplier<NodeGraph> graphSupplier) {
        renderAll(ACTION_PROVIDERS, node, graphSupplier);
    }

    static List<NodeActionProvider> assistProviders() {
        return ASSIST_PROVIDERS;
    }

    static List<NodeActionProvider> actionProviders() {
        return ACTION_PROVIDERS;
    }

    private static void renderAll(
            List<NodeActionProvider> providers,
            INode node,
            Supplier<NodeGraph> graphSupplier
    ) {
        for (NodeActionProvider provider : providers) {
            if (provider.render(node, graphSupplier)) {
                ImGui.separator();
            }
        }
    }
}

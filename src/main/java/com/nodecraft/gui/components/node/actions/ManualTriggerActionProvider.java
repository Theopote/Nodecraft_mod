package com.nodecraft.gui.components.node.actions;

import com.nodecraft.gui.components.customui.CustomUIEditorContext;
import com.nodecraft.gui.components.node.NodeActionProvider;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.nodes.flow.control.ManualTriggerNode;
import imgui.ImGui;
import java.util.function.Supplier;

/** Trigger chrome for {@link ManualTriggerNode}: pulse + MANUAL graph run. */
public final class ManualTriggerActionProvider implements NodeActionProvider {

    public static final ManualTriggerActionProvider INSTANCE = new ManualTriggerActionProvider();

    private ManualTriggerActionProvider() {
    }

    @Override
    public boolean render(INode node, Supplier<NodeGraph> graphSupplier) {
        if (!(node instanceof ManualTriggerNode trigger)) {
            return false;
        }

        ImGui.text("Manual Trigger");
        ImGui.textDisabled("Fires EXEC, then runs the graph (not auto-preview).");

        if (ImGui.button("Trigger")) {
            NodeGraph graph = graphSupplier == null ? null : graphSupplier.get();
            CustomUIEditorContext.bind(graph);
            try {
                trigger.fireTriggerFromUi();
            } finally {
                CustomUIEditorContext.unbind();
            }
        }

        ManualTriggerNode.SubmitFeedback feedback = trigger.getLastFeedback();
        if (feedback == ManualTriggerNode.SubmitFeedback.FAILED) {
            ImGui.textColored(0.9f, 0.35f, 0.3f, 1.0f, "Failed: " + trigger.getLastFailureReason());
        } else if (trigger.isPulsePending()) {
            ImGui.text("Pending");
        } else if (feedback == ManualTriggerNode.SubmitFeedback.SUBMITTED) {
            ImGui.textColored(0.35f, 0.75f, 0.4f, 1.0f, "Submitted");
        }
        return true;
    }
}

package com.nodecraft.gui.components.node.actions;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.components.node.NodeActionProvider;
import com.nodecraft.gui.editor.GraphManualExecution;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.execution.runtime.ExecutionSession;
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
            trigger.requestPulse();
            NodeGraph graph = graphSupplier == null ? null : graphSupplier.get();
            ExecutionSession session = GraphManualExecution.submit(graph);
            if (session != null) {
                NodeCraft.LOGGER.info("Manual Trigger submitted graph run session={}", session.sessionId());
            }
        }
        return true;
    }
}

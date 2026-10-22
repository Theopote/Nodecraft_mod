package com.nodecraft.nodesystem.nodes.flow.control;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.components.customui.CustomUIEditorContext;
import com.nodecraft.gui.components.customui.CustomUIMetricsSafe;
import com.nodecraft.gui.editor.GraphManualExecution;
import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.execution.runtime.ExecutionSession;
import com.nodecraft.nodesystem.graph.NodeGraph;
import imgui.ImGui;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One-shot EXEC source. Does not write the world; pair with Apply Changes / other WORLD_WRITE sinks.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "flow.control.manual_trigger",
    displayName = "Manual Trigger",
    description = "Fires a one-shot EXEC pulse when Trigger is pressed. Does not write the world.",
    category = "flow.control",
    order = 3
)
public class ManualTriggerNode extends BaseCustomUINode {

    private static final String OUTPUT_EXEC_ID = "output_exec";

    private final AtomicBoolean pulseRequested = new AtomicBoolean(false);

    public ManualTriggerNode() {
        super(UUID.randomUUID(), "flow.control.manual_trigger");
        addOutputPort(new BasePort(
            OUTPUT_EXEC_ID,
            "Exec",
            "One-shot EXEC pulse after Trigger is pressed",
            NodeDataType.EXEC,
            this
        ));
    }

    @Override
    public String getDescription() {
        return "Fires a one-shot EXEC pulse when Trigger is pressed. Does not write the world.";
    }

    public void requestPulse() {
        pulseRequested.set(true);
        markDirty();
    }

    public boolean isPulsePending() {
        return pulseRequested.get();
    }

    /**
     * Same semantics as the property-panel action: request one pulse and submit a MANUAL graph run.
     * Call only from a discrete button click — never from hold/drag or per-frame render.
     */
    public void fireTriggerFromUi() {
        requestPulse();
        NodeGraph graph = CustomUIEditorContext.currentGraph();
        ExecutionSession session = GraphManualExecution.submit(graph);
        if (session != null) {
            NodeCraft.LOGGER.info("Manual Trigger (canvas UI) submitted graph run session={}", session.sessionId());
        }
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        boolean fire = pulseRequested.getAndSet(false);
        outputValues.put(OUTPUT_EXEC_ID, fire ? Boolean.TRUE : Boolean.FALSE);
    }

    @Override
    protected float calculateUIHeight() {
        return CustomUIMetricsSafe.layoutFrameHeight()
            + CustomUIMetricsSafe.layoutItemSpacingY()
            + getMediumPadding();
    }

    @Override
    protected float calculateMinUIWidth() {
        return 140.0f + getContentMargin();
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        return layout(zoom, l -> {
            float edgeMargin = l.toPixels(getSmallPadding());
            float availableWidth = Math.max(72.0f, l.toPixelsExact(width) - edgeMargin * 2.0f);
            ImGui.setCursorPosX(ImGui.getCursorPosX() + edgeMargin);

            float buttonWidth = Math.min(availableWidth * 0.65f, l.toPixels(96.0f));
            if (ImGui.button("Trigger##manual_trigger", buttonWidth, ImGui.getFrameHeight())) {
                fireTriggerFromUi();
            }
            ImGui.sameLine();
            if (isPulsePending()) {
                ImGui.text("Pending");
            } else {
                ImGui.textDisabled("Idle");
            }
            return false;
        });
    }
}

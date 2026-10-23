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

    public enum SubmitFeedback {
        IDLE,
        PENDING,
        SUBMITTED,
        FAILED
    }

    private static final String OUTPUT_EXEC_ID = "output_exec";

    private final AtomicBoolean pulseRequested = new AtomicBoolean(false);
    private volatile SubmitFeedback lastFeedback = SubmitFeedback.IDLE;
    private volatile String lastFailureReason = "";

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

    public SubmitFeedback getLastFeedback() {
        return lastFeedback;
    }

    public String getLastFailureReason() {
        return lastFailureReason == null ? "" : lastFailureReason;
    }

    /**
     * Discrete UI click: request one pulse then submit a MANUAL graph run.
     * On submit failure the pending pulse is cleared so the node does not stick on Pending.
     * Does not call Apply/Bake; world writes only occur if the graph already contains WORLD_WRITE sinks.
     */
    public void fireTriggerFromUi() {
        requestPulse();
        lastFeedback = SubmitFeedback.PENDING;
        lastFailureReason = "";

        NodeGraph graph = CustomUIEditorContext.currentGraph();
        if (graph == null || graph.getNodes().isEmpty()) {
            clearPendingPulseOnFailure("No active graph");
            return;
        }

        ExecutionSession session = GraphManualExecution.submit(graph);
        if (session != null) {
            lastFeedback = SubmitFeedback.SUBMITTED;
            lastFailureReason = "";
            NodeCraft.LOGGER.info(
                "Manual Trigger (canvas UI) submitted graph run session={}", session.sessionId());
            return;
        }

        clearPendingPulseOnFailure("Graph run did not start (not in world, or scheduler rejected)");
    }

    private void clearPendingPulseOnFailure(String reason) {
        pulseRequested.set(false);
        lastFeedback = SubmitFeedback.FAILED;
        lastFailureReason = reason == null ? "" : reason;
        NodeCraft.LOGGER.warn("Manual Trigger submit failed: {}", lastFailureReason);
        markDirty();
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        boolean fire = pulseRequested.getAndSet(false);
        outputValues.put(OUTPUT_EXEC_ID, fire ? Boolean.TRUE : Boolean.FALSE);
        if (fire && lastFeedback == SubmitFeedback.SUBMITTED) {
            // Pulse consumed after a successful submit — return to idle for next click.
            lastFeedback = SubmitFeedback.IDLE;
        } else if (!fire && lastFeedback == SubmitFeedback.PENDING) {
            lastFeedback = SubmitFeedback.IDLE;
        }
    }

    @Override
    protected float calculateUIHeight() {
        // Trigger button + status share one row (sameLine), including Failed reason.
        return CustomUIMetricsSafe.layoutFrameHeight() + getSmallPadding();
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
            renderStatusLabel();
            return false;
        });
    }

    private void renderStatusLabel() {
        SubmitFeedback feedback = lastFeedback;
        if (isPulsePending() && feedback != SubmitFeedback.FAILED) {
            ImGui.text("Pending");
            return;
        }
        switch (feedback) {
            case SUBMITTED -> ImGui.textColored(0.35f, 0.75f, 0.4f, 1.0f, "Submitted");
            case FAILED -> {
                ImGui.textColored(0.9f, 0.35f, 0.3f, 1.0f, "Failed");
                if (!getLastFailureReason().isBlank()) {
                    ImGui.sameLine();
                    ImGui.textDisabled(truncate(getLastFailureReason(), 28));
                }
            }
            case PENDING -> ImGui.text("Pending");
            case IDLE -> ImGui.textDisabled("Idle");
        }
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, Math.max(1, max - 1)) + "…";
    }
}

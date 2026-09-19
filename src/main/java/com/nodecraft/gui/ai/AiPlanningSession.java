package com.nodecraft.gui.ai;

import com.nodecraft.gui.components.ai.AiAssistantComponent;
import com.nodecraft.gui.components.ai.AiAssistantComponent.AiGraphPlan;

/**
 * In-flight planning run state for one AI assistant controller.
 *
 * <p>Owns the last submitted prompt, pending-plan bookkeeping (via
 * {@link AiAssistantComponent}), repair/expansion attempt counters, world-context
 * snapshot used by remote retries, plan status text, and last apply/undo steps.
 * Does not own ImGui widgets or raw HTTP sockets.</p>
 */
public final class AiPlanningSession {

    private final AiAssistantComponent component;

    private String lastSubmittedPrompt = "";
    private String planStatusMessage = "";
    private int lastUndoStepCount = 0;
    private boolean lastApplyWasPatch = false;
    private int dslRepairAttempts = 0;
    private int graphExpansionAttempts = 0;
    private AiWorldContextSnapshot lastWorldContextSnapshot = null;

    public AiPlanningSession(AiAssistantComponent component) {
        this.component = component;
    }

    public AiAssistantComponent component() {
        return component;
    }

    public String lastSubmittedPrompt() {
        return lastSubmittedPrompt;
    }

    public void setLastSubmittedPrompt(String prompt) {
        lastSubmittedPrompt = prompt == null ? "" : prompt;
    }

    public String planStatusMessage() {
        return planStatusMessage;
    }

    public void setPlanStatusMessage(String message) {
        planStatusMessage = message == null ? "" : message;
    }

    public int lastUndoStepCount() {
        return lastUndoStepCount;
    }

    public boolean lastApplyWasPatch() {
        return lastApplyWasPatch;
    }

    public void recordApplyResult(int undoSteps, boolean patchMode) {
        lastUndoStepCount = Math.max(0, undoSteps);
        lastApplyWasPatch = patchMode;
    }

    public void clearApplyBookkeeping() {
        lastUndoStepCount = 0;
        lastApplyWasPatch = false;
    }

    public int dslRepairAttempts() {
        return dslRepairAttempts;
    }

    public void setDslRepairAttempts(int attempts) {
        dslRepairAttempts = Math.max(0, attempts);
    }

    public int graphExpansionAttempts() {
        return graphExpansionAttempts;
    }

    public void setGraphExpansionAttempts(int attempts) {
        graphExpansionAttempts = Math.max(0, attempts);
    }

    public AiWorldContextSnapshot lastWorldContextSnapshot() {
        return lastWorldContextSnapshot;
    }

    public void setLastWorldContextSnapshot(AiWorldContextSnapshot snapshot) {
        lastWorldContextSnapshot = snapshot;
    }

    public AiGraphPlan pendingPlan() {
        return component.getPendingPlan();
    }

    public void setPendingPlan(AiGraphPlan plan) {
        component.setPendingPlan(plan);
    }

    public boolean isRemotePlannerBusy() {
        return component.isRemotePlannerBusy();
    }

    /** Reset counters for a fresh remote submit (not a repair/expansion retry). */
    public void beginFreshRemoteRequest() {
        dslRepairAttempts = 0;
        graphExpansionAttempts = 0;
    }

    /** Clear in-flight planning fields when the user clears chat. */
    public void clearConversationPlanningState() {
        beginFreshRemoteRequest();
        lastWorldContextSnapshot = null;
        planStatusMessage = "Chat cleared.";
    }

    /** Clear apply bookkeeping + plan status on controller cleanup. */
    public void resetForCleanup() {
        clearApplyBookkeeping();
        planStatusMessage = "";
        lastSubmittedPrompt = "";
        beginFreshRemoteRequest();
        lastWorldContextSnapshot = null;
    }
}

package com.nodecraft.gui.ai;

import com.nodecraft.gui.components.ai.AiAssistantComponent;
import com.nodecraft.gui.components.ai.AiAssistantComponent.AiGraphPlan;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiPlanningSessionTest {

    @Test
    void beginFreshRemoteRequestClearsRetryCounters() {
        AiPlanningSession session = new AiPlanningSession(new AiAssistantComponent());
        session.setDslRepairAttempts(2);
        session.setGraphExpansionAttempts(1);

        session.beginFreshRemoteRequest();

        assertEquals(0, session.dslRepairAttempts());
        assertEquals(0, session.graphExpansionAttempts());
    }

    @Test
    void recordAndClearApplyBookkeeping() {
        AiPlanningSession session = new AiPlanningSession(new AiAssistantComponent());
        session.recordApplyResult(3, true);

        assertEquals(3, session.lastUndoStepCount());
        assertTrue(session.lastApplyWasPatch());

        session.clearApplyBookkeeping();
        assertEquals(0, session.lastUndoStepCount());
        assertFalse(session.lastApplyWasPatch());
    }

    @Test
    void pendingPlanDelegatesToComponent() {
        AiAssistantComponent component = new AiAssistantComponent();
        AiPlanningSession session = new AiPlanningSession(component);
        AiGraphPlan plan = new AiGraphPlan("summary", List.of(), List.of(), List.of());

        session.setPendingPlan(plan);
        assertEquals(plan, session.pendingPlan());
        assertEquals(plan, component.getPendingPlan());
    }

    @Test
    void clearConversationPlanningStateResetsInFlightFields() {
        AiPlanningSession session = new AiPlanningSession(new AiAssistantComponent());
        session.setLastSubmittedPrompt("hello");
        session.setDslRepairAttempts(1);
        session.setGraphExpansionAttempts(2);
        session.setLastWorldContextSnapshot(null);
        session.setPlanStatusMessage("busy");

        session.clearConversationPlanningState();

        assertEquals(0, session.dslRepairAttempts());
        assertEquals(0, session.graphExpansionAttempts());
        assertNull(session.lastWorldContextSnapshot());
        assertEquals("Chat cleared.", session.planStatusMessage());
        assertEquals("hello", session.lastSubmittedPrompt()); // prompt history kept for retry semantics
    }
}

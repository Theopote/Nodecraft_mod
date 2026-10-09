package com.nodecraft.gui.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiPlannerServiceTest {

    @Test
    void planLocalReturnsDslJsonWithLocalSource() {
        AiPlannerService planner = new AiPlannerService();
        AiPlannerService.LocalPlanPayload payload = planner.planLocal("sphere");

        assertNotNull(payload);
        assertFalse(payload.abstained(), payload.message());
        assertEquals("local-template", payload.source());
        assertNotNull(payload.dslJson());
        assertFalse(payload.dslJson().isBlank());
        assertTrue(payload.dslJson().contains("nodes") || payload.dslJson().contains("{"),
                payload.dslJson());
    }

    @Test
    void planLocalAbstainsForUnknownPrompt() {
        AiPlannerService planner = new AiPlannerService();
        AiPlannerService.LocalPlanPayload payload = planner.planLocal("帮我做一个入口空间");

        assertTrue(payload.abstained());
        assertEquals(AiMockPlanService.ABSTAIN_CODE, payload.abstainCode());
        assertNotNull(payload.message());
        assertTrue(payload.dslJson() == null || payload.dslJson().isBlank());
    }

    @Test
    void remoteAttemptLimitsArePositive() {
        AiPlannerService planner = new AiPlannerService();
        assertTrue(planner.maxDslRepairAttempts() > 0);
        assertTrue(planner.maxGraphExpansionAttempts() > 0);
    }

    @Test
    void sanitizeUserPromptForSnapshotTruncatesSafely() {
        AiPlannerService planner = new AiPlannerService();
        String sanitized = planner.sanitizeUserPromptForSnapshot("hello\nworld");
        assertNotNull(sanitized);
        assertFalse(sanitized.contains("\n"));
    }
}

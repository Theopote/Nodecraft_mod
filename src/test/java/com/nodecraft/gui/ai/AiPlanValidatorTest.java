package com.nodecraft.gui.ai;

import com.nodecraft.gui.components.ai.AiAssistantComponent.AiGraphPlan;
import com.nodecraft.gui.components.ai.AiAssistantComponent.AiPlanNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiPlanValidatorTest {

    private final AiPlanValidator validator = new AiPlanValidator();

    @Test
    void checkBeforeApplyRejectsNullAndInvalidPlans() {
        assertFalse(validator.checkBeforeApply(null).allowed());
        assertEquals("No plan available.", validator.checkBeforeApply(null).rejectionMessage());

        AiGraphPlan invalid = new AiGraphPlan(
                "bad",
                List.of(),
                List.of(),
                List.of("unknown type")
        );
        AiPlanValidator.GateResult gate = validator.checkBeforeApply(invalid);
        assertFalse(gate.allowed());
        assertTrue(gate.rejectionMessage().contains("validation errors"));
    }

    @Test
    void checkBeforeApplyAllowsValidPlan() {
        AiGraphPlan valid = new AiGraphPlan(
                "ok",
                List.of(new AiPlanNode("n1", "geometry.primitives.box", 0, 0, null)),
                List.of(),
                List.of()
        );
        assertTrue(validator.checkBeforeApply(valid).allowed());
    }

    @Test
    void checkBeforeDryRunUsesDryRunMessages() {
        assertEquals(
                "Dry run aborted: no plan available.",
                validator.checkBeforeDryRun(null).rejectionMessage()
        );

        AiGraphPlan invalid = new AiGraphPlan("bad", List.of(), List.of(), List.of("err"));
        assertEquals(
                "Dry run aborted: plan has validation errors.",
                validator.checkBeforeDryRun(invalid).rejectionMessage()
        );
    }

    @Test
    void parseModelResponseRejectsGarbage() {
        AiGraphDslSupport.ParseValidationResult parsed =
                validator.parseModelResponse("not-json-at-all", false);
        assertFalse(parsed.isSuccess());
        assertTrue(parsed.errors() == null || !parsed.errors().isEmpty() || parsed.graph() == null);
    }
}

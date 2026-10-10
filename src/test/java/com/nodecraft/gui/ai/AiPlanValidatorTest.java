package com.nodecraft.gui.ai;

import com.nodecraft.gui.ai.model.AiGraphPlan;
import com.nodecraft.gui.ai.model.AiPlanConnection;
import com.nodecraft.gui.ai.model.AiPlanNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiPlanValidatorTest {

    private static AiPlanValidator validator;

    @BeforeAll
    static void ensureRegistry() {
        NodeRegistry registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
        validator = new AiPlanValidator(registry);
    }

    @Test
    void checkBeforeApplyRejectsNullAndInvalidPlans() {
        assertFalse(validator.checkBeforeApply(null).allowed());
        assertEquals("Cannot apply: no plan available.", validator.checkBeforeApply(null).rejectionMessage());

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
    void checkBeforeApplyAllowsValidSourceOnlyPlan() {
        AiGraphPlan valid = new AiGraphPlan(
                "ok",
                List.of(new AiPlanNode("n1", "input.values.boolean_toggle", 0, 0, null)),
                List.of(),
                List.of()
        );
        assertTrue(validator.checkBeforeApply(valid).allowed());
    }

    @Test
    void checkBeforeApplyRejectsUnknownTypeAndMissingRequiredInput() {
        AiGraphPlan unknownType = new AiGraphPlan(
                "bad",
                List.of(new AiPlanNode("n1", "not.a.real.node", 0, 0, null)),
                List.of(),
                List.of()
        );
        assertFalse(validator.checkBeforeApply(unknownType).allowed());
        assertTrue(validator.checkBeforeApply(unknownType).rejectionMessage().contains("Unknown node type"));

        // Box defaults are optional; use a node that still has a required structural port.
        AiGraphPlan missingRequired = new AiGraphPlan(
                "bad",
                List.of(new AiPlanNode("n1", "reference.points.get_box_face", 0, 0, null)),
                List.of(),
                List.of()
        );
        assertFalse(validator.checkBeforeApply(missingRequired).allowed());
        assertTrue(validator.checkBeforeApply(missingRequired).rejectionMessage().contains("Required input not connected"));
    }

    @Test
    void checkBeforeApplyRejectsCycles() {
        AiGraphPlan cyclic = new AiGraphPlan(
                "cycle",
                List.of(
                        new AiPlanNode("n1", "math.scalar_math.absolute", 0, 0, null),
                        new AiPlanNode("n2", "math.scalar_math.absolute", 200, 0, null)
                ),
                List.of(
                        new AiPlanConnection("n1", "output_absolute", "n2", "input_value"),
                        new AiPlanConnection("n2", "output_absolute", "n1", "input_value")
                ),
                List.of()
        );
        assertFalse(validator.checkBeforeApply(cyclic).allowed());
        assertTrue(validator.checkBeforeApply(cyclic).rejectionMessage().contains("cycle"));
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

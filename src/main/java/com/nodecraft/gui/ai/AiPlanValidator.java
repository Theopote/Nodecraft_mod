package com.nodecraft.gui.ai;

import com.nodecraft.gui.ai.model.AiGraphPlan;
import com.nodecraft.nodesystem.registry.NodeRegistry;

/**
 * Named validation boundary before apply / dry-run and for model DSL payloads.
 *
 * <p>Delegates schema/type/DSL checks to {@link AiGraphDslSupport}. Does not mutate
 * the canvas graph.</p>
 */
public final class AiPlanValidator {

    private final NodeRegistry registry;

    public AiPlanValidator() {
        this(NodeRegistry.getInstance());
    }

    public AiPlanValidator(NodeRegistry registry) {
        this.registry = registry;
    }

    /**
     * Parse a model or local DSL response into a validated graph.
     *
     * @param structured when true, treat payload as structured tool JSON ({@code remote-tool})
     */
    public AiGraphDslSupport.ParseValidationResult parseModelResponse(String payload, boolean structured) {
        if (structured) {
            return AiGraphDslSupport.parseStructured(payload, registry);
        }
        return AiGraphDslSupport.parseAndValidate(payload, registry);
    }

    /** Validate a compact DSL JSON trial (e.g. inferred connections). */
    public AiGraphDslSupport.ParseValidationResult parseAndValidateJson(String dslJson) {
        return AiGraphDslSupport.parseAndValidate(dslJson, registry);
    }

    public GateResult checkBeforeApply(AiGraphPlan plan) {
        GateResult base = checkPlanPresentAndMarkedValid(plan, "Cannot apply");
        if (base != null) {
            return base;
        }
        return gateFromSemanticValidation(plan);
    }

    public GateResult checkBeforeDryRun(AiGraphPlan plan) {
        GateResult base = checkPlanPresentAndMarkedValid(plan, "Dry run aborted");
        if (base != null) {
            return base;
        }
        return gateFromSemanticValidation(plan);
    }

    private GateResult checkPlanPresentAndMarkedValid(AiGraphPlan plan, String prefix) {
        if (plan == null) {
            return GateResult.reject(prefix + ": no plan available.");
        }
        if (!plan.isValid()) {
            return GateResult.reject(prefix + ": plan has validation errors.");
        }
        return null;
    }

    private GateResult gateFromSemanticValidation(AiGraphPlan plan) {
        AiGraphDslSupport.PlanValidationResult validation = AiGraphDslSupport.validatePlan(plan, registry);
        if (!validation.isSuccess()) {
            String message = validation.errors().isEmpty()
                    ? "Plan failed semantic validation."
                    : validation.errors().getFirst();
            return GateResult.reject(message);
        }
        return GateResult.allow();
    }

    public record GateResult(boolean allowed, String rejectionMessage) {
        public static GateResult allow() {
            return new GateResult(true, "");
        }

        public static GateResult reject(String message) {
            return new GateResult(false, message == null ? "" : message);
        }
    }
}

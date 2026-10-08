package com.nodecraft.gui.ai;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.ai.model.AiGraphPlan;
import com.nodecraft.nodesystem.registry.NodeRegistry;

import java.util.List;
import java.util.Optional;

public final class AiPlanDslWorkflowService {

    private AiPlanDslWorkflowService() {
    }

    public static String toDslJson(AiGraphPlan plan) {
        return AiGraphPlanDslAdapterService.toDslJson(plan);
    }

    public static String toDslJsonCompact(AiGraphPlan plan) {
        return AiGraphPlanDslAdapterService.toDslJsonCompact(plan);
    }

    public static AiGraphPlan fromDsl(AiGraphDslSupport.DslGraph dslGraph) {
        return AiGraphPlanDslAdapterService.fromDsl(dslGraph);
    }

    public static AiGraphPlan buildMockGraphPlan(String prompt) {
        List<AiTemplateLibrary.Template> templates = AiTemplateLibrary.loadAll(AiTemplateLibrary.resolveTemplateDir());
        Optional<AiTemplateLibrary.MatchResult> bestMatch = AiTemplateLibrary.findBestMatch(prompt, templates);
        if (bestMatch.isPresent()) {
            AiTemplateLibrary.MatchResult match = bestMatch.get();
            AiGraphDslSupport.ParseValidationResult parsed =
                    AiGraphDslSupport.parseAndValidate(match.template().dslJson(), NodeRegistry.getInstance());
            if (parsed.isSuccess() && parsed.graph() != null) {
                NodeCraft.LOGGER.info("[AI_TEMPLATE] Using local template '{}' (score={}).", match.template().name(), match.score());
                return AiGraphPlanDslAdapterService.fromDsl(parsed.graph());
            }
            NodeCraft.LOGGER.warn("[AI_TEMPLATE] Matched template '{}' failed DSL validation, falling back to mock.", match.template().name());
        } else {
            NodeCraft.LOGGER.info("[AI_TEMPLATE] No confident local template match; using dynamic mock planner.");
        }

        AiMockPlanService.MockPlan mockPlan = AiMockPlanService.buildMockPlan(prompt);
        return AiGraphPlanDslAdapterService.fromMockPlan(mockPlan);
    }
}

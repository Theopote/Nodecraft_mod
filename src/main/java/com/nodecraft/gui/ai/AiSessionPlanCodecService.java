package com.nodecraft.gui.ai;

import com.nodecraft.gui.ai.model.AiGraphPlan;
import com.nodecraft.nodesystem.registry.NodeRegistry;

public final class AiSessionPlanCodecService {

    private AiSessionPlanCodecService() {
    }

    public static String serializePendingPlanToDsl(AiGraphPlan plan) {
        if (plan == null) {
            return "";
        }
        return AiPlanDslWorkflowService.toDslJson(plan);
    }

    public static AiGraphPlan deserializePendingPlanFromDsl(String pendingPlanDslJson) {
        AiGraphDslSupport.ParseValidationResult parsed =
                AiGraphDslSupport.parseAndValidate(pendingPlanDslJson, NodeRegistry.getInstance());
        if (!parsed.isSuccess() || parsed.graph() == null) {
            return null;
        }
        return AiPlanDslWorkflowService.fromDsl(parsed.graph());
    }
}

package com.nodecraft.gui.ai;

public final class AiPlanDslWorkflowService {

    private AiPlanDslWorkflowService() {
    }

    public static String toDslJson(AiGraphPlanDslAdapterService.GraphPlan plan) {
        return AiGraphPlanDslAdapterService.toDslJson(plan);
    }

    public static String toDslJsonCompact(AiGraphPlanDslAdapterService.GraphPlan plan) {
        return AiGraphPlanDslAdapterService.toDslJsonCompact(plan);
    }

    public static AiGraphPlanDslAdapterService.GraphPlan fromDsl(AiGraphDslSupport.DslGraph dslGraph) {
        return AiGraphPlanDslAdapterService.fromDsl(dslGraph);
    }

    public static AiGraphPlanDslAdapterService.GraphPlan buildMockGraphPlan(String prompt) {
        AiMockPlanService.MockPlan mockPlan = AiMockPlanService.buildMockPlan(prompt);
        return AiGraphPlanDslAdapterService.fromMockPlan(mockPlan);
    }
}

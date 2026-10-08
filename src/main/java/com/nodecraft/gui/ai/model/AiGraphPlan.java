package com.nodecraft.gui.ai.model;

import java.util.List;

/**
 * Canonical pending AI graph plan shared across UI, planner, validator, diff, and apply layers.
 */
public record AiGraphPlan(
        String summary,
        List<AiPlanNode> nodes,
        List<AiPlanConnection> connections,
        List<String> validationErrors
) {
    public boolean isValid() {
        return validationErrors == null || validationErrors.isEmpty();
    }
}

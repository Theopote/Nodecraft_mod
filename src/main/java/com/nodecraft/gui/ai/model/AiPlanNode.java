package com.nodecraft.gui.ai.model;

/**
 * One node entry in a pending AI graph plan.
 */
public record AiPlanNode(String ref, String typeId, float offsetX, float offsetY, Object nodeState) {
}

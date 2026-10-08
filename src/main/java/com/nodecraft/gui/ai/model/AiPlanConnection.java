package com.nodecraft.gui.ai.model;

/**
 * One connection entry in a pending AI graph plan.
 */
public record AiPlanConnection(String sourceRef, String sourcePortId, String targetRef, String targetPortId) {
}

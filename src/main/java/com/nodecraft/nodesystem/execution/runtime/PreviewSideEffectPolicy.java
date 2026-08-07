package com.nodecraft.nodesystem.execution.runtime;

import com.nodecraft.nodesystem.api.INode;

/**
 * Preview-mode permanent side-effect policy.
 * <p>
 * See {@code docs/contracts/preview-side-effects.md}.
 */
public final class PreviewSideEffectPolicy {

    /**
     * Type-id prefix for nodes that perform permanent bake/apply world side effects.
     */
    public static final String PERMANENT_SIDE_EFFECT_PREFIX = "output.execute.";

    private PreviewSideEffectPolicy() {
    }

    public static boolean isPermanentSideEffectTypeId(String typeId) {
        return typeId != null && typeId.startsWith(PERMANENT_SIDE_EFFECT_PREFIX);
    }

    public static boolean isPermanentSideEffectNode(INode node) {
        return node != null && isPermanentSideEffectTypeId(node.getTypeId());
    }

    /**
     * Whether {@code plan} requires skipping permanent side-effect nodes.
     */
    public static boolean shouldSkipPermanentSideEffects(ExecutionPlan plan) {
        return plan != null && plan.skipOutputExecuteSideEffects();
    }

    /**
     * Whether {@code node} must be skipped for the given plan.
     */
    public static boolean shouldSkipNode(ExecutionPlan plan, INode node) {
        return shouldSkipPermanentSideEffects(plan) && isPermanentSideEffectNode(node);
    }
}

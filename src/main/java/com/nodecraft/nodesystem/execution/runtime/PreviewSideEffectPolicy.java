package com.nodecraft.nodesystem.execution.runtime;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.NodeEffect;

/**
 * Preview-mode side-effect policy.
 * <p>
 * See {@code docs/contracts/preview-side-effects.md}.
 */
public final class PreviewSideEffectPolicy {

    private PreviewSideEffectPolicy() {
    }

    public static NodeEffect resolveEffect(INode node) {
        return NodeEffectResolver.resolve(node);
    }

    public static boolean isAllowedInPreview(INode node) {
        return resolveEffect(node).isAllowedInPreview();
    }

    public static boolean isForbiddenInPreview(INode node) {
        return !isAllowedInPreview(node);
    }

    /**
     * Whether {@code plan} requires skipping preview-forbidden nodes.
     */
    public static boolean shouldSkipPermanentSideEffects(ExecutionPlan plan) {
        return plan != null && plan.skipOutputExecuteSideEffects();
    }

    /**
     * Whether {@code node} must be skipped for the given plan.
     */
    public static boolean shouldSkipNode(ExecutionPlan plan, INode node) {
        return shouldSkipPermanentSideEffects(plan) && isForbiddenInPreview(node);
    }
}

package com.nodecraft.gui.ai;

import com.nodecraft.gui.ai.model.AiGraphPlan;
import com.nodecraft.gui.ai.model.AiPlanNode;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.execution.runtime.NodeEffectResolver;
import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.registry.NodeRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * AI apply effect safety boundary.
 *
 * <p>AI may <em>author</em> preview-forbidden nodes (for example {@code world.write.*} and
 * {@code output.execute.*}), but apply paths only mutate the editor graph — they never invoke
 * {@code NodeExecutor}, bake, or other runtime side effects. Execution policy remains governed by
 * {@link com.nodecraft.nodesystem.execution.runtime.PreviewSideEffectPolicy} at run time.</p>
 *
 * <p>See {@code docs/architecture/ai-assistant-subsystem.md} and
 * {@code docs/contracts/preview-side-effects.md}.</p>
 */
public final class AiPlanEffectPolicy {

    private AiPlanEffectPolicy() {
    }

    public record EffectSummary(
            int totalNodes,
            int previewForbiddenNodes,
            List<String> previewForbiddenTypeIds
    ) {
        public boolean hasPreviewForbiddenNodes() {
            return previewForbiddenNodes > 0;
        }
    }

    public static EffectSummary analyze(AiGraphPlan plan, NodeRegistry registry) {
        if (plan == null || plan.nodes() == null || plan.nodes().isEmpty()) {
            return new EffectSummary(0, 0, List.of());
        }
        if (registry == null) {
            return new EffectSummary(plan.nodes().size(), 0, List.of());
        }

        List<String> forbidden = new ArrayList<>();
        for (AiPlanNode node : plan.nodes()) {
            if (node == null || node.typeId() == null || node.typeId().isBlank()) {
                continue;
            }
            if (isPreviewForbiddenType(node.typeId(), registry)) {
                forbidden.add(node.typeId());
            }
        }
        return new EffectSummary(plan.nodes().size(), forbidden.size(), List.copyOf(forbidden));
    }

    public static boolean isPreviewForbiddenType(String typeId, NodeRegistry registry) {
        if (typeId == null || typeId.isBlank() || registry == null) {
            return false;
        }
        NodeInfo info = registry.getNodeInfo(typeId);
        if (info == null || info.getNodeClass() == null) {
            return false;
        }
        NodeEffect effect = NodeEffectResolver.resolve(info.getNodeClass(), typeId);
        return !effect.isAllowedInPreview();
    }
}

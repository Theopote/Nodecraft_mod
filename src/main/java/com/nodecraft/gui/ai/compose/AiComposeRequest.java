package com.nodecraft.gui.ai.compose;

import com.nodecraft.nodesystem.semantic.NodeCapability;
import com.nodecraft.nodesystem.semantic.NodeDomain;

import java.util.List;
import java.util.Set;

/**
 * Minimal Composer input. Prefer explicit seeds when known; otherwise seed from capabilities.
 */
public record AiComposeRequest(
        Set<NodeCapability> requiredCapabilities,
        Set<NodeDomain> preferredDomains,
        List<String> seedNodeIds,
        AiComposeGoal goal,
        boolean allowWorldWrite,
        int maxNodes,
        String prompt
) {
    public AiComposeRequest {
        requiredCapabilities = requiredCapabilities == null ? Set.of() : Set.copyOf(requiredCapabilities);
        preferredDomains = preferredDomains == null ? Set.of() : Set.copyOf(preferredDomains);
        seedNodeIds = seedNodeIds == null ? List.of() : List.copyOf(seedNodeIds);
        goal = goal == null ? AiComposeGoal.PREVIEW : goal;
        maxNodes = maxNodes <= 0 ? AiComposeCostPolicy.DEFAULT_MAX_NODES : maxNodes;
        prompt = prompt == null ? "" : prompt;
    }

    public static AiComposeRequest fromPrompt(String prompt) {
        boolean world = com.nodecraft.gui.ai.AiIntentAnalysisService.hasWorldApplyIntent(prompt);
        return new AiComposeRequest(
                com.nodecraft.gui.ai.AiPlanCapabilityCoverage.requiredFromPrompt(prompt),
                com.nodecraft.gui.ai.AiIntentAnalysisService.detectDomainTags(prompt),
                List.of(),
                world ? AiComposeGoal.WORLD_OUTPUT : AiComposeGoal.PREVIEW,
                world,
                AiComposeCostPolicy.DEFAULT_MAX_NODES,
                prompt
        );
    }
}

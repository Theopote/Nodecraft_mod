package com.nodecraft.gui.ai.compose;

import com.nodecraft.gui.ai.AiIntentAnalysisService;
import com.nodecraft.gui.ai.AiPlanCapabilityCoverage;
import com.nodecraft.nodesystem.semantic.NodeCapability;
import com.nodecraft.nodesystem.semantic.NodeDomain;

import java.util.List;
import java.util.Set;

/**
 * Minimal Composer input. Prefer explicit seeds when known; otherwise seed from capabilities.
 * Multi-seed is required for join workflows (e.g. Wall + Window Array).
 */
public record AiComposeRequest(
        String prompt,
        Set<NodeCapability> requiredCapabilities,
        Set<NodeDomain> preferredDomains,
        List<String> seedNodeIds,
        AiComposeGoal goal,
        int maxNodes
) {
    public AiComposeRequest {
        prompt = prompt == null ? "" : prompt;
        requiredCapabilities = requiredCapabilities == null ? Set.of() : Set.copyOf(requiredCapabilities);
        preferredDomains = preferredDomains == null ? Set.of() : Set.copyOf(preferredDomains);
        seedNodeIds = seedNodeIds == null ? List.of() : List.copyOf(seedNodeIds);
        goal = goal == null ? AiComposeGoal.PREVIEW : goal;
        maxNodes = maxNodes <= 0 ? AiComposeCostPolicy.DEFAULT_MAX_NODES : maxNodes;
    }

    public boolean allowWorldWrite() {
        return goal == AiComposeGoal.WORLD_OUTPUT;
    }

    public static AiComposeRequest fromPrompt(String prompt) {
        boolean world = AiIntentAnalysisService.hasWorldApplyIntent(prompt);
        return new AiComposeRequest(
                prompt,
                AiPlanCapabilityCoverage.requiredFromPrompt(prompt),
                AiIntentAnalysisService.detectDomainTags(prompt),
                List.of(),
                world ? AiComposeGoal.WORLD_OUTPUT : AiComposeGoal.PREVIEW,
                AiComposeCostPolicy.DEFAULT_MAX_NODES
        );
    }
}

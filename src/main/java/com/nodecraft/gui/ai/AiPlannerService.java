package com.nodecraft.gui.ai;

import java.util.List;

/**
 * Façade for producing AI plan payloads — local/mock templates and remote request prep.
 *
 * <p>Does not mutate the graph and does not own ImGui / HTTP sockets. Remote async submit
 * still goes through {@link com.nodecraft.gui.components.ai.AiAssistantComponent}; this
 * type prepares configs and builds local DSL JSON.</p>
 */
public final class AiPlannerService {

    private final AiRemotePlanningOrchestrator remoteOrchestrator;

    public AiPlannerService() {
        this(new AiRemotePlanningOrchestrator());
    }

    public AiPlannerService(AiRemotePlanningOrchestrator remoteOrchestrator) {
        this.remoteOrchestrator = remoteOrchestrator == null
                ? new AiRemotePlanningOrchestrator()
                : remoteOrchestrator;
    }

    /**
     * Synchronous local plan: template match or dynamic mock → DSL JSON.
     */
    public LocalPlanPayload planLocal(String prompt) {
        String dslJson = AiPlanDslWorkflowService.toDslJson(
                AiPlanDslWorkflowService.buildMockGraphPlan(prompt)
        );
        return new LocalPlanPayload(dslJson, "local-template");
    }

    public AiRemotePlanningOrchestrator.PreparedRequest prepareInitialRemoteRequest(
            AiRemotePlanningOrchestrator.RequestSettings settings,
            String userPrompt,
            String userPromptPayload,
            boolean complexGenerationPrompt
    ) {
        return remoteOrchestrator.prepareInitialRequest(
                settings,
                userPrompt,
                userPromptPayload,
                complexGenerationPrompt
        );
    }

    public AiRemotePlanningOrchestrator.PreparedRetryRequest prepareDslRepairRequest(
            AiRemotePlanningOrchestrator.RequestSettings settings,
            String originalPrompt,
            String invalidDslOrModelResponse,
            List<String> parseErrors,
            int currentAttempt,
            String frozenWorldContextJson
    ) {
        return remoteOrchestrator.prepareDslRepairRequest(
                settings,
                originalPrompt,
                invalidDslOrModelResponse,
                parseErrors,
                currentAttempt,
                frozenWorldContextJson
        );
    }

    public AiRemotePlanningOrchestrator.PreparedRetryRequest prepareGraphExpansionRequest(
            AiRemotePlanningOrchestrator.RequestSettings settings,
            String originalPrompt,
            AiGraphPlanDslAdapterService.GraphPlan underspecifiedPlan,
            String originalModelPayload,
            int currentAttempt,
            String frozenWorldContextJson
    ) {
        return remoteOrchestrator.prepareGraphExpansionRequest(
                settings,
                originalPrompt,
                underspecifiedPlan,
                originalModelPayload,
                currentAttempt,
                frozenWorldContextJson
        );
    }

    public boolean shouldRequestConnectedGraphExpansion(
            String prompt,
            AiGraphPlanDslAdapterService.GraphPlan plan,
            int currentAttempt,
            boolean complexGenerationPrompt
    ) {
        return remoteOrchestrator.shouldRequestConnectedGraphExpansion(
                prompt,
                plan,
                currentAttempt,
                complexGenerationPrompt
        );
    }

    public int maxDslRepairAttempts() {
        return remoteOrchestrator.maxDslRepairAttempts();
    }

    public int maxGraphExpansionAttempts() {
        return remoteOrchestrator.maxGraphExpansionAttempts();
    }

    public String sanitizeUserPromptForSnapshot(String prompt) {
        return remoteOrchestrator.sanitizeUserPromptForSnapshot(prompt);
    }

    public record LocalPlanPayload(String dslJson, String source) {
    }
}

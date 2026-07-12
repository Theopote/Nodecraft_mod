package com.nodecraft.gui.ai;

import com.nodecraft.gui.ai.AiIntentAnalysisService.UserIntent;
import com.nodecraft.nodesystem.registry.NodeRegistry;

import java.util.List;

/**
 * Prepares remote planner requests without depending on the ImGui panel.
 */
public final class AiRemotePlanningOrchestrator {

    private static final int SCHEMA_LIMIT_DEFAULT = 36;
    private static final int SCHEMA_LIMIT_GENERATE = 72;
    private static final int SCHEMA_LIMIT_REPAIR = 96;
    private static final int MAX_DSL_REPAIR_ATTEMPTS = 2;
    private static final int MAX_GRAPH_EXPANSION_ATTEMPTS = 1;
    private static final String MODIFY_PARAM_SYSTEM_HINT =
            """
                    If the user asks to modify a specific parameter value on an existing node,
                    return only the affected node with its updated params. Keep all other nodes and connections unchanged.
                    Use the same node ids as in the "Current plan in effect" JSON.""";
    private static final String RESTRUCTURE_SYSTEM_HINT =
            """
                    If the user asks to restructure, delete, or optimize the existing graph:
                    - Analyze the 'Current canvas graph snapshot' and connections carefully.
                    - Perform the requested structural changes (e.g., deleting, replacing, or inserting nodes and changing connections).
                    - Retain all other nodes and connections that the user did not ask to modify.
                    - Reuse the existing node IDs (e.g. n1, n2, or short UUIDs like 8ef1a2c3) for any nodes that are retained.
                    - Output the complete, updated graph containing both retained nodes and new/modified nodes.
                    """;
    private static final String DSL_REPAIR_SYSTEM_HINT =
            """
                You are now in DSL repair mode.
                You must fix ONLY invalid node type ids / invalid port ids based on the provided node library schema.
                Do not add explanations. Return JSON object only.
                Keep graph structure and user intent unchanged as much as possible.
                Every node.type must exactly match a listed typeId.
                Every connection.from.port and connection.to.port must exactly match declared port ids for those node types.
                If a connection cannot be made type-safe using listed nodes and ports, remove that connection.
                A smaller valid graph is better than an invalid complete graph.
                """;
    private static final String DSL_TYPE_REPAIR_HINT =
            """
                Additional strict requirement for this retry:
                - Fix data type compatibility for all connections.
                - Do NOT connect scalar values (float/integer/double) directly to geometry inputs.
                - Do NOT connect vectors to geometry inputs.
                - Do NOT connect vectors to list inputs unless the input port explicitly accepts vector data.
                - Do NOT guess converters. Use a converter node only if it appears in the provided node library.
                - If no compatible path exists, delete the invalid connection and keep the valid nodes.
                - Ensure output ports and input ports exist on the corresponding node types.
                - If needed, replace incorrect intermediate nodes with valid ones from the provided node library.
                - Ensure the output is valid JSON: do NOT include trailing commas, unescaped characters, or markdown tags.
                """;
    private static final String CONNECTED_GRAPH_EXPANSION_HINT =
            """
                You are now in connected graph expansion mode.
                The previous valid DSL was too small for the user's generation request.
                Return JSON only. Expand the plan into a connected workflow with at least 3 nodes and at least 2 valid connections when the library allows it.
                Use output.preview.* or output.execute.* nodes when compatible. Use world.write.* nodes only with compatible block/region/list inputs.
                Do not invent typeIds or ports. If no connected workflow is possible with the listed library, return {"error":"connected_workflow_not_possible:<reason>"}.
                """;

    public record RequestSettings(
            String apiBaseUrl,
            String apiKey,
            String model,
            String providerStrategy,
            String systemPrompt,
            int maxOutputTokens,
            int timeoutSeconds,
            boolean selectionContextEnabled,
            boolean debugLoggingEnabled,
            boolean includePromptPreviewInDebug
    ) {
    }

    public record PreparedRequest(
            AiRemotePlannerService.PlannerConfig config,
            UserIntent userIntent,
            int selectedSchemaCount,
            int totalSchemaCount,
            int schemaLimit,
            String requestSnapshot,
            String promptFingerprint
    ) {
    }

    public record PreparedRetryRequest(
            AiRemotePlannerService.PlannerConfig config,
            List<AiRemotePlannerService.ConversationMessage> conversation,
            int nextAttempt,
            int maxAttempts,
            int selectedSchemaCount,
            int totalSchemaCount,
            int schemaLimit,
            String requestSnapshot,
            String promptPayload,
            String diagnosticText
    ) {
    }

    public int maxDslRepairAttempts() {
        return MAX_DSL_REPAIR_ATTEMPTS;
    }

    public int maxGraphExpansionAttempts() {
        return MAX_GRAPH_EXPANSION_ATTEMPTS;
    }

    public PreparedRequest prepareInitialRequest(
            RequestSettings settings,
            String userPrompt,
            String userPromptPayload,
            boolean complexGenerationPrompt
    ) {
        NodeRegistry registry = NodeRegistry.getInstance();
        List<AiNodeSchemaCatalog.NodeSchema> allSchemas = AiNodeSchemaCatalog.collectAll(registry);
        UserIntent userIntent = AiIntentAnalysisService.classifyIntent(userPrompt);
        int schemaLimit = resolveRemoteSchemaLimit(userIntent, complexGenerationPrompt);
        List<AiNodeSchemaCatalog.NodeSchema> relevantSchemas = AiNodeSchemaCatalog.selectRelevant(
                allSchemas,
                userPrompt,
                schemaLimit
        );

        String systemPrompt = buildSystemPrompt(settings.systemPrompt(), relevantSchemas, userIntent);
        AiRemotePlannerService.PlannerConfig config = new AiRemotePlannerService.PlannerConfig(
                settings.apiBaseUrl(),
                settings.apiKey(),
                settings.model(),
                settings.providerStrategy(),
                systemPrompt,
                settings.maxOutputTokens(),
                settings.timeoutSeconds()
        );
        String promptFingerprint = computePromptFingerprint(userPrompt);
        String requestSnapshot = buildRemoteRequestSnapshot(
                config,
                userPrompt,
                userPromptPayload,
                relevantSchemas.size(),
                settings.selectionContextEnabled(),
                settings.debugLoggingEnabled(),
                settings.includePromptPreviewInDebug(),
                promptFingerprint
        );

        return new PreparedRequest(
                config,
                userIntent,
                relevantSchemas.size(),
                allSchemas.size(),
                schemaLimit,
                requestSnapshot,
                promptFingerprint
        );
    }

    public PreparedRetryRequest prepareDslRepairRequest(
            RequestSettings settings,
            String originalPrompt,
            String invalidDslOrModelResponse,
            List<String> parseErrors,
            int currentAttempt,
            String frozenWorldContextJson
    ) {
        SchemaSelection schemaSelection = selectRepairSchemas(originalPrompt);
        String systemPrompt = buildBaseSystemPrompt(settings.systemPrompt(), schemaSelection.relevantSchemas())
                + "\n\n" + DSL_REPAIR_SYSTEM_HINT;
        String errorsText = parseErrors == null || parseErrors.isEmpty()
                ? "(none)"
                : String.join("; ", parseErrors);
        boolean typeMismatchDetected = containsAny(
                errorsText.toLowerCase(),
                "type mismatch",
                "unsupported type relationship"
        );
        if (typeMismatchDetected || currentAttempt >= 1) {
            systemPrompt = systemPrompt + "\n\n" + DSL_TYPE_REPAIR_HINT;
        }

        int nextAttempt = currentAttempt + 1;
        String promptPayload = "Remote planner produced invalid DSL. Repair it now.\n\n"
                + "Repair attempt: " + nextAttempt + " / " + MAX_DSL_REPAIR_ATTEMPTS + "\n"
                + "Original user prompt:\n"
                + nullToEmpty(originalPrompt)
                + "\n\n"
                + "Validation errors:\n"
                + errorsText
                + "\n\n"
                + "Invalid DSL/model payload to repair:\n"
                + nullToEmpty(invalidDslOrModelResponse)
                + "\n\n"
                + buildFrozenWorldContextSection(frozenWorldContextJson)
                + "Return repaired JSON only.";

        return buildPreparedRetryRequest(
                settings,
                originalPrompt,
                systemPrompt,
                promptPayload,
                nextAttempt,
                MAX_DSL_REPAIR_ATTEMPTS,
                errorsText,
                schemaSelection
        );
    }

    public boolean shouldRequestConnectedGraphExpansion(
            String prompt,
            AiGraphPlanDslAdapterService.GraphPlan plan,
            int currentAttempt,
            boolean complexGenerationPrompt
    ) {
        if (currentAttempt >= MAX_GRAPH_EXPANSION_ATTEMPTS) {
            return false;
        }
        if (AiIntentAnalysisService.classifyIntent(prompt) != UserIntent.GENERATE_NEW) {
            return false;
        }
        if (isPlacementOnlyCanvasPrompt(prompt, complexGenerationPrompt)) {
            return false;
        }
        if (!complexGenerationPrompt) {
            return false;
        }
        int nodeCount = plan == null || plan.nodes() == null ? 0 : plan.nodes().size();
        int connectionCount = plan == null || plan.connections() == null ? 0 : plan.connections().size();
        return nodeCount <= 1 || connectionCount == 0;
    }

    public PreparedRetryRequest prepareGraphExpansionRequest(
            RequestSettings settings,
            String originalPrompt,
            AiGraphPlanDslAdapterService.GraphPlan underspecifiedPlan,
            String originalModelPayload,
            int currentAttempt,
            String frozenWorldContextJson
    ) {
        SchemaSelection schemaSelection = selectRepairSchemas(originalPrompt);
        String systemPrompt = buildBaseSystemPrompt(settings.systemPrompt(), schemaSelection.relevantSchemas())
                + "\n\n" + CONNECTED_GRAPH_EXPANSION_HINT;

        int nextAttempt = currentAttempt + 1;
        String currentDsl = underspecifiedPlan == null
                ? ""
                : AiGraphPlanDslAdapterService.toDslJsonCompact(underspecifiedPlan);
        String promptPayload = "Remote planner produced a valid but underspecified graph.\n\n"
                + "Expansion attempt: " + nextAttempt + " / " + MAX_GRAPH_EXPANSION_ATTEMPTS + "\n"
                + "Original user prompt:\n"
                + nullToEmpty(originalPrompt)
                + "\n\n"
                + "Current underspecified DSL:\n"
                + currentDsl
                + "\n\n"
                + "Original model payload:\n"
                + nullToEmpty(originalModelPayload)
                + "\n\n"
                + buildFrozenWorldContextSection(frozenWorldContextJson)
                + "Required outcome: return a connected NodeCraft graph, not a single standalone node. Return JSON only.";

        return buildPreparedRetryRequest(
                settings,
                originalPrompt,
                systemPrompt,
                promptPayload,
                nextAttempt,
                MAX_GRAPH_EXPANSION_ATTEMPTS,
                "",
                schemaSelection
        );
    }

    private String buildSystemPrompt(
            String configuredSystemPrompt,
            List<AiNodeSchemaCatalog.NodeSchema> relevantSchemas,
            UserIntent userIntent
    ) {
        String systemPrompt = buildBaseSystemPrompt(configuredSystemPrompt, relevantSchemas);
        if (userIntent == UserIntent.MODIFY_PARAM) {
            return systemPrompt + "\n\n" + MODIFY_PARAM_SYSTEM_HINT;
        }
        if (userIntent == UserIntent.RESTRUCTURE) {
            return systemPrompt + "\n\n" + RESTRUCTURE_SYSTEM_HINT;
        }
        return systemPrompt;
    }

    private String buildBaseSystemPrompt(
            String configuredSystemPrompt,
            List<AiNodeSchemaCatalog.NodeSchema> relevantSchemas
    ) {
        String nodeSchemaPrompt = AiPromptBuilder.buildSystemPrompt(relevantSchemas);
        return configuredSystemPrompt == null || configuredSystemPrompt.isBlank()
                ? nodeSchemaPrompt
                : configuredSystemPrompt + "\n\n" + nodeSchemaPrompt;
    }

    private int resolveRemoteSchemaLimit(UserIntent intent, boolean complexGenerationPrompt) {
        if (intent == UserIntent.GENERATE_NEW || complexGenerationPrompt) {
            return SCHEMA_LIMIT_GENERATE;
        }
        return SCHEMA_LIMIT_DEFAULT;
    }

    private String buildRemoteRequestSnapshot(
            AiRemotePlannerService.PlannerConfig config,
            String userPrompt,
            String userPromptPayload,
            int schemaCount,
            boolean selectionContextEnabled,
            boolean debugLoggingEnabled,
            boolean includePromptPreviewInDebug,
            String promptFingerprint
    ) {
        String detectedLanguage = AiIntentAnalysisService.detectInputLanguage(userPrompt);
        String normalizedIntentPreview = AiIntentAnalysisService.buildNormalizedIntentPreview(userPrompt);
        String promptPreview = debugLoggingEnabled && includePromptPreviewInDebug
                ? sanitizeUserPromptForSnapshot(userPrompt)
                : "(disabled)";

        return "baseUrl: " + nullToEmpty(config.apiBaseUrl()) + "\n" +
                "apiKeyMasked: " + maskSecret(config.apiKey()) + "\n" +
                "model: " + nullToEmpty(config.model()) + "\n" +
                "providerStrategy: " + nullToEmpty(config.providerStrategy()) + "\n" +
                "maxOutputTokens: " + config.maxOutputTokens() + "\n" +
                "timeoutSeconds: " + config.timeoutSeconds() + "\n" +
                "selectionContextEnabled: " + selectionContextEnabled + "\n" +
                "inputLanguageDetected: " + detectedLanguage + "\n" +
                "normalizedIntentPreview: " + normalizedIntentPreview + "\n" +
                "userPromptPreview: " + promptPreview + "\n" +
                "userPromptFingerprint: " + promptFingerprint + "\n" +
                "schemaCountInjected: " + schemaCount + "\n" +
                "systemPromptLength: " + (config.systemPrompt() == null ? 0 : config.systemPrompt().length()) + "\n" +
                "userPromptLength: " + (userPrompt == null ? 0 : userPrompt.length()) + "\n" +
                "payloadLength: " + (userPromptPayload == null ? 0 : userPromptPayload.length()) + "\n";
    }

    private PreparedRetryRequest buildPreparedRetryRequest(
            RequestSettings settings,
            String originalPrompt,
            String systemPrompt,
            String promptPayload,
            int nextAttempt,
            int maxAttempts,
            String diagnosticText,
            SchemaSelection schemaSelection
    ) {
        AiRemotePlannerService.PlannerConfig config = new AiRemotePlannerService.PlannerConfig(
                settings.apiBaseUrl(),
                settings.apiKey(),
                settings.model(),
                settings.providerStrategy(),
                systemPrompt,
                settings.maxOutputTokens(),
                settings.timeoutSeconds()
        );
        String requestSnapshot = buildRemoteRequestSnapshot(
                config,
                originalPrompt,
                promptPayload,
                schemaSelection.relevantSchemas().size(),
                settings.selectionContextEnabled(),
                settings.debugLoggingEnabled(),
                settings.includePromptPreviewInDebug(),
                computePromptFingerprint(originalPrompt)
        );
        return new PreparedRetryRequest(
                config,
                List.of(new AiRemotePlannerService.ConversationMessage("user", promptPayload)),
                nextAttempt,
                maxAttempts,
                schemaSelection.relevantSchemas().size(),
                schemaSelection.allSchemas().size(),
                SCHEMA_LIMIT_REPAIR,
                requestSnapshot,
                promptPayload,
                diagnosticText
        );
    }

    private SchemaSelection selectRepairSchemas(String prompt) {
        NodeRegistry registry = NodeRegistry.getInstance();
        List<AiNodeSchemaCatalog.NodeSchema> allSchemas = AiNodeSchemaCatalog.collectAll(registry);
        List<AiNodeSchemaCatalog.NodeSchema> relevantSchemas = AiNodeSchemaCatalog.selectRelevant(
                allSchemas,
                prompt,
                SCHEMA_LIMIT_REPAIR
        );
        return new SchemaSelection(allSchemas, relevantSchemas);
    }

    private static String buildFrozenWorldContextSection(String frozenWorldContextJson) {
        if (frozenWorldContextJson == null || frozenWorldContextJson.isBlank()) {
            return "";
        }
        return "CURRENT_WORLD_CONTEXT from the original request (frozen JSON snapshot):\n"
                + frozenWorldContextJson
                + "\n\n";
    }

    private boolean isPlacementOnlyCanvasPrompt(String prompt, boolean complexGenerationPrompt) {
        String text = prompt == null ? "" : prompt.toLowerCase();
        return containsAny(text, "画布", "节点放到", "放置节点", "添加节点", "canvas", "place node", "add node")
                && !complexGenerationPrompt;
    }

    private boolean containsAny(String text, String... keywords) {
        if (text == null || text.isBlank() || keywords == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isBlank() && text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    public String sanitizeUserPromptForSnapshot(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return "(empty)";
        }

        String sanitized = prompt;
        sanitized = sanitized.replaceAll("(?i)(api[_-]?key\\s*[:=]\\s*)([^\\s,;]+)", "$1***");
        sanitized = sanitized.replaceAll("(?i)(password\\s*[:=]\\s*)([^\\s,;]+)", "$1***");
        sanitized = sanitized.replaceAll("(?i)(token\\s*[:=]\\s*)([^\\s,;]+)", "$1***");
        sanitized = sanitized.replaceAll("(?i)(authorization\\s*[:=]\\s*bearer\\s+)([^\\s,;]+)", "$1***");
        sanitized = sanitized.replaceAll("(?i)\\bsk-[a-z0-9]{16,}\\b", "***");
        sanitized = sanitized.replaceAll("(?i)\\b[a-z0-9_\\-]{32,}\\b", "***");
        sanitized = sanitized.replaceAll("\\s+", " ").trim();

        if (sanitized.length() <= 280) {
            return sanitized;
        }
        return sanitized.substring(0, 280) + "...[truncated]";
    }

    public String computePromptFingerprint(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return "none";
        }
        return Integer.toHexString(prompt.hashCode());
    }

    private String maskSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            return "(empty)";
        }
        int len = secret.length();
        if (len <= 6) {
            return "***";
        }
        String prefix = secret.substring(0, 4);
        String suffix = secret.substring(Math.max(0, len - 2));
        return prefix + "***" + suffix;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record SchemaSelection(
            List<AiNodeSchemaCatalog.NodeSchema> allSchemas,
            List<AiNodeSchemaCatalog.NodeSchema> relevantSchemas
    ) {
    }
}

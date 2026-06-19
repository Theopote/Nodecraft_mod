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

    private String buildSystemPrompt(
            String configuredSystemPrompt,
            List<AiNodeSchemaCatalog.NodeSchema> relevantSchemas,
            UserIntent userIntent
    ) {
        String nodeSchemaPrompt = AiPromptBuilder.buildSystemPrompt(relevantSchemas);
        String systemPrompt = configuredSystemPrompt == null || configuredSystemPrompt.isBlank()
                ? nodeSchemaPrompt
                : configuredSystemPrompt + "\n\n" + nodeSchemaPrompt;
        if (userIntent == UserIntent.MODIFY_PARAM) {
            return systemPrompt + "\n\n" + MODIFY_PARAM_SYSTEM_HINT;
        }
        if (userIntent == UserIntent.RESTRUCTURE) {
            return systemPrompt + "\n\n" + RESTRUCTURE_SYSTEM_HINT;
        }
        return systemPrompt;
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
}

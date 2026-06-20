package com.nodecraft.gui.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.nodecraft.core.NodeCraft;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class AiSettingsStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final String API_KEY_ENV = "NODECRAFT_AI_API_KEY";
    public static final String OPENAI_API_KEY_ENV = "OPENAI_API_KEY";
    public static final String ANTHROPIC_API_KEY_ENV = "ANTHROPIC_API_KEY";

    public static final String PROVIDER_AUTO = "AUTO";
    public static final String PROVIDER_OPENAI_COMPAT = "OPENAI_COMPAT";
    public static final String PROVIDER_ANTHROPIC = "ANTHROPIC";

    private AiSettingsStore() {
    }

    public record AiSettingsData(
            String apiBaseUrl,
            String apiKey,
            String model,
            String providerStrategy,
            String systemPrompt,
            int maxOutputTokens,
            int timeoutSeconds,
            int conversationHistoryTurns,
            boolean showApiKey,
            boolean rememberApiKey,
            boolean enableRemotePlanner,
            boolean autoLayoutBeforeApply,
            boolean includeGraphContext,
            boolean includePlayerWorldContext,
            boolean includeSelectedWorldRegionContext,
            boolean previewOnlyMode,
            boolean patchApplyMode,
            boolean patchRemoveScopedConnections,
            boolean enterToSend,
            boolean debugLoggingEnabled,
            boolean includePromptPreviewInDebug
    ) {
    }

    public record LoadResult(AiSettingsData data, String statusMessage) {
    }

    public static AiSettingsData defaults() {
        return new AiSettingsData(
                "https://api.openai.com/v1",
                "",
                "gpt-4.1-mini",
                PROVIDER_AUTO,
                "You are a NodeCraft graph planning assistant.",
                2048,
                60,
                6,
                false,
                false,
                false,
                true,
                true,
                false,
                false,
                false,
                false,
                false,
                true,
                false,
                false
        );
    }

    public static Path resolveSettingsPath() {
        try {
            Path gameDir = FabricLoader.getInstance().getGameDir();
            return gameDir.resolve("nodecraft").resolve("config").resolve("ai_settings.json");
        } catch (IllegalStateException e) {
            NodeCraft.LOGGER.warn("Fabric game directory unavailable, falling back to local AI settings path.");
            return Paths.get("nodecraft", "config", "ai_settings.json");
        }
    }

    public static LoadResult load(Path settingsPath) {
        AiSettingsData current = defaults();
        try {
            if (settingsPath == null || !Files.exists(settingsPath)) {
                return new LoadResult(current, "AI settings file not found, using defaults.");
            }

            String json = Files.readString(settingsPath, StandardCharsets.UTF_8);
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root == null) {
                return new LoadResult(current, "AI settings file is empty, using defaults.");
            }

            boolean rememberApiKey = root.has("rememberApiKey") && root.get("rememberApiKey").getAsBoolean();
            current = new AiSettingsData(
                    root.has("apiBaseUrl") ? root.get("apiBaseUrl").getAsString() : current.apiBaseUrl(),
                    rememberApiKey && root.has("apiKey") ? root.get("apiKey").getAsString() : current.apiKey(),
                    root.has("model") ? root.get("model").getAsString() : current.model(),
                        root.has("providerStrategy")
                            ? sanitizeProviderStrategy(root.get("providerStrategy").getAsString())
                            : current.providerStrategy(),
                    root.has("systemPrompt") ? root.get("systemPrompt").getAsString() : current.systemPrompt(),
                        root.has("maxOutputTokens")
                            ? clampMaxOutputTokens(root.get("maxOutputTokens").getAsInt())
                            : current.maxOutputTokens(),
                    root.has("timeoutSeconds") ? clampTimeout(root.get("timeoutSeconds").getAsInt()) : current.timeoutSeconds(),
                    root.has("conversationHistoryTurns")
                            ? clampConversationHistoryTurns(root.get("conversationHistoryTurns").getAsInt())
                            : current.conversationHistoryTurns(),
                    root.has("showApiKey") && root.get("showApiKey").getAsBoolean(),
                    rememberApiKey,
                    root.has("enableRemotePlanner") && root.get("enableRemotePlanner").getAsBoolean(),
                    !root.has("autoLayoutBeforeApply") || root.get("autoLayoutBeforeApply").getAsBoolean(),
                    !root.has("includeGraphContext") || root.get("includeGraphContext").getAsBoolean(),
                    root.has("includePlayerWorldContext") && root.get("includePlayerWorldContext").getAsBoolean(),
                    root.has("includeSelectedWorldRegionContext") && root.get("includeSelectedWorldRegionContext").getAsBoolean(),
                    root.has("previewOnlyMode") && root.get("previewOnlyMode").getAsBoolean(),
                    root.has("patchApplyMode") && root.get("patchApplyMode").getAsBoolean(),
                    root.has("patchRemoveScopedConnections") && root.get("patchRemoveScopedConnections").getAsBoolean(),
                    !root.has("enterToSend") || root.get("enterToSend").getAsBoolean(),
                    root.has("debugLoggingEnabled") && root.get("debugLoggingEnabled").getAsBoolean(),
                    root.has("includePromptPreviewInDebug") && root.get("includePromptPreviewInDebug").getAsBoolean()
            );

            return new LoadResult(current, "AI settings loaded from disk.");
        } catch (Exception e) {
            NodeCraft.LOGGER.error("Failed to load AI settings", e);
            return new LoadResult(current, "Failed to load AI settings: " + e.getMessage());
        }
    }

    public static String save(Path settingsPath, AiSettingsData data) {
        try {
            if (settingsPath == null || data == null) {
                return "Failed to save AI settings: invalid settings path or data.";
            }

            Path parent = settingsPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            JsonObject root = new JsonObject();
            root.addProperty("apiBaseUrl", safe(data.apiBaseUrl()));
            root.addProperty("apiKey", data.rememberApiKey() ? safe(data.apiKey()) : "");
            root.addProperty("model", safe(data.model()));
            root.addProperty("providerStrategy", sanitizeProviderStrategy(data.providerStrategy()));
            root.addProperty("systemPrompt", safe(data.systemPrompt()));
            root.addProperty("maxOutputTokens", clampMaxOutputTokens(data.maxOutputTokens()));
            root.addProperty("timeoutSeconds", clampTimeout(data.timeoutSeconds()));
            root.addProperty("conversationHistoryTurns", clampConversationHistoryTurns(data.conversationHistoryTurns()));
            root.addProperty("showApiKey", data.showApiKey());
            root.addProperty("rememberApiKey", data.rememberApiKey());
            root.addProperty("enableRemotePlanner", data.enableRemotePlanner());
            root.addProperty("autoLayoutBeforeApply", data.autoLayoutBeforeApply());
            root.addProperty("includeGraphContext", data.includeGraphContext());
            root.addProperty("includePlayerWorldContext", data.includePlayerWorldContext());
            root.addProperty("includeSelectedWorldRegionContext", data.includeSelectedWorldRegionContext());
            root.addProperty("previewOnlyMode", data.previewOnlyMode());
            root.addProperty("patchApplyMode", data.patchApplyMode());
            root.addProperty("patchRemoveScopedConnections", data.patchRemoveScopedConnections());
            root.addProperty("enterToSend", data.enterToSend());
            root.addProperty("debugLoggingEnabled", data.debugLoggingEnabled());
            root.addProperty("includePromptPreviewInDebug", data.includePromptPreviewInDebug());

            Files.writeString(settingsPath, GSON.toJson(root), StandardCharsets.UTF_8);
            return "AI settings saved.";
        } catch (Exception e) {
            NodeCraft.LOGGER.error("Failed to save AI settings", e);
            return "Failed to save AI settings: " + e.getMessage();
        }
    }

    public static String validate(AiSettingsData data) {
        if (data == null) {
            return "Validation failed: AI settings data is missing.";
        }
        if (!data.enableRemotePlanner()) {
            return "Remote planner is disabled. Local mock planner remains active.";
        }
        if (isBlank(data.apiBaseUrl())) {
            return "Validation failed: API Base URL is required when remote planner is enabled.";
        }
        if (isBlank(resolveApiKey(data))) {
            return "Validation failed: API Key is required when remote planner is enabled. Set it in settings or via "
                    + API_KEY_ENV + ", " + OPENAI_API_KEY_ENV + ", or " + ANTHROPIC_API_KEY_ENV + ".";
        }
        if (isBlank(data.model())) {
            return "Validation failed: Model is required when remote planner is enabled.";
        }
        if (data.maxOutputTokens() < 512 || data.maxOutputTokens() > 4096) {
            return "Validation failed: Max output tokens must be between 512 and 4096.";
        }
        String provider = sanitizeProviderStrategy(data.providerStrategy());
        if (!PROVIDER_AUTO.equals(provider)
                && !PROVIDER_OPENAI_COMPAT.equals(provider)
                && !PROVIDER_ANTHROPIC.equals(provider)) {
            return "Validation failed: Provider strategy must be AUTO, OPENAI_COMPAT, or ANTHROPIC.";
        }
        if (!data.apiBaseUrl().startsWith("http://") && !data.apiBaseUrl().startsWith("https://")) {
            return "Validation failed: API Base URL must start with http:// or https://.";
        }
        return "AI settings look valid. Remote planner wiring can now use these values.";
    }

    public static String buildSummary(AiSettingsData data) {
        if (data == null) {
            return "Planner: Unknown";
        }
        String plannerMode = data.enableRemotePlanner() ? "Planner: Remote" : "Planner: Local";
        String modelName = isBlank(data.model()) ? "(no model)" : data.model();
        String provider = sanitizeProviderStrategy(data.providerStrategy());
        String keyStatus = isBlank(data.apiKey())
                ? (isBlank(resolveApiKey(data)) ? "API Key: missing" : "API Key: env")
                : (data.rememberApiKey() ? "API Key: saved" : "API Key: session");
        String layoutMode = data.autoLayoutBeforeApply() ? "Layout: Auto" : "Layout: Plan";
        return plannerMode + " | Provider: " + provider + " | Model: " + modelName + " | MaxTokens: "
            + clampMaxOutputTokens(data.maxOutputTokens()) + " | " + keyStatus + " | " + layoutMode;
    }

    public static String resolveApiKey(AiSettingsData data) {
        if (data != null && !isBlank(data.apiKey())) {
            return data.apiKey();
        }
        String key = System.getenv(API_KEY_ENV);
        if (!isBlank(key)) {
            return key;
        }
        String provider = data == null ? PROVIDER_AUTO : sanitizeProviderStrategy(data.providerStrategy());
        if (PROVIDER_ANTHROPIC.equals(provider)) {
            key = System.getenv(ANTHROPIC_API_KEY_ENV);
            if (!isBlank(key)) {
                return key;
            }
        }
        key = System.getenv(OPENAI_API_KEY_ENV);
        if (!isBlank(key)) {
            return key;
        }
        key = System.getenv(ANTHROPIC_API_KEY_ENV);
        return isBlank(key) ? "" : key;
    }

    private static String safe(String text) {
        return text == null ? "" : text;
    }

    private static int clampTimeout(int timeoutSeconds) {
        return Math.max(5, Math.min(600, timeoutSeconds));
    }

    private static int clampConversationHistoryTurns(int turns) {
        return Math.max(1, Math.min(20, turns));
    }

    private static int clampMaxOutputTokens(int maxOutputTokens) {
        return Math.max(512, Math.min(4096, maxOutputTokens));
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    private static String sanitizeProviderStrategy(String providerStrategy) {
        if (providerStrategy == null) {
            return PROVIDER_AUTO;
        }
        String normalized = providerStrategy.trim().toUpperCase();
        return switch (normalized) {
            case PROVIDER_OPENAI_COMPAT -> PROVIDER_OPENAI_COMPAT;
            case PROVIDER_ANTHROPIC -> PROVIDER_ANTHROPIC;
            default -> PROVIDER_AUTO;
        };
    }
}

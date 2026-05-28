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

    private AiSettingsStore() {
    }

    public record AiSettingsData(
            String apiBaseUrl,
            String apiKey,
            String model,
            String systemPrompt,
            int timeoutSeconds,
            int conversationHistoryTurns,
            boolean showApiKey,
            boolean enableRemotePlanner,
            boolean autoLayoutBeforeApply,
            boolean includeGraphContext,
            boolean previewOnlyMode,
            boolean patchApplyMode,
            boolean patchRemoveScopedConnections
    ) {
    }

    public record LoadResult(AiSettingsData data, String statusMessage) {
    }

    public static AiSettingsData defaults() {
        return new AiSettingsData(
                "https://api.openai.com/v1",
                "",
                "gpt-4.1-mini",
                "You are a NodeCraft graph planning assistant.",
                60,
                6,
                false,
                false,
                true,
                true,
                false,
                true,
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

            current = new AiSettingsData(
                    root.has("apiBaseUrl") ? root.get("apiBaseUrl").getAsString() : current.apiBaseUrl(),
                    root.has("apiKey") ? root.get("apiKey").getAsString() : current.apiKey(),
                    root.has("model") ? root.get("model").getAsString() : current.model(),
                    root.has("systemPrompt") ? root.get("systemPrompt").getAsString() : current.systemPrompt(),
                    root.has("timeoutSeconds") ? clampTimeout(root.get("timeoutSeconds").getAsInt()) : current.timeoutSeconds(),
                        root.has("conversationHistoryTurns")
                            ? clampConversationHistoryTurns(root.get("conversationHistoryTurns").getAsInt())
                            : current.conversationHistoryTurns(),
                    root.has("showApiKey") && root.get("showApiKey").getAsBoolean(),
                    root.has("enableRemotePlanner") && root.get("enableRemotePlanner").getAsBoolean(),
                    !root.has("autoLayoutBeforeApply") || root.get("autoLayoutBeforeApply").getAsBoolean(),
                    !root.has("includeGraphContext") || root.get("includeGraphContext").getAsBoolean(),
                    root.has("previewOnlyMode") && root.get("previewOnlyMode").getAsBoolean(),
                    !root.has("patchApplyMode") || root.get("patchApplyMode").getAsBoolean(),
                    root.has("patchRemoveScopedConnections") && root.get("patchRemoveScopedConnections").getAsBoolean()
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
            root.addProperty("apiKey", safe(data.apiKey()));
            root.addProperty("model", safe(data.model()));
            root.addProperty("systemPrompt", safe(data.systemPrompt()));
            root.addProperty("timeoutSeconds", clampTimeout(data.timeoutSeconds()));
            root.addProperty("conversationHistoryTurns", clampConversationHistoryTurns(data.conversationHistoryTurns()));
            root.addProperty("showApiKey", data.showApiKey());
            root.addProperty("enableRemotePlanner", data.enableRemotePlanner());
            root.addProperty("autoLayoutBeforeApply", data.autoLayoutBeforeApply());
            root.addProperty("includeGraphContext", data.includeGraphContext());
            root.addProperty("previewOnlyMode", data.previewOnlyMode());
            root.addProperty("patchApplyMode", data.patchApplyMode());
            root.addProperty("patchRemoveScopedConnections", data.patchRemoveScopedConnections());

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
        if (isBlank(data.apiKey())) {
            return "Validation failed: API Key is required when remote planner is enabled.";
        }
        if (isBlank(data.model())) {
            return "Validation failed: Model is required when remote planner is enabled.";
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
        String keyStatus = isBlank(data.apiKey()) ? "API Key: missing" : "API Key: set";
        String layoutMode = data.autoLayoutBeforeApply() ? "Layout: Auto" : "Layout: Plan";
        return plannerMode + " | Model: " + modelName + " | " + keyStatus + " | " + layoutMode;
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

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}

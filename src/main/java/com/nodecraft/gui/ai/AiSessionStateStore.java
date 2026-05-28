package com.nodecraft.gui.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.nodecraft.core.NodeCraft;
import org.jspecify.annotations.NonNull;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public final class AiSessionStateStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "ai_session_state.json";

    private AiSessionStateStore() {
    }

    public record ChatMessageData(String role, String content, long timestampMs) {
    }

    public record AiSessionStateData(List<ChatMessageData> chatMessages, String pendingPlanDslJson) {
    }

    public record LoadResult(AiSessionStateData data, String statusMessage) {
    }

    public static AiSessionStateData defaults() {
        return new AiSessionStateData(List.of(), "");
    }

    public static Path resolveSessionStatePath(Path aiSettingsPath) {
        if (aiSettingsPath != null && aiSettingsPath.getParent() != null) {
            return aiSettingsPath.getParent().resolve(FILE_NAME);
        }
        return Paths.get("nodecraft", "config", FILE_NAME);
    }

    public static LoadResult load(Path sessionPath) {
        AiSessionStateData current = defaults();
        try {
            if (sessionPath == null || !Files.exists(sessionPath)) {
                return new LoadResult(current, "AI session file not found, using empty session.");
            }

            String json = Files.readString(sessionPath, StandardCharsets.UTF_8);
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root == null) {
                return new LoadResult(current, "AI session file is empty, using empty session.");
            }

            List<ChatMessageData> messages = new ArrayList<>();
            if (root.has("chatMessages") && root.get("chatMessages").isJsonArray()) {
                JsonArray messageArray = root.getAsJsonArray("chatMessages");
                for (int i = 0; i < messageArray.size(); i++) {
                    if (!messageArray.get(i).isJsonObject()) {
                        continue;
                    }
                    JsonObject messageObj = messageArray.get(i).getAsJsonObject();
                    String role = messageObj.has("role") ? messageObj.get("role").getAsString() : "assistant";
                    String content = messageObj.has("content") ? messageObj.get("content").getAsString() : "";
                    long timestampMs = messageObj.has("timestampMs") ? messageObj.get("timestampMs").getAsLong() : System.currentTimeMillis();
                    if (content != null && !content.isBlank()) {
                        messages.add(new ChatMessageData(role, content, timestampMs));
                    }
                }
            }

            String pendingPlanDslJson = root.has("pendingPlanDslJson")
                    ? root.get("pendingPlanDslJson").getAsString()
                    : "";

            current = new AiSessionStateData(messages, pendingPlanDslJson);
            return new LoadResult(current, "AI session state loaded from disk.");
        } catch (Exception e) {
            NodeCraft.LOGGER.error("Failed to load AI session state", e);
            return new LoadResult(current, "Failed to load AI session state: " + e.getMessage());
        }
    }

    public static void save(Path sessionPath, AiSessionStateData data) {
        try {
            if (sessionPath == null || data == null) {
                return;
            }

            Path parent = sessionPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            JsonObject root = getJsonObject(data);
            root.addProperty("pendingPlanDslJson", data.pendingPlanDslJson() == null ? "" : data.pendingPlanDslJson());
            root.addProperty("savedAtMs", System.currentTimeMillis());

            Files.writeString(sessionPath, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception e) {
            NodeCraft.LOGGER.error("Failed to save AI session state", e);
        }
    }

    private static @NonNull JsonObject getJsonObject(AiSessionStateData data) {
        JsonObject root = new JsonObject();
        JsonArray messageArray = new JsonArray();
        if (data.chatMessages() != null) {
            for (ChatMessageData message : data.chatMessages()) {
                if (message == null || message.content() == null || message.content().isBlank()) {
                    continue;
                }
                JsonObject messageObj = new JsonObject();
                messageObj.addProperty("role", message.role() == null ? "assistant" : message.role());
                messageObj.addProperty("content", message.content());
                messageObj.addProperty("timestampMs", message.timestampMs());
                messageArray.add(messageObj);
            }
        }
        root.add("chatMessages", messageArray);
        return root;
    }
}

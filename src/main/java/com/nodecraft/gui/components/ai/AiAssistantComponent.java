package com.nodecraft.gui.components.ai;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.ai.AiRemotePlannerService;
import com.nodecraft.gui.ai.AiSessionStateStore;
import com.nodecraft.gui.components.EditorComponent;
import net.minecraft.client.MinecraftClient;
import org.jspecify.annotations.NonNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * AI assistant state holder component.
 *
 * This component owns core AI panel state so UI/container components can stay thinner
 * and communicate through events instead of direct field coupling.
 */
public class AiAssistantComponent implements EditorComponent {

    private static final String COMPONENT_ID = "ai_assistant";
    private static final int MAX_CHAT_MESSAGES = 120;
    private static final int MAX_CHAT_TOTAL_CHARS = 50000;
    private static final int MAX_CHAT_MESSAGE_CHARS = 3000;

    private boolean visible = true;
    private UUID selectedNodeId = null;

    private final List<AiChatMessage> chatMessages = new ArrayList<>();
    private AiGraphPlan pendingPlan = null;

    private Path sessionStatePath;
    private boolean sessionRestoreInProgress = false;
    private boolean sessionSaveDirty = false;
    private long sessionSaveDueAtMs = 0L;

    private final AiRemotePlannerService remotePlannerService = new AiRemotePlannerService();
    private final RemotePlannerState remotePlannerState = new RemotePlannerState();

    public record AiChatMessage(String role, String content, long timestampMs) {
    }

    public record AiPlanNode(String ref, String typeId, float offsetX, float offsetY, Object nodeState) {
    }

    public record AiPlanConnection(String sourceRef, String sourcePortId, String targetRef, String targetPortId) {
    }

    public record AiGraphPlan(String summary, List<AiPlanNode> nodes, List<AiPlanConnection> connections, List<String> validationErrors) {
        boolean isValid() {
            return validationErrors == null || validationErrors.isEmpty();
        }
    }

    public record RemotePollResult(String prompt, AiRemotePlannerService.RemotePlanResult result, String exceptionMessage) {
        public boolean hasException() {
            return exceptionMessage != null && !exceptionMessage.isBlank();
        }
    }

    public record RemotePlannerSnapshot(
            String rawResponse,
            String modelText,
            String requestSnapshot,
            String errorCategory,
            String errorMessage,
            int statusCode,
            int attempts
    ) {
    }

    @FunctionalInterface
    public interface PendingPlanSerializer {
        String serialize(AiGraphPlan plan);
    }

    @FunctionalInterface
    public interface PendingPlanDeserializer {
        AiGraphPlan deserialize(String dslJson);
    }

    @Override
    public void render(float x, float y, float width, float height, float paddingX, float paddingY) {
        // Rendered by the host container (PropertyPanelComponent) for now.
    }

    @Override
    public void init() {
        // No-op.
    }

    @Override
    public void cleanup() {
        flushSessionStateIfDue(0L, null);
        chatMessages.clear();
        pendingPlan = null;
        selectedNodeId = null;
        cancelRemotePlannerRequest();
        remotePlannerService.shutdown();
        clearRemoteDebugState();
        sessionRestoreInProgress = false;
        sessionSaveDirty = false;
        sessionSaveDueAtMs = 0L;
    }

    @Override
    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public String getComponentId() {
        return COMPONENT_ID;
    }

    @Override
    public boolean handleEvent(String eventType, Object data) {
        switch (eventType) {
            case "nodeSelected" -> {
                if (data instanceof UUID nodeId) {
                    selectedNodeId = nodeId;
                    return true;
                }
                if (data == null) {
                    selectedNodeId = null;
                    return true;
                }
            }
            case "nodeSelectionCleared", "graphChanged" -> {
                selectedNodeId = null;
                return true;
            }
            default -> {
                // no-op
            }
        }
        return false;
    }

    public UUID getSelectedNodeId() {
        return selectedNodeId;
    }

    public List<AiChatMessage> getChatMessages() {
        return chatMessages;
    }

    public void setChatMessages(List<AiChatMessage> messages) {
        chatMessages.clear();
        if (messages != null && !messages.isEmpty()) {
            for (AiChatMessage message : messages) {
                if (message == null) {
                    continue;
                }
                addChatMessage(message.role(), message.content(), message.timestampMs());
            }
        }
        trimChatMessagesForCapacity();
    }

    public void clearConversationState() {
        chatMessages.clear();
        pendingPlan = null;
        clearRemoteDebugState();
    }

    public void addChatMessage(String role, String content, long timestampMs) {
        if (content == null || content.isBlank()) {
            return;
        }

        String compacted = compactChatContent(content);
        if (compacted.isBlank()) {
            return;
        }

        chatMessages.add(new AiChatMessage(role == null ? "assistant" : role, compacted, timestampMs));
        trimChatMessagesForCapacity();
    }

    public AiGraphPlan getPendingPlan() {
        return pendingPlan;
    }

    public void setPendingPlan(AiGraphPlan pendingPlan) {
        this.pendingPlan = pendingPlan;
    }

    public void initializeSessionStore(Path aiSettingsPath) {
        sessionStatePath = AiSessionStateStore.resolveSessionStatePath(aiSettingsPath);
    }

    public String loadSessionState(PendingPlanDeserializer deserializer) {
        if (sessionStatePath == null) {
            return "AI session state path is not initialized.";
        }

        AiSessionStateStore.LoadResult result = AiSessionStateStore.load(sessionStatePath);
        applySessionStateData(result.data(), deserializer);
        return result.statusMessage();
    }

    public void queueSessionStateSave(long debounceMs) {
        if (sessionRestoreInProgress) {
            return;
        }
        sessionSaveDirty = true;
        sessionSaveDueAtMs = System.currentTimeMillis() + debounceMs;
    }

    public void flushSessionStateIfDue(long debounceMs, PendingPlanSerializer serializer) {
        if (!sessionSaveDirty) {
            return;
        }
        if (debounceMs > 0L && System.currentTimeMillis() < sessionSaveDueAtMs) {
            return;
        }
        saveSessionStateNow(serializer);
    }

    public void saveSessionStateNow(PendingPlanSerializer serializer) {
        if (sessionRestoreInProgress || sessionStatePath == null) {
            return;
        }

        List<AiSessionStateStore.ChatMessageData> messages = getChatMessageData();

        String pendingPlanDslJson = "";
        if (pendingPlan != null && serializer != null) {
            pendingPlanDslJson = serializer.serialize(pendingPlan);
        }
        if (pendingPlanDslJson == null) {
            pendingPlanDslJson = "";
        }

        AiSessionStateStore.save(sessionStatePath, new AiSessionStateStore.AiSessionStateData(messages, pendingPlanDslJson));
        sessionSaveDirty = false;
        sessionSaveDueAtMs = 0L;
    }

    private @NonNull List<AiSessionStateStore.ChatMessageData> getChatMessageData() {
        List<AiSessionStateStore.ChatMessageData> messages = new ArrayList<>(chatMessages.size());
        for (AiChatMessage message : chatMessages) {
            if (message == null || message.content() == null || message.content().isBlank()) {
                continue;
            }
            messages.add(new AiSessionStateStore.ChatMessageData(
                    message.role() == null ? "assistant" : message.role(),
                    message.content(),
                    message.timestampMs()
            ));
        }
        return messages;
    }

    private void applySessionStateData(AiSessionStateStore.AiSessionStateData data, PendingPlanDeserializer deserializer) {
        sessionRestoreInProgress = true;
        try {
            chatMessages.clear();
            pendingPlan = null;

            if (data == null) {
                return;
            }

            List<AiSessionStateStore.ChatMessageData> messages = data.chatMessages();
            if (messages != null) {
                for (AiSessionStateStore.ChatMessageData message : messages) {
                    if (message == null || message.content() == null || message.content().isBlank()) {
                        continue;
                    }
                        addChatMessage(
                            message.role() == null ? "assistant" : message.role(),
                            message.content(),
                            message.timestampMs()
                        );
                }
            }

            String pendingPlanDslJson = data.pendingPlanDslJson();
            if (deserializer != null && pendingPlanDslJson != null && !pendingPlanDslJson.isBlank()) {
                try {
                    pendingPlan = deserializer.deserialize(pendingPlanDslJson);
                } catch (Exception e) {
                    NodeCraft.LOGGER.warn("Failed to deserialize persisted AI pending plan", e);
                    pendingPlan = null;
                }
            }
        } finally {
            trimChatMessagesForCapacity();
            sessionRestoreInProgress = false;
        }
    }

    private String compactChatContent(String content) {
        String normalized = content == null ? "" : content.strip();
        if (normalized.isBlank()) {
            return "";
        }

        if (normalized.length() <= MAX_CHAT_MESSAGE_CHARS) {
            return normalized;
        }
        return normalized.substring(0, MAX_CHAT_MESSAGE_CHARS) + "\n...[truncated, original chars=" + normalized.length() + "]";
    }

    private void trimChatMessagesForCapacity() {
        while (chatMessages.size() > MAX_CHAT_MESSAGES) {
            chatMessages.removeFirst();
        }

        int totalChars = 0;
        for (AiChatMessage message : chatMessages) {
            totalChars += message.content() == null ? 0 : message.content().length();
        }

        while (totalChars > MAX_CHAT_TOTAL_CHARS && !chatMessages.isEmpty()) {
            AiChatMessage removed = chatMessages.removeFirst();
            totalChars -= removed.content() == null ? 0 : removed.content().length();
        }
    }

    public boolean isSessionRestoreInProgress() {
        return sessionRestoreInProgress;
    }

    public boolean submitRemotePlannerRequest(
            String prompt,
            AiRemotePlannerService.PlannerConfig config,
            List<AiRemotePlannerService.ConversationMessage> conversationHistory,
            String requestSnapshot
    ) {
        if (!remotePlannerState.beginRequest(prompt, requestSnapshot)) {
            return false;
        }

        remotePlannerState.setRemotePlanFuture(
                remotePlannerService.requestPlanAsync(config, conversationHistory, this::appendStreamingTokenOnClientThread)
        );
        return true;
    }

    public CompletableFuture<AiRemotePlannerService.RemotePlanResult> testRemoteConnectionAsync(
            AiRemotePlannerService.PlannerConfig config
    ) {
        return remotePlannerService.testConnectionAsync(config);
    }

    public RemotePollResult pollRemotePlannerResultIfReady() {
        return remotePlannerState.pollRemotePlannerResultIfReady();
    }

    public boolean isRemotePlannerBusy() {
        return remotePlannerState.isBusy();
    }

    public void cancelRemotePlannerRequest() {
        remotePlannerState.cancelRemotePlannerRequest();
    }

    public void clearRemoteDebugState() {
        remotePlannerState.clearRemoteDebugState();
    }

    public String getRemoteStreamingBuffer() {
        return remotePlannerState.getRemoteStreamingBuffer();
    }

    public RemotePlannerSnapshot getRemotePlannerSnapshot() {
        return remotePlannerState.snapshot();
    }

    private void appendStreamingToken(String token) {
        remotePlannerState.appendStreamingToken(token);
    }

    private void appendStreamingTokenOnClientThread(String token) {
        runOnClientThread(() -> appendStreamingToken(token));
    }

    private void runOnClientThread(Runnable action) {
        if (action == null) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            action.run();
            return;
        }
        if (client.isOnThread()) {
            action.run();
            return;
        }
        client.execute(action);
    }

    public String getLastRemoteRawResponse() {
        return getRemotePlannerSnapshot().rawResponse();
    }

    public String getLastRemoteModelText() {
        return getRemotePlannerSnapshot().modelText();
    }

    public String getLastRemoteRequestSnapshot() {
        return getRemotePlannerSnapshot().requestSnapshot();
    }

    public String getLastRemoteErrorCategory() {
        return getRemotePlannerSnapshot().errorCategory();
    }

    public String getLastRemoteErrorMessage() {
        return getRemotePlannerSnapshot().errorMessage();
    }

    public int getLastRemoteStatusCode() {
        return getRemotePlannerSnapshot().statusCode();
    }

    public int getLastRemoteAttempts() {
        return getRemotePlannerSnapshot().attempts();
    }
}

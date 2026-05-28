package com.nodecraft.gui.components;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.ai.AiRemotePlannerService;
import com.nodecraft.gui.ai.AiSessionStateStore;
import com.nodecraft.gui.ai.AiSettingsStore;
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

    private boolean visible = true;
    private UUID selectedNodeId = null;

    private final List<AiChatMessage> chatMessages = new ArrayList<>();
    private AiGraphPlan pendingPlan = null;

    private Path sessionStatePath;
    private boolean sessionRestoreInProgress = false;
    private boolean sessionSaveDirty = false;
    private long sessionSaveDueAtMs = 0L;

    private final AiRemotePlannerService remotePlannerService = new AiRemotePlannerService();
    private CompletableFuture<AiRemotePlannerService.RemotePlanResult> remotePlanFuture = null;
    private String remotePendingPrompt = "";
    private String lastRemoteRawResponse = "";
    private String lastRemoteModelText = "";
    private String lastRemoteRequestSnapshot = "";
    private String lastRemoteErrorCategory = "";
    private String lastRemoteErrorMessage = "";
    private int lastRemoteStatusCode = 0;
    private int lastRemoteAttempts = 0;

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
            chatMessages.addAll(messages);
        }
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
                    chatMessages.add(new AiChatMessage(
                            message.role() == null ? "assistant" : message.role(),
                            message.content(),
                            message.timestampMs()
                    ));
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
            sessionRestoreInProgress = false;
        }
    }

    public boolean isSessionRestoreInProgress() {
        return sessionRestoreInProgress;
    }

    public void submitRemotePlannerRequest(
            String prompt,
            AiRemotePlannerService.PlannerConfig config,
            List<AiRemotePlannerService.ConversationMessage> conversationHistory,
            String requestSnapshot
    ) {
        remotePendingPrompt = prompt == null ? "" : prompt;
        lastRemoteRawResponse = "";
        lastRemoteModelText = "";
        lastRemoteRequestSnapshot = requestSnapshot == null ? "" : requestSnapshot;
        lastRemoteErrorCategory = "";
        lastRemoteErrorMessage = "";
        lastRemoteStatusCode = 0;
        lastRemoteAttempts = 0;
        remotePlanFuture = remotePlannerService.requestPlanAsync(config, conversationHistory);
    }

    public RemotePollResult pollRemotePlannerResultIfReady() {
        if (remotePlanFuture == null || !remotePlanFuture.isDone()) {
            return null;
        }

        AiRemotePlannerService.RemotePlanResult result;
        try {
            result = remotePlanFuture.join();
        } catch (Exception e) {
            String prompt = remotePendingPrompt;
            remotePlanFuture = null;
            remotePendingPrompt = "";
            return new RemotePollResult(prompt, null, e.getMessage());
        }

        String prompt = remotePendingPrompt;
        remotePlanFuture = null;
        remotePendingPrompt = "";
        lastRemoteAttempts = result.attempts();
        lastRemoteErrorCategory = result.errorCategory();
        lastRemoteErrorMessage = result.errorMessage() == null ? "" : result.errorMessage();
        lastRemoteStatusCode = result.statusCode();
        lastRemoteRawResponse = result.rawResponse() == null ? "" : result.rawResponse();
        lastRemoteModelText = result.modelContent() == null ? "" : result.modelContent();
        return new RemotePollResult(prompt, result, null);
    }

    public boolean isRemotePlannerBusy() {
        return remotePlanFuture != null && !remotePlanFuture.isDone();
    }

    public void cancelRemotePlannerRequest() {
        if (remotePlanFuture != null && !remotePlanFuture.isDone()) {
            remotePlanFuture.cancel(true);
        }
        remotePlanFuture = null;
        remotePendingPrompt = "";
    }

    public void clearRemoteDebugState() {
        lastRemoteRawResponse = "";
        lastRemoteModelText = "";
        lastRemoteRequestSnapshot = "";
        lastRemoteErrorCategory = "";
        lastRemoteErrorMessage = "";
        lastRemoteStatusCode = 0;
        lastRemoteAttempts = 0;
    }

    public String getLastRemoteRawResponse() {
        return lastRemoteRawResponse;
    }

    public String getLastRemoteModelText() {
        return lastRemoteModelText;
    }

    public String getLastRemoteRequestSnapshot() {
        return lastRemoteRequestSnapshot;
    }

    public String getLastRemoteErrorCategory() {
        return lastRemoteErrorCategory;
    }

    public String getLastRemoteErrorMessage() {
        return lastRemoteErrorMessage;
    }

    public int getLastRemoteStatusCode() {
        return lastRemoteStatusCode;
    }

    public int getLastRemoteAttempts() {
        return lastRemoteAttempts;
    }
}

package com.nodecraft.gui.components.ai;

import com.nodecraft.gui.ai.AiRemotePlannerService;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

final class RemotePlannerState {

    private final AtomicReference<CompletableFuture<AiRemotePlannerService.RemotePlanResult>> remotePlanFutureRef = new AtomicReference<>(null);
    private volatile String remotePendingPrompt = "";
    private volatile String lastRemoteRawResponse = "";
    private volatile String lastRemoteModelText = "";
    private volatile String lastRemoteRequestSnapshot = "";
    private volatile String lastRemoteErrorCategory = "";
    private volatile String lastRemoteErrorMessage = "";
    private volatile int lastRemoteStatusCode = 0;
    private volatile int lastRemoteAttempts = 0;
    private final StringBuilder remoteStreamingBuffer = new StringBuilder();

    boolean isBusy() {
        CompletableFuture<AiRemotePlannerService.RemotePlanResult> future = remotePlanFutureRef.get();
        return future != null && !future.isDone();
    }

    boolean beginRequest(String prompt, String requestSnapshot) {
        if (isBusy()) {
            lastRemoteErrorCategory = "request";
            lastRemoteErrorMessage = "Remote planner is already running.";
            return false;
        }

        remotePendingPrompt = prompt == null ? "" : prompt;
        lastRemoteRawResponse = "";
        lastRemoteModelText = "";
        lastRemoteRequestSnapshot = requestSnapshot == null ? "" : requestSnapshot;
        lastRemoteErrorCategory = "";
        lastRemoteErrorMessage = "";
        lastRemoteStatusCode = 0;
        lastRemoteAttempts = 0;
        clearStreamingBuffer();
        return true;
    }

    void setRemotePlanFuture(CompletableFuture<AiRemotePlannerService.RemotePlanResult> future) {
        remotePlanFutureRef.set(future);
    }

    AiAssistantComponent.RemotePollResult pollRemotePlannerResultIfReady() {
        CompletableFuture<AiRemotePlannerService.RemotePlanResult> future = remotePlanFutureRef.get();
        if (future == null || !future.isDone()) {
            return null;
        }

        String prompt = remotePendingPrompt;
        AiRemotePlannerService.RemotePlanResult result;
        try {
            result = future.join();
        } catch (Exception e) {
            remotePlanFutureRef.set(null);
            remotePendingPrompt = "";
            clearStreamingBuffer();
            lastRemoteErrorCategory = "request";
            lastRemoteErrorMessage = e.getMessage() == null ? "Remote planner failed." : e.getMessage();
            return new AiAssistantComponent.RemotePollResult(prompt, null, lastRemoteErrorMessage);
        }

        remotePlanFutureRef.set(null);
        remotePendingPrompt = "";
        lastRemoteAttempts = result.attempts();
        lastRemoteErrorCategory = result.errorCategory();
        lastRemoteErrorMessage = result.errorMessage() == null ? "" : result.errorMessage();
        lastRemoteStatusCode = result.statusCode();
        lastRemoteRawResponse = result.rawResponse() == null ? "" : result.rawResponse();
        lastRemoteModelText = result.modelContent() == null ? "" : result.modelContent();
        clearStreamingBuffer();
        return new AiAssistantComponent.RemotePollResult(prompt, result, null);
    }

    void cancelRemotePlannerRequest() {
        CompletableFuture<AiRemotePlannerService.RemotePlanResult> future = remotePlanFutureRef.getAndSet(null);
        if (future != null && !future.isDone()) {
            future.cancel(true);
        }
        remotePendingPrompt = "";
        clearStreamingBuffer();
    }

    void clearRemoteDebugState() {
        lastRemoteRawResponse = "";
        lastRemoteModelText = "";
        lastRemoteRequestSnapshot = "";
        lastRemoteErrorCategory = "";
        lastRemoteErrorMessage = "";
        lastRemoteStatusCode = 0;
        lastRemoteAttempts = 0;
        clearStreamingBuffer();
    }

    String getRemoteStreamingBuffer() {
        synchronized (remoteStreamingBuffer) {
            return remoteStreamingBuffer.toString();
        }
    }

    void appendStreamingToken(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        synchronized (remoteStreamingBuffer) {
            remoteStreamingBuffer.append(token);
            if (remoteStreamingBuffer.length() > 6000) {
                int excess = remoteStreamingBuffer.length() - 6000;
                remoteStreamingBuffer.delete(0, excess);
            }
        }
    }

    private void clearStreamingBuffer() {
        synchronized (remoteStreamingBuffer) {
            remoteStreamingBuffer.setLength(0);
        }
    }

    String getLastRemoteRawResponse() {
        return lastRemoteRawResponse;
    }

    String getLastRemoteModelText() {
        return lastRemoteModelText;
    }

    String getLastRemoteRequestSnapshot() {
        return lastRemoteRequestSnapshot;
    }

    String getLastRemoteErrorCategory() {
        return lastRemoteErrorCategory;
    }

    String getLastRemoteErrorMessage() {
        return lastRemoteErrorMessage;
    }

    int getLastRemoteStatusCode() {
        return lastRemoteStatusCode;
    }

    int getLastRemoteAttempts() {
        return lastRemoteAttempts;
    }

    AiAssistantComponent.RemotePlannerSnapshot snapshot() {
        return new AiAssistantComponent.RemotePlannerSnapshot(
                lastRemoteRawResponse,
                lastRemoteModelText,
                lastRemoteRequestSnapshot,
                lastRemoteErrorCategory,
                lastRemoteErrorMessage,
                lastRemoteStatusCode,
                lastRemoteAttempts
        );
    }
}
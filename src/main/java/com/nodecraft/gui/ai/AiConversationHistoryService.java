package com.nodecraft.gui.ai;

import java.util.ArrayList;
import java.util.List;

public final class AiConversationHistoryService {

    private static final List<String> STATUS_PREFIXES = List.of(
            "Remote planner request submitted",
            "Retrying last request",
            "Remote planner request canceled",
            "Plan JSON validated",
            "Remote planner fallback applied",
            "Undo completed",
            "Applied AI plan",
            "Patch apply completed"
    );

    private AiConversationHistoryService() {
    }

    public record ChatLine(String role, String content, long timestampMs) {
    }

    public static List<ChatLine> selectRecentPlanningMessages(
            List<ChatLine> allMessages,
            String latestUserPrompt,
            int limit
    ) {
        if (allMessages == null || allMessages.isEmpty() || limit <= 0) {
            return List.of();
        }

        List<ChatLine> recent = new ArrayList<>();
        boolean skippedCurrentUserPrompt = false;
        String normalizedPrompt = latestUserPrompt == null ? "" : latestUserPrompt.trim();

        for (int i = allMessages.size() - 1; i >= 0 && recent.size() < limit; i--) {
            ChatLine message = allMessages.get(i);
            if (!shouldIncludeInHistory(message)) {
                continue;
            }

            if (!skippedCurrentUserPrompt
                    && "user".equalsIgnoreCase(message.role())
                    && message.content().trim().equals(normalizedPrompt)) {
                skippedCurrentUserPrompt = true;
                continue;
            }

            recent.add(0, message);
        }

        return recent;
    }

    public static boolean shouldIncludeInHistory(ChatLine message) {
        if (message == null || message.content() == null || message.content().isBlank()) {
            return false;
        }

        String normalized = message.content().trim();
        for (String prefix : STATUS_PREFIXES) {
            if (normalized.startsWith(prefix)) {
                return false;
            }
        }

        return true;
    }
}

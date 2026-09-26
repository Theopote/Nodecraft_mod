package com.nodecraft.gui.ai;

import java.util.ArrayList;
import java.util.List;

public final class AiConversationHistoryService {

    private static final List<String> STATUS_PREFIXES = List.of(
            "Remote planner request submitted",
            "Retrying last request",
            "Remote planner request canceled",
            "Remote planner failed",
            "Plan JSON validated",
            "Remote planner fallback applied",
            "AI is thinking",
            "AI is generating a local draft plan",
            "Undo completed",
            "Applied AI plan",
            "Patch apply completed"
    );

    private AiConversationHistoryService() {
    }

    public record ChatLine(String role, String content, long timestampMs) {
    }

    public static List<AiRemotePlannerService.ConversationMessage> toConversationMessages(
            List<ChatLine> lines,
            int maxCharsPerMessage,
            int maxTotalChars
    ) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }

        int perMessageLimit = Math.max(200, maxCharsPerMessage);
        int totalLimit = Math.max(perMessageLimit, maxTotalChars);

        List<AiRemotePlannerService.ConversationMessage> result = new ArrayList<>(lines.size());
        int totalChars = 0;

        for (ChatLine line : lines) {
            if (line == null || line.content() == null || line.content().isBlank()) {
                continue;
            }

            String compact = compactMessage(line.content(), perMessageLimit);
            if (compact.isBlank()) {
                continue;
            }

            result.add(new AiRemotePlannerService.ConversationMessage(normalizeRole(line.role()), compact));
            totalChars += compact.length();
        }

        while (result.size() > 1 && totalChars > totalLimit) {
            AiRemotePlannerService.ConversationMessage removed = result.removeFirst();
            totalChars -= removed.content().length();
        }

        return result;
    }

    public static String compactMessage(String text, int maxChars) {
        if (text == null || text.isBlank()) {
            return "";
        }

        int safeMax = Math.max(80, maxChars);
        String normalized = text.trim();
        if (normalized.length() <= safeMax) {
            return normalized;
        }

        int keepHead = Math.max(40, (int) (safeMax * 0.65));
        int keepTail = Math.max(20, safeMax - keepHead - 32);
        int omitted = Math.max(0, normalized.length() - keepHead - keepTail);

        if (keepHead + keepTail >= normalized.length() || omitted <= 0) {
            return normalized.substring(0, safeMax);
        }

        return normalized.substring(0, keepHead)
                + "\n...[truncated " + omitted + " chars]...\n"
                + normalized.substring(normalized.length() - keepTail);
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

            recent.addFirst(message);
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

    private static String normalizeRole(String role) {
        if (role == null) {
            return "user";
        }
        String normalized = role.toLowerCase();
        return "assistant".equals(normalized) ? "assistant" : "user";
    }
}

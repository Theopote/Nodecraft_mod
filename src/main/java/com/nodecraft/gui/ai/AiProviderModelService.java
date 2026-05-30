package com.nodecraft.gui.ai;

import java.util.Locale;

public final class AiProviderModelService {

    private static final String[] OPENAI_MODELS = {
            "gpt-4.1-mini",
            "gpt-4.1",
            "gpt-4o-mini",
            "gpt-4o"
    };
    private static final String[] ANTHROPIC_MODELS = {
            "claude-3-5-haiku-latest",
            "claude-3-7-sonnet-latest",
            "claude-sonnet-4-0"
    };
    private static final String[] DEEPSEEK_MODELS = {
            "deepseek-chat",
            "deepseek-reasoner"
    };
    private static final String[] QWEN_MODELS = {
            "qwen-max",
            "qwen-plus",
            "qwen-turbo",
            "qwen3-32b"
    };
    private static final String[] GROQ_MODELS = {
            "llama-3.3-70b-versatile",
            "llama-3.1-8b-instant",
            "qwen/qwen3-32b"
    };

    private AiProviderModelService() {
    }

    public static String providerStrategyFromIndex(int index, String[] options) {
        if (options == null || options.length == 0) {
            return "";
        }
        int safeIndex = Math.max(0, Math.min(options.length - 1, index));
        return options[safeIndex];
    }

    public static int indexFromProviderStrategy(String strategy, String[] options) {
        if (strategy == null || strategy.isBlank() || options == null || options.length == 0) {
            return 0;
        }
        for (int i = 0; i < options.length; i++) {
            if (options[i].equalsIgnoreCase(strategy)) {
                return i;
            }
        }
        return 0;
    }

    public static String resolveDetectedProviderLabel(String baseUrl) {
        String normalized = normalizeProviderInput(baseUrl);
        if (normalized.isBlank()) {
            return "Unknown";
        }

        if (normalized.contains("deepseek")) {
            return "DeepSeek";
        }
        if (normalized.contains("dashscope") || normalized.contains("aliyuncs") || normalized.contains("qwen")) {
            return "Qwen (DashScope)";
        }
        if (normalized.contains("anthropic")) {
            return "Anthropic";
        }
        if (normalized.contains("groq")) {
            return "Groq";
        }
        return "OpenAI-Compatible";
    }

    public static String[] resolveSuggestedModels(String baseUrl) {
        String normalized = normalizeProviderInput(baseUrl);
        if (normalized.isBlank()) {
            return OPENAI_MODELS;
        }

        if (normalized.contains("deepseek")) {
            return DEEPSEEK_MODELS;
        }
        if (normalized.contains("dashscope") || normalized.contains("aliyuncs") || normalized.contains("qwen")) {
            return QWEN_MODELS;
        }
        if (normalized.contains("anthropic")) {
            return ANTHROPIC_MODELS;
        }
        if (normalized.contains("groq")) {
            return GROQ_MODELS;
        }
        return OPENAI_MODELS;
    }

    public static String normalizeProviderInput(String baseUrl) {
        if (baseUrl == null) {
            return "";
        }
        return baseUrl.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isModelInSuggestions(String model, String[] suggestedModels) {
        if (model == null || suggestedModels == null) {
            return false;
        }
        for (String suggestion : suggestedModels) {
            if (suggestion != null && suggestion.equalsIgnoreCase(model)) {
                return true;
            }
        }
        return false;
    }
}

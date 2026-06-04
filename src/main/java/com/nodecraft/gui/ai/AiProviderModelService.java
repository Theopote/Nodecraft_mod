package com.nodecraft.gui.ai;

import java.util.Locale;

public final class AiProviderModelService {

    private static final String[] OPENAI_MODELS = {
            "gpt-5.2",
            "gpt-5.2-pro",
            "gpt-5.1",
            "gpt-5",
            "gpt-5-mini",
            "gpt-5-nano",
            "gpt-4.1",
            "gpt-4.1-mini",
            "gpt-4.1-nano",
            "o4-mini",
            "o3",
            "gpt-4o",
            "gpt-4o-mini"
    };
    private static final String[] OPENROUTER_MODELS = {
            "openai/gpt-5.2",
            "openai/gpt-5",
            "anthropic/claude-sonnet-4.5",
            "google/gemini-2.5-pro",
            "deepseek/deepseek-chat"
    };
    private static final String[] GEMINI_MODELS = {
            "gemini-2.5-pro",
            "gemini-2.5-flash",
            "gemini-2.5-flash-lite",
            "gemini-2.0-flash"
    };
    private static final String[] OLLAMA_MODELS = {
            "llama3.2",
            "llama3.1",
            "qwen2.5-coder",
            "mistral"
    };
    private static final String[] ANTHROPIC_MODELS = {
            "claude-opus-4-1",
            "claude-opus-4-0",
            "claude-sonnet-4-0",
            "claude-3-7-sonnet-latest",
            "claude-3-5-haiku-latest"
    };
    private static final String[] DEEPSEEK_MODELS = {
            "deepseek-chat",
            "deepseek-reasoner"
    };
    private static final String[] QWEN_MODELS = {
            "qwen-max",
            "qwen-plus",
            "qwen-turbo",
            "qwen3-235b-a22b",
            "qwen3-32b"
    };
    private static final String[] GROQ_MODELS = {
            "llama-3.3-70b-versatile",
            "llama-3.1-8b-instant",
            "qwen/qwen3-32b"
    };

    private static final ProviderPreset[] PROVIDER_PRESETS = {
            new ProviderPreset("OpenAI", "https://api.openai.com/v1", "OPENAI_COMPAT", OPENAI_MODELS),
            new ProviderPreset("Anthropic", "https://api.anthropic.com/v1", "ANTHROPIC", ANTHROPIC_MODELS),
            new ProviderPreset("DeepSeek", "https://api.deepseek.com/v1", "OPENAI_COMPAT", DEEPSEEK_MODELS),
            new ProviderPreset("Qwen (DashScope)", "https://dashscope.aliyuncs.com/compatible-mode/v1", "OPENAI_COMPAT", QWEN_MODELS),
            new ProviderPreset("Groq", "https://api.groq.com/openai/v1", "OPENAI_COMPAT", GROQ_MODELS),
            new ProviderPreset("OpenRouter", "https://openrouter.ai/api/v1", "OPENAI_COMPAT", OPENROUTER_MODELS),
            new ProviderPreset("Gemini OpenAI-Compatible", "https://generativelanguage.googleapis.com/v1beta/openai", "OPENAI_COMPAT", GEMINI_MODELS),
            new ProviderPreset("Ollama Local", "http://localhost:11434/v1", "OPENAI_COMPAT", OLLAMA_MODELS),
            new ProviderPreset("Custom OpenAI-Compatible", "", "OPENAI_COMPAT", OPENAI_MODELS)
    };

    public record ProviderPreset(String label, String baseUrl, String providerStrategy, String[] models) {
    }

    private AiProviderModelService() {
    }

    public static ProviderPreset[] providerPresets() {
        return PROVIDER_PRESETS;
    }

    public static String[] providerPresetLabels() {
        String[] labels = new String[PROVIDER_PRESETS.length];
        for (int i = 0; i < PROVIDER_PRESETS.length; i++) {
            labels[i] = PROVIDER_PRESETS[i].label();
        }
        return labels;
    }

    public static int resolveProviderPresetIndex(String baseUrl) {
        String normalized = normalizeProviderInput(baseUrl);
        if (normalized.isBlank()) {
            return PROVIDER_PRESETS.length - 1;
        }

        for (int i = 0; i < PROVIDER_PRESETS.length; i++) {
            String presetBaseUrl = normalizeProviderInput(PROVIDER_PRESETS[i].baseUrl());
            if (!presetBaseUrl.isBlank() && normalized.equals(presetBaseUrl)) {
                return i;
            }
        }

        String detected = resolveDetectedProviderLabel(baseUrl);
        for (int i = 0; i < PROVIDER_PRESETS.length; i++) {
            if (PROVIDER_PRESETS[i].label().equalsIgnoreCase(detected)) {
                return i;
            }
        }
        return PROVIDER_PRESETS.length - 1;
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
        if (normalized.contains("openrouter")) {
            return "OpenRouter";
        }
        if (normalized.contains("generativelanguage") || normalized.contains("googleapis") || normalized.contains("gemini")) {
            return "Gemini OpenAI-Compatible";
        }
        if (normalized.contains("localhost:11434") || normalized.contains("127.0.0.1:11434")) {
            return "Ollama Local";
        }
        if (normalized.contains("openai.com")) {
            return "OpenAI";
        }
        return "Custom OpenAI-Compatible";
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
        if (normalized.contains("openrouter")) {
            return OPENROUTER_MODELS;
        }
        if (normalized.contains("generativelanguage") || normalized.contains("googleapis") || normalized.contains("gemini")) {
            return GEMINI_MODELS;
        }
        if (normalized.contains("localhost:11434") || normalized.contains("127.0.0.1:11434")) {
            return OLLAMA_MODELS;
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

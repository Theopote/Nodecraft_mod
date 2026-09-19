package com.nodecraft.gui.ai;

/**
 * 1.0 credential policy for remote planning.
 *
 * <p>Primary path: point {@code apiBaseUrl} at a <strong>local proxy</strong> that holds the
 * provider key so the Minecraft client never stores a long-lived provider secret.
 * Secondary: environment variables. Session / plaintext disk remember remain for development only.
 * OS credential stores are deferred — they do not keep secrets out of a shared-mod JVM process.
 */
public final class AiCredentialPolicy {

    public enum CredentialSource {
        LOCAL_PROXY,
        ENVIRONMENT,
        SESSION,
        DISK_PLAINTEXT,
        MISSING
    }

    private AiCredentialPolicy() {
    }

    public static boolean looksLikeLocalProxy(String apiBaseUrl) {
        if (apiBaseUrl == null || apiBaseUrl.isBlank()) {
            return false;
        }
        String normalized = apiBaseUrl.trim().toLowerCase();
        return normalized.contains("://localhost")
                || normalized.contains("://127.0.0.1")
                || normalized.contains("://[::1]")
                || normalized.contains("://0.0.0.0")
                || normalized.contains("://host.docker.internal");
    }

    public static CredentialSource resolveSource(AiSettingsStore.AiSettingsData data) {
        if (data == null) {
            return CredentialSource.MISSING;
        }
        boolean hasInlineKey = !isBlank(data.apiKey());
        if (hasInlineKey) {
            return data.rememberApiKey() ? CredentialSource.DISK_PLAINTEXT : CredentialSource.SESSION;
        }
        if (!isBlank(AiSettingsStore.resolveApiKey(data))) {
            return CredentialSource.ENVIRONMENT;
        }
        if (looksLikeLocalProxy(data.apiBaseUrl())) {
            // Proxy may authenticate without a client-held provider key.
            return CredentialSource.LOCAL_PROXY;
        }
        return CredentialSource.MISSING;
    }

    public static String keyStatusLabel(AiSettingsStore.AiSettingsData data) {
        return switch (resolveSource(data)) {
            case LOCAL_PROXY -> "API Key: proxy";
            case ENVIRONMENT -> "API Key: env";
            case SESSION -> "API Key: session";
            case DISK_PLAINTEXT -> "API Key: saved";
            case MISSING -> "API Key: missing";
        };
    }

    public static String settingsGuidance(AiSettingsStore.AiSettingsData data) {
        if (data == null || !data.enableRemotePlanner()) {
            return "Remote planner is off. Local mock planner does not need an API key.";
        }
        if (looksLikeLocalProxy(data.apiBaseUrl()) && isBlank(data.apiKey())) {
            return "1.0 path: local proxy holds the provider key. Leave the API key empty unless the proxy needs a local token.";
        }
        return switch (resolveSource(data)) {
            case LOCAL_PROXY ->
                    "1.0 path: API base URL looks like a local proxy. Prefer keeping the provider key out of this client.";
            case ENVIRONMENT ->
                    "Using an environment variable for the API key. Prefer a local proxy for 1.0 so the provider key stays out of Minecraft.";
            case SESSION ->
                    "API key is in memory for this session only. For 1.0, prefer a local proxy over pasting a provider key here.";
            case DISK_PLAINTEXT ->
                    "Remember-on-disk stores the key as plain text. Development only — prefer a local proxy for 1.0.";
            case MISSING ->
                    "Set API Base URL to a local proxy (recommended), or provide a key via env / session when calling a provider directly.";
        };
    }

    public static String diskPersistWarning() {
        return "Remembered API keys are stored as plain text. Prefer a local proxy for 1.0.";
    }

    public static String primaryPathSummary() {
        return "1.0 primary: local proxy. Secondary: env vars. Session/disk remember: development only.";
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}

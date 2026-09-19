package com.nodecraft.gui.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiCredentialPolicyTest {

    @Test
    void looksLikeLocalProxyDetectsLoopbackHosts() {
        assertTrue(AiCredentialPolicy.looksLikeLocalProxy("http://localhost:8080/v1"));
        assertTrue(AiCredentialPolicy.looksLikeLocalProxy("https://127.0.0.1:4000"));
        assertTrue(AiCredentialPolicy.looksLikeLocalProxy("http://[::1]/8080"));
        assertTrue(AiCredentialPolicy.looksLikeLocalProxy("http://host.docker.internal:4000/v1"));
        assertFalse(AiCredentialPolicy.looksLikeLocalProxy("https://api.openai.com/v1"));
        assertFalse(AiCredentialPolicy.looksLikeLocalProxy(null));
    }

    @Test
    void resolveSourcePrefersInlineKeyThenEnvThenLocalProxy() {
        AiSettingsStore.AiSettingsData session = settings(
                "https://api.openai.com/v1", "sk-session", false, true);
        assertEquals(AiCredentialPolicy.CredentialSource.SESSION, AiCredentialPolicy.resolveSource(session));
        assertEquals("API Key: session", AiCredentialPolicy.keyStatusLabel(session));

        AiSettingsStore.AiSettingsData disk = settings(
                "https://api.openai.com/v1", "sk-disk", true, true);
        assertEquals(AiCredentialPolicy.CredentialSource.DISK_PLAINTEXT, AiCredentialPolicy.resolveSource(disk));
        assertEquals("API Key: saved", AiCredentialPolicy.keyStatusLabel(disk));

        AiSettingsStore.AiSettingsData proxy = settings(
                "http://localhost:8080/v1", "", false, true);
        assertEquals(AiCredentialPolicy.CredentialSource.LOCAL_PROXY, AiCredentialPolicy.resolveSource(proxy));
        assertEquals("API Key: proxy", AiCredentialPolicy.keyStatusLabel(proxy));

        AiSettingsStore.AiSettingsData missing = settings(
                "https://api.openai.com/v1", "", false, true);
        assertEquals(AiCredentialPolicy.CredentialSource.MISSING, AiCredentialPolicy.resolveSource(missing));
    }

    @Test
    void settingsGuidanceMentionsLocalProxyAsPrimaryPath() {
        AiSettingsStore.AiSettingsData proxy = settings(
                "http://127.0.0.1:4000/v1", "", false, true);
        assertTrue(AiCredentialPolicy.settingsGuidance(proxy).contains("local proxy"));

        AiSettingsStore.AiSettingsData disk = settings(
                "https://api.openai.com/v1", "sk-disk", true, true);
        assertTrue(AiCredentialPolicy.settingsGuidance(disk).contains("plain text"));
        assertTrue(AiCredentialPolicy.diskPersistWarning().contains("local proxy"));
        assertTrue(AiCredentialPolicy.primaryPathSummary().startsWith("1.0 primary: local proxy"));
    }

    private static AiSettingsStore.AiSettingsData settings(
            String apiBaseUrl,
            String apiKey,
            boolean rememberApiKey,
            boolean enableRemotePlanner
    ) {
        AiSettingsStore.AiSettingsData defaults = AiSettingsStore.defaults();
        return new AiSettingsStore.AiSettingsData(
                apiBaseUrl,
                apiKey,
                defaults.model(),
                defaults.providerStrategy(),
                defaults.systemPrompt(),
                defaults.maxOutputTokens(),
                defaults.timeoutSeconds(),
                defaults.conversationHistoryTurns(),
                defaults.showApiKey(),
                rememberApiKey,
                enableRemotePlanner,
                defaults.autoLayoutBeforeApply(),
                defaults.includeGraphContext(),
                defaults.includePlayerWorldContext(),
                defaults.includeSelectedWorldRegionContext(),
                defaults.previewOnlyMode(),
                defaults.patchApplyMode(),
                defaults.patchRemoveScopedConnections(),
                defaults.enterToSend(),
                defaults.debugLoggingEnabled(),
                defaults.includePromptPreviewInDebug()
        );
    }
}

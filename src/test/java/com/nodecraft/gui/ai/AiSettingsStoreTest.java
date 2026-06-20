package com.nodecraft.gui.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiSettingsStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void saveDoesNotPersistApiKeyWhenRememberKeyIsDisabled() throws Exception {
        Path settingsPath = tempDir.resolve("ai_settings.json");
        String secret = "sk-test-secret";

        String status = AiSettingsStore.save(settingsPath, settingsWithApiKey(secret, false));
        String savedJson = Files.readString(settingsPath, StandardCharsets.UTF_8);
        AiSettingsStore.LoadResult loaded = AiSettingsStore.load(settingsPath);

        assertEquals("AI settings saved.", status);
        assertFalse(savedJson.contains(secret));
        assertEquals("", loaded.data().apiKey());
        assertFalse(loaded.data().rememberApiKey());
    }

    @Test
    void savePersistsApiKeyOnlyWhenRememberKeyIsEnabled() throws Exception {
        Path settingsPath = tempDir.resolve("ai_settings.json");
        String secret = "sk-test-secret";

        String status = AiSettingsStore.save(settingsPath, settingsWithApiKey(secret, true));
        String savedJson = Files.readString(settingsPath, StandardCharsets.UTF_8);
        AiSettingsStore.LoadResult loaded = AiSettingsStore.load(settingsPath);

        assertEquals("AI settings saved.", status);
        assertTrue(savedJson.contains(secret));
        assertEquals(secret, loaded.data().apiKey());
        assertTrue(loaded.data().rememberApiKey());
    }

    private static AiSettingsStore.AiSettingsData settingsWithApiKey(String apiKey, boolean rememberApiKey) {
        AiSettingsStore.AiSettingsData defaults = AiSettingsStore.defaults();
        return new AiSettingsStore.AiSettingsData(
                defaults.apiBaseUrl(),
                apiKey,
                defaults.model(),
                defaults.providerStrategy(),
                defaults.systemPrompt(),
                defaults.maxOutputTokens(),
                defaults.timeoutSeconds(),
                defaults.conversationHistoryTurns(),
                defaults.showApiKey(),
                rememberApiKey,
                defaults.enableRemotePlanner(),
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

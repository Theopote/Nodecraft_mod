package com.nodecraft.gui.ai;

import com.nodecraft.gui.components.ai.AiAssistantComponent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiAssistantControllerTest {

    @TempDir
    Path tempDir;

    @Test
    void validateSettingsRejectsBlankApiBaseUrl() {
        AiAssistantUiBindings ui = new AiAssistantUiBindings();
        AiAssistantController controller = newController(ui, tempDir.resolve("ai_settings.json"));

        ui.aiEnableRemotePlanner.set(true);
        ui.aiApiBaseUrl.set("   ");
        ui.aiApiKey.set("sk-test");
        ui.aiModel.set("gpt-test");

        String validation = controller.validateSettings();
        assertTrue(validation.startsWith("Validation failed"), validation);
        assertTrue(validation.toLowerCase().contains("base"), validation);
    }

    @Test
    void setPromptAndClearConversationAreOwnedByController() {
        AiAssistantUiBindings ui = new AiAssistantUiBindings();
        AiAssistantComponent component = new AiAssistantComponent();
        AiAssistantController controller = new AiAssistantController(
                component,
                () -> null,
                text -> {
                },
                ui,
                tempDir.resolve("ai_settings.json")
        );

        controller.setPrompt("build a small arch");
        assertEquals("build a small arch", ui.aiPromptInput.get());

        component.addChatMessage("user", "hello", System.currentTimeMillis());
        assertFalse(component.getChatMessages().isEmpty());

        controller.clearConversation();
        assertTrue(component.getChatMessages().isEmpty());
        assertTrue(ui.aiPromptInput.get() == null || ui.aiPromptInput.get().isBlank());
        assertEquals("Chat cleared.", controller.planStatusMessage());
    }

    @Test
    void saveSettingsDoesNotPersistApiKeyWhenRememberDisabled() throws Exception {
        Path settingsPath = tempDir.resolve("ai_settings.json");
        AiAssistantUiBindings ui = new AiAssistantUiBindings();
        AiAssistantController controller = newController(ui, settingsPath);

        ui.aiApiBaseUrl.set("https://api.openai.com/v1");
        ui.aiApiKey.set("sk-should-not-persist");
        ui.aiModel.set("gpt-4.1-mini");
        ui.aiRememberApiKey.set(false);

        controller.saveSettings();

        String savedJson = Files.readString(settingsPath, StandardCharsets.UTF_8);
        assertFalse(savedJson.contains("sk-should-not-persist"));
        assertTrue(
                controller.settingsStatusMessage().toLowerCase().contains("saved"),
                controller.settingsStatusMessage()
        );
    }

    private static AiAssistantController newController(AiAssistantUiBindings ui, Path settingsPath) {
        return new AiAssistantController(
                new AiAssistantComponent(),
                () -> null,
                text -> {
                },
                ui,
                settingsPath
        );
    }
}

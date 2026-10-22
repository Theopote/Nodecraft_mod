package com.nodecraft.gui.editor.impl;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImGuiNodeRendererCustomUITest {

    @Test
    void rendererDoesNotHardcodeCustomUIDisabled() throws Exception {
        Path source = Path.of("src/main/java/com/nodecraft/gui/editor/impl/ImGuiNodeRenderer.java");
        String content = Files.readString(source);

        assertFalse(content.contains("boolean nodeHasCustomUI = false;"),
                "Custom UI must not be hard-disabled in ImGuiNodeRenderer");
        assertTrue(content.contains("layout.hasCustomUI()"),
                "Custom UI flag should come from NodeVisualLayout");
        assertTrue(content.contains("renderCustomUI("),
                "Custom UI render path should be present");
        assertTrue(content.contains("beginInteractionFrame()"),
                "Custom UI interaction state should reset once per frame");
        assertFalse(content.contains("resetCustomUIInteractionState();"),
                "Per-node interaction reset would break multi-node custom UI");
    }
}

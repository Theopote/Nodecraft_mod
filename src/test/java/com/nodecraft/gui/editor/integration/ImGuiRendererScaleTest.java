package com.nodecraft.gui.editor.integration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImGuiRendererScaleTest {

    @Test
    void keepsUiScaleStableAcrossMinecraftGuiScales() {
        assertEquals(1.0f, ImGuiRenderer.scaleForMinecraftGuiScale(1.0d), 0.0001f);
        assertEquals(1.0f, ImGuiRenderer.scaleForMinecraftGuiScale(2.0d), 0.0001f);
        assertEquals(1.0f, ImGuiRenderer.scaleForMinecraftGuiScale(3.0d), 0.0001f);
        assertEquals(1.0f, ImGuiRenderer.scaleForMinecraftGuiScale(4.0d), 0.0001f);
    }

    @Test
    void clampsInvalidAndExtremeGuiScales() {
        assertEquals(1.0f, ImGuiRenderer.scaleForMinecraftGuiScale(0.0d), 0.0001f);
        assertEquals(1.0f, ImGuiRenderer.scaleForMinecraftGuiScale(Double.NaN), 0.0001f);
        assertEquals(1.0f, ImGuiRenderer.scaleForMinecraftGuiScale(8.0d), 0.0001f);
    }
}

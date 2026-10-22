package com.nodecraft.gui.components.customui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockStateCompactWidgetLayoutTest {

    @Test
    void preferredHeightIncludesMultilineEditorNotSingleFrame() {
        float frameHeight = 20.0f;
        float textLineHeight = 16.0f;
        float padding = 8.0f;
        float contentWidth = 160.0f;
        float height = BlockStateCompactWidget.preferredHeight(
            null, true, "", frameHeight, textLineHeight, contentWidth, padding);
        float editorHeight = BlockStateCompactWidget.multilineEditorHeight(frameHeight);
        assertTrue(height >= editorHeight + padding);
        assertTrue(height > frameHeight + padding,
            "Multiline editor must reserve more than one frame height");
    }

    @Test
    void preferredHeightReadOnlyIncludesBannerAndWrappedText() {
        float frameHeight = 18.0f;
        float textLineHeight = 16.0f;
        float padding = 6.0f;
        float contentWidth = 120.0f;
        String longText = "facing=north,half=top,waterlogged=false,extra=padding";
        float height = BlockStateCompactWidget.preferredHeight(
            null, false, longText, frameHeight, textLineHeight, contentWidth, padding);
        assertTrue(height > textLineHeight + padding,
            "Read-only layout must exceed a single text line (banner + wrapped body)");
    }
}

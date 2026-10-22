package com.nodecraft.gui.components.customui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockStateCompactWidgetLayoutTest {

    @Test
    void preferredHeightIncludesMultilineEditorNotSingleFrame() {
        float frameHeight = 20.0f;
        float padding = 8.0f;
        float height = BlockStateCompactWidget.preferredHeight(null, true, frameHeight, padding);
        float editorHeight = BlockStateCompactWidget.multilineEditorHeight(frameHeight);
        assertTrue(height >= editorHeight + padding);
        assertTrue(height > frameHeight + padding,
            "Multiline editor must reserve more than one frame height");
    }

    @Test
    void preferredHeightWithoutCombosMatchesEditorOnly() {
        float frameHeight = 18.0f;
        float padding = 6.0f;
        float height = BlockStateCompactWidget.preferredHeight(null, false, frameHeight, padding);
        assertTrue(Math.abs(height - (BlockStateCompactWidget.multilineEditorHeight(frameHeight) + padding)) < 0.01f);
    }
}

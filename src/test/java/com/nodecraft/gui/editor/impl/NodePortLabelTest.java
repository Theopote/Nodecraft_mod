package com.nodecraft.gui.editor.impl;

import com.nodecraft.gui.editor.session.NodeDisplayMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NodePortLabelTest {

    @Test
    void firstLetterUsesFirstUnicodeLetterUppercased() {
        assertEquals("W", NodePortLabelUtil.firstLetter("Wall Geometry"));
        assertEquals("G", NodePortLabelUtil.firstLetter("geometry"));
        assertEquals("G", NodePortLabelUtil.firstLetter("  Grid"));
        assertEquals("?", NodePortLabelUtil.firstLetter(""));
        assertEquals("?", NodePortLabelUtil.firstLetter(null));
        assertEquals("?", NodePortLabelUtil.firstLetter("123"));
    }

    @Test
    void portLabelDependsOnDisplayMode() {
        assertEquals("Wall Geometry", NodePortLabelUtil.portLabel(NodeDisplayMode.TEXT_ONLY, "Wall Geometry"));
        assertEquals("W", NodePortLabelUtil.portLabel(NodeDisplayMode.ICON_ONLY, "Wall Geometry"));
        assertEquals("", NodePortLabelUtil.portLabel(NodeDisplayMode.TEXT_ONLY, null));
        assertEquals("?", NodePortLabelUtil.portLabel(NodeDisplayMode.ICON_ONLY, null));
    }

    @Test
    void duplicateInitialsAreAllowed() {
        assertEquals("G", NodePortLabelUtil.firstLetter("Geometry"));
        assertEquals("G", NodePortLabelUtil.firstLetter("Grid"));
    }
}

package com.nodecraft.gui.editor.impl;

import com.nodecraft.gui.editor.session.NodeDisplayMode;

/**
 * Port label helpers shared by layout and rendering.
 */
public final class NodePortLabelUtil {

    private NodePortLabelUtil() {
    }

    /**
     * Returns the label drawn beside a port for the given display mode.
     * Full English names for TEXT_ONLY; first letter for ICON_ONLY.
     */
    public static String portLabel(NodeDisplayMode mode, String displayName) {
        if (mode == NodeDisplayMode.ICON_ONLY) {
            return firstLetter(displayName);
        }
        return displayName != null ? displayName : "";
    }

    /**
     * First Unicode letter of {@code displayName}, uppercased. Returns {@code "?"} when none.
     * Duplicate initials across ports are intentional.
     */
    public static String firstLetter(String displayName) {
        if (displayName == null || displayName.isEmpty()) {
            return "?";
        }
        for (int i = 0; i < displayName.length(); ) {
            int cp = displayName.codePointAt(i);
            if (Character.isLetter(cp)) {
                return new String(Character.toChars(Character.toUpperCase(cp)));
            }
            i += Character.charCount(cp);
        }
        return "?";
    }
}

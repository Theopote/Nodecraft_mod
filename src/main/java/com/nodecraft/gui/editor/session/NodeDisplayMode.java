package com.nodecraft.gui.editor.session;

/**
 * Canvas node presentation mode. All modes use a compact layout; only the
 * information density of titles/ports/icons differs.
 */
public enum NodeDisplayMode {
    /** Full English title and port names; no node icon. */
    TEXT_ONLY,
    /** Centered icon; ports show first letter only. */
    ICON_ONLY
}

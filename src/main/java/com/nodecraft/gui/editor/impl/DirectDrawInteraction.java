package com.nodecraft.gui.editor.impl;

/**
 * Direct-draw custom UI interaction result for a single frame.
 *
 * <p>{@code changed} (value mutated this frame) is not the same as {@code active}
 * (pointer still capturing the control). A drag with no value delta must keep
 * {@code active == true} so the parent node cannot start dragging.
 */
public record DirectDrawInteraction(boolean hovered, boolean active, boolean changed) {

    public static final DirectDrawInteraction NONE = new DirectDrawInteraction(false, false, false);

    /**
     * Maps the legacy {@code renderCustomUIDirect} boolean, which historically meant
     * "value changed this frame". Does <strong>not</strong> imply continuous capture —
     * the renderer must combine this with mouse-down tracking.
     */
    public static DirectDrawInteraction fromLegacyChanged(boolean changed) {
        if (!changed) {
            return NONE;
        }
        return new DirectDrawInteraction(true, true, true);
    }
}

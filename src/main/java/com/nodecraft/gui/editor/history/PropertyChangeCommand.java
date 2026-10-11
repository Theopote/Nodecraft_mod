package com.nodecraft.gui.editor.history;

import java.util.UUID;

/**
 * Immutable description of a single node property edit for undo/redo.
 */
public record PropertyChangeCommand(
        UUID nodeId,
        String propertyName,
        Object oldValue,
        Object newValue
) {
}

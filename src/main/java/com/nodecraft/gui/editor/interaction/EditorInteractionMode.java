package com.nodecraft.gui.editor.interaction;

/**
 * Exclusive canvas gesture mode for the node editor.
 * <p>
 * See {@code docs/architecture/editor-interaction-mode.md}.
 */
public enum EditorInteractionMode {
    /** No exclusive gesture in progress. */
    IDLE,
    /** Dragging one or more selected nodes. */
    DRAGGING_NODE,
    /** Marquee / box selection. */
    BOX_SELECTING,
    /** Dragging a connection wire from a port. */
    CREATING_CONNECTION,
    /** Panning the canvas. */
    PANNING_CANVAS
}

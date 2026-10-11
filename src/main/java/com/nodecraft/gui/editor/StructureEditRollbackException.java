package com.nodecraft.gui.editor;

/**
 * Thrown when a graph structure edit fails and restoring the before-snapshot also fails,
 * leaving the document potentially inconsistent.
 */
public final class StructureEditRollbackException extends RuntimeException {

    private final String editLabel;

    public StructureEditRollbackException(String editLabel, String message) {
        super(message);
        this.editLabel = editLabel;
    }

    public StructureEditRollbackException(String editLabel, String message, Throwable cause) {
        super(message, cause);
        this.editLabel = editLabel;
    }

    public String getEditLabel() {
        return editLabel;
    }
}

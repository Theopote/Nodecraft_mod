package com.nodecraft.gui.components.property.core;

/**
 * Thrown when a property write is refused or rolled back because undo snapshots
 * could not be isolated safely.
 */
public final class PropertyApplyException extends RuntimeException {

    public PropertyApplyException(String message, Throwable cause) {
        super(message, cause);
    }
}

package com.nodecraft.gui.components.property.core;

/**
 * Pure helpers for multi-axis vector / BlockPos edit-session boundaries.
 */
public final class VectorEditSessionLogic {

    private VectorEditSessionLogic() {
    }

    /**
     * Ends the property edit session only when at least one axis deactivated
     * and no axis remains active (e.g. focus moved X → Y keeps the session open).
     */
    public static boolean shouldFinishSession(boolean anyActive, boolean anyDeactivated) {
        return anyDeactivated && !anyActive;
    }
}

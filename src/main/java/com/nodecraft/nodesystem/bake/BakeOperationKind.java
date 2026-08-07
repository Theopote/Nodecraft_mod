package com.nodecraft.nodesystem.bake;

/**
 * Defines the semantic meaning of a bake operation in the history system.
 * This allows BakePlacementService to correctly route inverse records
 * to the appropriate history stack.
 */
public enum BakeOperationKind {
    /**
     * Normal bake operation - user creates/modifies blocks.
     * Inverse goes to undo stack.
     */
    APPLY,

    /**
     * Undo operation - user reverts a previous change.
     * Inverse goes to redo stack.
     */
    UNDO,

    /**
     * Redo operation - user reapplies a previously undone change.
     * Inverse goes to undo stack.
     */
    REDO,

    /**
     * No history recording - temporary/preview operations.
     * Inverse is discarded.
     */
    NONE,

    /**
     * Internal rollback of an aborted APPLY/UNDO/REDO task.
     * Restores captured previous states; never commits BakeHistory.
     */
    ROLLBACK
}

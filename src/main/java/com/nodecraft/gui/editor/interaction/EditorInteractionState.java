package com.nodecraft.gui.editor.interaction;

import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Canvas interaction state: exclusive mode + node selection.
 */
public final class EditorInteractionState {

    private EditorInteractionMode mode = EditorInteractionMode.IDLE;
    private @Nullable UUID primarySelectedNodeId;
    private final Set<UUID> selectedNodeIds = new HashSet<>();

    public EditorInteractionMode getMode() {
        return mode;
    }

    public boolean isIdle() {
        return mode == EditorInteractionMode.IDLE;
    }

    /**
     * Attempts to enter {@code next}. Entering a non-idle mode is only allowed from {@link EditorInteractionMode#IDLE}.
     * Returning to {@link EditorInteractionMode#IDLE} always succeeds.
     *
     * @return true if the mode changed (or was already the requested mode)
     */
    public boolean tryEnter(EditorInteractionMode next) {
        Objects.requireNonNull(next, "next");
        if (mode == next) {
            return true;
        }
        if (next == EditorInteractionMode.IDLE) {
            mode = EditorInteractionMode.IDLE;
            return true;
        }
        if (mode != EditorInteractionMode.IDLE) {
            return false;
        }
        mode = next;
        return true;
    }

    /**
     * Unconditionally sets the mode (escape hatches / gesture completion).
     */
    public void forceMode(EditorInteractionMode next) {
        mode = Objects.requireNonNull(next, "next");
    }

    public void resetToIdle() {
        mode = EditorInteractionMode.IDLE;
    }

    public @Nullable UUID getPrimarySelectedNodeId() {
        return primarySelectedNodeId;
    }

    /**
     * Live mutable multi-selection set. Callers may add/remove entries.
     */
    public Set<UUID> getSelectedNodeIds() {
        return selectedNodeIds;
    }

    public void setPrimarySelectedNodeId(@Nullable UUID nodeId) {
        this.primarySelectedNodeId = nodeId;
        if (nodeId != null) {
            selectedNodeIds.add(nodeId);
        }
    }

    public void clearSelection() {
        selectedNodeIds.clear();
        primarySelectedNodeId = null;
    }

    public void removeFromSelection(UUID nodeId) {
        if (nodeId == null) {
            return;
        }
        selectedNodeIds.remove(nodeId);
        if (nodeId.equals(primarySelectedNodeId)) {
            primarySelectedNodeId = selectedNodeIds.isEmpty() ? null : selectedNodeIds.iterator().next();
        }
    }

    public boolean hasSelection() {
        return !selectedNodeIds.isEmpty();
    }
}

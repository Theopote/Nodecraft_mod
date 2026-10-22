package com.nodecraft.gui.components.customui;

import com.nodecraft.gui.editor.impl.ImGuiNodeEditor;
import com.nodecraft.gui.editor.impl.ImGuiNodeHistory;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Records undo history for canvas custom UI edits (property coalescing and node-state snapshots).
 */
public final class CustomUIPropertyHistory {

    private static final AtomicLong DRAG_SESSION_COUNTER = new AtomicLong(1L);

    private CustomUIPropertyHistory() {
    }

    public static long openDragSession() {
        return DRAG_SESSION_COUNTER.incrementAndGet();
    }

    public static void recordPropertyChange(
            UUID nodeId,
            String propertyName,
            Object oldValue,
            Object newValue,
            long editSessionId
    ) {
        if (nodeId == null || propertyName == null || propertyName.isBlank()) {
            return;
        }
        try {
            ImGuiNodeHistory history = ImGuiNodeEditor.getInstance().getHistory();
            history.recordPropertyChange(nodeId, propertyName, oldValue, newValue, editSessionId);
        } catch (Throwable ignored) {
            // Headless / editor unavailable — mutation still applies locally.
        }
    }

    public static void recordNodeStateChange(UUID nodeId, Object beforeState, Object afterState, String label) {
        if (nodeId == null) {
            return;
        }
        try {
            ImGuiNodeHistory history = ImGuiNodeEditor.getInstance().getHistory();
            history.recordNodeStateChange(nodeId, beforeState, afterState, label);
        } catch (Throwable ignored) {
            // Headless / editor unavailable.
        }
    }
}

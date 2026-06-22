package com.nodecraft.nodesystem.preview.gizmo;

import com.google.gson.Gson;
import com.nodecraft.gui.editor.impl.ImGuiNodeEditor;
import com.nodecraft.gui.editor.impl.ImGuiNodeHistory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Captures node state at gizmo drag start and records undo entries on commit.
 */
public final class GizmoUndoCoordinator {

    private static final Gson GSON = new Gson();
    private static final Map<UUID, Object> DRAG_START_STATES = new ConcurrentHashMap<>();

    private GizmoUndoCoordinator() {
    }

    public static void beginDrag(UUID nodeId, Object beforeState) {
        if (nodeId == null || beforeState == null) {
            return;
        }
        DRAG_START_STATES.put(nodeId, deepCopy(beforeState));
    }

    public static void commitDrag(UUID nodeId, Object afterState, String label) {
        if (nodeId == null) {
            return;
        }
        Object beforeState = DRAG_START_STATES.remove(nodeId);
        if (beforeState == null || afterState == null) {
            return;
        }
        if (statesEqual(beforeState, afterState)) {
            return;
        }
        ImGuiNodeHistory history = ImGuiNodeEditor.getInstance().getHistory();
        history.recordNodeStateChange(nodeId, beforeState, afterState, label == null ? "Gizmo Transform" : label);
    }

    public static void cancelDrag(UUID nodeId) {
        if (nodeId != null) {
            DRAG_START_STATES.remove(nodeId);
        }
    }

    private static boolean statesEqual(Object left, Object right) {
        return GSON.toJson(left).equals(GSON.toJson(right));
    }

    private static Object deepCopy(Object state) {
        return GSON.fromJson(GSON.toJson(state), Object.class);
    }
}

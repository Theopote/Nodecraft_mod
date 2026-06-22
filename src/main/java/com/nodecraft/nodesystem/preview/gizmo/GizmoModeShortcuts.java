package com.nodecraft.nodesystem.preview.gizmo;

import com.nodecraft.gui.editor.impl.ImGuiNodeEditor;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.nodes.output.preview.GeometryViewerNode;
import com.nodecraft.nodesystem.preview.gizmo.GizmoTransformTarget;
import org.lwjgl.glfw.GLFW;

import java.util.Set;
import java.util.UUID;

/**
 * Keyboard shortcuts for gizmo mode switching (G/R/S).
 */
public final class GizmoModeShortcuts {

    private GizmoModeShortcuts() {
    }

    public static boolean handleKey(int keyCode, boolean textInputActive) {
        if (textInputActive) {
            return false;
        }
        String mode = switch (keyCode) {
            case GLFW.GLFW_KEY_G -> "move";
            case GLFW.GLFW_KEY_R -> "rotate";
            case GLFW.GLFW_KEY_S -> "scale";
            default -> null;
        };
        if (mode == null) {
            return false;
        }
        return applyModeToSelection(mode);
    }

    private static boolean applyModeToSelection(String mode) {
        ImGuiNodeEditor editor = ImGuiNodeEditor.getInstance();
        Set<UUID> selected = editor.getSelectedNodeIds();
        if (selected.isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (UUID nodeId : selected) {
            INode node = editor.getCurrentGraph() != null ? editor.getCurrentGraph().getNode(nodeId) : null;
            if (node instanceof GizmoTransformTarget target) {
                target.setGizmoMode(mode);
                target.markGizmoDirty();
                changed = true;
                continue;
            }
            if (node instanceof GeometryViewerNode viewer) {
                viewer.setGizmoMode(mode);
                viewer.markDirty();
                changed = true;
            }
        }
        return changed;
    }
}

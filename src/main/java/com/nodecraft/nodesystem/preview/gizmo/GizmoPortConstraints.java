package com.nodecraft.nodesystem.preview.gizmo;

import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.core.BaseNode;

/**
 * Describes which gizmo handles are allowed when transform input ports are wired.
 */
public record GizmoPortConstraints(
    boolean moveEnabled,
    boolean rotateEnabled,
    boolean scaleEnabled
) {
    public static GizmoPortConstraints allEnabled() {
        return new GizmoPortConstraints(true, true, true);
    }

    public static GizmoPortConstraints fromNode(BaseNode node, String translationPortId, String rotXPortId,
                                               String rotYPortId, String rotZPortId, String scalePortId) {
        if (node == null) {
            return allEnabled();
        }
        return new GizmoPortConstraints(
            !isPortConnected(node, translationPortId),
            !isAnyPortConnected(node, rotXPortId, rotYPortId, rotZPortId),
            !isPortConnected(node, scalePortId)
        );
    }

    public String resolveEffectiveMode(String requestedMode) {
        String mode = requestedMode == null || requestedMode.isBlank() ? "all" : requestedMode.trim().toLowerCase();
        boolean wantsMove = "all".equals(mode) || "move".equals(mode) || "translate".equals(mode);
        boolean wantsRotate = "all".equals(mode) || "rotate".equals(mode) || "rotation".equals(mode);
        boolean wantsScale = "all".equals(mode) || "scale".equals(mode);

        boolean hasMove = wantsMove && moveEnabled;
        boolean hasRotate = wantsRotate && rotateEnabled;
        boolean hasScale = wantsScale && scaleEnabled;

        if (hasMove && hasRotate && hasScale) {
            return "all";
        }
        if (hasMove && !hasRotate && !hasScale) {
            return "move";
        }
        if (!hasMove && hasRotate && !hasScale) {
            return "rotate";
        }
        if (!hasMove && !hasRotate && hasScale) {
            return "scale";
        }
        if (hasMove && hasRotate) {
            return "all";
        }
        if (hasMove && hasScale) {
            return "all";
        }
        if (hasRotate && hasScale) {
            return "all";
        }
        return "none";
    }

    public boolean isInteractive() {
        return moveEnabled || rotateEnabled || scaleEnabled;
    }

    private static boolean isAnyPortConnected(BaseNode node, String... portIds) {
        for (String portId : portIds) {
            if (isPortConnected(node, portId)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPortConnected(BaseNode node, String portId) {
        if (portId == null) {
            return false;
        }
        for (IPort port : node.getInputPorts()) {
            if (port.getId().equals(portId)) {
                return port.isConnected();
            }
        }
        return false;
    }
}

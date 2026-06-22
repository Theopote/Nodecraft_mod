package com.nodecraft.nodesystem.preview.gizmo;

import com.nodecraft.gui.editor.impl.ImGuiNodeEditor;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.nodes.transform.basic_transforms.TransformGeometryNode;

import java.util.UUID;

/**
 * Resolves upstream transform nodes for viewer-side gizmo binding.
 */
public final class GizmoGraphBinding {

    private GizmoGraphBinding() {
    }

    public static GizmoTransformTarget findUpstreamTransformTarget(UUID consumerNodeId, String inputPortId) {
        if (consumerNodeId == null || inputPortId == null) {
            return null;
        }
        NodeGraph graph = ImGuiNodeEditor.getInstance().getCurrentGraph();
        if (graph == null) {
            return null;
        }
        UUID sourceNodeId = graph.getConnectedOutputNodeId(consumerNodeId, inputPortId);
        if (sourceNodeId == null) {
            return null;
        }
        INode source = graph.getNode(sourceNodeId);
        if (source instanceof GizmoTransformTarget target) {
            return target;
        }
        if (source instanceof TransformGeometryNode transformNode) {
            return transformNode;
        }
        return null;
    }
}

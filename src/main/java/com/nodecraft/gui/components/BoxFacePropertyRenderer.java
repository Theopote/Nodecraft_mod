package com.nodecraft.gui.components;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import imgui.ImGui;

final class BoxFacePropertyRenderer {

    static final PropertyRenderer RENDERER = BoxFacePropertyRenderer::render;

    private BoxFacePropertyRenderer() {
    }

    private static void render(PropertyPanelComponent panel, INode node, PropertyDescriptor prop, boolean isDisabled) {
        try {
            BoxFaceData face = (BoxFaceData) prop.getter.invoke(node);
            if (face == null) {
                ImGui.textDisabled("(null)");
                return;
            }

            ImGui.text("Face: " + face.getName() + " (#" + face.getIndex() + ")");
            ImGui.text("Center: " + PropertyValueFormatters.formatVector3d(face.getCenter()));
            ImGui.text("Normal: " + PropertyValueFormatters.formatVector3d(face.getNormal()));
            ImGui.text("Corners: " + face.getCorners().size());
            ImGui.text("Edges: " + face.getEdgeCount());
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    }
}

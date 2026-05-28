package com.nodecraft.gui.components;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import imgui.ImGui;

final class BoxGeometryPropertyRenderer {

    static final PropertyRenderer RENDERER = BoxGeometryPropertyRenderer::render;

    private BoxGeometryPropertyRenderer() {
    }

    private static void render(PropertyPanelComponent panel, INode node, PropertyDescriptor prop, boolean isDisabled) {
        try {
            BoxGeometryData box = (BoxGeometryData) prop.getter.invoke(node);
            if (box == null) {
                ImGui.textDisabled("(null)");
                return;
            }

            ImGui.text("Center: " + PropertyValueFormatters.formatVector3d(box.getCenter()));
            ImGui.text("Half Extents: " + PropertyValueFormatters.formatVector3d(box.getHalfExtents()));
            ImGui.text("Oriented: " + (box.isOriented() ? "Yes" : "No"));
            ImGui.text("Corners: " + box.getCornerCount());
            ImGui.text("Faces: " + box.getFaceCount());
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    }
}

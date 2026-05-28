package com.nodecraft.gui.components;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.datatypes.TorusGeometryData;
import imgui.ImGui;

final class TorusGeometryPropertyRenderer {

    static final PropertyRenderer RENDERER = TorusGeometryPropertyRenderer::render;

    private TorusGeometryPropertyRenderer() {
    }

    private static void render(PropertyPanelComponent panel, INode node, PropertyDescriptor prop, boolean isDisabled) {
        try {
            TorusGeometryData torus = (TorusGeometryData) prop.getter.invoke(node);
            if (torus == null) {
                ImGui.textDisabled("(null)");
                return;
            }

            ImGui.text("Center: " + PropertyValueFormatters.formatVector3d(torus.getCenter()));
            ImGui.text("Axis: " + PropertyValueFormatters.formatVector3d(torus.getAxis()));
            ImGui.text(String.format("Major Radius: %.2f", torus.getMajorRadius()));
            ImGui.text(String.format("Minor Radius: %.2f", torus.getMinorRadius()));
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    }
}

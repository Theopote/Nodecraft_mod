package com.nodecraft.gui.components;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.datatypes.HemisphereGeometryData;
import imgui.ImGui;

final class HemisphereGeometryPropertyRenderer {

    static final PropertyRenderer RENDERER = HemisphereGeometryPropertyRenderer::render;

    private HemisphereGeometryPropertyRenderer() {
    }

    private static void render(PropertyPanelComponent panel, INode node, PropertyDescriptor prop, boolean isDisabled) {
        try {
            HemisphereGeometryData hemisphere = (HemisphereGeometryData) prop.getter.invoke(node);
            if (hemisphere == null) {
                ImGui.textDisabled("(null)");
                return;
            }

            ImGui.text("Center: " + PropertyValueFormatters.formatVector3d(hemisphere.getCenter()));
            ImGui.text("Axis: " + PropertyValueFormatters.formatVector3d(hemisphere.getAxis()));
            ImGui.text(String.format("Radius: %.2f", hemisphere.getRadius()));
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    }
}

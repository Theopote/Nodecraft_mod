package com.nodecraft.gui.components;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.datatypes.PointData;
import imgui.ImGui;

final class PointPropertyRenderer {

    static final PropertyRenderer RENDERER = PointPropertyRenderer::render;

    private PointPropertyRenderer() {
    }

    private static void render(PropertyPanelComponent panel, INode node, PropertyDescriptor prop, boolean isDisabled) {
        try {
            PointData point = (PointData) prop.getter.invoke(node);
            if (point == null) {
                ImGui.textDisabled("(null)");
                return;
            }

            ImGui.text("Position: " + PropertyValueFormatters.formatVector3d(point.getPosition()));
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    }
}

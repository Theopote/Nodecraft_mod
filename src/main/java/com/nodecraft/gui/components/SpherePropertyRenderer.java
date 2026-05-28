package com.nodecraft.gui.components;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.datatypes.SphereData;
import imgui.ImGui;

final class SpherePropertyRenderer {

    static final PropertyRenderer RENDERER = SpherePropertyRenderer::render;

    private SpherePropertyRenderer() {
    }

    private static void render(PropertyPanelComponent panel, INode node, PropertyDescriptor prop, boolean isDisabled) {
        try {
            SphereData sphere = (SphereData) prop.getter.invoke(node);
            if (sphere == null) {
                ImGui.textDisabled("(null)");
                return;
            }

            ImGui.text("Center: " + PropertyValueFormatters.formatVector3d(sphere.getCenter()));
            ImGui.text(String.format("Radius: %.2f", sphere.getRadius()));
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    }
}

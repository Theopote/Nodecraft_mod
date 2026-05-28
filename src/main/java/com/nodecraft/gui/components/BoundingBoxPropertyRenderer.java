package com.nodecraft.gui.components;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.datatypes.BoundingBoxData;
import imgui.ImGui;

final class BoundingBoxPropertyRenderer {

    static final PropertyRenderer RENDERER = BoundingBoxPropertyRenderer::render;

    private BoundingBoxPropertyRenderer() {
    }

    private static void render(PropertyPanelComponent panel, INode node, PropertyDescriptor prop, boolean isDisabled) {
        try {
            BoundingBoxData box = (BoundingBoxData) prop.getter.invoke(node);
            if (box == null) {
                ImGui.textDisabled("(null)");
                return;
            }

            ImGui.text("Min: " + PropertyValueFormatters.formatVector3d(box.getMin()));
            ImGui.text("Max: " + PropertyValueFormatters.formatVector3d(box.getMax()));
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    }
}

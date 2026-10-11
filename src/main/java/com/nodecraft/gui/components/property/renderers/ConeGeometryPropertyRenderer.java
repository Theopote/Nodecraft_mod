package com.nodecraft.gui.components.property.renderers;

import com.nodecraft.gui.components.PropertyPanelComponent;
import com.nodecraft.gui.components.property.core.PropertyDescriptor;
import com.nodecraft.gui.components.property.core.PropertyRenderer;
import com.nodecraft.gui.components.property.core.PropertyValueFormatters;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.datatypes.ConeGeometryData;
import imgui.ImGui;

public final class ConeGeometryPropertyRenderer {
    public static final PropertyRenderer RENDERER = ConeGeometryPropertyRenderer::render;

    private ConeGeometryPropertyRenderer() {
    }

    private static void render(PropertyPanelComponent panel, INode node, PropertyDescriptor prop, boolean isDisabled) {
        try {
            ConeGeometryData cone = (ConeGeometryData) prop.getter.invoke(node);
            if (cone == null) {
                ImGui.textDisabled("(null)");
                return;
            }

            ImGui.text("Base Center: " + PropertyValueFormatters.formatVector3d(cone.getBaseCenter()));
            ImGui.text("Apex: " + PropertyValueFormatters.formatVector3d(cone.getApex()));
            ImGui.text("Axis: " + PropertyValueFormatters.formatVector3d(cone.getAxisVector()));
            ImGui.text(String.format("Height: %.2f", cone.getHeight()));
            ImGui.text(String.format("Base Radius: %.2f", cone.getBaseRadius()));
        } catch (Throwable e) {
            panel.handlePropertyError(node, prop, e);
        }
    }
}

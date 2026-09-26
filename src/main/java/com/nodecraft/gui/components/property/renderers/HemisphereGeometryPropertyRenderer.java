package com.nodecraft.gui.components.property.renderers;

import com.nodecraft.gui.components.PropertyPanelComponent;
import com.nodecraft.gui.components.property.core.PropertyDescriptor;
import com.nodecraft.gui.components.property.core.PropertyRenderer;
import com.nodecraft.gui.components.property.core.PropertyValueFormatters;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.datatypes.HemisphereGeometryData;
import imgui.ImGui;

public final class HemisphereGeometryPropertyRenderer {
    public static final PropertyRenderer RENDERER = HemisphereGeometryPropertyRenderer::render;

    private HemisphereGeometryPropertyRenderer() {
    }

    private static void render(PropertyPanelComponent panel, INode node, PropertyDescriptor prop, boolean isDisabled) {
        try {
            HemisphereGeometryData hemisphere = (HemisphereGeometryData) prop.getter.invoke(node);
            if (hemisphere == null) {
                ImGui.textDisabled("(null)");
                return;
            }

            ImGui.text("Center: " + PropertyValueFormatters.formatVector3d(hemisphere.center()));
            ImGui.text("Axis: " + PropertyValueFormatters.formatVector3d(hemisphere.axis()));
            ImGui.text(String.format("Radius: %.2f", hemisphere.radius()));
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    }
}

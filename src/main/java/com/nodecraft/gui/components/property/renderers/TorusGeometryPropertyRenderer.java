package com.nodecraft.gui.components.property.renderers;

import com.nodecraft.gui.components.PropertyPanelComponent;
import com.nodecraft.gui.components.property.core.PropertyDescriptor;
import com.nodecraft.gui.components.property.core.PropertyRenderer;
import com.nodecraft.gui.components.property.core.PropertyValueFormatters;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.datatypes.TorusGeometryData;
import imgui.ImGui;

public final class TorusGeometryPropertyRenderer {
    public static final PropertyRenderer RENDERER = TorusGeometryPropertyRenderer::render;

    private TorusGeometryPropertyRenderer() {
    }

    private static void render(PropertyPanelComponent panel, INode node, PropertyDescriptor prop, boolean isDisabled) {
        try {
            TorusGeometryData torus = (TorusGeometryData) prop.getter.invoke(node);
            if (torus == null) {
                ImGui.textDisabled("(null)");
                return;
            }

            ImGui.text("Center: " + PropertyValueFormatters.formatVector3d(torus.center()));
            ImGui.text("Axis: " + PropertyValueFormatters.formatVector3d(torus.axis()));
            ImGui.text(String.format("Major Radius: %.2f", torus.majorRadius()));
            ImGui.text(String.format("Minor Radius: %.2f", torus.minorRadius()));
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    }
}

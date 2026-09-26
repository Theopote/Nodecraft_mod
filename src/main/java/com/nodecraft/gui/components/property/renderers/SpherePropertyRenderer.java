package com.nodecraft.gui.components.property.renderers;

import com.nodecraft.gui.components.PropertyPanelComponent;
import com.nodecraft.gui.components.property.core.PropertyDescriptor;
import com.nodecraft.gui.components.property.core.PropertyRenderer;
import com.nodecraft.gui.components.property.core.PropertyValueFormatters;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.datatypes.SphereData;
import imgui.ImGui;

public final class SpherePropertyRenderer {
    public static final PropertyRenderer RENDERER = SpherePropertyRenderer::render;

    private SpherePropertyRenderer() {
    }

    private static void render(PropertyPanelComponent panel, INode node, PropertyDescriptor prop, boolean isDisabled) {
        try {
            SphereData sphere = (SphereData) prop.getter.invoke(node);
            if (sphere == null) {
                ImGui.textDisabled("(null)");
                return;
            }

            ImGui.text("Center: " + PropertyValueFormatters.formatVector3d(sphere.center()));
            ImGui.text(String.format("Radius: %.2f", sphere.radius()));
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    }
}

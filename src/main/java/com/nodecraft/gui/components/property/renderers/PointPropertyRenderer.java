package com.nodecraft.gui.components.property.renderers;

import com.nodecraft.gui.components.PropertyPanelComponent;
import com.nodecraft.gui.components.property.core.PropertyDescriptor;
import com.nodecraft.gui.components.property.core.PropertyRenderer;
import com.nodecraft.gui.components.property.core.PropertyValueFormatters;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.datatypes.PointData;
import imgui.ImGui;

public final class PointPropertyRenderer {
    public static final PropertyRenderer RENDERER = PointPropertyRenderer::render;

    private PointPropertyRenderer() {
    }

    private static void render(PropertyPanelComponent panel, INode node, PropertyDescriptor prop, boolean isDisabled) {
        try {
            PointData point = (PointData) prop.getter.invoke(node);
            if (point == null) {
                ImGui.textDisabled("(null)");
                return;
            }

            ImGui.text("Position: " + PropertyValueFormatters.formatVector3d(point.position()));
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    }
}

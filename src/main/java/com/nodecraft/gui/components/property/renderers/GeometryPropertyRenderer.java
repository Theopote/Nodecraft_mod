package com.nodecraft.gui.components.property.renderers;

import com.nodecraft.gui.components.PropertyPanelComponent;
import com.nodecraft.gui.components.property.core.PropertyDescriptor;
import com.nodecraft.gui.components.property.core.PropertyRenderer;
import com.nodecraft.gui.components.property.core.PropertyValueFormatters;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import imgui.ImGui;

public final class GeometryPropertyRenderer {
    public static final PropertyRenderer RENDERER = GeometryPropertyRenderer::render;

    private GeometryPropertyRenderer() {
    }

    private static void render(PropertyPanelComponent panel, INode node, PropertyDescriptor prop, boolean isDisabled) {
        try {
            Object value = prop.getter.invoke(node);
            if (value == null) {
                ImGui.textDisabled("(null)");
                return;
            }

            PropertyRenderer fallbackGeometryRenderer = panel.getRendererForType(GeometryData.class);
            PropertyRenderer delegate = panel.getRendererForType(value.getClass());
            if (delegate != null
                    && delegate != fallbackGeometryRenderer
                    && delegate != RENDERER) {
                delegate.render(panel, node, prop, isDisabled);
                return;
            }

            ImGui.text("Type: " + value.getClass().getSimpleName());
            ImGui.textWrapped(value.toString());
        } catch (Throwable e) {
            panel.handlePropertyError(node, prop, e);
        }
    }
}

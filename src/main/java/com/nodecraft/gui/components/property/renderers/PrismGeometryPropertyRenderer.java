package com.nodecraft.gui.components.property.renderers;

import com.nodecraft.gui.components.PropertyPanelComponent;
import com.nodecraft.gui.components.property.core.PropertyDescriptor;
import com.nodecraft.gui.components.property.core.PropertyRenderer;
import com.nodecraft.gui.components.property.core.PropertyValueFormatters;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.datatypes.PrismGeometryData;
import imgui.ImGui;

public final class PrismGeometryPropertyRenderer {
    public static final PropertyRenderer RENDERER = PrismGeometryPropertyRenderer::render;

    private PrismGeometryPropertyRenderer() {
    }

    private static void render(PropertyPanelComponent panel, INode node, PropertyDescriptor prop, boolean isDisabled) {
        try {
            PrismGeometryData prism = (PrismGeometryData) prop.getter.invoke(node);
            if (prism == null) {
                ImGui.textDisabled("(null)");
                return;
            }

            ImGui.text("Base Vertices: " + prism.baseVertices().size());
            ImGui.text("Side Count: " + prism.getSideCount());
            ImGui.text(String.format("Height: %.2f", prism.getHeight()));
            ImGui.text("Extrusion: " + PropertyValueFormatters.formatVector3d(prism.extrusionVector()));
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    }
}

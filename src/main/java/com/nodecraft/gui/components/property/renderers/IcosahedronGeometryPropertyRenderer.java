package com.nodecraft.gui.components.property.renderers;

import com.nodecraft.gui.components.PropertyPanelComponent;
import com.nodecraft.gui.components.property.core.PropertyDescriptor;
import com.nodecraft.gui.components.property.core.PropertyRenderer;
import com.nodecraft.gui.components.property.core.PropertyValueFormatters;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.datatypes.IcosahedronGeometryData;
import imgui.ImGui;

public final class IcosahedronGeometryPropertyRenderer {
    public static final PropertyRenderer RENDERER = IcosahedronGeometryPropertyRenderer::render;

    private IcosahedronGeometryPropertyRenderer() {
    }

    private static void render(PropertyPanelComponent panel, INode node, PropertyDescriptor prop, boolean isDisabled) {
        try {
            IcosahedronGeometryData icosa = (IcosahedronGeometryData) prop.getter.invoke(node);
            if (icosa == null) {
                ImGui.textDisabled("(null)");
                return;
            }

            ImGui.text("Center: " + PropertyValueFormatters.formatVector3d(icosa.getCenter()));
            ImGui.text(String.format("Edge Length: %.2f", icosa.getEdgeLength()));
            ImGui.text(String.format("Circumradius: %.2f", icosa.getCircumradius()));
            ImGui.text("Vertices: " + icosa.getVertices().size());
        } catch (Throwable e) {
            panel.handlePropertyError(node, prop, e);
        }
    }
}

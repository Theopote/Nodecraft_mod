package com.nodecraft.gui.components;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.datatypes.PrismGeometryData;
import imgui.ImGui;

final class PrismGeometryPropertyRenderer {

    static final PropertyRenderer RENDERER = PrismGeometryPropertyRenderer::render;

    private PrismGeometryPropertyRenderer() {
    }

    private static void render(PropertyPanelComponent panel, INode node, PropertyDescriptor prop, boolean isDisabled) {
        try {
            PrismGeometryData prism = (PrismGeometryData) prop.getter.invoke(node);
            if (prism == null) {
                ImGui.textDisabled("(null)");
                return;
            }

            ImGui.text("Base Vertices: " + prism.getBaseVertices().size());
            ImGui.text("Side Count: " + prism.getSideCount());
            ImGui.text(String.format("Height: %.2f", prism.getHeight()));
            ImGui.text("Extrusion: " + PropertyValueFormatters.formatVector3d(prism.getExtrusionVector()));
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    }
}

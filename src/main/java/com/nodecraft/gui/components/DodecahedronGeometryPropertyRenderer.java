package com.nodecraft.gui.components;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.datatypes.DodecahedronGeometryData;
import imgui.ImGui;

final class DodecahedronGeometryPropertyRenderer {

    static final PropertyRenderer RENDERER = DodecahedronGeometryPropertyRenderer::render;

    private DodecahedronGeometryPropertyRenderer() {
    }

    private static void render(PropertyPanelComponent panel, INode node, PropertyDescriptor prop, boolean isDisabled) {
        try {
            DodecahedronGeometryData dod = (DodecahedronGeometryData) prop.getter.invoke(node);
            if (dod == null) {
                ImGui.textDisabled("(null)");
                return;
            }

            ImGui.text("Center: " + PropertyValueFormatters.formatVector3d(dod.getCenter()));
            ImGui.text(String.format("Edge Length: %.2f", dod.getEdgeLength()));
            ImGui.text(String.format("Circumradius: %.2f", dod.getCircumradius()));
            ImGui.text("Vertices: " + dod.getVertices().size());
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    }
}

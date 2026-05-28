package com.nodecraft.gui.components;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.datatypes.OctahedronGeometryData;
import imgui.ImGui;

final class OctahedronGeometryPropertyRenderer {

    static final PropertyRenderer RENDERER = OctahedronGeometryPropertyRenderer::render;

    private OctahedronGeometryPropertyRenderer() {
    }

    private static void render(PropertyPanelComponent panel, INode node, PropertyDescriptor prop, boolean isDisabled) {
        try {
            OctahedronGeometryData octahedron = (OctahedronGeometryData) prop.getter.invoke(node);
            if (octahedron == null) {
                ImGui.textDisabled("(null)");
                return;
            }

            ImGui.text("Center: " + PropertyValueFormatters.formatVector3d(octahedron.getCenter()));
            ImGui.text(String.format("Vertex Radius: %.2f", octahedron.getVertexRadius()));
            ImGui.text("Vertices: " + octahedron.getVertices().size());
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    }
}

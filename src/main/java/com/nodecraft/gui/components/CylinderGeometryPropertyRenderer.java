package com.nodecraft.gui.components;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.datatypes.CylinderGeometryData;
import imgui.ImGui;
import org.joml.Vector3d;

final class CylinderGeometryPropertyRenderer {

    static final PropertyRenderer RENDERER = CylinderGeometryPropertyRenderer::render;

    private CylinderGeometryPropertyRenderer() {
    }

    private static void render(PropertyPanelComponent panel, INode node, PropertyDescriptor prop, boolean isDisabled) {
        try {
            CylinderGeometryData cylinder = (CylinderGeometryData) prop.getter.invoke(node);
            if (cylinder == null) {
                ImGui.textDisabled("(null)");
                return;
            }

            Vector3d axis = cylinder.getEnd().sub(cylinder.getStart(), new Vector3d());
            ImGui.text("Start: " + PropertyValueFormatters.formatVector3d(cylinder.getStart()));
            ImGui.text("End: " + PropertyValueFormatters.formatVector3d(cylinder.getEnd()));
            ImGui.text("Axis: " + PropertyValueFormatters.formatVector3d(axis));
            ImGui.text(String.format("Length: %.2f", axis.length()));
            ImGui.text(String.format("Radius: %.2f", cylinder.getRadius()));
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    }
}

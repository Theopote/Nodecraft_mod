package com.nodecraft.gui.components;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.datatypes.TorusGeometryData;
import imgui.ImGui;
import org.joml.Vector3d;

final class TorusGeometryPropertyRenderer {

    static final PropertyRenderer RENDERER = TorusGeometryPropertyRenderer::render;

    private TorusGeometryPropertyRenderer() {
    }

    private static void render(PropertyPanelComponent panel, INode node, PropertyDescriptor prop, boolean isDisabled) {
        try {
            TorusGeometryData torus = (TorusGeometryData) prop.getter.invoke(node);
            if (torus == null) {
                ImGui.textDisabled("(null)");
                return;
            }

            ImGui.text("Center: " + formatVector3d(torus.getCenter()));
            ImGui.text("Axis: " + formatVector3d(torus.getAxis()));
            ImGui.text(String.format("Major Radius: %.2f", torus.getMajorRadius()));
            ImGui.text(String.format("Minor Radius: %.2f", torus.getMinorRadius()));
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    }

    private static String formatVector3d(Vector3d vec) {
        if (vec == null) {
            return "(null)";
        }
        return String.format("(%.2f, %.2f, %.2f)", vec.x, vec.y, vec.z);
    }
}

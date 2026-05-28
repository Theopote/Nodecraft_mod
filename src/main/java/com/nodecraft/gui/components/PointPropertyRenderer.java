package com.nodecraft.gui.components;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.datatypes.PointData;
import imgui.ImGui;
import org.joml.Vector3d;

final class PointPropertyRenderer {

    static final PropertyRenderer RENDERER = PointPropertyRenderer::render;

    private PointPropertyRenderer() {
    }

    private static void render(PropertyPanelComponent panel, INode node, PropertyDescriptor prop, boolean isDisabled) {
        try {
            PointData point = (PointData) prop.getter.invoke(node);
            if (point == null) {
                ImGui.textDisabled("(null)");
                return;
            }

            ImGui.text("Position: " + formatVector3d(point.getPosition()));
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

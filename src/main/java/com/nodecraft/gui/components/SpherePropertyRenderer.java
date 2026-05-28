package com.nodecraft.gui.components;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.datatypes.SphereData;
import imgui.ImGui;
import org.joml.Vector3d;

final class SpherePropertyRenderer {

    static final PropertyRenderer RENDERER = SpherePropertyRenderer::render;

    private SpherePropertyRenderer() {
    }

    private static void render(PropertyPanelComponent panel, INode node, PropertyDescriptor prop, boolean isDisabled) {
        try {
            SphereData sphere = (SphereData) prop.getter.invoke(node);
            if (sphere == null) {
                ImGui.textDisabled("(null)");
                return;
            }

            ImGui.text("Center: " + formatVector3d(sphere.getCenter()));
            ImGui.text(String.format("Radius: %.2f", sphere.getRadius()));
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

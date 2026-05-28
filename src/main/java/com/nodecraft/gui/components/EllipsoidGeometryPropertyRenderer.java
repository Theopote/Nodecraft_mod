package com.nodecraft.gui.components;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.datatypes.EllipsoidGeometryData;
import imgui.ImGui;
import org.joml.Vector3d;

final class EllipsoidGeometryPropertyRenderer {

    static final PropertyRenderer RENDERER = EllipsoidGeometryPropertyRenderer::render;

    private EllipsoidGeometryPropertyRenderer() {
    }

    private static void render(PropertyPanelComponent panel, INode node, PropertyDescriptor prop, boolean isDisabled) {
        try {
            EllipsoidGeometryData ellipsoid = (EllipsoidGeometryData) prop.getter.invoke(node);
            if (ellipsoid == null) {
                ImGui.textDisabled("(null)");
                return;
            }

            ImGui.text("Center: " + formatVector3d(ellipsoid.getCenter()));
            ImGui.text("Radii: " + formatVector3d(ellipsoid.getRadii()));
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

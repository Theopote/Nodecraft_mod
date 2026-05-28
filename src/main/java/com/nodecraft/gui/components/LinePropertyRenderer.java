package com.nodecraft.gui.components;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.datatypes.LineData;
import imgui.ImGui;
import net.minecraft.util.math.Vec3d;

final class LinePropertyRenderer {

    static final PropertyRenderer RENDERER = LinePropertyRenderer::render;

    private LinePropertyRenderer() {
    }

    private static void render(PropertyPanelComponent panel, INode node, PropertyDescriptor prop, boolean isDisabled) {
        try {
            LineData line = (LineData) prop.getter.invoke(node);
            if (line == null) {
                ImGui.textDisabled("(null)");
                return;
            }

            ImGui.text("Start: " + formatVec3d(line.getStart()));
            ImGui.text("End: " + formatVec3d(line.getEnd()));
            ImGui.text("Direction: " + formatVec3d(line.getDirection()));
            ImGui.text(String.format("Length: %.2f", line.getLength()));
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    }

    private static String formatVec3d(Vec3d vec) {
        if (vec == null) {
            return "(null)";
        }
        return String.format("(%.2f, %.2f, %.2f)", vec.x, vec.y, vec.z);
    }
}

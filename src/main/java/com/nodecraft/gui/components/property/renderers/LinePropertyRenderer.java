package com.nodecraft.gui.components.property.renderers;

import com.nodecraft.gui.components.PropertyPanelComponent;
import com.nodecraft.gui.components.property.core.PropertyDescriptor;
import com.nodecraft.gui.components.property.core.PropertyRenderer;
import com.nodecraft.gui.components.property.core.PropertyValueFormatters;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.datatypes.LineData;
import imgui.ImGui;

public final class LinePropertyRenderer {
    public static final PropertyRenderer RENDERER = LinePropertyRenderer::render;

    private LinePropertyRenderer() {
    }

    private static void render(PropertyPanelComponent panel, INode node, PropertyDescriptor prop, boolean isDisabled) {
        try {
            LineData line = (LineData) prop.getter.invoke(node);
            if (line == null) {
                ImGui.textDisabled("(null)");
                return;
            }

            ImGui.text("Start: " + PropertyValueFormatters.formatVec3d(line.start()));
            ImGui.text("End: " + PropertyValueFormatters.formatVec3d(line.end()));
            ImGui.text("Direction: " + PropertyValueFormatters.formatVec3d(line.getDirection()));
            ImGui.text(String.format("Length: %.2f", line.getLength()));
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    }
}

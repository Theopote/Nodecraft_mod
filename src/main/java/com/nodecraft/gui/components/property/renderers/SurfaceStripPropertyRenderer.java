package com.nodecraft.gui.components.property.renderers;

import com.nodecraft.gui.components.PropertyPanelComponent;
import com.nodecraft.gui.components.property.core.PropertyDescriptor;
import com.nodecraft.gui.components.property.core.PropertyRenderer;
import com.nodecraft.gui.components.property.core.PropertyValueFormatters;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.datatypes.SurfaceStripData;
import imgui.ImGui;

public final class SurfaceStripPropertyRenderer {
    public static final PropertyRenderer RENDERER = SurfaceStripPropertyRenderer::render;

    private SurfaceStripPropertyRenderer() {
    }

    private static void render(PropertyPanelComponent panel, INode node, PropertyDescriptor prop, boolean isDisabled) {
        try {
            SurfaceStripData strip = (SurfaceStripData) prop.getter.invoke(node);
            if (strip == null) {
                ImGui.textDisabled("(null)");
                return;
            }

            ImGui.text("Sections: " + strip.getSectionCount());
            ImGui.text("Points / Section: " + strip.getPointsPerSection());
            ImGui.text("Flattened Points: " + strip.getFlattenedPoints().size());
            ImGui.text("All Closed: " + (strip.areAllSectionsClosed() ? "Yes" : "No"));
        } catch (Throwable e) {
            panel.handlePropertyError(node, prop, e);
        }
    }
}

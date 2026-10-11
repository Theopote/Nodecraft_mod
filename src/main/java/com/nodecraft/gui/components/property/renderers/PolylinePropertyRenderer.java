package com.nodecraft.gui.components.property.renderers;

import com.nodecraft.gui.components.PropertyPanelComponent;
import com.nodecraft.gui.components.property.core.PropertyDescriptor;
import com.nodecraft.gui.components.property.core.PropertyRenderer;
import com.nodecraft.gui.components.property.core.PropertyValueFormatters;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import imgui.ImGui;

public final class PolylinePropertyRenderer {
    public static final PropertyRenderer RENDERER = PolylinePropertyRenderer::render;

    private PolylinePropertyRenderer() {
    }

    private static void render(PropertyPanelComponent panel, INode node, PropertyDescriptor prop, boolean isDisabled) {
        if (isDisabled) {
            PropertyEditorUi.renderDisabled(prop.displayName);
            return;
        }

        try {
            PolylineData polyline = (PolylineData) prop.getter.invoke(node);
            if (polyline == null) {
                ImGui.textDisabled("(null)");
                return;
            }

            ImGui.text(PropertyValueFormatters.formatPolylineSummary(polyline));
            if (ImGui.treeNode("Details##polyline_" + prop.name)) {
                ImGui.text("Points: " + polyline.getPointCount());
                ImGui.text("Segments: " + polyline.getSegmentCount());
                ImGui.text(String.format(java.util.Locale.ROOT, "Length: %.2f", polyline.getLength()));
                ImGui.text("Closed: " + (polyline.isClosed() ? "Yes" : "No"));
                ImGui.treePop();
            }
        } catch (Throwable e) {
            panel.handlePropertyError(node, prop, e);
        }
    }
}

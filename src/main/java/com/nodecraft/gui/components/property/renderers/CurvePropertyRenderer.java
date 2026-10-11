package com.nodecraft.gui.components.property.renderers;

import com.nodecraft.gui.components.PropertyPanelComponent;
import com.nodecraft.gui.components.property.core.PropertyDescriptor;
import com.nodecraft.gui.components.property.core.PropertyRenderer;
import com.nodecraft.gui.components.property.core.PropertyValueFormatters;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.util.Curve;
import imgui.ImGui;

public final class CurvePropertyRenderer {
    public static final PropertyRenderer RENDERER = CurvePropertyRenderer::render;

    private CurvePropertyRenderer() {
    }

    private static void render(PropertyPanelComponent panel, INode node, PropertyDescriptor prop, boolean isDisabled) {
        try {
            Curve curve = (Curve) prop.getter.invoke(node);
            if (curve == null) {
                ImGui.textDisabled("(null)");
                return;
            }

            ImGui.text("Type: " + curve.getCurveType());
            ImGui.text("Control Points: " + curve.size());
            ImGui.text("Resolution: " + curve.getResolution());
            ImGui.text("Sample Points: " + curve.getSamplePoints().size());
        } catch (Throwable e) {
            panel.handlePropertyError(node, prop, e);
        }
    }
}

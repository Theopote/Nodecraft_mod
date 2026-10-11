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
        if (isDisabled) {
            PropertyEditorUi.renderDisabled(prop.displayName);
            return;
        }

        try {
            Curve curve = (Curve) prop.getter.invoke(node);
            if (curve == null) {
                ImGui.textDisabled("(null)");
                return;
            }

            ImGui.text(PropertyValueFormatters.formatCurveSummary(curve));
            if (ImGui.treeNode("Details##curve_" + prop.name)) {
                ImGui.text("Type: " + curve.getCurveType());
                ImGui.text("Control Points: " + curve.size());
                ImGui.text("Resolution: " + curve.getResolution());
                // Sample points are expensive (recomputed each call) — only when expanded.
                ImGui.text("Sample Points: " + curve.getSamplePoints().size());
                ImGui.treePop();
            }
        } catch (Throwable e) {
            panel.handlePropertyError(node, prop, e);
        }
    }
}

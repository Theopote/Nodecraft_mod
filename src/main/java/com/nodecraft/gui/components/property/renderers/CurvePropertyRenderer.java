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

                // Sampling is expensive and not cached on Curve — compute only on demand.
                String sampleKey = panel.getTempValueKey(node, prop.name + "_sample_count");
                Integer[] sampleCount = panel.getOrCreateTempValue(sampleKey, () -> new Integer[]{null});
                if (sampleCount[0] != null) {
                    ImGui.text("Sample Points: " + sampleCount[0]);
                    if (ImGui.smallButton("Refresh##curve_samples_" + prop.name)) {
                        sampleCount[0] = curve.getSamplePoints().size();
                    }
                } else if (ImGui.smallButton("Compute sample count##curve_samples_" + prop.name)) {
                    sampleCount[0] = curve.getSamplePoints().size();
                } else {
                    ImGui.sameLine();
                    ImGui.textDisabled("(not computed)");
                }
                ImGui.treePop();
            }
        } catch (Throwable e) {
            panel.handlePropertyError(node, prop, e);
        }
    }
}

package com.nodecraft.gui.components.property.renderers;

import com.nodecraft.gui.components.PropertyPanelComponent;
import com.nodecraft.gui.components.property.core.PropertyDescriptor;
import com.nodecraft.gui.components.property.core.PropertyRenderer;
import com.nodecraft.gui.components.property.core.PropertyValueFormatters;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.util.Curve;
import imgui.ImGui;

import java.util.Objects;

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

                String sampleKey = panel.getTempValueKey(node, prop.name + "_sample_cache");
                SampleCache cache = panel.getOrCreateTempValue(sampleKey, SampleCache::new);
                int fingerprint = fingerprint(curve);
                if (cache.count != null && cache.fingerprint != fingerprint) {
                    cache.count = null;
                }

                if (cache.count != null) {
                    ImGui.text("Sample Points: " + cache.count);
                    if (ImGui.smallButton("Refresh##curve_samples_" + prop.name)) {
                        cache.count = curve.getSamplePoints().size();
                        cache.fingerprint = fingerprint;
                    }
                } else {
                    ImGui.textDisabled("Sample Points: (stale — recompute)");
                    if (ImGui.smallButton("Compute sample count##curve_samples_" + prop.name)) {
                        cache.count = curve.getSamplePoints().size();
                        cache.fingerprint = fingerprint;
                    }
                }
                ImGui.treePop();
            }
        } catch (Throwable e) {
            panel.handlePropertyError(node, prop, e);
        }
    }

    static int fingerprint(Curve curve) {
        return Objects.hash(
                curve.getCurveType(),
                curve.getResolution(),
                curve.size(),
                System.identityHashCode(curve)
        );
    }

    private static final class SampleCache {
        Integer count;
        int fingerprint;
    }
}

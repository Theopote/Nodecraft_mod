package com.nodecraft.gui.components.property.renderers;

import com.nodecraft.gui.components.PropertyPanelComponent;
import com.nodecraft.gui.components.property.core.PropertyDescriptor;
import com.nodecraft.gui.components.property.core.PropertyRenderer;
import com.nodecraft.gui.components.property.core.PropertyValueFormatters;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.datatypes.RegionData;
import imgui.ImGui;

public final class RegionPropertyRenderer {
    public static final PropertyRenderer RENDERER = RegionPropertyRenderer::render;

    private RegionPropertyRenderer() {
    }

    private static void render(PropertyPanelComponent panel, INode node, PropertyDescriptor prop, boolean isDisabled) {
        if (isDisabled) {
            PropertyEditorUi.renderDisabled(prop.displayName);
            return;
        }

        try {
            RegionData region = (RegionData) prop.getter.invoke(node);
            if (region == null) {
                ImGui.textDisabled("(null)");
                return;
            }

            ImGui.text(PropertyValueFormatters.formatRegionSummary(region));
            if (ImGui.treeNode("Details##region_" + prop.name)) {
                ImGui.text("Complete: " + (region.isComplete() ? "Yes" : "No"));
                ImGui.text("Corner 1: " + PropertyValueFormatters.formatBlockPos(region.corner1()));
                ImGui.text("Corner 2: " + PropertyValueFormatters.formatBlockPos(region.corner2()));
                if (region.isComplete()) {
                    ImGui.text("Min: " + PropertyValueFormatters.formatBlockPos(region.getMinCorner()));
                    ImGui.text("Max: " + PropertyValueFormatters.formatBlockPos(region.getMaxCorner()));
                }
                ImGui.treePop();
            }
        } catch (Throwable e) {
            panel.handlePropertyError(node, prop, e);
        }
    }
}

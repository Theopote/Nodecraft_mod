package com.nodecraft.gui.components;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.datatypes.RegionData;
import imgui.ImGui;
import net.minecraft.util.math.BlockPos;

final class RegionPropertyRenderer {

    static final PropertyRenderer RENDERER = RegionPropertyRenderer::render;

    private RegionPropertyRenderer() {
    }

    private static void render(PropertyPanelComponent panel, INode node, PropertyDescriptor prop, boolean isDisabled) {
        try {
            RegionData region = (RegionData) prop.getter.invoke(node);
            if (region == null) {
                ImGui.textDisabled("(null)");
                return;
            }

            ImGui.text("Complete: " + (region.isComplete() ? "Yes" : "No"));
            ImGui.text("Corner 1: " + formatBlockPos(region.corner1()));
            ImGui.text("Corner 2: " + formatBlockPos(region.corner2()));
            if (region.isComplete()) {
                ImGui.text("Min: " + formatBlockPos(region.getMinCorner()));
                ImGui.text("Max: " + formatBlockPos(region.getMaxCorner()));
            }
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    }

    private static String formatBlockPos(BlockPos pos) {
        if (pos == null) {
            return "(null)";
        }
        return String.format("(%d, %d, %d)", pos.getX(), pos.getY(), pos.getZ());
    }
}

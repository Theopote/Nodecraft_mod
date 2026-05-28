package com.nodecraft.gui.components;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.datatypes.PlantStructure;
import imgui.ImGui;

final class PlantBlockPropertyRenderer {

    static final PropertyRenderer RENDERER = PlantBlockPropertyRenderer::render;

    private PlantBlockPropertyRenderer() {
    }

    private static void render(PropertyPanelComponent panel, INode node, PropertyDescriptor prop, boolean isDisabled) {
        try {
            PlantStructure.PlantBlock block = (PlantStructure.PlantBlock) prop.getter.invoke(node);
            if (block == null) {
                ImGui.textDisabled("(null)");
                return;
            }

            ImGui.text("Position: " + PropertyValueFormatters.formatBlockPos(block.getPosition()));
            ImGui.text("Block Type: " + block.getBlockType());
            ImGui.text(String.format("Thickness: %.2f", block.getThickness()));
            ImGui.text("Properties: " + block.getProperties().size());
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    }
}

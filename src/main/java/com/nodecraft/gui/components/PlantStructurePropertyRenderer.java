package com.nodecraft.gui.components;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.datatypes.PlantStructure;
import imgui.ImGui;

final class PlantStructurePropertyRenderer {

    static final PropertyRenderer RENDERER = PlantStructurePropertyRenderer::render;

    private PlantStructurePropertyRenderer() {
    }

    private static void render(PropertyPanelComponent panel, INode node, PropertyDescriptor prop, boolean isDisabled) {
        try {
            PlantStructure structure = (PlantStructure) prop.getter.invoke(node);
            if (structure == null) {
                ImGui.textDisabled("(null)");
                return;
            }

            ImGui.text("Total Blocks: " + structure.getTotalBlockCount());
            ImGui.text("Trunk: " + structure.getTrunkBlockCount());
            ImGui.text("Branches: " + structure.getBranchBlockCount());
            ImGui.text("Leaves: " + structure.getLeafBlockCount());
            ImGui.text("Flowers: " + structure.getFlowerBlockCount());
            ImGui.text("Roots: " + structure.getRootBlockCount());
            ImGui.text("Metadata: " + structure.getMetadata().size());
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    }
}

package com.nodecraft.gui.components.property.renderers;

import com.nodecraft.gui.components.PropertyPanelComponent;
import com.nodecraft.gui.components.property.core.PropertyDescriptor;
import com.nodecraft.gui.components.property.core.PropertyRenderer;
import com.nodecraft.nodesystem.api.INode;
import imgui.ImGui;
import net.minecraft.util.math.BlockPos;

public final class BlockPosPropertyRenderer {
    public static final PropertyRenderer RENDERER = BlockPosPropertyRenderer::render;

    private BlockPosPropertyRenderer() {
    }

    private static void render(PropertyPanelComponent panel, INode node, PropertyDescriptor prop, boolean isDisabled) {
        if (isDisabled) {
            ImGui.textDisabled("(disabled)");
            return;
        }

        try {
            BlockPos pos = (BlockPos) prop.getter.invoke(node);
            if (pos == null) {
                ImGui.textDisabled("(null)");
                return;
            }

            boolean isReadOnly = prop.setter == null;
            String tempKey = panel.getTempValueKey(node, prop.name + "_blockpos");
            int[] values = panel.getOrCreateTempValue(tempKey, () -> new int[]{pos.getX(), pos.getY(), pos.getZ()});

            if (!panel.isPropertyBeingEdited(node, prop.name)) {
                values[0] = pos.getX();
                values[1] = pos.getY();
                values[2] = pos.getZ();
            }

            if (isReadOnly) {
                ImGui.beginDisabled();
            }

            boolean changed = false;
            int[] xValue = {values[0]};
            int[] yValue = {values[1]};
            int[] zValue = {values[2]};

            changed |= ImGui.dragInt("X##" + prop.name, xValue, 1);
            values[0] = xValue[0];
            if (ImGui.isItemActive()) {
                panel.markPropertyBeingEdited(node, prop.name);
            }

            changed |= ImGui.dragInt("Y##" + prop.name, yValue, 1);
            values[1] = yValue[0];
            if (ImGui.isItemActive()) {
                panel.markPropertyBeingEdited(node, prop.name);
            }

            changed |= ImGui.dragInt("Z##" + prop.name, zValue, 1);
            values[2] = zValue[0];
            if (ImGui.isItemActive()) {
                panel.markPropertyBeingEdited(node, prop.name);
            }

            if (ImGui.isItemDeactivated()) {
                panel.markPropertyEditingFinished(node, prop.name);
            }

            if (isReadOnly) {
                ImGui.endDisabled();
            }

            if (!isReadOnly && changed) {
                panel.applyPropertyValue(node, prop, new BlockPos(values[0], values[1], values[2]));
            }

            panel.clearPropertyError(node, prop.name);
        } catch (Throwable e) {
            panel.handlePropertyError(node, prop, e);
        }
    }
}

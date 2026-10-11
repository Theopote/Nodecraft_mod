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
            PropertyEditorUi.renderDisabled(prop.displayName);
            return;
        }

        try {
            BlockPos pos = (BlockPos) prop.getter.invoke(node);
            if (pos == null) {
                ImGui.textDisabled("(null)");
                return;
            }

            boolean isReadOnly = prop.setter == null;
            VectorComponentEditor.renderBlockPosRow(
                    panel,
                    node,
                    prop,
                    pos.getX(),
                    pos.getY(),
                    pos.getZ(),
                    isReadOnly,
                    (x, y, z) -> panel.applyPropertyValue(node, prop, new BlockPos(x, y, z))
            );

            panel.clearPropertyError(node, prop.name);
        } catch (Throwable e) {
            panel.handlePropertyError(node, prop, e);
        }
    }
}

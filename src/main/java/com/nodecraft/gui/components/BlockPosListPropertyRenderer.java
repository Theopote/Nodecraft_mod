package com.nodecraft.gui.components;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.util.BlockPosList;
import imgui.ImGui;
import net.minecraft.util.math.BlockPos;

import java.util.List;

final class BlockPosListPropertyRenderer {

    static final PropertyRenderer RENDERER = BlockPosListPropertyRenderer::render;

    private BlockPosListPropertyRenderer() {
    }

    private static void render(PropertyPanelComponent panel, INode node, PropertyDescriptor prop, boolean isDisabled) {
        try {
            BlockPosList positions = (BlockPosList) prop.getter.invoke(node);
            if (positions == null) {
                ImGui.textDisabled("(null)");
                return;
            }

            ImGui.text("Count: " + positions.size());
            List<BlockPos> preview = positions.getPositions();
            if (!preview.isEmpty()) {
                ImGui.text("First: " + formatBlockPos(preview.getFirst()));
            }
            if (preview.size() > 1) {
                ImGui.text("Last: " + formatBlockPos(preview.getLast()));
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

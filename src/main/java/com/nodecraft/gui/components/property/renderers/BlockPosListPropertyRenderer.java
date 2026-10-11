package com.nodecraft.gui.components.property.renderers;

import com.nodecraft.gui.components.PropertyPanelComponent;
import com.nodecraft.gui.components.property.core.PropertyDescriptor;
import com.nodecraft.gui.components.property.core.PropertyRenderer;
import com.nodecraft.gui.components.property.core.PropertyValueFormatters;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.util.BlockPosList;
import imgui.ImGui;
import net.minecraft.util.math.BlockPos;

import java.util.List;

public final class BlockPosListPropertyRenderer {
    public static final PropertyRenderer RENDERER = BlockPosListPropertyRenderer::render;

    private static final int PREVIEW_LIMIT = ListPropertyRenderer.DEFAULT_LIST_PREVIEW_LIMIT;

    private BlockPosListPropertyRenderer() {
    }

    private static void render(PropertyPanelComponent panel, INode node, PropertyDescriptor prop, boolean isDisabled) {
        if (isDisabled) {
            PropertyEditorUi.renderDisabled(prop.displayName);
            return;
        }

        try {
            BlockPosList positions = (BlockPosList) prop.getter.invoke(node);
            if (positions == null) {
                ImGui.textDisabled("(null)");
                return;
            }

            List<BlockPos> preview = positions.getPositions();
            ImGui.text(PropertyValueFormatters.formatBlockPosListSummary(positions.size()));
            if (!preview.isEmpty()) {
                ImGui.textDisabled(
                        "First " + PropertyValueFormatters.formatBlockPos(preview.getFirst())
                                + (preview.size() > 1
                                ? " · Last " + PropertyValueFormatters.formatBlockPos(preview.getLast())
                                : "")
                );
            }

            if (ImGui.treeNode("Details##blockposlist_" + prop.name)) {
                int limit = Math.min(preview.size(), PREVIEW_LIMIT);
                for (int i = 0; i < limit; i++) {
                    ImGui.text(String.format("[%d] %s", i, PropertyValueFormatters.formatBlockPos(preview.get(i))));
                }
                if (preview.size() > limit) {
                    ImGui.textDisabled("… and " + (preview.size() - limit) + " more");
                }
                ImGui.treePop();
            }
        } catch (Throwable e) {
            panel.handlePropertyError(node, prop, e);
        }
    }
}

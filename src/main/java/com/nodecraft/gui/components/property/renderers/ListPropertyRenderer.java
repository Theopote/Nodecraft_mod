package com.nodecraft.gui.components.property.renderers;

import com.nodecraft.gui.components.PropertyPanelComponent;
import com.nodecraft.gui.components.property.core.PropertyDescriptor;
import com.nodecraft.gui.components.property.core.PropertyRenderer;
import com.nodecraft.gui.components.property.core.PropertyValueFormatters;
import com.nodecraft.nodesystem.api.INode;
import imgui.ImGui;

import java.util.List;

public final class ListPropertyRenderer {
    public static final PropertyRenderer RENDERER = ListPropertyRenderer::render;

    /** Max items shown inside the Details fold for property-panel lists. */
    static final int DEFAULT_LIST_PREVIEW_LIMIT = 32;

    private ListPropertyRenderer() {
    }

    private static void render(PropertyPanelComponent panel, INode node, PropertyDescriptor prop, boolean isDisabled) {
        if (isDisabled) {
            PropertyEditorUi.renderDisabled(prop.displayName);
            return;
        }

        try {
            Object value = prop.getter.invoke(node);
            if (!(value instanceof List<?> list)) {
                if (value == null) {
                    ImGui.textDisabled("(null)");
                } else {
                    ImGui.textWrapped(value.toString());
                }
                return;
            }

            ImGui.text(PropertyValueFormatters.formatListSummary(list.size()));
            if (ImGui.treeNode("Details##list_" + prop.name)) {
                int limit = Math.min(list.size(), DEFAULT_LIST_PREVIEW_LIMIT);
                for (int i = 0; i < limit; i++) {
                    Object item = list.get(i);
                    ImGui.text(String.format("[%d] %s", i, item != null ? item.toString() : "null"));
                }
                if (list.size() > limit) {
                    ImGui.textDisabled("… and " + (list.size() - limit) + " more");
                }
                ImGui.treePop();
            }
        } catch (Throwable e) {
            panel.handlePropertyError(node, prop, e);
        }
    }
}

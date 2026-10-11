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

    /** Max items shown per page inside the Details fold. */
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
                renderPagedItems(
                        panel,
                        node,
                        prop.name,
                        list.size(),
                        (index) -> {
                            Object item = list.get(index);
                            ImGui.text(String.format("[%d] %s", index, item != null ? item.toString() : "null"));
                        }
                );
                ImGui.treePop();
            }
        } catch (Throwable e) {
            panel.handlePropertyError(node, prop, e);
        }
    }

    static void renderPagedItems(
            PropertyPanelComponent panel,
            INode node,
            String propName,
            int size,
            IndexRenderer renderer
    ) {
        if (size <= 0) {
            ImGui.textDisabled("(empty)");
            return;
        }

        String pageKey = panel.getTempValueKey(node, propName + "_list_page");
        int[] pageState = panel.getOrCreateTempValue(pageKey, () -> new int[]{0});
        int pageSize = DEFAULT_LIST_PREVIEW_LIMIT;
        int maxStart = Math.max(0, size - 1);
        if (pageState[0] < 0) {
            pageState[0] = 0;
        }
        if (pageState[0] > maxStart) {
            pageState[0] = maxStart;
        }

        if (ImGui.smallButton("Prev##list_prev_" + propName)) {
            pageState[0] = Math.max(0, pageState[0] - pageSize);
        }
        ImGui.sameLine();
        if (ImGui.smallButton("Next##list_next_" + propName)) {
            if (pageState[0] + pageSize < size) {
                pageState[0] = pageState[0] + pageSize;
            }
        }
        ImGui.sameLine();
        ImGui.setNextItemWidth(80.0f);
        int[] startEdit = panel.getOrCreateTempValue(
                panel.getTempValueKey(node, propName + "_list_start_edit"),
                () -> new int[]{pageState[0]}
        );
        startEdit[0] = pageState[0];
        if (ImGui.dragInt("Start##list_start_" + propName, startEdit, 1.0f, 0, maxStart)) {
            pageState[0] = Math.max(0, Math.min(maxStart, startEdit[0]));
        }

        int start = pageState[0];
        int end = Math.min(size, start + pageSize);
        ImGui.text(String.format("Showing [%d .. %d) of %d", start, end, size));

        for (int i = start; i < end; i++) {
            renderer.render(i);
        }
        if (end < size) {
            ImGui.textDisabled("… " + (size - end) + " more after this page");
        }
    }

    @FunctionalInterface
    interface IndexRenderer {
        void render(int index);
    }
}

package com.nodecraft.gui.components.property.renderers;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.components.PropertyPanelComponent;
import com.nodecraft.gui.components.property.core.PropertyDescriptor;
import com.nodecraft.gui.components.property.core.PropertyRenderer;
import com.nodecraft.gui.components.property.support.EnumPropertyLabels;
import com.nodecraft.nodesystem.api.INode;
import imgui.ImGui;
import imgui.type.ImInt;

public final class EnumPropertyRenderer {
    public static final PropertyRenderer RENDERER = EnumPropertyRenderer::render;

    private EnumPropertyRenderer() {
    }

    private static void render(PropertyPanelComponent panel, INode node, PropertyDescriptor prop, boolean isDisabled) {
        if (isDisabled) {
            PropertyEditorUi.renderDisabled(prop.displayName);
            return;
        }

        try {
            Enum<?> currentValue = (Enum<?>) prop.getter.invoke(node);
            if (currentValue == null) {
                ImGui.textDisabled("(空)");
                return;
            }

            Enum<?>[] values = currentValue.getDeclaringClass().getEnumConstants();
            String[] names = EnumPropertyLabels.buildDisplayNames(node, prop, values);

            int currentIndex = currentValue.ordinal();
            ImInt selectedIndex = new ImInt(currentIndex);
            boolean isReadOnly = prop.setter == null;

            if (isReadOnly) {
                ImGui.beginDisabled();
            }
            if (ImGui.combo("##" + prop.name, selectedIndex, names)
                    && !isReadOnly
                    && selectedIndex.get() != currentIndex) {
                panel.applyPropertyValue(node, prop, values[selectedIndex.get()]);
                NodeCraft.LOGGER.debug(
                        "自动保存属性 '{}' 到节点 {}: {}",
                        prop.name,
                        node.getId(),
                        values[selectedIndex.get()]);
            }
            if (ImGui.isItemActive()) {
                panel.markPropertyBeingEdited(node, prop.name);
            }
            if (ImGui.isItemDeactivated()) {
                panel.markPropertyEditingFinished(node, prop.name);
            }
            if (isReadOnly) {
                ImGui.endDisabled();
            }

            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(EnumPropertyLabels.buildTooltip(node, prop, values, names, selectedIndex.get()));
            }

            panel.clearPropertyError(prop.name);
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    }
}

package com.nodecraft.gui.components.property.renderers;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.components.PropertyPanelComponent;
import com.nodecraft.gui.components.property.core.PropertyDescriptor;
import com.nodecraft.gui.components.property.core.PropertyRenderer;
import com.nodecraft.gui.components.property.support.EnumPropertyLabels;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.preview.PreviewBackend;
import com.nodecraft.nodesystem.preview.TrackedWorldCompatGate;
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

            Enum<?>[] values = resolveEnumValues(currentValue);
            String[] names = EnumPropertyLabels.buildDisplayNames(node, prop, values);

            int currentIndex = indexOf(values, currentValue);
            if (currentIndex < 0) {
                currentIndex = 0;
            }
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

    private static Enum<?>[] resolveEnumValues(Enum<?> currentValue) {
        if (currentValue instanceof PreviewBackend backend) {
            return TrackedWorldCompatGate.backendsForEditor(backend);
        }
        return currentValue.getDeclaringClass().getEnumConstants();
    }

    private static int indexOf(Enum<?>[] values, Enum<?> current) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) {
                return i;
            }
        }
        return -1;
    }
}

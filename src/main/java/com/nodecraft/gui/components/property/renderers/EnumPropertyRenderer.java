package com.nodecraft.gui.components.property.renderers;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.components.PropertyPanelComponent;
import com.nodecraft.gui.components.property.core.PropertyDescriptor;
import com.nodecraft.gui.components.property.core.PropertyRenderer;
import com.nodecraft.gui.components.property.support.EnumPropertyLabels;
import com.nodecraft.gui.components.property.support.PropertySemanticHints;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.preview.PreviewBackend;
import com.nodecraft.nodesystem.preview.TrackedWorldCompatGate;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
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
                ImGui.textDisabled("(null)");
                return;
            }

            Enum<?>[] values = resolveEnumValues(currentValue);
            String[] names = EnumPropertyLabels.buildDisplayNames(node, prop, values);
            int currentIndex = indexOf(values, currentValue);
            if (currentIndex < 0) {
                currentIndex = 0;
            }

            boolean isReadOnly = prop.setter == null;
            if (isReadOnly) {
                PropertyEditorUi.renderReadOnlyText(names[currentIndex]);
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(EnumPropertyLabels.buildTooltip(node, prop, values, names, currentIndex));
                }
                panel.clearPropertyError(node, prop.name);
                return;
            }

            if (PropertySemanticHints.useSegmentedEnumControl(values.length)) {
                renderSegmented(panel, node, prop, values, names, currentIndex);
            } else {
                renderCombo(panel, node, prop, values, names, currentIndex);
            }

            panel.clearPropertyError(node, prop.name);
        } catch (Throwable e) {
            panel.handlePropertyError(node, prop, e);
        }
    }

    private static void renderSegmented(
            PropertyPanelComponent panel,
            INode node,
            PropertyDescriptor prop,
            Enum<?>[] values,
            String[] names,
            int currentIndex
    ) throws Throwable {
        ImGui.pushID("segmented_" + prop.name);
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                ImGui.sameLine();
            }
            boolean selected = i == currentIndex;
            if (selected) {
                ImGui.pushStyleColor(ImGuiCol.Button, 0.28f, 0.45f, 0.78f, 1.0f);
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.32f, 0.50f, 0.84f, 1.0f);
                ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.24f, 0.40f, 0.72f, 1.0f);
            }
            if (ImGui.button(names[i])) {
                if (!selected) {
                    panel.applyPropertyValue(node, prop, values[i]);
                    NodeCraft.LOGGER.debug("Saved property '{}' on node {}: {}", prop.name, node.getId(), values[i]);
                }
            }
            if (selected) {
                ImGui.popStyleColor(3);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(EnumPropertyLabels.buildTooltip(node, prop, values, names, i));
            }
        }
        ImGui.popID();
    }

    private static void renderCombo(
            PropertyPanelComponent panel,
            INode node,
            PropertyDescriptor prop,
            Enum<?>[] values,
            String[] names,
            int currentIndex
    ) throws Throwable {
        ImInt selectedIndex = new ImInt(currentIndex);
        if (ImGui.combo("##" + prop.name, selectedIndex, names)
                && selectedIndex.get() != currentIndex) {
            panel.applyPropertyValue(node, prop, values[selectedIndex.get()]);
            NodeCraft.LOGGER.debug("Saved property '{}' on node {}: {}", prop.name, node.getId(), values[selectedIndex.get()]);
        }
        if (ImGui.isItemActive()) {
            panel.refreshPropertyBeingEdited(node, prop.name);
        }
        if (ImGui.isItemDeactivated()) {
            panel.markPropertyEditingFinished(node, prop.name);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(EnumPropertyLabels.buildTooltip(node, prop, values, names, selectedIndex.get()));
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

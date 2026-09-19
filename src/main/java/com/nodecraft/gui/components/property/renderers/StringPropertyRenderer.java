package com.nodecraft.gui.components.property.renderers;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.components.PropertyPanelComponent;
import com.nodecraft.gui.components.property.core.PropertyDescriptor;
import com.nodecraft.gui.components.property.core.PropertyRenderer;
import com.nodecraft.gui.components.property.support.GeometryViewerPropertySupport;
import com.nodecraft.gui.components.property.support.StringColorPropertyEditor;
import com.nodecraft.nodesystem.api.INode;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.type.ImString;

public final class StringPropertyRenderer {
    public static final PropertyRenderer RENDERER = StringPropertyRenderer::render;

    private StringPropertyRenderer() {
    }

    private static void render(PropertyPanelComponent panel, INode node, PropertyDescriptor prop, boolean isDisabled) {
        if (isDisabled) {
            PropertyEditorUi.renderDisabled(prop.displayName);
            return;
        }

        try {
            String currentValue = (String) prop.getter.invoke(node);
            if (currentValue == null) {
                currentValue = "";
            }
            final String resolvedValue = currentValue;
            boolean isReadOnly = prop.setter == null;

            if (StringColorPropertyEditor.shouldUseColorPicker(prop, resolvedValue)) {
                StringColorPropertyEditor.render(panel, node, prop, resolvedValue, isReadOnly);
                panel.clearPropertyError(prop.name);
                return;
            }

            String tempKey = panel.getTempValueKey(node, prop.name);
            ImString imStr = panel.getOrReplaceTempValue(
                    tempKey,
                    ImString.class,
                    () -> new ImString(resolvedValue, 256));

            if (!panel.isPropertyBeingEdited(node, prop.name) && !imStr.get().equals(resolvedValue)) {
                imStr.set(resolvedValue);
            }

            int flags = ImGuiInputTextFlags.None;
            if (isReadOnly) {
                flags |= ImGuiInputTextFlags.ReadOnly;
            }

            boolean changed = ImGui.inputText("##" + prop.name, imStr, flags | ImGuiInputTextFlags.EnterReturnsTrue);
            boolean isActive = ImGui.isItemActive();
            boolean wasDeactivated = ImGui.isItemDeactivated();
            boolean wasBeingEdited = panel.isPropertyBeingEdited(node, prop.name);

            if (isActive && !wasBeingEdited) {
                panel.markPropertyBeingEdited(node, prop.name);
            }

            boolean shouldSave = false;
            if (changed) {
                shouldSave = true;
            } else if (wasDeactivated && wasBeingEdited) {
                shouldSave = !imStr.get().equals(resolvedValue);
            }

            if (shouldSave && !isReadOnly && !imStr.get().equals(resolvedValue)) {
                panel.applyPropertyValue(node, prop, imStr.get());
                NodeCraft.LOGGER.debug("自动保存属性 '{}' 到节点 {}: {}", prop.name, node.getId(), imStr.get());
            }

            if (wasDeactivated && wasBeingEdited) {
                panel.markPropertyEditingFinished(node, prop.name);
            }

            if (GeometryViewerPropertySupport.isBlockType(node, prop)) {
                GeometryViewerPropertySupport.renderBlockTypeHint(imStr.get());
            }

            panel.clearPropertyError(prop.name);
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    }
}

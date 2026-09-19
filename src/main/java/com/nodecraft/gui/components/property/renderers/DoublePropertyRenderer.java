package com.nodecraft.gui.components.property.renderers;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.components.PropertyPanelComponent;
import com.nodecraft.gui.components.property.core.PropertyDescriptor;
import com.nodecraft.gui.components.property.core.PropertyRenderer;
import com.nodecraft.nodesystem.api.INode;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.type.ImString;

public final class DoublePropertyRenderer {
    public static final PropertyRenderer RENDERER = DoublePropertyRenderer::render;

    private DoublePropertyRenderer() {
    }

    private static void render(PropertyPanelComponent panel, INode node, PropertyDescriptor prop, boolean isDisabled) {
        if (isDisabled) {
            PropertyEditorUi.renderDisabled(prop.displayName);
            return;
        }

        try {
            double currentValue = (double) prop.getter.invoke(node);

            String tempKey = panel.getTempValueKey(node, prop.name);
            ImString textValue = panel.getOrReplaceTempValue(
                    tempKey,
                    ImString.class,
                    () -> new ImString(String.format("%.12f", currentValue), 64));

            if (!panel.isPropertyBeingEdited(node, prop.name)) {
                try {
                    double currentTextValue = Double.parseDouble(textValue.get());
                    if (Math.abs(currentTextValue - currentValue) > 1e-12) {
                        textValue.set(String.format("%.12f", currentValue));
                    }
                } catch (NumberFormatException e) {
                    textValue.set(String.format("%.12f", currentValue));
                }
            }

            int flags = ImGuiInputTextFlags.CharsDecimal;
            boolean isReadOnly = prop.setter == null;
            if (isReadOnly) {
                flags |= ImGuiInputTextFlags.ReadOnly;
            }

            boolean changed = ImGui.inputText("##" + prop.name, textValue, flags | ImGuiInputTextFlags.EnterReturnsTrue);
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
                try {
                    double newValue = Double.parseDouble(textValue.get());
                    shouldSave = Math.abs(newValue - currentValue) > 1e-12;
                } catch (NumberFormatException ignored) {
                    shouldSave = false;
                }
            }

            if (shouldSave && !isReadOnly) {
                try {
                    double newValue = Double.parseDouble(textValue.get());
                    if (Math.abs(newValue - currentValue) > 1e-12) {
                        panel.applyPropertyValue(node, prop, newValue);
                        NodeCraft.LOGGER.debug("自动保存属性 '{}' 到节点 {}: {}", prop.name, node.getId(), newValue);
                    }
                } catch (NumberFormatException e) {
                    ImGui.sameLine();
                    ImGui.textColored(1.0f, 0.3f, 0.3f, 1.0f, "无效数字");
                }
            }

            if (wasDeactivated && wasBeingEdited) {
                panel.markPropertyEditingFinished(node, prop.name);
            }

            if (!isReadOnly) {
                ImGui.sameLine();
                ImGui.pushItemWidth(ImGui.getContentRegionAvailX() * 0.25f);
                float[] dragValue = new float[]{0.0f};
                if (ImGui.dragFloat("##drag_" + prop.name, dragValue, 0.01f, 0.0f, 0.0f, "%.3f")) {
                    try {
                        double baseValue = Double.parseDouble(textValue.get());
                        double newValue = baseValue + dragValue[0];
                        textValue.set(String.format("%.12f", newValue));
                        panel.applyPropertyValue(node, prop, newValue);
                        NodeCraft.LOGGER.debug("自动保存属性 '{}' 到节点 {}: {}", prop.name, node.getId(), newValue);
                        dragValue[0] = 0.0f;
                    } catch (NumberFormatException ignored) {
                        // ignore drag when text is invalid
                    }
                }
                if (ImGui.isItemActive()) {
                    panel.markPropertyBeingEdited(node, prop.name);
                }
                if (ImGui.isItemDeactivated()) {
                    panel.markPropertyEditingFinished(node, prop.name);
                }
                ImGui.popItemWidth();
            }

            panel.clearPropertyError(prop.name);
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    }
}

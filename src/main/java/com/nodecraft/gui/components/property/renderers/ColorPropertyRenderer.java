package com.nodecraft.gui.components.property.renderers;

import com.nodecraft.gui.components.PropertyPanelComponent;
import com.nodecraft.gui.components.property.core.PropertyDescriptor;
import com.nodecraft.gui.components.property.core.PropertyRenderer;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.datatypes.ColorData;
import com.nodecraft.nodesystem.util.Color;
import imgui.ImGui;

public final class ColorPropertyRenderer {
    public static final PropertyRenderer COLOR_DATA_RENDERER = ColorPropertyRenderer::renderColorData;
    public static final PropertyRenderer NODE_COLOR_RENDERER = ColorPropertyRenderer::renderNodeColor;

    private ColorPropertyRenderer() {
    }

    private static void renderColorData(PropertyPanelComponent panel, INode node, PropertyDescriptor prop, boolean isDisabled) {
        if (isDisabled) {
            ImGui.textDisabled("(disabled)");
            return;
        }

        try {
            ColorData color = (ColorData) prop.getter.invoke(node);
            if (color == null) {
                ImGui.textDisabled("(null)");
                return;
            }

            boolean isReadOnly = prop.setter == null;
            String tempKey = panel.getTempValueKey(node, prop.name + "_color");
            float[] values = panel.getOrCreateTempValue(tempKey, () -> new float[]{color.r(), color.g(), color.b(), color.a()});

            if (!panel.isPropertyBeingEdited(node, prop.name)) {
                values[0] = color.r();
                values[1] = color.g();
                values[2] = color.b();
                values[3] = color.a();
            }

            if (isReadOnly) {
                ImGui.beginDisabled();
            }

            boolean changed = ImGui.colorEdit4("##" + prop.name, values);
            if (ImGui.isItemActive()) {
                panel.markPropertyBeingEdited(node, prop.name);
            }
            if (ImGui.isItemDeactivated()) {
                panel.markPropertyEditingFinished(node, prop.name);
            }

            if (isReadOnly) {
                ImGui.endDisabled();
            }

            if (!isReadOnly && changed) {
                panel.applyPropertyValue(node, prop, new ColorData(values[0], values[1], values[2], values[3]));
            }

            panel.clearPropertyError(prop.name);
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    }

    private static void renderNodeColor(PropertyPanelComponent panel, INode node, PropertyDescriptor prop, boolean isDisabled) {
        if (isDisabled) {
            ImGui.textDisabled("(disabled)");
            return;
        }

        try {
            Color color = (Color) prop.getter.invoke(node);
            if (color == null) {
                ImGui.textDisabled("(null)");
                return;
            }

            boolean isReadOnly = prop.setter == null;
            String tempKey = panel.getTempValueKey(node, prop.name + "_node_color");
            float[] values = panel.getOrCreateTempValue(
                    tempKey,
                    () -> new float[]{color.red(), color.green(), color.blue(), color.alpha()}
            );

            if (!panel.isPropertyBeingEdited(node, prop.name)) {
                values[0] = color.red();
                values[1] = color.green();
                values[2] = color.blue();
                values[3] = color.alpha();
            }

            if (isReadOnly) {
                ImGui.beginDisabled();
            }

            boolean changed = ImGui.colorEdit4("##" + prop.name, values);
            if (ImGui.isItemActive()) {
                panel.markPropertyBeingEdited(node, prop.name);
            }
            if (ImGui.isItemDeactivated()) {
                panel.markPropertyEditingFinished(node, prop.name);
            }

            if (isReadOnly) {
                ImGui.endDisabled();
            }

            if (!isReadOnly && changed) {
                panel.applyPropertyValue(node, prop, new Color(values[0], values[1], values[2], values[3]));
            }

            panel.clearPropertyError(prop.name);
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    }
}

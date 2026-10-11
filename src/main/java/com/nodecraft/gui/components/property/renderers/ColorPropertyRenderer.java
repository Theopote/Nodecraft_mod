package com.nodecraft.gui.components.property.renderers;

import com.nodecraft.gui.components.PropertyPanelComponent;
import com.nodecraft.gui.components.property.core.PropertyDescriptor;
import com.nodecraft.gui.components.property.core.PropertyRenderer;
import com.nodecraft.gui.components.property.core.PropertyValueFormatters;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.datatypes.ColorData;
import com.nodecraft.nodesystem.util.Color;
import imgui.ImGui;
import imgui.flag.ImGuiColorEditFlags;

public final class ColorPropertyRenderer {
    public static final PropertyRenderer COLOR_DATA_RENDERER = ColorPropertyRenderer::renderColorData;
    public static final PropertyRenderer NODE_COLOR_RENDERER = ColorPropertyRenderer::renderNodeColor;

    private ColorPropertyRenderer() {
    }

    private static void renderColorData(PropertyPanelComponent panel, INode node, PropertyDescriptor prop, boolean isDisabled) {
        if (isDisabled) {
            PropertyEditorUi.renderDisabled(prop.displayName);
            return;
        }

        try {
            ColorData color = (ColorData) prop.getter.invoke(node);
            if (color == null) {
                ImGui.textDisabled("(null)");
                return;
            }

            boolean isReadOnly = prop.setter == null;
            if (isReadOnly) {
                renderReadOnlySwatch(
                        "##ro_" + prop.name,
                        color.r(), color.g(), color.b(), color.a(),
                        PropertyValueFormatters.formatColorDataSummary(color)
                );
                panel.clearPropertyError(node, prop.name);
                return;
            }

            String tempKey = panel.getTempValueKey(node, prop.name + "_color");
            float[] values = panel.getOrCreateTempValue(tempKey, () -> new float[]{color.r(), color.g(), color.b(), color.a()});

            if (!panel.isPropertyBeingEdited(node, prop.name)) {
                values[0] = color.r();
                values[1] = color.g();
                values[2] = color.b();
                values[3] = color.a();
            }

            boolean changed = ImGui.colorEdit4("##" + prop.name, values);
            if (ImGui.isItemActive()) {
                panel.refreshPropertyBeingEdited(node, prop.name);
            }
            if (ImGui.isItemDeactivated()) {
                panel.markPropertyEditingFinished(node, prop.name);
            }

            if (changed) {
                panel.applyPropertyValue(node, prop, new ColorData(values[0], values[1], values[2], values[3]));
            }

            panel.clearPropertyError(node, prop.name);
        } catch (Throwable e) {
            panel.handlePropertyError(node, prop, e);
        }
    }

    private static void renderNodeColor(PropertyPanelComponent panel, INode node, PropertyDescriptor prop, boolean isDisabled) {
        if (isDisabled) {
            PropertyEditorUi.renderDisabled(prop.displayName);
            return;
        }

        try {
            Color color = (Color) prop.getter.invoke(node);
            if (color == null) {
                ImGui.textDisabled("(null)");
                return;
            }

            boolean isReadOnly = prop.setter == null;
            if (isReadOnly) {
                renderReadOnlySwatch(
                        "##ro_node_" + prop.name,
                        color.red(), color.green(), color.blue(), color.alpha(),
                        PropertyValueFormatters.formatNodeColorSummary(color)
                );
                panel.clearPropertyError(node, prop.name);
                return;
            }

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

            boolean changed = ImGui.colorEdit4("##" + prop.name, values);
            if (ImGui.isItemActive()) {
                panel.refreshPropertyBeingEdited(node, prop.name);
            }
            if (ImGui.isItemDeactivated()) {
                panel.markPropertyEditingFinished(node, prop.name);
            }

            if (changed) {
                panel.applyPropertyValue(node, prop, new Color(values[0], values[1], values[2], values[3]));
            }

            panel.clearPropertyError(node, prop.name);
        } catch (Throwable e) {
            panel.handlePropertyError(node, prop, e);
        }
    }

    private static void renderReadOnlySwatch(String id, float r, float g, float b, float a, String summary) {
        float[] rgba = {r, g, b, a};
        ImGui.colorButton(id, rgba, ImGuiColorEditFlags.NoTooltip | ImGuiColorEditFlags.AlphaPreviewHalf);
        ImGui.sameLine();
        PropertyEditorUi.renderReadOnlyText(summary);
    }
}

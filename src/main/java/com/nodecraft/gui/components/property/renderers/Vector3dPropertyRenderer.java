package com.nodecraft.gui.components.property.renderers;

import com.nodecraft.gui.components.PropertyPanelComponent;
import com.nodecraft.gui.components.property.core.PropertyDescriptor;
import com.nodecraft.gui.components.property.core.PropertyRenderer;
import com.nodecraft.gui.components.property.core.PropertyValueFormatters;
import com.nodecraft.nodesystem.api.INode;
import imgui.ImGui;
import org.joml.Vector3d;

public final class Vector3dPropertyRenderer {
    public static final PropertyRenderer RENDERER = Vector3dPropertyRenderer::render;

    private Vector3dPropertyRenderer() {
    }

    private static void render(PropertyPanelComponent panel, INode node, PropertyDescriptor prop, boolean isDisabled) {
        if (isDisabled) {
            ImGui.textDisabled("(disabled)");
            return;
        }

        try {
            Vector3d vector = (Vector3d) prop.getter.invoke(node);
            if (vector == null) {
                ImGui.textDisabled("(null)");
                return;
            }

            boolean isReadOnly = prop.setter == null;
            String tempKey = panel.getTempValueKey(node, prop.name + "_vector3d");
            float[] values = panel.getOrCreateTempValue(tempKey, () -> new float[]{(float) vector.x, (float) vector.y, (float) vector.z});

            if (!panel.isPropertyBeingEdited(node, prop.name)) {
                values[0] = (float) vector.x;
                values[1] = (float) vector.y;
                values[2] = (float) vector.z;
            }

            if (isReadOnly) {
                ImGui.beginDisabled();
            }

            boolean changed = false;
            float[] xValue = {values[0]};
            float[] yValue = {values[1]};
            float[] zValue = {values[2]};

            changed |= ImGui.dragFloat("X##" + prop.name, xValue, 0.01f, 0.0f, 0.0f, PropertyValueFormatters.DECIMAL_DISPLAY_FORMAT);
            values[0] = xValue[0];
            if (ImGui.isItemActive()) {
                panel.markPropertyBeingEdited(node, prop.name);
            }

            changed |= ImGui.dragFloat("Y##" + prop.name, yValue, 0.01f, 0.0f, 0.0f, PropertyValueFormatters.DECIMAL_DISPLAY_FORMAT);
            values[1] = yValue[0];
            if (ImGui.isItemActive()) {
                panel.markPropertyBeingEdited(node, prop.name);
            }

            changed |= ImGui.dragFloat("Z##" + prop.name, zValue, 0.01f, 0.0f, 0.0f, PropertyValueFormatters.DECIMAL_DISPLAY_FORMAT);
            values[2] = zValue[0];
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
                panel.applyPropertyValue(node, prop, new Vector3d(values[0], values[1], values[2]));
            }

            panel.clearPropertyError(prop.name);
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    }
}

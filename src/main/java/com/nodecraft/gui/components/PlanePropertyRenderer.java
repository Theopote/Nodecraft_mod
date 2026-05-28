package com.nodecraft.gui.components;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import imgui.ImGui;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;

final class PlanePropertyRenderer {

    static final PropertyRenderer RENDERER = PlanePropertyRenderer::render;

    private PlanePropertyRenderer() {
    }

    private static void render(PropertyPanelComponent panel, INode node, PropertyDescriptor prop, boolean isDisabled) {
        if (isDisabled) {
            ImGui.textDisabled("(disabled)");
            return;
        }

        try {
            PlaneData plane = (PlaneData) prop.getter.invoke(node);
            if (plane == null) {
                ImGui.textDisabled("(null)");
                return;
            }

            boolean isReadOnly = prop.setter == null;
            Vector3d normal = plane.getNormal();
            double d = -normal.dot(plane.getPoint());
            String tempKey = panel.getTempValueKey(node, prop.name + "_plane");
            float[] values = panel.getOrCreateTempValue(
                    tempKey,
                    () -> new float[]{(float) normal.x, (float) normal.y, (float) normal.z, (float) d}
            );

            if (!panel.isPropertyBeingEdited(node, prop.name)) {
                values[0] = (float) normal.x;
                values[1] = (float) normal.y;
                values[2] = (float) normal.z;
                values[3] = (float) d;
            }

            if (isReadOnly) {
                ImGui.beginDisabled();
            }
            boolean changed = false;
            float[] xValue = {values[0]};
            float[] yValue = {values[1]};
            float[] zValue = {values[2]};
            float[] dValue = {values[3]};
            changed |= ImGui.dragFloat("Normal X##" + prop.name, xValue, 0.01f);
            values[0] = xValue[0];
            if (ImGui.isItemActive()) {
                panel.markPropertyBeingEdited(node, prop.name);
            }
            changed |= ImGui.dragFloat("Normal Y##" + prop.name, yValue, 0.01f);
            values[1] = yValue[0];
            if (ImGui.isItemActive()) {
                panel.markPropertyBeingEdited(node, prop.name);
            }
            changed |= ImGui.dragFloat("Normal Z##" + prop.name, zValue, 0.01f);
            values[2] = zValue[0];
            if (ImGui.isItemActive()) {
                panel.markPropertyBeingEdited(node, prop.name);
            }
            changed |= ImGui.dragFloat("Offset D##" + prop.name, dValue, 0.01f);
            values[3] = dValue[0];
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
                Vector3d newNormal = new Vector3d(values[0], values[1], values[2]);
                if (newNormal.lengthSquared() > 1.0e-8) {
                    newNormal.normalize();
                    double newD = values[3];
                    Vec3d origin = new Vec3d(-newD * newNormal.x, -newD * newNormal.y, -newD * newNormal.z);
                    Vec3d mcNormal = new Vec3d(newNormal.x, newNormal.y, newNormal.z);
                    panel.applyPropertyValue(node, prop, new PlaneData(origin, mcNormal));
                } else {
                    ImGui.textColored(1.0f, 0.3f, 0.3f, 1.0f, "Normal cannot be zero");
                }
            }

            panel.clearPropertyError(prop.name);
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    }
}

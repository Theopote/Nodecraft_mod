package com.nodecraft.gui.components.property.renderers;

import com.nodecraft.gui.components.PropertyPanelComponent;
import com.nodecraft.gui.components.property.core.PlaneEquationEdit;
import com.nodecraft.gui.components.property.core.PropertyDescriptor;
import com.nodecraft.gui.components.property.core.PropertyRenderer;
import com.nodecraft.gui.components.property.core.PropertyValueFormatters;
import com.nodecraft.gui.components.property.core.VectorEditSessionLogic;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import imgui.ImGui;
import org.joml.Vector3d;

public final class PlanePropertyRenderer {
    public static final PropertyRenderer RENDERER = PlanePropertyRenderer::render;

    private PlanePropertyRenderer() {
    }

    private static void render(PropertyPanelComponent panel, INode node, PropertyDescriptor prop, boolean isDisabled) {
        if (isDisabled) {
            PropertyEditorUi.renderDisabled(prop.displayName);
            return;
        }

        try {
            PlaneData plane = (PlaneData) prop.getter.invoke(node);
            if (plane == null) {
                ImGui.textDisabled("(null)");
                return;
            }

            boolean isReadOnly = prop.setter == null;
            if (isReadOnly) {
                PropertyEditorUi.renderReadOnlyText(PlaneEquationEdit.formatReadOnly(plane));
                panel.clearPropertyError(node, prop.name);
                return;
            }

            Vector3d normal = plane.getNormal();
            float offsetD = (float) PlaneEquationEdit.displayOffset(plane);
            String tempKey = panel.getTempValueKey(node, prop.name + "_plane");
            float[] nx = panel.getOrCreateTempValue(tempKey + "_nx", () -> new float[]{(float) normal.x});
            float[] ny = panel.getOrCreateTempValue(tempKey + "_ny", () -> new float[]{(float) normal.y});
            float[] nz = panel.getOrCreateTempValue(tempKey + "_nz", () -> new float[]{(float) normal.z});
            float[] d = panel.getOrCreateTempValue(tempKey + "_d", () -> new float[]{offsetD});
            boolean[] axisEditing = panel.getOrCreateTempValue(tempKey + "_axis_editing", () -> new boolean[4]);

            if (!axisEditing[0]) {
                nx[0] = (float) normal.x;
            }
            if (!axisEditing[1]) {
                ny[0] = (float) normal.y;
            }
            if (!axisEditing[2]) {
                nz[0] = (float) normal.z;
            }
            if (!axisEditing[3]) {
                d[0] = offsetD;
            }

            boolean wasBeingEdited = panel.isPropertyBeingEdited(node, prop.name);
            ImGui.pushID("plane_" + prop.name);
            AxisResult xResult = renderDragFloat("NX", nx, 0.01f);
            AxisResult yResult = renderDragFloat("NY", ny, 0.01f);
            AxisResult zResult = renderDragFloat("NZ", nz, 0.01f);
            AxisResult dResult = renderDragFloat("D", d, 0.01f);
            ImGui.popID();

            updateAxisEditing(axisEditing, xResult, 0);
            updateAxisEditing(axisEditing, yResult, 1);
            updateAxisEditing(axisEditing, zResult, 2);
            updateAxisEditing(axisEditing, dResult, 3);

            boolean anyActive = xResult.active || yResult.active || zResult.active || dResult.active;
            boolean anyDeactivated = xResult.deactivated || yResult.deactivated || zResult.deactivated || dResult.deactivated;
            boolean changed = xResult.changed || yResult.changed || zResult.changed || dResult.changed;

            if (anyActive) {
                panel.refreshPropertyBeingEdited(node, prop.name);
            }

            if (changed) {
                PlaneData committed = PlaneEquationEdit.commit(nx[0], ny[0], nz[0], d[0]);
                if (committed == null) {
                    ImGui.textColored(1.0f, 0.3f, 0.3f, 1.0f, "Normal cannot be zero");
                } else {
                    panel.applyPropertyValue(node, prop, committed);
                }
            }

            if (wasBeingEdited && VectorEditSessionLogic.shouldFinishSession(anyActive, anyDeactivated)) {
                clearAxisEditing(axisEditing);
                panel.markPropertyEditingFinished(node, prop.name);
            }

            panel.clearPropertyError(node, prop.name);
        } catch (Throwable e) {
            panel.handlePropertyError(node, prop, e);
        }
    }

    private record AxisResult(boolean changed, boolean active, boolean deactivated) {
    }

    private static AxisResult renderDragFloat(String label, float[] holder, float speed) {
        boolean changed = ImGui.dragFloat(
                label,
                holder,
                speed,
                0.0f,
                0.0f,
                PropertyValueFormatters.DECIMAL_DISPLAY_FORMAT
        );
        return new AxisResult(changed, ImGui.isItemActive(), ImGui.isItemDeactivated());
    }

    private static void updateAxisEditing(boolean[] axisEditing, AxisResult result, int index) {
        if (result.active) {
            axisEditing[index] = true;
        } else if (result.deactivated) {
            axisEditing[index] = false;
        }
    }

    private static void clearAxisEditing(boolean[] axisEditing) {
        for (int i = 0; i < axisEditing.length; i++) {
            axisEditing[i] = false;
        }
    }
}

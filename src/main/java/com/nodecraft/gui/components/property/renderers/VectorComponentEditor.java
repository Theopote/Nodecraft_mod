package com.nodecraft.gui.components.property.renderers;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.components.PropertyPanelComponent;
import com.nodecraft.gui.components.property.core.PropertyDescriptor;
import com.nodecraft.gui.components.property.core.PropertyValueFormatters;
import com.nodecraft.nodesystem.api.INode;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.type.ImString;

final class VectorComponentEditor {

    private VectorComponentEditor() {
    }

    static void renderVec3Row(
            PropertyPanelComponent panel,
            INode node,
            PropertyDescriptor prop,
            double x,
            double y,
            double z,
            boolean isReadOnly,
            Vec3Writer writer
    ) throws Throwable {
        if (isReadOnly) {
            PropertyEditorUi.renderReadOnlyText(String.format("X %s  Y %s  Z %s",
                    PropertyValueFormatters.formatEditNumber(x),
                    PropertyValueFormatters.formatEditNumber(y),
                    PropertyValueFormatters.formatEditNumber(z)));
            return;
        }

        String tempKey = panel.getTempValueKey(node, prop.name + "_vec3");
        ImString xStr = panel.getOrCreateTempValue(tempKey + "_x", () -> new ImString(PropertyValueFormatters.formatEditNumber(x), 64));
        ImString yStr = panel.getOrCreateTempValue(tempKey + "_y", () -> new ImString(PropertyValueFormatters.formatEditNumber(y), 64));
        ImString zStr = panel.getOrCreateTempValue(tempKey + "_z", () -> new ImString(PropertyValueFormatters.formatEditNumber(z), 64));

        syncAxisText(panel, node, prop.name + "_x", xStr, x);
        syncAxisText(panel, node, prop.name + "_y", yStr, y);
        syncAxisText(panel, node, prop.name + "_z", zStr, z);

        int flags = ImGuiInputTextFlags.CharsDecimal | ImGuiInputTextFlags.EnterReturnsTrue;
        float width = ImGui.getContentRegionAvailX() / 3.0f - ImGui.getStyle().getItemSpacingX();
        boolean changed = false;

        ImGui.pushID("vec3_" + prop.name);
        changed |= renderAxis(panel, node, prop.name + "_x", "X", xStr, width, flags);
        ImGui.sameLine();
        changed |= renderAxis(panel, node, prop.name + "_y", "Y", yStr, width, flags);
        ImGui.sameLine();
        changed |= renderAxis(panel, node, prop.name + "_z", "Z", zStr, width, flags);
        ImGui.popID();

        if (changed) {
            try {
                double newX = Double.parseDouble(xStr.get());
                double newY = Double.parseDouble(yStr.get());
                double newZ = Double.parseDouble(zStr.get());
                if (PropertyValueFormatters.doubleValueChanged(newX, x)
                        || PropertyValueFormatters.doubleValueChanged(newY, y)
                        || PropertyValueFormatters.doubleValueChanged(newZ, z)) {
                    writer.write(newX, newY, newZ);
                    NodeCraft.LOGGER.debug("Saved vector property '{}' on node {}", prop.name, node.getId());
                }
            } catch (NumberFormatException e) {
                ImGui.textColored(1.0f, 0.3f, 0.3f, 1.0f, "Invalid coordinate");
            }
        }
    }

    @FunctionalInterface
    interface Vec3Writer {
        void write(double x, double y, double z) throws Throwable;
    }

    private static void syncAxisText(
            PropertyPanelComponent panel,
            INode node,
            String axisKey,
            ImString text,
            double liveValue
    ) {
        if (panel.isPropertyBeingEdited(node, axisKey)) {
            return;
        }
        try {
            double parsed = Double.parseDouble(text.get());
            if (PropertyValueFormatters.doubleValueChanged(parsed, liveValue)) {
                text.set(PropertyValueFormatters.formatEditNumber(liveValue));
            }
        } catch (NumberFormatException e) {
            text.set(PropertyValueFormatters.formatEditNumber(liveValue));
        }
    }

    private static boolean renderAxis(
            PropertyPanelComponent panel,
            INode node,
            String axisKey,
            String label,
            ImString text,
            float width,
            int flags
    ) {
        ImGui.pushItemWidth(width);
        boolean changed = ImGui.inputText(label, text, flags);
        if (ImGui.isItemActive()) {
            panel.refreshPropertyBeingEdited(node, axisKey);
        }
        if (ImGui.isItemDeactivated()) {
            panel.markPropertyEditingFinished(node, axisKey);
        }
        ImGui.popItemWidth();
        return changed;
    }
}

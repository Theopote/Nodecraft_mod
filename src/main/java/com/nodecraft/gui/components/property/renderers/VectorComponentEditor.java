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

        syncAxisText(panel, node, prop.name, xStr, x);
        syncAxisText(panel, node, prop.name, yStr, y);
        syncAxisText(panel, node, prop.name, zStr, z);

        int flags = ImGuiInputTextFlags.CharsDecimal | ImGuiInputTextFlags.EnterReturnsTrue;
        float width = ImGui.getContentRegionAvailX() / 3.0f - ImGui.getStyle().getItemSpacingX();
        boolean wasBeingEdited = panel.isPropertyBeingEdited(node, prop.name);

        ImGui.pushID("vec3_" + prop.name);
        AxisResult xResult = renderAxis("X", xStr, width, flags);
        ImGui.sameLine();
        AxisResult yResult = renderAxis("Y", yStr, width, flags);
        ImGui.sameLine();
        AxisResult zResult = renderAxis("Z", zStr, width, flags);
        ImGui.popID();

        if (xResult.active || yResult.active || zResult.active) {
            panel.refreshPropertyBeingEdited(node, prop.name);
        }

        boolean shouldSave = xResult.changed || yResult.changed || zResult.changed;
        if (!shouldSave && wasBeingEdited && (xResult.deactivated || yResult.deactivated || zResult.deactivated)) {
            shouldSave = true;
        }

        if (shouldSave) {
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

        if (wasBeingEdited && (xResult.deactivated || yResult.deactivated || zResult.deactivated)) {
            panel.markPropertyEditingFinished(node, prop.name);
        }
    }

    @FunctionalInterface
    interface Vec3Writer {
        void write(double x, double y, double z) throws Throwable;
    }

    private record AxisResult(boolean changed, boolean active, boolean deactivated) {
    }

    private static void syncAxisText(
            PropertyPanelComponent panel,
            INode node,
            String sessionKey,
            ImString text,
            double liveValue
    ) {
        if (panel.isPropertyBeingEdited(node, sessionKey)) {
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

    private static AxisResult renderAxis(
            String label,
            ImString text,
            float width,
            int flags
    ) {
        ImGui.pushItemWidth(width);
        boolean changed = ImGui.inputText(label, text, flags);
        boolean active = ImGui.isItemActive();
        boolean deactivated = ImGui.isItemDeactivated();
        ImGui.popItemWidth();
        return new AxisResult(changed, active, deactivated);
    }
}

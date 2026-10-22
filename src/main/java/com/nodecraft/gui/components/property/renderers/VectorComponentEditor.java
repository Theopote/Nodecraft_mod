package com.nodecraft.gui.components.property.renderers;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.components.PropertyPanelComponent;
import com.nodecraft.gui.components.property.core.PropertyDescriptor;
import com.nodecraft.gui.components.property.core.PropertyValueFormatters;
import com.nodecraft.gui.components.property.core.VectorEditSessionLogic;
import com.nodecraft.nodesystem.api.INode;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.type.ImString;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

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
        renderVec3Row(panel, node, prop, x, y, z, isReadOnly, writer, null);
    }

    static void renderVec3Row(
            PropertyPanelComponent panel,
            INode node,
            PropertyDescriptor prop,
            double x,
            double y,
            double z,
            boolean isReadOnly,
            Vec3Writer writer,
            @Nullable Double lengthForTooltip
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
        boolean[] axisEditing = panel.getOrCreateTempValue(tempKey + "_axis_editing", () -> new boolean[3]);

        syncAxisText(axisEditing[0], xStr, x);
        syncAxisText(axisEditing[1], yStr, y);
        syncAxisText(axisEditing[2], zStr, z);

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

        updateAxisEditingFlags(axisEditing, xResult, yResult, zResult);

        boolean anyActive = xResult.active || yResult.active || zResult.active;
        boolean anyDeactivated = xResult.deactivated || yResult.deactivated || zResult.deactivated;
        boolean anyHovered = xResult.hovered || yResult.hovered || zResult.hovered;

        if (anyActive) {
            panel.refreshPropertyBeingEdited(node, prop.name);
        }

        boolean shouldSave = xResult.changed || yResult.changed || zResult.changed;
        if (!shouldSave && wasBeingEdited && anyDeactivated) {
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

        if (wasBeingEdited && VectorEditSessionLogic.shouldFinishSession(anyActive, anyDeactivated)) {
            clearAxisEditing(axisEditing);
            panel.markPropertyEditingFinished(node, prop.name);
        }

        if (lengthForTooltip != null && anyHovered) {
            ImGui.setTooltip(String.format(Locale.ROOT, "Length: %.2f", lengthForTooltip));
        }
    }

    static void renderBlockPosRow(
            PropertyPanelComponent panel,
            INode node,
            PropertyDescriptor prop,
            int x,
            int y,
            int z,
            boolean isReadOnly,
            BlockPosWriter writer
    ) throws Throwable {
        if (isReadOnly) {
            PropertyEditorUi.renderReadOnlyText(String.format(Locale.ROOT, "X %d  Y %d  Z %d", x, y, z));
            return;
        }

        String tempKey = panel.getTempValueKey(node, prop.name + "_blockpos");
        int[] xHolder = panel.getOrCreateTempValue(tempKey + "_x", () -> new int[]{x});
        int[] yHolder = panel.getOrCreateTempValue(tempKey + "_y", () -> new int[]{y});
        int[] zHolder = panel.getOrCreateTempValue(tempKey + "_z", () -> new int[]{z});
        boolean[] axisEditing = panel.getOrCreateTempValue(tempKey + "_axis_editing", () -> new boolean[3]);

        if (!axisEditing[0]) {
            xHolder[0] = x;
        }
        if (!axisEditing[1]) {
            yHolder[0] = y;
        }
        if (!axisEditing[2]) {
            zHolder[0] = z;
        }

        float width = ImGui.getContentRegionAvailX() / 3.0f - ImGui.getStyle().getItemSpacingX();
        boolean wasBeingEdited = panel.isPropertyBeingEdited(node, prop.name);

        ImGui.pushID("blockpos_" + prop.name);
        AxisResult xResult = renderDragIntAxis("X", xHolder, width);
        ImGui.sameLine();
        AxisResult yResult = renderDragIntAxis("Y", yHolder, width);
        ImGui.sameLine();
        AxisResult zResult = renderDragIntAxis("Z", zHolder, width);
        ImGui.popID();

        updateAxisEditingFlags(axisEditing, xResult, yResult, zResult);

        boolean anyActive = xResult.active || yResult.active || zResult.active;
        boolean anyDeactivated = xResult.deactivated || yResult.deactivated || zResult.deactivated;
        boolean changed = xResult.changed || yResult.changed || zResult.changed;

        if (anyActive) {
            panel.refreshPropertyBeingEdited(node, prop.name);
        }

        if (changed && (xHolder[0] != x || yHolder[0] != y || zHolder[0] != z)) {
            writer.write(xHolder[0], yHolder[0], zHolder[0]);
            NodeCraft.LOGGER.debug("Saved BlockPos property '{}' on node {}", prop.name, node.getId());
        }

        if (wasBeingEdited && VectorEditSessionLogic.shouldFinishSession(anyActive, anyDeactivated)) {
            clearAxisEditing(axisEditing);
            panel.markPropertyEditingFinished(node, prop.name);
        }
    }

    @FunctionalInterface
    interface Vec3Writer {
        void write(double x, double y, double z) throws Throwable;
    }

    @FunctionalInterface
    interface BlockPosWriter {
        void write(int x, int y, int z) throws Throwable;
    }

    private record AxisResult(boolean changed, boolean active, boolean deactivated, boolean hovered) {
    }

    private static void syncAxisText(boolean axisEditing, ImString text, double liveValue) {
        if (axisEditing) {
            return;
        }
        String formatted = PropertyValueFormatters.formatEditNumber(liveValue);
        if (!formatted.equals(text.get())) {
            text.set(formatted);
        }
    }

    private static void updateAxisEditingFlags(boolean[] axisEditing, AxisResult x, AxisResult y, AxisResult z) {
        if (x.active) {
            axisEditing[0] = true;
        } else if (x.deactivated) {
            axisEditing[0] = false;
        }
        if (y.active) {
            axisEditing[1] = true;
        } else if (y.deactivated) {
            axisEditing[1] = false;
        }
        if (z.active) {
            axisEditing[2] = true;
        } else if (z.deactivated) {
            axisEditing[2] = false;
        }
    }

    private static void clearAxisEditing(boolean[] axisEditing) {
        axisEditing[0] = false;
        axisEditing[1] = false;
        axisEditing[2] = false;
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
        boolean hovered = ImGui.isItemHovered();
        ImGui.popItemWidth();
        return new AxisResult(changed, active, deactivated, hovered);
    }

    private static AxisResult renderDragIntAxis(String label, int[] holder, float width) {
        ImGui.pushItemWidth(width);
        boolean changed = ImGui.dragInt(label, holder, 1);
        boolean active = ImGui.isItemActive();
        boolean deactivated = ImGui.isItemDeactivated();
        boolean hovered = ImGui.isItemHovered();
        ImGui.popItemWidth();
        return new AxisResult(changed, active, deactivated, hovered);
    }
}

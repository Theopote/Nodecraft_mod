package com.nodecraft.gui.components.property.renderers;

import com.nodecraft.gui.components.PropertyPanelComponent;
import com.nodecraft.gui.components.property.core.PropertyDescriptor;
import com.nodecraft.gui.components.property.core.PropertyRenderer;
import com.nodecraft.gui.components.property.core.PropertyValueFormatters;
import com.nodecraft.core.NodeCraft;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.util.Vec3;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.type.ImString;

public final class Vec3PropertyRenderer {
    public static final PropertyRenderer RENDERER = Vec3PropertyRenderer::render;

    private Vec3PropertyRenderer() {
    }

    private static void render(PropertyPanelComponent panel, INode node, PropertyDescriptor prop, boolean isDisabled) {
        if (isDisabled) {
            ImGui.textDisabled("(disabled)");
            return;
        }

        try {
            Vec3 vec = (Vec3) prop.getter.invoke(node);
            if (vec == null) {
                ImGui.textDisabled("(null)");
                return;
            }

            String tempKey = panel.getTempValueKey(node, prop.name + "_vec3");
            ImString xStr = panel.getOrCreateTempValue(tempKey + "_x", () -> new ImString(PropertyValueFormatters.formatDecimal(vec.getX()), 64));
            ImString yStr = panel.getOrCreateTempValue(tempKey + "_y", () -> new ImString(PropertyValueFormatters.formatDecimal(vec.getY()), 64));
            ImString zStr = panel.getOrCreateTempValue(tempKey + "_z", () -> new ImString(PropertyValueFormatters.formatDecimal(vec.getZ()), 64));

            boolean isReadOnly = prop.setter == null;

            if (!panel.isPropertyBeingEdited(node, prop.name + "_x")) {
                xStr.set(PropertyValueFormatters.formatDecimal(vec.getX()));
            }
            if (!panel.isPropertyBeingEdited(node, prop.name + "_y")) {
                yStr.set(PropertyValueFormatters.formatDecimal(vec.getY()));
            }
            if (!panel.isPropertyBeingEdited(node, prop.name + "_z")) {
                zStr.set(PropertyValueFormatters.formatDecimal(vec.getZ()));
            }

            boolean changed = false;
            if (isReadOnly) {
                ImGui.beginDisabled();
            }

            float width = ImGui.getContentRegionAvailX() / 3 - ImGui.getStyle().getItemSpacingX();

            ImGui.pushID("vec3_" + prop.name);

            ImGui.pushItemWidth(width);
            if (ImGui.inputText("X", xStr, ImGuiInputTextFlags.CharsDecimal | ImGuiInputTextFlags.EnterReturnsTrue)) {
                changed = true;
            }
            if (ImGui.isItemActive()) {
                panel.markPropertyBeingEdited(node, prop.name + "_x");
            }
            if (ImGui.isItemDeactivated()) {
                panel.markPropertyEditingFinished(node, prop.name + "_x");
            }
            ImGui.popItemWidth();

            ImGui.sameLine();
            ImGui.pushItemWidth(width);
            if (ImGui.inputText("Y", yStr, ImGuiInputTextFlags.CharsDecimal | ImGuiInputTextFlags.EnterReturnsTrue)) {
                changed = true;
            }
            if (ImGui.isItemActive()) {
                panel.markPropertyBeingEdited(node, prop.name + "_y");
            }
            if (ImGui.isItemDeactivated()) {
                panel.markPropertyEditingFinished(node, prop.name + "_y");
            }
            ImGui.popItemWidth();

            ImGui.sameLine();
            ImGui.pushItemWidth(width);
            if (ImGui.inputText("Z", zStr, ImGuiInputTextFlags.CharsDecimal | ImGuiInputTextFlags.EnterReturnsTrue)) {
                changed = true;
            }
            if (ImGui.isItemActive()) {
                panel.markPropertyBeingEdited(node, prop.name + "_z");
            }
            if (ImGui.isItemDeactivated()) {
                panel.markPropertyEditingFinished(node, prop.name + "_z");
            }
            ImGui.popItemWidth();

            ImGui.popID();

            if (changed && !isReadOnly) {
                try {
                    Vec3 newVec = new Vec3(
                            Double.parseDouble(xStr.get()),
                            Double.parseDouble(yStr.get()),
                            Double.parseDouble(zStr.get())
                    );
                    if (!newVec.equals(vec)) {
                        panel.applyPropertyValue(node, prop, newVec);
                        NodeCraft.LOGGER.debug("Auto-saved property '{}' for node {}: {}", prop.name, node.getId(), newVec);
                    }
                } catch (NumberFormatException e) {
                    ImGui.textColored(1.0f, 0.3f, 0.3f, 1.0f, "Invalid coordinate");
                }
            }

            if (isReadOnly) {
                ImGui.endDisabled();
            }

            if (ImGui.isItemHovered()) {
                double length = vec.length();
                ImGui.setTooltip(String.format("Length: %.2f", length));
            }

            panel.clearPropertyError(prop.name);
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    }
}

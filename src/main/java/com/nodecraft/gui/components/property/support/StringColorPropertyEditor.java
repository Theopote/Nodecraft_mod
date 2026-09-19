package com.nodecraft.gui.components.property.support;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.components.PropertyPanelComponent;
import com.nodecraft.gui.components.property.core.PropertyDescriptor;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.util.Color;
import imgui.ImGui;
import java.util.Locale;

/**
 * Hex-string color picker chrome for string properties whose name/value look like colors.
 * <p>
 * See {@code docs/architecture/property-panel-breakup.md} (Phase 5).
 */
public final class StringColorPropertyEditor {

    private StringColorPropertyEditor() {
    }

    public static boolean shouldUseColorPicker(PropertyDescriptor prop, String value) {
        if (prop == null) {
            return false;
        }
        String name = prop.name != null ? prop.name.toLowerCase(Locale.ROOT) : "";
        String displayName = prop.displayName != null ? prop.displayName.toLowerCase(Locale.ROOT) : "";
        boolean colorNamed = name.contains("color") || displayName.contains("color");
        if (isHexColorString(value)) {
            return true;
        }
        if (!colorNamed) {
            return false;
        }
        return value == null || value.isBlank();
    }

    public static void render(
            PropertyPanelComponent panel,
            INode node,
            PropertyDescriptor prop,
            String currentValue,
            boolean isReadOnly
    ) throws Throwable {
        String normalized = normalizeHexColor(currentValue);
        String tempKey = panel.getTempValueKey(node, prop.name + "_hex_color");
        float[] rgb = panel.getOrCreateTempValue(tempKey, () -> {
            Color parsed = Color.fromHex(normalized);
            return new float[]{parsed.getRed(), parsed.getGreen(), parsed.getBlue()};
        });

        if (!panel.isPropertyBeingEdited(node, prop.name)) {
            Color parsed = Color.fromHex(normalized);
            rgb[0] = parsed.getRed();
            rgb[1] = parsed.getGreen();
            rgb[2] = parsed.getBlue();
        }

        if (isReadOnly) {
            ImGui.beginDisabled();
        }

        boolean colorChanged = ImGui.colorEdit3("##" + prop.name + "_picker", rgb);
        if (ImGui.isItemActive()) {
            panel.markPropertyBeingEdited(node, prop.name);
        }
        if (ImGui.isItemDeactivated()) {
            panel.markPropertyEditingFinished(node, prop.name);
        }

        if (isReadOnly) {
            ImGui.endDisabled();
        }

        if (!isReadOnly && colorChanged) {
            String newHex = toHexColor(rgb);
            if (!newHex.equalsIgnoreCase(normalized)) {
                panel.applyPropertyValue(node, prop, newHex);
                NodeCraft.LOGGER.debug("自动保存属性 '{}' 到节点 {}: {}", prop.name, node.getId(), newHex);
            }
        }
    }

    public static boolean isHexColorString(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        if (!trimmed.startsWith("#")) {
            return false;
        }
        return trimmed.matches("^#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?$");
    }

    public static String normalizeHexColor(String value) {
        if (isHexColorString(value)) {
            return value.trim();
        }
        return "#000000";
    }

    public static String toHexColor(float[] rgb) {
        int r = Math.max(0, Math.min(255, Math.round(rgb[0] * 255.0f)));
        int g = Math.max(0, Math.min(255, Math.round(rgb[1] * 255.0f)));
        int b = Math.max(0, Math.min(255, Math.round(rgb[2] * 255.0f)));
        return String.format(Locale.ROOT, "#%02X%02X%02X", r, g, b);
    }
}

package com.nodecraft.gui.components.property.renderers;

import com.nodecraft.gui.components.property.core.PropertyValueFormatters;
import imgui.ImGui;

import java.util.Locale;

/**
 * Shared chrome for primitive property editors.
 */
final class PropertyEditorUi {
    private PropertyEditorUi() {
    }

    static boolean renderDisabled(String displayName) {
        ImGui.textDisabled("(disabled)");
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Property '" + displayName + "' is temporarily disabled after repeated errors.");
        }
        return true;
    }

    static void renderReadOnlyText(String text) {
        ImGui.text(text != null ? text : "(null)");
    }

    static void renderReadOnlyBoolean(boolean value) {
        ImGui.text(value ? "Yes" : "No");
    }

    static void renderReadOnlyFloat(float value) {
        ImGui.text(PropertyValueFormatters.formatDecimal(value));
    }

    static void renderReadOnlyInt(int value) {
        ImGui.text(String.format(Locale.ROOT, "%d", value));
    }

    static void renderReadOnlyDouble(double value) {
        ImGui.text(PropertyValueFormatters.formatDecimal(value));
    }
}

package com.nodecraft.gui.components.property.renderers;

import imgui.ImGui;

/**
 * Shared chrome for primitive property editors.
 */
final class PropertyEditorUi {
    private PropertyEditorUi() {
    }

    static boolean renderDisabled(String displayName) {
        ImGui.textDisabled("(已禁用)");
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("属性 '" + displayName + "' 因频繁错误已被禁用");
        }
        return true;
    }
}

package com.nodecraft.gui.components.property.renderers;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.components.PropertyPanelComponent;
import com.nodecraft.gui.components.property.core.PropertyDescriptor;
import com.nodecraft.gui.components.property.core.PropertyRenderer;
import com.nodecraft.nodesystem.api.INode;
import imgui.ImGui;

public final class IntPropertyRenderer {
    public static final PropertyRenderer RENDERER = IntPropertyRenderer::render;

    private IntPropertyRenderer() {
    }

    private static void render(PropertyPanelComponent panel, INode node, PropertyDescriptor prop, boolean isDisabled) {
        if (isDisabled) {
            PropertyEditorUi.renderDisabled(prop.displayName);
            return;
        }

        try {
            int currentValue = (int) prop.getter.invoke(node);
            int[] valArr = {currentValue};
            boolean isReadOnly = prop.setter == null;

            if (isReadOnly) {
                ImGui.beginDisabled();
            }
            if (ImGui.dragInt("##" + prop.name, valArr, 1) && !isReadOnly && valArr[0] != currentValue) {
                panel.applyPropertyValue(node, prop, valArr[0]);
                NodeCraft.LOGGER.debug("自动保存属性 '{}' 到节点 {}: {}", prop.name, node.getId(), valArr[0]);
            }
            if (ImGui.isItemActive()) {
                panel.markPropertyBeingEdited(node, prop.name);
            }
            if (ImGui.isItemDeactivated()) {
                panel.markPropertyEditingFinished(node, prop.name);
            }
            if (isReadOnly) {
                ImGui.endDisabled();
            }

            panel.clearPropertyError(prop.name);
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    }
}

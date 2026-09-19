package com.nodecraft.gui.components.property.renderers;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.components.PropertyPanelComponent;
import com.nodecraft.gui.components.property.core.PropertyDescriptor;
import com.nodecraft.gui.components.property.core.PropertyRenderer;
import com.nodecraft.nodesystem.api.INode;
import imgui.ImGui;
import imgui.type.ImBoolean;

public final class BooleanPropertyRenderer {
    public static final PropertyRenderer RENDERER = BooleanPropertyRenderer::render;

    private BooleanPropertyRenderer() {
    }

    private static void render(PropertyPanelComponent panel, INode node, PropertyDescriptor prop, boolean isDisabled) {
        if (isDisabled) {
            PropertyEditorUi.renderDisabled(prop.displayName);
            return;
        }

        try {
            boolean currentValue = (boolean) prop.getter.invoke(node);
            ImBoolean imVal = new ImBoolean(currentValue);
            boolean isReadOnly = prop.setter == null;

            if (isReadOnly) {
                ImGui.beginDisabled();
            }
            if (ImGui.checkbox("##" + prop.name, imVal)) {
                if (!isReadOnly) {
                    panel.applyPropertyValue(node, prop, imVal.get());
                    NodeCraft.LOGGER.debug("自动保存属性 '{}' 到节点 {}: {}", prop.name, node.getId(), imVal.get());
                }
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

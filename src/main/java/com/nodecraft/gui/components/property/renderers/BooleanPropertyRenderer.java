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
            boolean isReadOnly = prop.setter == null;

            if (isReadOnly) {
                PropertyEditorUi.renderReadOnlyBoolean(currentValue);
            } else {
                ImBoolean imVal = new ImBoolean(currentValue);
                if (ImGui.checkbox("##" + prop.name, imVal)) {
                    panel.applyPropertyValue(node, prop, imVal.get());
                    NodeCraft.LOGGER.debug("Saved property '{}' on node {}: {}", prop.name, node.getId(), imVal.get());
                }
            }

            panel.clearPropertyError(node, prop.name);
        } catch (Throwable e) {
            panel.handlePropertyError(node, prop, e);
        }
    }
}

package com.nodecraft.gui.components.property.renderers;

import com.nodecraft.gui.components.PropertyPanelComponent;
import com.nodecraft.gui.components.property.core.PropertyDescriptor;
import com.nodecraft.gui.components.property.core.PropertyRenderer;
import com.nodecraft.nodesystem.api.INode;

public final class FloatPropertyRenderer {
    public static final PropertyRenderer RENDERER = FloatPropertyRenderer::render;

    private FloatPropertyRenderer() {
    }

    private static void render(PropertyPanelComponent panel, INode node, PropertyDescriptor prop, boolean isDisabled) {
        try {
            ScalarPropertyEditor.render(panel, node, prop, isDisabled, ScalarPropertyEditor.ValueKind.FLOAT);
            panel.clearPropertyError(node, prop.name);
        } catch (Throwable e) {
            panel.handlePropertyError(node, prop, e);
        }
    }
}

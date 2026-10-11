package com.nodecraft.gui.components.property.renderers;

import com.nodecraft.gui.components.PropertyPanelComponent;
import com.nodecraft.gui.components.property.core.PropertyDescriptor;
import com.nodecraft.gui.components.property.core.PropertyRenderer;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.util.Vec3;
import imgui.ImGui;

public final class Vec3PropertyRenderer {
    public static final PropertyRenderer RENDERER = Vec3PropertyRenderer::render;

    private Vec3PropertyRenderer() {
    }

    private static void render(PropertyPanelComponent panel, INode node, PropertyDescriptor prop, boolean isDisabled) {
        if (isDisabled) {
            PropertyEditorUi.renderDisabled(prop.displayName);
            return;
        }

        try {
            Vec3 vec = (Vec3) prop.getter.invoke(node);
            if (vec == null) {
                ImGui.textDisabled("(null)");
                return;
            }

            boolean isReadOnly = prop.setter == null;
            VectorComponentEditor.renderVec3Row(
                    panel,
                    node,
                    prop,
                    vec.getX(),
                    vec.getY(),
                    vec.getZ(),
                    isReadOnly,
                    (x, y, z) -> panel.applyPropertyValue(node, prop, new Vec3(x, y, z)),
                    vec.length()
            );

            panel.clearPropertyError(node, prop.name);
        } catch (Throwable e) {
            panel.handlePropertyError(node, prop, e);
        }
    }
}

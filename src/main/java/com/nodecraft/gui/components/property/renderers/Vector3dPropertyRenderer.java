package com.nodecraft.gui.components.property.renderers;

import com.nodecraft.gui.components.PropertyPanelComponent;
import com.nodecraft.gui.components.property.core.PropertyDescriptor;
import com.nodecraft.gui.components.property.core.PropertyRenderer;
import com.nodecraft.nodesystem.api.INode;
import imgui.ImGui;
import org.joml.Vector3d;

public final class Vector3dPropertyRenderer {
    public static final PropertyRenderer RENDERER = Vector3dPropertyRenderer::render;

    private Vector3dPropertyRenderer() {
    }

    private static void render(PropertyPanelComponent panel, INode node, PropertyDescriptor prop, boolean isDisabled) {
        if (isDisabled) {
            PropertyEditorUi.renderDisabled(prop.displayName);
            return;
        }

        try {
            Vector3d vector = (Vector3d) prop.getter.invoke(node);
            if (vector == null) {
                ImGui.textDisabled("(null)");
                return;
            }

            boolean isReadOnly = prop.setter == null;
            VectorComponentEditor.renderVec3Row(
                    panel,
                    node,
                    prop,
                    vector.x,
                    vector.y,
                    vector.z,
                    isReadOnly,
                    (x, y, z) -> panel.applyPropertyValue(node, prop, new Vector3d(x, y, z)),
                    vector.length()
            );

            panel.clearPropertyError(node, prop.name);
        } catch (Throwable e) {
            panel.handlePropertyError(node, prop, e);
        }
    }
}

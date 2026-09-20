package com.nodecraft.gui.components.property.renderers;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.components.PropertyPanelComponent;
import com.nodecraft.gui.components.property.core.PropertyDescriptor;
import com.nodecraft.gui.components.property.core.PropertyRenderer;
import com.nodecraft.gui.components.property.core.PropertyValueFormatters;
import com.nodecraft.gui.components.property.support.GeometryViewerPropertySupport;
import com.nodecraft.nodesystem.api.INode;
import imgui.ImGui;

import java.util.Locale;

public final class FloatPropertyRenderer {
    public static final PropertyRenderer RENDERER = FloatPropertyRenderer::render;

    private FloatPropertyRenderer() {
    }

    private static void render(PropertyPanelComponent panel, INode node, PropertyDescriptor prop, boolean isDisabled) {
        if (isDisabled) {
            PropertyEditorUi.renderDisabled(prop.displayName);
            return;
        }

        try {
            float currentValue = (float) prop.getter.invoke(node);
            float[] valArr = {currentValue};
            boolean isReadOnly = prop.setter == null;
            boolean isGeometryTransparency = GeometryViewerPropertySupport.isTransparency(node, prop);

            if (isReadOnly) {
                ImGui.beginDisabled();
            }
            boolean changed = isGeometryTransparency
                    ? ImGui.sliderFloat("##" + prop.name, valArr, 0.0f, 1.0f, PropertyValueFormatters.DECIMAL_DISPLAY_FORMAT)
                    : ImGui.dragFloat("##" + prop.name, valArr, 0.01f, 0.0f, 0.0f, PropertyValueFormatters.DECIMAL_DISPLAY_FORMAT);
            if (changed && !isReadOnly && valArr[0] != currentValue) {
                panel.applyPropertyValue(node, prop, valArr[0]);
                NodeCraft.LOGGER.debug("自动保存属性 '{}' 到节点 {}: {}", prop.name, node.getId(), valArr[0]);
            }
            if (ImGui.isItemActive()) {
                panel.markPropertyBeingEdited(node, prop.name);
            }
            if (ImGui.isItemDeactivated()) {
                panel.markPropertyEditingFinished(node, prop.name);
            }

            if (isGeometryTransparency) {
                ImGui.sameLine();
                ImGui.text(String.format(Locale.ROOT, "%d%%", Math.round(valArr[0] * 100.0f)));
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

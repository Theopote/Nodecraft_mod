package com.nodecraft.gui.components.property.renderers;

import com.nodecraft.gui.components.PropertyPanelComponent;
import com.nodecraft.gui.components.property.core.PropertyDescriptor;
import com.nodecraft.gui.components.property.core.PropertyRenderer;
import com.nodecraft.gui.components.property.core.PropertyValueFormatters;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import imgui.ImGui;

public final class PolygonProfilePropertyRenderer {
    public static final PropertyRenderer RENDERER = PolygonProfilePropertyRenderer::render;

    private PolygonProfilePropertyRenderer() {
    }

    private static void render(PropertyPanelComponent panel, INode node, PropertyDescriptor prop, boolean isDisabled) {
        try {
            PolygonProfileData profile = (PolygonProfileData) prop.getter.invoke(node);
            if (profile == null) {
                ImGui.textDisabled("(null)");
                return;
            }

            ImGui.text("Center: " + PropertyValueFormatters.formatVector3d(profile.getCenter()));
            ImGui.text("Edges: " + profile.getEdgeCount());
            ImGui.text("Unique Points: " + profile.getUniquePoints().size());
            ImGui.text("Plane Normal: " + PropertyValueFormatters.formatVector3d(profile.plane().getNormal()));
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    }
}

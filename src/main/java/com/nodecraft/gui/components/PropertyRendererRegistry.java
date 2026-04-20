package com.nodecraft.gui.components;

import com.nodecraft.core.NodeCraft;
import imgui.ImGui;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

final class PropertyRendererRegistry {

    private static final Map<Class<?>, PropertyPanelComponent.PropertyRenderer> REGISTRY = new HashMap<>();

    private static final PropertyPanelComponent.PropertyRenderer FALLBACK_RENDERER = (panel, node, prop, isDisabled) -> {
        try {
            Object value = prop.getter.invoke(node);
            if (value == null) {
                ImGui.textDisabled("(null)");
                return;
            }

            boolean isReadOnly = prop.setter == null || isDisabled;
            if (isReadOnly) {
                ImGui.beginDisabled();
            }

            ImGui.text(value.toString());

            if (value instanceof Collection<?> collection) {
                ImGui.sameLine();
                ImGui.textDisabled("(" + collection.size() + " items)");
            } else if (value instanceof Map<?, ?> map) {
                ImGui.sameLine();
                ImGui.textDisabled("(" + map.size() + " entries)");
            }

            if (isReadOnly) {
                ImGui.endDisabled();
            }
        } catch (Throwable e) {
            NodeCraft.LOGGER.error("Failed to render property '{}'", prop.name, e);
            ImGui.textColored(1.0f, 0.2f, 0.2f, 1.0f, "Error rendering property");
        }
    };

    private PropertyRendererRegistry() {
    }

    static void registerRenderer(Class<?> type, PropertyPanelComponent.PropertyRenderer renderer) {
        if (type == null || renderer == null) {
            throw new IllegalArgumentException("type and renderer must not be null");
        }

        REGISTRY.put(type, renderer);
        NodeCraft.LOGGER.debug("Registered property renderer: {}", type.getName());
    }

    static PropertyPanelComponent.PropertyRenderer getRendererForType(
            Class<?> type,
            PropertyPanelComponent.PropertyRenderer enumRenderer
    ) {
        PropertyPanelComponent.PropertyRenderer renderer = REGISTRY.get(type);
        if (renderer != null) {
            return renderer;
        }

        if (type.isEnum()) {
            return enumRenderer;
        }

        for (Map.Entry<Class<?>, PropertyPanelComponent.PropertyRenderer> entry : REGISTRY.entrySet()) {
            if (entry.getKey().isAssignableFrom(type)) {
                return entry.getValue();
            }
        }

        return FALLBACK_RENDERER;
    }
}

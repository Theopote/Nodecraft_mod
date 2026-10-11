package com.nodecraft.gui.components.property.core;

import com.nodecraft.core.NodeCraft;
import imgui.ImGui;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public final class PropertyRendererRegistry {

    private static final Map<Class<?>, PropertyRenderer> REGISTRY = new HashMap<>();

    private static final PropertyRenderer FALLBACK_RENDERER = (panel, node, prop, isDisabled) -> {
        try {
            Object value = prop.getter.invoke(node);
            if (value == null) {
                ImGui.textDisabled("(null)");
                return;
            }

            ImGui.text(value.toString());

            if (value instanceof Collection<?> collection) {
                ImGui.sameLine();
                ImGui.textDisabled("(" + collection.size() + " items)");
            } else if (value instanceof Map<?, ?> map) {
                ImGui.sameLine();
                ImGui.textDisabled("(" + map.size() + " entries)");
            }
        } catch (Throwable e) {
            NodeCraft.LOGGER.error("Failed to render property '{}'", prop.name, e);
            ImGui.textColored(1.0f, 0.2f, 0.2f, 1.0f, "Error rendering property");
        }
    };

    private PropertyRendererRegistry() {
    }

    public static void registerRenderer(Class<?> type, PropertyRenderer renderer) {
        if (type == null || renderer == null) {
            throw new IllegalArgumentException("type and renderer must not be null");
        }

        REGISTRY.put(type, renderer);
        NodeCraft.LOGGER.debug("Registered property renderer: {}", type.getName());
    }

        public static PropertyRenderer getRendererForType(
            Class<?> type,
            PropertyRenderer enumRenderer
    ) {
        PropertyRenderer renderer = REGISTRY.get(type);
        if (renderer != null) {
            return renderer;
        }

        if (type.isEnum()) {
            return enumRenderer;
        }

        for (Map.Entry<Class<?>, PropertyRenderer> entry : REGISTRY.entrySet()) {
            if (entry.getKey().isAssignableFrom(type)) {
                return entry.getValue();
            }
        }

        return FALLBACK_RENDERER;
    }
}

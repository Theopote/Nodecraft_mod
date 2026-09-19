package com.nodecraft.gui.components.property.core;

import com.nodecraft.gui.components.property.renderers.BooleanPropertyRenderer;
import com.nodecraft.gui.components.property.renderers.DoublePropertyRenderer;
import com.nodecraft.gui.components.property.renderers.EnumPropertyRenderer;
import com.nodecraft.gui.components.property.renderers.FloatPropertyRenderer;
import com.nodecraft.gui.components.property.renderers.IntPropertyRenderer;
import com.nodecraft.gui.components.property.renderers.StringPropertyRenderer;

/**
 * Registers primitive property editors (bool / number / string / enum).
 * <p>
 * See {@code docs/architecture/property-panel-breakup.md} (Phase 2).
 */
public final class PropertyEditorRegistry {

    private static volatile boolean primitivesRegistered;

    private PropertyEditorRegistry() {
    }

    public static void registerPrimitives() {
        if (primitivesRegistered) {
            return;
        }
        synchronized (PropertyEditorRegistry.class) {
            if (primitivesRegistered) {
                return;
            }
            PropertyRendererRegistry.registerRenderer(boolean.class, BooleanPropertyRenderer.RENDERER);
            PropertyRendererRegistry.registerRenderer(Boolean.class, BooleanPropertyRenderer.RENDERER);
            PropertyRendererRegistry.registerRenderer(String.class, StringPropertyRenderer.RENDERER);
            PropertyRendererRegistry.registerRenderer(int.class, IntPropertyRenderer.RENDERER);
            PropertyRendererRegistry.registerRenderer(Integer.class, IntPropertyRenderer.RENDERER);
            PropertyRendererRegistry.registerRenderer(float.class, FloatPropertyRenderer.RENDERER);
            PropertyRendererRegistry.registerRenderer(Float.class, FloatPropertyRenderer.RENDERER);
            PropertyRendererRegistry.registerRenderer(double.class, DoublePropertyRenderer.RENDERER);
            PropertyRendererRegistry.registerRenderer(Double.class, DoublePropertyRenderer.RENDERER);
            primitivesRegistered = true;
        }
    }

    public static PropertyRenderer enumRenderer() {
        return EnumPropertyRenderer.RENDERER;
    }
}

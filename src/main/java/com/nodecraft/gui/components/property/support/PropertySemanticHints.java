package com.nodecraft.gui.components.property.support;

import com.nodecraft.gui.components.property.core.PropertyDescriptor;
import com.nodecraft.nodesystem.api.INode;

import java.util.Locale;

/**
 * Heuristic UI semantics inferred from property names and node context.
 */
public final class PropertySemanticHints {

    public enum ScalarMode {
        GENERIC,
        PERCENT,
        ANGLE,
        INTEGER_COUNT
    }

    private PropertySemanticHints() {
    }

    public static ScalarMode scalarMode(INode node, PropertyDescriptor prop, Class<?> type) {
        if (GeometryViewerPropertySupport.isTransparency(node, prop)) {
            return ScalarMode.PERCENT;
        }

        String key = normalizedKey(prop);
        if (isIntegerType(type) && (key.contains("count")
                || key.contains("segment")
                || key.contains("step")
                || key.contains("iter")
                || key.contains("repeat")
                || key.contains("division"))) {
            return ScalarMode.INTEGER_COUNT;
        }

        if (isFloatingType(type) && (key.contains("angle")
                || key.contains("rotation")
                || key.contains("degree")
                || key.contains("yaw")
                || key.contains("pitch")
                || key.contains("roll"))) {
            return ScalarMode.ANGLE;
        }

        if (isFloatingType(type) && (key.contains("opacity")
                || key.contains("alpha")
                || key.contains("transparent")
                || key.contains("transparency")
                || key.contains("ratio")
                || key.contains("percent"))) {
            return ScalarMode.PERCENT;
        }

        if (isIntegerType(type)) {
            return ScalarMode.INTEGER_COUNT;
        }
        return ScalarMode.GENERIC;
    }

    public static boolean useSegmentedEnumControl(int optionCount) {
        return optionCount >= 2 && optionCount <= 4;
    }

    public static boolean isCoordinateProperty(PropertyDescriptor prop) {
        String key = normalizedKey(prop);
        return key.contains("position")
                || key.contains("offset")
                || key.contains("center")
                || key.contains("origin")
                || key.contains("vector")
                || key.contains("scale")
                || key.contains("size")
                || key.contains("translation");
    }

    private static String normalizedKey(PropertyDescriptor prop) {
        StringBuilder key = new StringBuilder(prop.name.toLowerCase(Locale.ROOT));
        if (prop.displayName != null && !prop.displayName.isBlank()) {
            key.append(' ').append(prop.displayName.toLowerCase(Locale.ROOT));
        }
        return key.toString();
    }

    private static boolean isIntegerType(Class<?> type) {
        return type == int.class || type == Integer.class;
    }

    private static boolean isFloatingType(Class<?> type) {
        return type == float.class || type == Float.class || type == double.class || type == Double.class;
    }
}

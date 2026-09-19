package com.nodecraft.gui.components.property.support;

import com.nodecraft.gui.components.property.core.PropertyDescriptor;
import com.nodecraft.nodesystem.api.INode;
import java.util.Locale;

/**
 * Display labels / tooltips for enum property editors.
 */
public final class EnumPropertyLabels {

    private EnumPropertyLabels() {
    }

    public static String[] buildDisplayNames(INode node, PropertyDescriptor prop, Enum<?>[] values) {
        String[] labels = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            String override = GeometryViewerPropertySupport.enumDisplayNameOverride(node, prop, values[i]);
            labels[i] = override != null ? override : humanizeEnumName(values[i].name());
        }
        return labels;
    }

    public static String buildTooltip(
            INode node,
            PropertyDescriptor prop,
            Enum<?>[] values,
            String[] names,
            int selectedIndex
    ) {
        String override = GeometryViewerPropertySupport.enumTooltipOverride(node, prop, names, selectedIndex);
        if (override != null) {
            return override;
        }

        StringBuilder tooltip = new StringBuilder("可用值:\n");
        for (String name : names) {
            tooltip.append("- ").append(name).append("\n");
        }
        return tooltip.toString();
    }

    public static String humanizeEnumName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String[] parts = raw.toLowerCase(Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                out.append(part.substring(1));
            }
        }
        return out.toString();
    }
}

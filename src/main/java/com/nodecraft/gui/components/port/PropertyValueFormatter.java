package com.nodecraft.gui.components.port;

import com.nodecraft.nodesystem.util.Vec3;
import java.util.Collection;
import java.util.Map;

final class PropertyValueFormatter {

    private PropertyValueFormatter() {
    }

    static String formatValuePreview(Object value) {
        if (value == null) {
            return "(empty)";
        }

        String preview = switch (value) {
            case Collection<?> collection -> "Collection (" + collection.size() + ")";
            case Map<?, ?> map -> "Map (" + map.size() + ")";
            case Vec3 vec -> String.format("Vec3(%.2f, %.2f, %.2f)", vec.getX(), vec.getY(), vec.getZ());
            default -> value.toString();
        };

        return preview.length() > 96 ? preview.substring(0, 93) + "..." : preview;
    }

    static String formatValueDetails(Object value, String label) {
        if (value == null) {
            return label + ": (empty)";
        }

        String typeName = value.getClass().getSimpleName();
        String rendered = value.toString();
        if (rendered.length() > 600) {
            rendered = rendered.substring(0, 597) + "...";
        }
        return label + "\nType: " + typeName + "\nValue: " + rendered;
    }
}

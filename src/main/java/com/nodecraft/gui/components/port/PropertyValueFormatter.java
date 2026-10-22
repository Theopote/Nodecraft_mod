package com.nodecraft.gui.components.port;

import com.nodecraft.gui.components.property.core.PropertyValueFormatters;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.VectorData;
import com.nodecraft.nodesystem.util.Vec3;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;

import java.util.Collection;
import java.util.Map;

final class PropertyValueFormatter {

    private PropertyValueFormatter() {
    }

    static String formatValuePreview(Object value) {
        if (value == null) {
            return "(empty)";
        }

        String preview = formatValue(value);
        return preview.length() > 96 ? preview.substring(0, 93) + "..." : preview;
    }

    static String formatValueDetails(Object value, String label) {
        if (value == null) {
            return label + ": (empty)";
        }

        String typeName = value.getClass().getSimpleName();
        String rendered = formatValue(value);
        if (rendered.length() > 600) {
            rendered = rendered.substring(0, 597) + "...";
        }
        return label + "\nType: " + typeName + "\nValue: " + rendered;
    }

    private static String formatValue(Object value) {
        return switch (value) {
            case Collection<?> collection -> "Collection (" + collection.size() + ")";
            case Map<?, ?> map -> "Map (" + map.size() + ")";
            case Number number -> PropertyValueFormatters.formatNumber(number);
            case PointData point -> PropertyValueFormatters.formatTriad(
                    "Point", point.getX(), point.getY(), point.getZ());
            case VectorData vector -> PropertyValueFormatters.formatTriad(
                    "Vector", vector.x(), vector.y(), vector.z());
            case Vector3d vec -> PropertyValueFormatters.formatVector3d(vec);
            case Vec3d vec -> PropertyValueFormatters.formatVec3d(vec);
            case Vec3 vec -> PropertyValueFormatters.formatTriad(
                    "Vec3", vec.getX(), vec.getY(), vec.getZ());
            default -> value.toString();
        };
    }
}

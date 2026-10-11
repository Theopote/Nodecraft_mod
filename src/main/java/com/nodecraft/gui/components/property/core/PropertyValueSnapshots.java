package com.nodecraft.gui.components.property.core;

import com.google.gson.Gson;

/**
 * Deep copies property values for undo/redo snapshots.
 */
public final class PropertyValueSnapshots {

    private static final Gson GSON = new Gson();

    private PropertyValueSnapshots() {
    }

    public static Object copy(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof String || value instanceof Character) {
            return value;
        }
        if (value instanceof Enum<?> enumValue) {
            return enumValue;
        }
        try {
            return GSON.fromJson(GSON.toJson(value), value.getClass());
        } catch (Exception ignored) {
            return value;
        }
    }
}

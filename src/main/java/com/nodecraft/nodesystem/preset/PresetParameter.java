package com.nodecraft.nodesystem.preset;

import java.util.List;
import java.util.Map;

/**
 * Defines a configurable parameter for a preset.
 *
 * <p>Parameters allow users to customize preset behavior without editing the node graph.
 * Each parameter has a type, default value, constraints, and display properties.</p>
 *
 * @param options For dropdown type
 */
public record PresetParameter(String id, String name, ParameterType type, Object defaultValue, Object minValue,
                              Object maxValue, Object step, String description, String group,
                              List<ParameterOption> options) {

    /**
     * Validates and constrains a parameter value.
     *
     * @param value the value to validate
     * @return the validated (and possibly clamped) value
     * @throws IllegalArgumentException if the value is invalid
     */
    public Object validateValue(Object value) {
        if (value == null) {
            return defaultValue;
        }

        return switch (type) {
            case INTEGER -> validateInteger(value);
            case FLOAT, ANGLE -> validateFloat(value);
            case BOOLEAN -> validateBoolean(value);
            case STRING -> validateString(value);
            case DROPDOWN -> validateDropdown(value);
            case BLOCK_SELECTOR -> validateString(value); // Block ID as string
            case COLOR -> validateString(value); // Color as hex string
            case VECTOR3 -> validateVector3(value);
        };
    }

    private Object validateInteger(Object value) {
        int intValue;
        if (value instanceof Integer) {
            intValue = (Integer) value;
        } else if (value instanceof Number) {
            intValue = ((Number) value).intValue();
        } else {
            try {
                intValue = Integer.parseInt(value.toString());
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }

        if (minValue != null && intValue < ((Number) minValue).intValue()) {
            intValue = ((Number) minValue).intValue();
        }
        if (maxValue != null && intValue > ((Number) maxValue).intValue()) {
            intValue = ((Number) maxValue).intValue();
        }

        return intValue;
    }

    private Object validateFloat(Object value) {
        double doubleValue;
        if (value instanceof Double) {
            doubleValue = (Double) value;
        } else if (value instanceof Number) {
            doubleValue = ((Number) value).doubleValue();
        } else {
            try {
                doubleValue = Double.parseDouble(value.toString());
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }

        if (minValue != null && doubleValue < ((Number) minValue).doubleValue()) {
            doubleValue = ((Number) minValue).doubleValue();
        }
        if (maxValue != null && doubleValue > ((Number) maxValue).doubleValue()) {
            doubleValue = ((Number) maxValue).doubleValue();
        }

        return doubleValue;
    }

    private Object validateBoolean(Object value) {
        if (value instanceof Boolean) {
            return value;
        }
        return Boolean.parseBoolean(value.toString());
    }

    private Object validateString(Object value) {
        return value.toString();
    }

    private Object validateDropdown(Object value) {
        String strValue = value.toString();
        if (options != null) {
            boolean validOption = options.stream()
                    .anyMatch(opt -> opt.value().equals(strValue));
            if (!validOption) {
                return defaultValue;
            }
        }
        return strValue;
    }

    private Object validateVector3(Object value) {
        if (value instanceof Map) {
            return value; // Already a map with x, y, z
        }
        return defaultValue;
    }

    /**
     * Represents an option for dropdown parameters.
     */
    public record ParameterOption(String value, String label) {
    }
}

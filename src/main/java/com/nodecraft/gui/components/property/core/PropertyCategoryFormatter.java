package com.nodecraft.gui.components.property.core;

final class PropertyCategoryFormatter {

    private PropertyCategoryFormatter() {
    }

    static String normalize(String category) {
        if (category == null) {
            return "";
        }

        String trimmed = category.trim();
        if (trimmed.isEmpty()) {
            return "";
        }

        return switch (trimmed) {
            case "精度", "数值", "Values" -> "数值";
            case "UI设置", "UI配置" -> "UI设置";
            case "范围", "区间" -> "范围";
            default -> trimmed;
        };
    }

    static String format(String category) {
        String normalized = normalize(category);
        return switch (normalized) {
            case "UI设置" -> "UI Settings";
            case "数值" -> "Values";
            case "范围" -> "Range";
            case "Advanced" -> "Advanced";
            case "Compatibility", "Compat" -> "Compatibility";
            default -> formatSingleWord(normalized);
        };
    }

    private static String formatSingleWord(String word) {
        if (word == null || word.isEmpty()) {
            return "";
        }

        String[] parts = word.split("_");
        StringBuilder formatted = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                formatted.append(Character.toUpperCase(part.charAt(0)));
                formatted.append(part.substring(1).toLowerCase());
                formatted.append(" ");
            }
        }
        return formatted.toString().trim();
    }
}

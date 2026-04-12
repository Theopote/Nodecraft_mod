package com.nodecraft.gui.editor.impl;

import imgui.ImGui;

import java.util.HashMap;
import java.util.Map;

/**
 * Shared rendering constants and category color helpers for the node editor.
 */
public class NodeRenderConstants {

    // Node layout constants (unscaled)
    public static final float NODE_HORIZONTAL_PADDING = 6.0f;
    public static final float NODE_VERTICAL_PADDING = 5.0f;
    public static final float PORT_CIRCLE_TO_TEXT_PADDING = 4.0f;
    public static final float INTER_PORT_COLUMN_PADDING = 8.0f;
    public static final float MIN_NODE_WIDTH_UNSCALED = 100.0f;
    public static final float PORT_RADIUS_UNSCALED = 4.0f;
    public static final float PORT_BORDER_THICKNESS_UNSCALED = 1.5f;
    public static final float NODE_CORNER_RADIUS_UNSCALED = 6.0f;
    public static final float NODE_BORDER_THICKNESS_UNSCALED = 2.0f;

    // Compact reroute node visual constants
    public static final String REROUTE_NODE_TYPE_ID = "utilities.assist.reroute";
    public static final float REROUTE_NODE_WIDTH_UNSCALED = 26.0f;
    public static final float REROUTE_NODE_HEIGHT_UNSCALED = 14.0f;
    public static final float REROUTE_PORT_RADIUS_UNSCALED = 3.2f;

    // Port color constants.
    public static final int DEFAULT_CATEGORY_COLOR = ImGui.colorConvertFloat4ToU32(0.6f, 0.6f, 0.6f, 1.0f);
    public static final int PORT_COLOR_INPUT = ImGui.colorConvertFloat4ToU32(0.4f, 0.7f, 1.0f, 1.0f);
    public static final int PORT_COLOR_OUTPUT = ImGui.colorConvertFloat4ToU32(1.0f, 0.7f, 0.4f, 1.0f);
    public static final int PORT_COLOR_INPUT_HIGHLIGHT = ImGui.colorConvertFloat4ToU32(0.5f, 0.8f, 1.0f, 1.0f);
    public static final int PORT_COLOR_OUTPUT_HIGHLIGHT = ImGui.colorConvertFloat4ToU32(1.0f, 0.8f, 0.5f, 1.0f);

    // Precomputed port border colors.
    public static final int PORT_INPUT_BORDER_COLOR = adjustBrightnessFast(PORT_COLOR_INPUT, 1.5f);
    public static final int PORT_OUTPUT_BORDER_COLOR = adjustBrightnessFast(PORT_COLOR_OUTPUT, 1.5f);

    // Cache cleanup parameters.
    public static final int CACHE_CLEANUP_INTERVAL = 1000;
    public static final int MAX_CACHE_SIZE = 10000;

    // Category color mapping.
    private static final Map<String, Integer> CATEGORY_COLORS = new HashMap<>();

    static {
        // Keep category colors unified unless a distinct color is intentionally introduced.
        CATEGORY_COLORS.put("world", DEFAULT_CATEGORY_COLOR);
        CATEGORY_COLORS.put("reference", DEFAULT_CATEGORY_COLOR);
        CATEGORY_COLORS.put("visualization", DEFAULT_CATEGORY_COLOR);
        CATEGORY_COLORS.put("utilities", DEFAULT_CATEGORY_COLOR);
        CATEGORY_COLORS.put("spatial", DEFAULT_CATEGORY_COLOR);
        CATEGORY_COLORS.put("geometry", DEFAULT_CATEGORY_COLOR);
        CATEGORY_COLORS.put("material", DEFAULT_CATEGORY_COLOR);
        CATEGORY_COLORS.put("math", DEFAULT_CATEGORY_COLOR);
        CATEGORY_COLORS.put("pattern", DEFAULT_CATEGORY_COLOR);
        CATEGORY_COLORS.put("input", DEFAULT_CATEGORY_COLOR);
        CATEGORY_COLORS.put("inputs", DEFAULT_CATEGORY_COLOR);
        CATEGORY_COLORS.put("output", DEFAULT_CATEGORY_COLOR);
        CATEGORY_COLORS.put("deferred", DEFAULT_CATEGORY_COLOR);
        CATEGORY_COLORS.put("transform", DEFAULT_CATEGORY_COLOR);
        CATEGORY_COLORS.put("flora", DEFAULT_CATEGORY_COLOR);
        CATEGORY_COLORS.put("data", DEFAULT_CATEGORY_COLOR);
        CATEGORY_COLORS.put("animation", DEFAULT_CATEGORY_COLOR);
        CATEGORY_COLORS.put("General", DEFAULT_CATEGORY_COLOR);
    }

    /**
     * Returns the display color for a category label.
     */
    public static int getCategoryColor(String categoryName) {
        if (categoryName == null) return DEFAULT_CATEGORY_COLOR;
        String topLevelCategory = categoryName.contains(" / ")
                ? categoryName.substring(0, categoryName.indexOf(" / "))
                : categoryName;
        return CATEGORY_COLORS.getOrDefault(topLevelCategory.toLowerCase(), DEFAULT_CATEGORY_COLOR);
    }

    /**
     * Extracts the top-level category name from a node type ID.
     */
    public static String getCategoryFromNodeTypeId(String nodeTypeId) {
        if (nodeTypeId == null || nodeTypeId.isEmpty()) return "General";

        // Match the known top-level category prefixes first.
        if (nodeTypeId.startsWith("world.")) return "world";
        if (nodeTypeId.startsWith("reference.")) return "reference";
        if (nodeTypeId.startsWith("output.")) return "output";
        if (nodeTypeId.startsWith("deferred.")) return "deferred";
        if (nodeTypeId.startsWith("visualization.")) return "visualization";
        if (nodeTypeId.startsWith("utilities.")) return "utilities";
        if (nodeTypeId.startsWith("spatial.")) return "spatial";
        if (nodeTypeId.startsWith("geometry.")) return "geometry";
        if (nodeTypeId.startsWith("material.")) return "material";
        if (nodeTypeId.startsWith("math.")) return "math";
        if (nodeTypeId.startsWith("pattern.")) return "pattern";
        if (nodeTypeId.startsWith("input.")) return "input";
        if (nodeTypeId.startsWith("inputs.")) return "inputs";
        if (nodeTypeId.startsWith("transform.")) return "transform";
        if (nodeTypeId.startsWith("flora.")) return "flora";
        if (nodeTypeId.startsWith("data.")) return "data";
        if (nodeTypeId.startsWith("animation.")) return "animation";

        // Handle display labels that already contain a formatted " / " path.
        if (nodeTypeId.contains(" / ")) {
            String category = nodeTypeId.substring(0, nodeTypeId.indexOf(" / ")).toLowerCase();
            if (CATEGORY_COLORS.containsKey(category)) {
                return category;
            }
        }

        return "General";
    }

    /**
     * Applies a simple brightness multiplier to an ARGB color.
     */
    private static int adjustBrightnessFast(int color, float factor) {
        int alpha = (color >> 24) & 0xFF;
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;

        red = Math.min(255, (int) (red * factor));
        green = Math.min(255, (int) (green * factor));
        blue = Math.min(255, (int) (blue * factor));

        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }
}

package com.nodecraft.gui.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class AiMockPlanService {

    private AiMockPlanService() {
    }

    public record MockNode(String ref, String typeId, float offsetX, float offsetY, Object nodeState) {
    }

    public record MockConnection(String sourceRef, String sourcePortId, String targetRef, String targetPortId) {
    }

    public record MockPlan(String summary, List<MockNode> nodes, List<MockConnection> connections, List<String> validationErrors) {
        public boolean isValid() {
            return validationErrors == null || validationErrors.isEmpty();
        }
    }

    record ParsedParameters(double radius, double width, double thickness, double turns, double pitch, double height) {
    }

    enum MockTemplateKind {
        MOBIUS,
        SPHERE,
        BOX_FILL,
        HELIX_PATH,
        GENERIC
    }

    record TemplateSelection(MockTemplateKind kind, double score) {
    }

    public static MockPlan buildMockPlan(String prompt) {
        String lowerPrompt = prompt == null ? "" : prompt.toLowerCase(Locale.ROOT);
        ParsedParameters params = parseAiPromptParameters(prompt);
        List<MockNode> nodes = new ArrayList<>();
        List<MockConnection> connections = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        MockTemplateKind templateKind = selectTemplate(lowerPrompt).kind();
        switch (templateKind) {
            case MOBIUS -> buildMobiusTemplate(params, nodes, connections);
            case HELIX_PATH -> buildHelixPathTemplate(params, nodes, connections);
            case BOX_FILL -> buildBoxFillTemplate(params, nodes, connections);
            case SPHERE -> buildSphereTemplate(params, nodes, connections);
            default -> buildGenericTemplate(params, nodes, connections);
        }

        validatePlan(nodes, connections, errors);
        String summary = buildAiPlanSummary(params, templateKind);
        return new MockPlan(summary, nodes, connections, errors);
    }

        private static void buildMobiusTemplate(ParsedParameters params, List<MockNode> nodes, List<MockConnection> connections) {
        nodes.add(new MockNode("center", "reference.points.point_from_coordinates", -720.0f, -180.0f,
            createNodeState("x", 0, "y", 80, "z", 0, "showLabel", true)));
        nodes.add(new MockNode("axis", "reference.vectors.vector", -720.0f, 120.0f,
            createNodeState("x", 0.0d, "y", 1.0d, "z", 0.0d, "showLabel", true, "precision", 2)));
        nodes.add(new MockNode("radius", "input.numeric.float", -360.0f, -220.0f,
            createNodeState("value", (float) params.radius(), "minValue", 0.1f, "maxValue", 2048.0f, "precision", 2)));
        nodes.add(new MockNode("width", "input.numeric.float", -360.0f, -60.0f,
            createNodeState("value", (float) params.width(), "minValue", 0.1f, "maxValue", 512.0f, "precision", 2)));
        nodes.add(new MockNode("thickness", "input.numeric.float", -360.0f, 100.0f,
            createNodeState("value", (float) params.thickness(), "minValue", 0.1f, "maxValue", 512.0f, "precision", 2)));
        nodes.add(new MockNode("two", "input.numeric.float", -360.0f, 260.0f,
            createNodeState("value", 2.0f, "minValue", 2.0f, "maxValue", 2.0f, "precision", 0, "showLabel", false)));
        nodes.add(new MockNode("width_half", "math.scalar_math.division", -120.0f, 20.0f, null));
        nodes.add(new MockNode("minor_max", "math.scalar_math.max", 120.0f, 100.0f, null));
        nodes.add(new MockNode("torus", "geometry.primitives.torus", 0.0f, 0.0f, null));
        nodes.add(new MockNode("bake", "output.execute.bake_geometry_to_blocks", 360.0f, 0.0f,
            createNodeState("fillGeometry", params.thickness() <= 1.2d)));
        nodes.add(new MockNode("preview", "output.preview.geometry_viewer", 720.0f, -120.0f,
            createNodeState(
                "previewEnabled", true,
                "previewColor", pickPreviewColorByWidth(params.width()),
                "transparency", pickPreviewTransparencyByThickness(params.thickness()),
                "showOutline", params.width() >= 2.0d
            )));
        nodes.add(new MockNode("apply", "output.execute.apply_changes", 720.0f, 120.0f,
            createNodeState(
                "recordUndo", true,
                "useAsyncBake", true,
                "solidGeometry", params.thickness() >= 1.0d
            )));

        connections.add(new MockConnection("center", "output_coordinate", "torus", "input_center"));
        connections.add(new MockConnection("axis", "output_vector", "torus", "input_axis"));
        connections.add(new MockConnection("radius", "output_value", "torus", "input_major_radius"));
        connections.add(new MockConnection("width", "output_value", "width_half", "input_a"));
        connections.add(new MockConnection("two", "output_value", "width_half", "input_b"));
        connections.add(new MockConnection("width_half", "output_quotient", "minor_max", "input_a"));
        connections.add(new MockConnection("thickness", "output_value", "minor_max", "input_b"));
        connections.add(new MockConnection("minor_max", "output_max", "torus", "input_minor_radius"));
        connections.add(new MockConnection("torus", "output_geometry", "bake", "input_geometry"));
        connections.add(new MockConnection("bake", "output_blocks", "preview", "input_blocks"));
        connections.add(new MockConnection("bake", "output_blocks", "apply", "input_blocks"));
        }

        private static void buildSphereTemplate(ParsedParameters params, List<MockNode> nodes, List<MockConnection> connections) {
        nodes.add(new MockNode("center", "reference.points.point_from_coordinates", -520.0f, -120.0f,
            createNodeState("x", 0, "y", 80, "z", 0, "showLabel", true)));
        nodes.add(new MockNode("radius", "input.numeric.float", -520.0f, 40.0f,
            createNodeState("value", (float) params.radius(), "minValue", 1.0f, "maxValue", 2048.0f, "precision", 2)));
        nodes.add(new MockNode("sphere", "geometry.primitives.sphere", -180.0f, -20.0f, null));
        nodes.add(new MockNode("bake", "output.execute.bake_geometry_to_blocks", 120.0f, -20.0f,
            createNodeState("fillGeometry", params.thickness() >= 1.0d)));
        nodes.add(new MockNode("preview", "output.preview.geometry_viewer", 420.0f, -120.0f,
            createNodeState("previewEnabled", true, "previewColor", "#3A86FF", "transparency", 0.34f, "showOutline", true)));
        nodes.add(new MockNode("apply", "output.execute.apply_changes", 420.0f, 120.0f,
            createNodeState("recordUndo", true, "useAsyncBake", true, "solidGeometry", true)));

        connections.add(new MockConnection("center", "output_coordinate", "sphere", "input_center"));
        connections.add(new MockConnection("radius", "output_value", "sphere", "input_radius"));
        connections.add(new MockConnection("sphere", "output_geometry", "bake", "input_geometry"));
        connections.add(new MockConnection("bake", "output_blocks", "preview", "input_blocks"));
        connections.add(new MockConnection("bake", "output_blocks", "apply", "input_blocks"));
        }

        private static void buildBoxFillTemplate(ParsedParameters params, List<MockNode> nodes, List<MockConnection> connections) {
        int sizeX = clampInt((int) Math.round(params.radius() * 2.0d), 4, 256);
        int sizeY = clampInt((int) Math.round(params.height()), 4, 256);
        int sizeZ = clampInt((int) Math.round(Math.max(params.radius() * 1.8d, params.width() * 4.0d)), 4, 256);

        nodes.add(new MockNode("center", "reference.points.point_from_coordinates", -660.0f, -120.0f,
            createNodeState("x", 0, "y", 72, "z", 0, "showLabel", true)));
        nodes.add(new MockNode("size_x", "input.numeric.integer", -660.0f, 60.0f,
            createNodeState("value", sizeX, "minValue", 1, "maxValue", 1024, "step", 1)));
        nodes.add(new MockNode("size_y", "input.numeric.integer", -660.0f, 220.0f,
            createNodeState("value", sizeY, "minValue", 1, "maxValue", 512, "step", 1)));
        nodes.add(new MockNode("size_z", "input.numeric.integer", -660.0f, 380.0f,
            createNodeState("value", sizeZ, "minValue", 1, "maxValue", 1024, "step", 1)));
        nodes.add(new MockNode("box", "geometry.primitives.box", -280.0f, 180.0f, null));
        nodes.add(new MockNode("bake", "output.execute.bake_geometry_to_blocks", 80.0f, 180.0f,
            createNodeState("fillGeometry", true)));
        nodes.add(new MockNode("preview", "output.preview.geometry_viewer", 420.0f, 80.0f,
            createNodeState("previewEnabled", true, "previewColor", "#2AA876", "transparency", 0.30f, "showOutline", true)));
        nodes.add(new MockNode("apply", "output.execute.apply_changes", 420.0f, 280.0f,
            createNodeState("recordUndo", true, "useAsyncBake", true, "solidGeometry", true)));

        connections.add(new MockConnection("center", "output_coordinate", "box", "input_center"));
        connections.add(new MockConnection("size_x", "output_value", "box", "input_size_x"));
        connections.add(new MockConnection("size_y", "output_value", "box", "input_size_y"));
        connections.add(new MockConnection("size_z", "output_value", "box", "input_size_z"));
        connections.add(new MockConnection("box", "output_geometry", "bake", "input_geometry"));
        connections.add(new MockConnection("bake", "output_blocks", "preview", "input_blocks"));
        connections.add(new MockConnection("bake", "output_blocks", "apply", "input_blocks"));
        }

        private static void buildHelixPathTemplate(ParsedParameters params, List<MockNode> nodes, List<MockConnection> connections) {
        int segmentsPerTurn = clampInt((int) Math.round(Math.max(12.0d, params.width() * 8.0d)), 12, 96);
        float seedRadius = (float) Math.max(0.6d, Math.min(2.0d, params.thickness() * 0.8d));

        nodes.add(new MockNode("center", "reference.points.point_from_coordinates", -1000.0f, -180.0f,
            createNodeState("x", 0, "y", 72, "z", 0, "showLabel", true)));
        nodes.add(new MockNode("axis", "reference.vectors.vector", -1000.0f, 40.0f,
            createNodeState("x", 0.0d, "y", 1.0d, "z", 0.0d, "showLabel", false, "precision", 2)));
        nodes.add(new MockNode("radius", "input.numeric.float", -1000.0f, 220.0f,
            createNodeState("value", (float) params.radius(), "minValue", 1.0f, "maxValue", 2048.0f, "precision", 2)));
        nodes.add(new MockNode("pitch", "input.numeric.float", -1000.0f, 380.0f,
            createNodeState("value", (float) params.pitch(), "minValue", 0.2f, "maxValue", 256.0f, "precision", 2)));
        nodes.add(new MockNode("turns", "input.numeric.float", -760.0f, 380.0f,
            createNodeState("value", (float) params.turns(), "minValue", 0.5f, "maxValue", 128.0f, "precision", 2)));
        nodes.add(new MockNode("segments", "input.numeric.integer", -760.0f, 220.0f,
            createNodeState("value", segmentsPerTurn, "minValue", 6, "maxValue", 128, "step", 1)));

        nodes.add(new MockNode("seed_radius", "input.numeric.float", -760.0f, 40.0f,
            createNodeState("value", seedRadius, "minValue", 0.25f, "maxValue", 8.0f, "precision", 2, "showLabel", false)));
        nodes.add(new MockNode("seed_sphere", "geometry.primitives.sphere", -520.0f, 40.0f, null));
        nodes.add(new MockNode("seed_bake", "output.execute.bake_geometry_to_blocks", -260.0f, 40.0f,
            createNodeState("fillGeometry", true)));

        nodes.add(new MockNode("helix", "geometry.curves.helix", -520.0f, 280.0f, null));
        nodes.add(new MockNode("path_preview", "output.preview.preview_curves", -260.0f, 280.0f,
            createNodeState("previewEnabled", true, "pathColor", "#FFD933", "lineWidth", 1.8f, "showDirection", true)));
        nodes.add(new MockNode("along_path", "pattern.linear.along_path", 40.0f, 180.0f,
            createNodeState("orientToPath", true, "deduplicateAnchors", true)));

        nodes.add(new MockNode("preview", "output.preview.geometry_viewer", 360.0f, 80.0f,
            createNodeState("previewEnabled", true, "previewColor", "#45B36B", "transparency", 0.36f, "showOutline", false)));
        nodes.add(new MockNode("apply", "output.execute.apply_changes", 360.0f, 280.0f,
            createNodeState("recordUndo", true, "useAsyncBake", true, "solidGeometry", false)));

        connections.add(new MockConnection("center", "output_coordinate", "seed_sphere", "input_center"));
        connections.add(new MockConnection("seed_radius", "output_value", "seed_sphere", "input_radius"));
        connections.add(new MockConnection("seed_sphere", "output_geometry", "seed_bake", "input_geometry"));

        connections.add(new MockConnection("center", "output_coordinate", "helix", "input_center"));
        connections.add(new MockConnection("axis", "output_vector", "helix", "input_axis"));
        connections.add(new MockConnection("radius", "output_value", "helix", "input_radius"));
        connections.add(new MockConnection("pitch", "output_value", "helix", "input_pitch"));
        connections.add(new MockConnection("turns", "output_value", "helix", "input_turns"));
        connections.add(new MockConnection("segments", "output_value", "helix", "input_segments_per_turn"));

        connections.add(new MockConnection("helix", "output_curve", "path_preview", "input_curve"));
        connections.add(new MockConnection("seed_bake", "output_blocks", "along_path", "input_coordinates"));
        connections.add(new MockConnection("helix", "output_curve", "along_path", "input_curve"));

        connections.add(new MockConnection("along_path", "output_array_coordinates", "preview", "input_blocks"));
        connections.add(new MockConnection("along_path", "output_array_coordinates", "apply", "input_blocks"));
        }

        private static void buildGenericTemplate(ParsedParameters params, List<MockNode> nodes, List<MockConnection> connections) {
        buildSphereTemplate(params, nodes, connections);
        }

        private static TemplateSelection selectTemplate(String lowerPrompt) {
        if (containsAny(lowerPrompt, "mobius", "möbius", "莫比乌斯")) {
            return new TemplateSelection(MockTemplateKind.MOBIUS, 1000.0d);
        }

        double helixScore = keywordScore(lowerPrompt,
            "helix", "spiral", "coil", "spring", "curve", "path", "road", "bridge", "trail",
            "螺旋", "曲线", "路径", "道路", "轨迹", "盘旋", "线");
        double boxScore = keywordScore(lowerPrompt,
            "box", "cube", "room", "wall", "platform", "region", "fill", "volume", "cuboid",
            "盒", "立方体", "区域", "填充", "体积", "房间", "墙");
        double sphereScore = keywordScore(lowerPrompt,
            "sphere", "ball", "orb", "dome", "planet", "bubble",
            "球", "球体", "穹顶", "圆球");

        TemplateSelection best = new TemplateSelection(MockTemplateKind.SPHERE, Math.max(1.0d, sphereScore));
        if (boxScore > best.score()) {
            best = new TemplateSelection(MockTemplateKind.BOX_FILL, boxScore);
        }
        if (helixScore > best.score()) {
            best = new TemplateSelection(MockTemplateKind.HELIX_PATH, helixScore);
        }

        if (best.score() <= 0.0d) {
            return new TemplateSelection(MockTemplateKind.GENERIC, 0.0d);
        }
        return best;
        }

    private static ParsedParameters parseAiPromptParameters(String prompt) {
        String text = prompt == null ? "" : prompt;
        double radius = parsePromptNumber(text, "radius", "r", "major radius", "环半径", "半径", "主半径");
        double width = parsePromptNumber(text, "width", "w", "band width", "带宽", "宽度");
        double thickness = parsePromptNumber(text, "thickness", "t", "minor radius", "厚度", "管半径", "截面半径");
        double turns = parsePromptNumber(text, "turns", "loops", "圈数", "匝数");
        double pitch = parsePromptNumber(text, "pitch", "step", "螺距", "间距");
        double height = parsePromptNumber(text, "height", "h", "高度");

        if (radius <= 0.0d) {
            radius = 12.0d;
        }
        if (width <= 0.0d) {
            width = 2.0d;
        }
        if (thickness <= 0.0d) {
            thickness = Math.max(0.8d, width * 0.4d);
        }
        if (turns <= 0.0d) {
            turns = 3.0d;
        }
        if (pitch <= 0.0d) {
            pitch = Math.max(2.0d, thickness * 3.0d);
        }
        if (height <= 0.0d) {
            height = Math.max(8.0d, width * 4.0d);
        }

        return new ParsedParameters(radius, width, thickness, turns, pitch, height);
    }

    private static double parsePromptNumber(String text, String... aliases) {
        if (text == null || text.isBlank() || aliases == null) {
            return -1.0d;
        }

        for (String alias : aliases) {
            if (alias == null || alias.isBlank()) {
                continue;
            }

            String escapedAlias = java.util.regex.Pattern.quote(alias);
            String pattern = "(?i)(?:^|[^a-zA-Z0-9_])" + escapedAlias + "\\s*[=:是为]?\\s*(-?\\d+(?:\\.\\d+)?)";
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(pattern).matcher(text);
            if (matcher.find()) {
                try {
                    return Double.parseDouble(matcher.group(1));
                } catch (NumberFormatException ignored) {
                    // Skip malformed numeric capture and continue.
                }
            }
        }

        return -1.0d;
    }

    private static String pickPreviewColorByWidth(double width) {
        if (width >= 4.0d) {
            return "#3A86FF";
        }
        if (width >= 2.5d) {
            return "#2AA876";
        }
        return "#45B36B";
    }

    private static float pickPreviewTransparencyByThickness(double thickness) {
        if (thickness >= 2.0d) {
            return 0.28f;
        }
        if (thickness >= 1.2d) {
            return 0.34f;
        }
        return 0.42f;
    }

    private static String buildAiPlanSummary(ParsedParameters params, MockTemplateKind templateKind) {
        String templateName = switch (Objects.requireNonNullElse(templateKind, MockTemplateKind.GENERIC)) {
            case MOBIUS -> "mobius";
            case SPHERE -> "sphere";
            case BOX_FILL -> "box_fill";
            case HELIX_PATH -> "helix_path";
            case GENERIC -> "generic";
        };
        return String.format(
                Locale.ROOT,
                "Mock plan generated locally with template=%s. Parsed parameters: radius=%.2f, width=%.2f, thickness=%.2f, turns=%.2f, pitch=%.2f, height=%.2f. "
                        + "Template uses known node IDs/ports so local fallback stays executable while remote planner is unavailable.",
                templateName,
                params.radius(),
                params.width(),
                params.thickness(),
                params.turns(),
                params.pitch(),
                params.height()
        );
    }

    private static double keywordScore(String lowerPrompt, String... keywords) {
        if (lowerPrompt == null || lowerPrompt.isBlank() || keywords == null || keywords.length == 0) {
            return 0.0d;
        }
        double score = 0.0d;
        for (String keyword : keywords) {
            if (keyword == null || keyword.isBlank()) {
                continue;
            }
            if (lowerPrompt.contains(keyword.toLowerCase(Locale.ROOT))) {
                score += Math.max(1.0d, keyword.length() * 0.1d);
            }
        }
        return score;
    }

    private static boolean containsAny(String lowerPrompt, String... keywords) {
        return keywordScore(lowerPrompt, keywords) > 0.0d;
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void validatePlan(List<MockNode> nodes, List<MockConnection> connections, List<String> errors) {
        Set<String> refs = new java.util.HashSet<>();
        for (MockNode node : nodes) {
            if (node.ref() == null || node.ref().isBlank()) {
                errors.add("Node reference cannot be empty.");
                continue;
            }
            if (!refs.add(node.ref())) {
                errors.add("Duplicate node reference: " + node.ref());
            }
            if (node.typeId() == null || node.typeId().isBlank()) {
                errors.add("Node type cannot be empty for reference: " + node.ref());
            }
        }

        for (MockConnection connection : connections) {
            if (!refs.contains(connection.sourceRef())) {
                errors.add("Unknown source reference: " + connection.sourceRef());
            }
            if (!refs.contains(connection.targetRef())) {
                errors.add("Unknown target reference: " + connection.targetRef());
            }
            if (connection.sourcePortId() == null || connection.sourcePortId().isBlank()) {
                errors.add("Connection source port is empty for source ref: " + connection.sourceRef());
            }
            if (connection.targetPortId() == null || connection.targetPortId().isBlank()) {
                errors.add("Connection target port is empty for target ref: " + connection.targetRef());
            }
        }
    }

    private static Map<String, Object> createNodeState(Object... keyValues) {
        Map<String, Object> state = new HashMap<>();
        if (keyValues == null || keyValues.length == 0) {
            return state;
        }
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            Object key = keyValues[i];
            Object value = keyValues[i + 1];
            if (key instanceof String keyString && !keyString.isBlank()) {
                state.put(keyString, value);
            }
        }
        return state;
    }
}

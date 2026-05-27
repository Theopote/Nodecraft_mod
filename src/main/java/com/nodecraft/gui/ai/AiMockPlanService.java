package com.nodecraft.gui.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    record ParsedParameters(double radius, double width, double thickness) {
    }

    public static MockPlan buildMockPlan(String prompt) {
        String lowerPrompt = prompt == null ? "" : prompt.toLowerCase(Locale.ROOT);
        ParsedParameters params = parseAiPromptParameters(prompt);
        List<MockNode> nodes = new ArrayList<>();
        List<MockConnection> connections = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        if (lowerPrompt.contains("mobius") || lowerPrompt.contains("möbius") || lowerPrompt.contains("莫比乌斯")) {
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
        } else {
            nodes.add(new MockNode("preview", "output.preview.geometry_viewer", 0.0f, -100.0f,
                    createNodeState("previewEnabled", true, "previewColor", "#4CAF50", "transparency", 0.40f)));
            nodes.add(new MockNode("apply", "output.execute.apply_changes", 360.0f, 80.0f,
                    createNodeState("recordUndo", true)));
            connections.add(new MockConnection("preview", "output_blocks", "apply", "input_blocks"));
        }

        validatePlan(nodes, connections, errors);
        String summary = buildAiPlanSummary(params);
        return new MockPlan(summary, nodes, connections, errors);
    }

    private static ParsedParameters parseAiPromptParameters(String prompt) {
        String text = prompt == null ? "" : prompt;
        double radius = parsePromptNumber(text, "radius", "r", "major radius", "环半径", "半径", "主半径");
        double width = parsePromptNumber(text, "width", "w", "band width", "带宽", "宽度");
        double thickness = parsePromptNumber(text, "thickness", "t", "minor radius", "厚度", "管半径", "截面半径");

        if (radius <= 0.0d) {
            radius = 12.0d;
        }
        if (width <= 0.0d) {
            width = 2.0d;
        }
        if (thickness <= 0.0d) {
            thickness = Math.max(0.8d, width * 0.4d);
        }

        return new ParsedParameters(radius, width, thickness);
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

    private static String buildAiPlanSummary(ParsedParameters params) {
        return String.format(
                Locale.ROOT,
                "Mock plan generated locally. Parsed parameters: radius=%.2f, width=%.2f, thickness=%.2f. "
                        + "Minor radius is wired as max(width/2, thickness). Backend planner can later reuse the same apply path.",
                params.radius(),
                params.width(),
                params.thickness()
        );
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

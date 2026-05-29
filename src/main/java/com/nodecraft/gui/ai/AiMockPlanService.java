package com.nodecraft.gui.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nodecraft.core.NodeCraft;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
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
        PLACEMENT,
        MOBIUS,
        SPHERE,
        BOX_FILL,
        HELIX_PATH,
        TOWER,
        ARCH_PATH,
        RING_WALKWAY,
        MULTI_LEVEL_PLATFORM,
        GENERIC
    }

    record TemplateSelection(MockTemplateKind kind, double score) {
    }

    record TemplateSelectionResult(List<TemplateSelection> topCandidates, List<TemplateSelection> rankedCandidates) {
    }

    record WeightedKeyword(String token, double weight) {
    }

    record ExternalTemplate(String summary, List<MockNode> nodes, List<MockConnection> connections) {
    }

    private static final Gson GSON = new Gson();
    private static final String TEMPLATE_WEIGHTS_FILE_NAME = "ai_mock_template_weights.json";
    private static final String TEMPLATE_OVERRIDES_FILE_NAME = "ai_mock_plan_templates.json";

    private static volatile boolean templateWeightsInitialized = false;
    private static volatile String templateWeightsSource = "built-in";
    private static volatile String templateOverridesSource = "built-in";
    private static volatile Map<MockTemplateKind, List<WeightedKeyword>> templatePositiveKeywords = createPositiveKeywordTable();
    private static volatile Map<MockTemplateKind, List<WeightedKeyword>> templateNegativeKeywords = createNegativeKeywordTable();
    private static volatile Map<MockTemplateKind, ExternalTemplate> templateOverrides = Map.of();

    public static MockPlan buildMockPlan(String prompt) {
        ensureTemplateWeightsLoaded();
        String lowerPrompt = prompt == null ? "" : prompt.toLowerCase(Locale.ROOT);
        ParsedParameters params = parseAiPromptParameters(prompt);

        TemplateSelectionResult selectionResult = selectTemplateCandidates(lowerPrompt);
        List<TemplateSelection> candidates = selectionResult.topCandidates();
        List<MockNode> nodes = new ArrayList<>();
        List<MockConnection> connections = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        MockTemplateKind selectedTemplate = MockTemplateKind.GENERIC;
        MockTemplateKind attemptedFallback = candidates.size() > 1 ? candidates.get(1).kind() : MockTemplateKind.GENERIC;
        boolean fallbackUsed = false;

        for (int i = 0; i < Math.min(2, candidates.size()); i++) {
            MockTemplateKind candidateKind = candidates.get(i).kind();
            List<MockNode> trialNodes = new ArrayList<>();
            List<MockConnection> trialConnections = new ArrayList<>();
            List<String> trialErrors = new ArrayList<>();

            buildTemplate(candidateKind, params, trialNodes, trialConnections);
            validatePlan(trialNodes, trialConnections, trialErrors);
            if (trialErrors.isEmpty()) {
                nodes = trialNodes;
                connections = trialConnections;
                errors = trialErrors;
                selectedTemplate = candidateKind;
                fallbackUsed = i > 0;
                break;
            }
        }

        if (nodes.isEmpty()) {
            buildGenericTemplate(params, nodes, connections);
            validatePlan(nodes, connections, errors);
            selectedTemplate = MockTemplateKind.GENERIC;
        }

        String summary = buildAiPlanSummary(params, selectedTemplate, attemptedFallback, fallbackUsed, selectionResult.rankedCandidates());
        return new MockPlan(summary, nodes, connections, errors);
    }

    private static void buildTemplate(
        MockTemplateKind kind,
        ParsedParameters params,
        List<MockNode> nodes,
        List<MockConnection> connections
    ) {
        if (applyExternalTemplate(kind, nodes, connections)) {
            return;
        }

        switch (kind) {
            case MOBIUS -> buildMobiusTemplate(params, nodes, connections);
            case HELIX_PATH -> buildHelixPathTemplate(params, nodes, connections);
            case BOX_FILL -> buildBoxFillTemplate(params, nodes, connections);
            case TOWER -> buildTowerTemplate(params, nodes, connections);
            case ARCH_PATH -> buildArchPathTemplate(params, nodes, connections);
            case RING_WALKWAY -> buildRingWalkwayTemplate(params, nodes, connections);
            case MULTI_LEVEL_PLATFORM -> buildMultiLevelPlatformTemplate(params, nodes, connections);
            case SPHERE -> buildSphereTemplate(params, nodes, connections);
            default -> buildGenericTemplate(params, nodes, connections);
        }
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

    private static void buildTowerTemplate(ParsedParameters params, List<MockNode> nodes, List<MockConnection> connections) {
        int baseY = 72;
        int topY = baseY + clampInt((int) Math.round(params.height()), 8, 192);
        float radius = (float) Math.max(1.0d, Math.min(64.0d, params.radius() * 0.35d));

        nodes.add(new MockNode("tower_bottom", "reference.points.point_from_coordinates", -640.0f, -80.0f,
            createNodeState("x", 0, "y", baseY, "z", 0, "showLabel", true)));
        nodes.add(new MockNode("tower_top", "reference.points.point_from_coordinates", -640.0f, 120.0f,
            createNodeState("x", 0, "y", topY, "z", 0, "showLabel", true)));
        nodes.add(new MockNode("tower_radius", "input.numeric.float", -640.0f, 300.0f,
            createNodeState("value", radius, "minValue", 1.0f, "maxValue", 128.0f, "precision", 2)));
        nodes.add(new MockNode("tower_cylinder", "geometry.primitives.cylinder", -260.0f, 120.0f, null));
        nodes.add(new MockNode("tower_bake", "output.execute.bake_geometry_to_blocks", 80.0f, 120.0f,
            createNodeState("fillGeometry", true)));
        nodes.add(new MockNode("preview", "output.preview.geometry_viewer", 420.0f, 30.0f,
            createNodeState("previewEnabled", true, "previewColor", "#6BA368", "transparency", 0.30f, "showOutline", true)));
        nodes.add(new MockNode("apply", "output.execute.apply_changes", 420.0f, 220.0f,
            createNodeState("recordUndo", true, "useAsyncBake", true, "solidGeometry", true)));

        connections.add(new MockConnection("tower_bottom", "output_coordinate", "tower_cylinder", "input_start"));
        connections.add(new MockConnection("tower_top", "output_coordinate", "tower_cylinder", "input_end"));
        connections.add(new MockConnection("tower_radius", "output_value", "tower_cylinder", "input_radius"));
        connections.add(new MockConnection("tower_cylinder", "output_geometry", "tower_bake", "input_geometry"));
        connections.add(new MockConnection("tower_bake", "output_blocks", "preview", "input_blocks"));
        connections.add(new MockConnection("tower_bake", "output_blocks", "apply", "input_blocks"));
    }

    private static void buildArchPathTemplate(ParsedParameters params, List<MockNode> nodes, List<MockConnection> connections) {
        int segments = clampInt((int) Math.round(Math.max(16.0d, params.width() * 10.0d)), 16, 120);
        float seedRadius = (float) Math.max(0.8d, Math.min(3.0d, params.thickness()));

        nodes.add(new MockNode("arch_center", "reference.points.point_from_coordinates", -980.0f, -160.0f,
            createNodeState("x", 0, "y", 72, "z", 0, "showLabel", true)));
        nodes.add(new MockNode("arch_normal", "reference.vectors.vector", -980.0f, 20.0f,
            createNodeState("x", 0.0d, "y", 0.0d, "z", 1.0d, "showLabel", false, "precision", 2)));
        nodes.add(new MockNode("arch_radius", "input.numeric.float", -980.0f, 200.0f,
            createNodeState("value", (float) Math.max(4.0d, params.radius()), "minValue", 1.0f, "maxValue", 512.0f, "precision", 2)));
        nodes.add(new MockNode("arch_start", "input.numeric.float", -980.0f, 360.0f,
            createNodeState("value", 180.0f, "minValue", -360.0f, "maxValue", 360.0f, "precision", 1, "showLabel", false)));
        nodes.add(new MockNode("arch_end", "input.numeric.float", -760.0f, 360.0f,
            createNodeState("value", 0.0f, "minValue", -360.0f, "maxValue", 360.0f, "precision", 1, "showLabel", false)));
        nodes.add(new MockNode("arch_segments", "input.numeric.integer", -760.0f, 200.0f,
            createNodeState("value", segments, "minValue", 8, "maxValue", 256, "step", 1)));

        nodes.add(new MockNode("arch_curve", "geometry.curves.arc", -540.0f, 200.0f, null));
        nodes.add(new MockNode("path_preview", "output.preview.preview_curves", -280.0f, 200.0f,
            createNodeState("previewEnabled", true, "pathColor", "#FFD933", "lineWidth", 1.8f, "showDirection", false)));

        nodes.add(new MockNode("seed_radius", "input.numeric.float", -760.0f, 20.0f,
            createNodeState("value", seedRadius, "minValue", 0.25f, "maxValue", 8.0f, "precision", 2, "showLabel", false)));
        nodes.add(new MockNode("seed_sphere", "geometry.primitives.sphere", -540.0f, 20.0f, null));
        nodes.add(new MockNode("seed_bake", "output.execute.bake_geometry_to_blocks", -280.0f, 20.0f,
            createNodeState("fillGeometry", true)));

        nodes.add(new MockNode("along_path", "pattern.linear.along_path", 20.0f, 120.0f,
            createNodeState("orientToPath", true, "deduplicateAnchors", true)));
        nodes.add(new MockNode("preview", "output.preview.geometry_viewer", 360.0f, 40.0f,
            createNodeState("previewEnabled", true, "previewColor", "#F4A261", "transparency", 0.34f, "showOutline", false)));
        nodes.add(new MockNode("apply", "output.execute.apply_changes", 360.0f, 220.0f,
            createNodeState("recordUndo", true, "useAsyncBake", true, "solidGeometry", false)));

        connections.add(new MockConnection("arch_center", "output_coordinate", "arch_curve", "input_center"));
        connections.add(new MockConnection("arch_normal", "output_vector", "arch_curve", "input_normal"));
        connections.add(new MockConnection("arch_radius", "output_value", "arch_curve", "input_radius"));
        connections.add(new MockConnection("arch_start", "output_value", "arch_curve", "input_start_angle"));
        connections.add(new MockConnection("arch_end", "output_value", "arch_curve", "input_end_angle"));
        connections.add(new MockConnection("arch_segments", "output_value", "arch_curve", "input_resolution"));

        connections.add(new MockConnection("arch_curve", "output_curve", "path_preview", "input_curve"));

        connections.add(new MockConnection("arch_center", "output_coordinate", "seed_sphere", "input_center"));
        connections.add(new MockConnection("seed_radius", "output_value", "seed_sphere", "input_radius"));
        connections.add(new MockConnection("seed_sphere", "output_geometry", "seed_bake", "input_geometry"));

        connections.add(new MockConnection("seed_bake", "output_blocks", "along_path", "input_coordinates"));
        connections.add(new MockConnection("arch_curve", "output_curve", "along_path", "input_curve"));

        connections.add(new MockConnection("along_path", "output_array_coordinates", "preview", "input_blocks"));
        connections.add(new MockConnection("along_path", "output_array_coordinates", "apply", "input_blocks"));
    }

    private static void buildRingWalkwayTemplate(ParsedParameters params, List<MockNode> nodes, List<MockConnection> connections) {
        float majorRadius = (float) Math.max(6.0d, params.radius());
        float minorRadius = (float) Math.max(1.2d, Math.min(8.0d, params.width() * 0.6d));

        nodes.add(new MockNode("center", "reference.points.point_from_coordinates", -700.0f, -120.0f,
            createNodeState("x", 0, "y", 72, "z", 0, "showLabel", true)));
        nodes.add(new MockNode("axis", "reference.vectors.vector", -700.0f, 60.0f,
            createNodeState("x", 0.0d, "y", 1.0d, "z", 0.0d, "showLabel", false, "precision", 2)));
        nodes.add(new MockNode("major", "input.numeric.float", -700.0f, 240.0f,
            createNodeState("value", majorRadius, "minValue", 2.0f, "maxValue", 1024.0f, "precision", 2)));
        nodes.add(new MockNode("minor", "input.numeric.float", -700.0f, 380.0f,
            createNodeState("value", minorRadius, "minValue", 0.5f, "maxValue", 128.0f, "precision", 2)));

        nodes.add(new MockNode("torus", "geometry.primitives.torus", -320.0f, 150.0f, null));
        nodes.add(new MockNode("bake", "output.execute.bake_geometry_to_blocks", 40.0f, 150.0f,
            createNodeState("fillGeometry", true)));
        nodes.add(new MockNode("preview", "output.preview.geometry_viewer", 380.0f, 60.0f,
            createNodeState("previewEnabled", true, "previewColor", "#7FB069", "transparency", 0.30f, "showOutline", true)));
        nodes.add(new MockNode("apply", "output.execute.apply_changes", 380.0f, 250.0f,
            createNodeState("recordUndo", true, "useAsyncBake", true, "solidGeometry", true)));

        connections.add(new MockConnection("center", "output_coordinate", "torus", "input_center"));
        connections.add(new MockConnection("axis", "output_vector", "torus", "input_axis"));
        connections.add(new MockConnection("major", "output_value", "torus", "input_major_radius"));
        connections.add(new MockConnection("minor", "output_value", "torus", "input_minor_radius"));
        connections.add(new MockConnection("torus", "output_geometry", "bake", "input_geometry"));
        connections.add(new MockConnection("bake", "output_blocks", "preview", "input_blocks"));
        connections.add(new MockConnection("bake", "output_blocks", "apply", "input_blocks"));
    }

    private static void buildMultiLevelPlatformTemplate(ParsedParameters params, List<MockNode> nodes, List<MockConnection> connections) {
        int baseSize = clampInt((int) Math.round(Math.max(10.0d, params.radius() * 1.6d)), 8, 180);
        int middleSize = clampInt(baseSize - 4, 6, 160);
        int topSize = clampInt(middleSize - 4, 4, 140);
        int thickness = clampInt((int) Math.round(Math.max(2.0d, params.thickness() * 2.0d)), 1, 16);
        int gap = clampInt((int) Math.round(Math.max(4.0d, params.height() * 0.25d)), 3, 48);

        nodes.add(new MockNode("level0_center", "reference.points.point_from_coordinates", -1120.0f, -180.0f,
            createNodeState("x", 0, "y", 70, "z", 0, "showLabel", true)));
        nodes.add(new MockNode("level1_center", "reference.points.point_from_coordinates", -1120.0f, 0.0f,
            createNodeState("x", 0, "y", 70 + gap, "z", 0, "showLabel", false)));
        nodes.add(new MockNode("level2_center", "reference.points.point_from_coordinates", -1120.0f, 180.0f,
            createNodeState("x", 0, "y", 70 + gap * 2, "z", 0, "showLabel", false)));

        nodes.add(new MockNode("sx0", "input.numeric.integer", -900.0f, -230.0f,
            createNodeState("value", baseSize, "minValue", 2, "maxValue", 256, "step", 1, "showLabel", false)));
        nodes.add(new MockNode("sz0", "input.numeric.integer", -900.0f, -120.0f,
            createNodeState("value", baseSize, "minValue", 2, "maxValue", 256, "step", 1, "showLabel", false)));

        nodes.add(new MockNode("sx1", "input.numeric.integer", -900.0f, -10.0f,
            createNodeState("value", middleSize, "minValue", 2, "maxValue", 256, "step", 1, "showLabel", false)));
        nodes.add(new MockNode("sz1", "input.numeric.integer", -900.0f, 100.0f,
            createNodeState("value", middleSize, "minValue", 2, "maxValue", 256, "step", 1, "showLabel", false)));

        nodes.add(new MockNode("sx2", "input.numeric.integer", -900.0f, 210.0f,
            createNodeState("value", topSize, "minValue", 2, "maxValue", 256, "step", 1, "showLabel", false)));
        nodes.add(new MockNode("sz2", "input.numeric.integer", -900.0f, 320.0f,
            createNodeState("value", topSize, "minValue", 2, "maxValue", 256, "step", 1, "showLabel", false)));

        nodes.add(new MockNode("sy", "input.numeric.integer", -900.0f, 430.0f,
            createNodeState("value", thickness, "minValue", 1, "maxValue", 32, "step", 1, "showLabel", false)));

        nodes.add(new MockNode("box0", "geometry.primitives.box", -620.0f, -120.0f, null));
        nodes.add(new MockNode("box1", "geometry.primitives.box", -620.0f, 80.0f, null));
        nodes.add(new MockNode("box2", "geometry.primitives.box", -620.0f, 280.0f, null));

        nodes.add(new MockNode("union", "geometry.boolean.union", -320.0f, 120.0f,
            createNodeState("inputCount", 3)));
        nodes.add(new MockNode("bake", "output.execute.bake_geometry_to_blocks", 20.0f, 120.0f,
            createNodeState("fillGeometry", true)));
        nodes.add(new MockNode("preview", "output.preview.geometry_viewer", 360.0f, 40.0f,
            createNodeState("previewEnabled", true, "previewColor", "#5AA9E6", "transparency", 0.32f, "showOutline", true)));
        nodes.add(new MockNode("apply", "output.execute.apply_changes", 360.0f, 230.0f,
            createNodeState("recordUndo", true, "useAsyncBake", true, "solidGeometry", true)));

        connections.add(new MockConnection("level0_center", "output_coordinate", "box0", "input_center"));
        connections.add(new MockConnection("sx0", "output_value", "box0", "input_size_x"));
        connections.add(new MockConnection("sy", "output_value", "box0", "input_size_y"));
        connections.add(new MockConnection("sz0", "output_value", "box0", "input_size_z"));

        connections.add(new MockConnection("level1_center", "output_coordinate", "box1", "input_center"));
        connections.add(new MockConnection("sx1", "output_value", "box1", "input_size_x"));
        connections.add(new MockConnection("sy", "output_value", "box1", "input_size_y"));
        connections.add(new MockConnection("sz1", "output_value", "box1", "input_size_z"));

        connections.add(new MockConnection("level2_center", "output_coordinate", "box2", "input_center"));
        connections.add(new MockConnection("sx2", "output_value", "box2", "input_size_x"));
        connections.add(new MockConnection("sy", "output_value", "box2", "input_size_y"));
        connections.add(new MockConnection("sz2", "output_value", "box2", "input_size_z"));

        connections.add(new MockConnection("box0", "output_geometry", "union", "input_geometry_0"));
        connections.add(new MockConnection("box1", "output_geometry", "union", "input_geometry_1"));
        connections.add(new MockConnection("box2", "output_geometry", "union", "input_geometry_2"));

        connections.add(new MockConnection("union", "output_geometry", "bake", "input_geometry"));
        connections.add(new MockConnection("bake", "output_blocks", "preview", "input_blocks"));
        connections.add(new MockConnection("bake", "output_blocks", "apply", "input_blocks"));
    }

    private static void buildGenericTemplate(ParsedParameters params, List<MockNode> nodes, List<MockConnection> connections) {
        buildSphereTemplate(params, nodes, connections);
    }

    private static void buildPlacementTemplate(ParsedParameters params, List<MockNode> nodes, List<MockConnection> connections) {
        nodes.add(new MockNode(
            "selected_block",
            "world.selection.selected_block",
            0.0f,
            0.0f,
            createNodeState("showBlockPreview", true)
        ));
    }

    private static TemplateSelectionResult selectTemplateCandidates(String lowerPrompt) {
        if (containsAny(lowerPrompt, "place", "add", "create", "insert", "放置", "添加", "插入", "生成一个节点", "放一个", "选择节点", "方块选择", "selected block", "block selector")) {
            List<TemplateSelection> ranked = List.of(
                new TemplateSelection(MockTemplateKind.PLACEMENT, 1000.0d),
                new TemplateSelection(MockTemplateKind.GENERIC, 1.0d)
            );
            return new TemplateSelectionResult(ranked, ranked);
        }

        if (containsAny(lowerPrompt, "mobius", "möbius", "莫比乌斯")) {
            List<TemplateSelection> ranked = List.of(
                new TemplateSelection(MockTemplateKind.MOBIUS, 1000.0d),
                new TemplateSelection(MockTemplateKind.RING_WALKWAY, 10.0d)
            );
            return new TemplateSelectionResult(ranked, ranked);
        }

        List<TemplateSelection> scored = new ArrayList<>();
        for (MockTemplateKind kind : List.of(
            MockTemplateKind.PLACEMENT,
            MockTemplateKind.HELIX_PATH,
            MockTemplateKind.BOX_FILL,
            MockTemplateKind.SPHERE,
            MockTemplateKind.TOWER,
            MockTemplateKind.ARCH_PATH,
            MockTemplateKind.RING_WALKWAY,
            MockTemplateKind.MULTI_LEVEL_PLATFORM
        )) {
            scored.add(new TemplateSelection(kind, scoreFromWeightedKeywords(lowerPrompt, kind)));
        }

        scored.sort(Comparator.comparingDouble(TemplateSelection::score).reversed());

        List<TemplateSelection> top = new ArrayList<>();
        for (TemplateSelection candidate : scored) {
            if (candidate.score() > 0.0d) {
                top.add(candidate);
            }
            if (top.size() >= 2) {
                break;
            }
        }

        if (top.isEmpty()) {
            List<TemplateSelection> fallback = List.of(
                new TemplateSelection(MockTemplateKind.GENERIC, 0.0d),
                new TemplateSelection(MockTemplateKind.SPHERE, 0.0d)
            );
            return new TemplateSelectionResult(fallback, scored);
        }
        if (top.size() == 1) {
            top.add(new TemplateSelection(MockTemplateKind.GENERIC, 0.0d));
        }
        return new TemplateSelectionResult(top, scored);
    }

    private static double scoreFromWeightedKeywords(String lowerPrompt, MockTemplateKind kind) {
        double positive = weightedKeywordScore(lowerPrompt, templatePositiveKeywords.get(kind));
        double negative = weightedKeywordScore(lowerPrompt, templateNegativeKeywords.get(kind));
        return positive - negative;
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
            height = Math.max(12.0d, width * 8.0d);
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

    private static String buildAiPlanSummary(
        ParsedParameters params,
        MockTemplateKind templateKind,
        MockTemplateKind fallbackTemplate,
        boolean fallbackUsed,
        List<TemplateSelection> rankedCandidates
    ) {
        String templateName = switch (Objects.requireNonNullElse(templateKind, MockTemplateKind.GENERIC)) {
            case PLACEMENT -> "placement";
            case MOBIUS -> "mobius";
            case SPHERE -> "sphere";
            case BOX_FILL -> "box_fill";
            case HELIX_PATH -> "helix_path";
            case TOWER -> "tower";
            case ARCH_PATH -> "arch_path";
            case RING_WALKWAY -> "ring_walkway";
            case MULTI_LEVEL_PLATFORM -> "multi_level_platform";
            case GENERIC -> "generic";
        };
        String fallbackName = switch (Objects.requireNonNullElse(fallbackTemplate, MockTemplateKind.GENERIC)) {
            case PLACEMENT -> "placement";
            case MOBIUS -> "mobius";
            case SPHERE -> "sphere";
            case BOX_FILL -> "box_fill";
            case HELIX_PATH -> "helix_path";
            case TOWER -> "tower";
            case ARCH_PATH -> "arch_path";
            case RING_WALKWAY -> "ring_walkway";
            case MULTI_LEVEL_PLATFORM -> "multi_level_platform";
            case GENERIC -> "generic";
        };

        String scoreDebug = buildScoreDebugText(rankedCandidates, 3);

        return String.format(
            Locale.ROOT,
            "Mock plan generated locally with template=%s (fallbackCandidate=%s, fallbackUsed=%s). "
                + "Parsed parameters: radius=%.2f, width=%.2f, thickness=%.2f, turns=%.2f, pitch=%.2f, height=%.2f. "
                + "Score debug: %s. "
                + "Weights source: %s. "
                + "Template overrides source: %s. "
                + "Template uses known node IDs/ports so local fallback stays executable while remote planner is unavailable.",
            templateName,
            fallbackName,
            fallbackUsed,
            params.radius(),
            params.width(),
            params.thickness(),
            params.turns(),
            params.pitch(),
            params.height(),
            scoreDebug,
            templateWeightsSource,
            templateOverridesSource
        );
    }

    private static boolean applyExternalTemplate(
        MockTemplateKind kind,
        List<MockNode> nodes,
        List<MockConnection> connections
    ) {
        ExternalTemplate template = templateOverrides.get(kind);
        if (template == null || template.nodes() == null || template.nodes().isEmpty()) {
            return false;
        }

        nodes.addAll(template.nodes());
        if (template.connections() != null && !template.connections().isEmpty()) {
            connections.addAll(template.connections());
        }
        return true;
    }

    private static void ensureTemplateWeightsLoaded() {
        if (templateWeightsInitialized) {
            return;
        }
        synchronized (AiMockPlanService.class) {
            if (templateWeightsInitialized) {
                return;
            }

            Map<MockTemplateKind, List<WeightedKeyword>> positive = deepCopyKeywordTable(createPositiveKeywordTable());
            Map<MockTemplateKind, List<WeightedKeyword>> negative = deepCopyKeywordTable(createNegativeKeywordTable());
            String weightsSource = "built-in";
            String overridesSource = "built-in";
            Map<MockTemplateKind, ExternalTemplate> loadedOverrides = Map.of();

            try {
                Path overridePath = resolveTemplateWeightsPath();
                if (overridePath != null && Files.exists(overridePath)) {
                    String json = Files.readString(overridePath, StandardCharsets.UTF_8);
                    JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                    applyKeywordTableOverrides(root, "positive", positive);
                    applyKeywordTableOverrides(root, "negative", negative);
                    weightsSource = "file:" + overridePath.toString();
                }
            } catch (Exception e) {
                NodeCraft.LOGGER.warn("AiMockPlanService: failed to load template weight overrides, using built-in defaults.", e);
                weightsSource = "built-in(fallback)";
            }

            try {
                Path overridesPath = resolveTemplateOverridesPath();
                if (overridesPath != null && Files.exists(overridesPath)) {
                    String json = Files.readString(overridesPath, StandardCharsets.UTF_8);
                    loadedOverrides = parseTemplateOverrides(json);
                    overridesSource = "file:" + overridesPath;
                }
            } catch (Exception e) {
                NodeCraft.LOGGER.warn("AiMockPlanService: failed to load mock template overrides, using built-in templates.", e);
                loadedOverrides = Map.of();
                overridesSource = "built-in(fallback)";
            }

            templatePositiveKeywords = positive;
            templateNegativeKeywords = negative;
            templateOverrides = loadedOverrides;
            templateWeightsSource = weightsSource;
            templateOverridesSource = overridesSource;
            templateWeightsInitialized = true;
        }
    }

    private static Path resolveTemplateWeightsPath() {
        Path settingsPath = AiSettingsStore.resolveSettingsPath();
        Path parent = settingsPath == null ? null : settingsPath.getParent();
        if (parent == null) {
            return null;
        }
        return parent.resolve(TEMPLATE_WEIGHTS_FILE_NAME);
    }

    private static Path resolveTemplateOverridesPath() {
        Path settingsPath = AiSettingsStore.resolveSettingsPath();
        Path parent = settingsPath == null ? null : settingsPath.getParent();
        if (parent == null) {
            return null;
        }
        return parent.resolve(TEMPLATE_OVERRIDES_FILE_NAME);
    }

    private static Map<MockTemplateKind, ExternalTemplate> parseTemplateOverrides(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }

        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (!root.has("templates") || !root.get("templates").isJsonObject()) {
            return Map.of();
        }

        Map<MockTemplateKind, ExternalTemplate> overrides = new HashMap<>();
        JsonObject templates = root.getAsJsonObject("templates");
        for (Map.Entry<String, JsonElement> entry : templates.entrySet()) {
            MockTemplateKind kind = parseTemplateKind(entry.getKey());
            if (kind == null || entry.getValue() == null || !entry.getValue().isJsonObject()) {
                continue;
            }

            ExternalTemplate template = parseExternalTemplate(entry.getValue().getAsJsonObject());
            if (template != null) {
                overrides.put(kind, template);
            }
        }
        return overrides;
    }

    private static ExternalTemplate parseExternalTemplate(JsonObject object) {
        if (object == null) {
            return null;
        }

        List<MockNode> nodes = parseExternalNodes(object.get("nodes"));
        if (nodes.isEmpty()) {
            return null;
        }
        List<MockConnection> connections = parseExternalConnections(object.get("connections"));
        String summary = object.has("summary") && object.get("summary").isJsonPrimitive()
            ? object.get("summary").getAsString()
            : "";
        return new ExternalTemplate(summary, List.copyOf(nodes), List.copyOf(connections));
    }

    private static List<MockNode> parseExternalNodes(JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return List.of();
        }

        List<MockNode> nodes = new ArrayList<>();
        for (JsonElement item : element.getAsJsonArray()) {
            if (item == null || !item.isJsonObject()) {
                continue;
            }
            JsonObject node = item.getAsJsonObject();
            String ref = asTrimmedString(node.get("ref"));
            String typeId = asTrimmedString(node.get("typeId"));
            if (ref.isBlank() || typeId.isBlank()) {
                continue;
            }

            float offsetX = asFloat(node.get("offsetX"), 0.0f);
            float offsetY = asFloat(node.get("offsetY"), 0.0f);
            Object nodeState = node.has("nodeState") ? GSON.fromJson(node.get("nodeState"), Object.class) : null;
            nodes.add(new MockNode(ref, typeId, offsetX, offsetY, nodeState));
        }
        return nodes;
    }

    private static List<MockConnection> parseExternalConnections(JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return List.of();
        }

        List<MockConnection> connections = new ArrayList<>();
        for (JsonElement item : element.getAsJsonArray()) {
            if (item == null || !item.isJsonObject()) {
                continue;
            }
            JsonObject conn = item.getAsJsonObject();
            String sourceRef = asTrimmedString(conn.get("sourceRef"));
            String sourcePortId = asTrimmedString(conn.get("sourcePortId"));
            String targetRef = asTrimmedString(conn.get("targetRef"));
            String targetPortId = asTrimmedString(conn.get("targetPortId"));
            if (sourceRef.isBlank() || sourcePortId.isBlank() || targetRef.isBlank() || targetPortId.isBlank()) {
                continue;
            }
            connections.add(new MockConnection(sourceRef, sourcePortId, targetRef, targetPortId));
        }
        return connections;
    }

    private static String asTrimmedString(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            return "";
        }
        return element.getAsString().trim();
    }

    private static float asFloat(JsonElement element, float fallback) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            return fallback;
        }
        return element.getAsFloat();
    }

    private static Map<MockTemplateKind, List<WeightedKeyword>> deepCopyKeywordTable(
        Map<MockTemplateKind, List<WeightedKeyword>> source
    ) {
        Map<MockTemplateKind, List<WeightedKeyword>> copy = new HashMap<>();
        if (source == null || source.isEmpty()) {
            return copy;
        }
        for (Map.Entry<MockTemplateKind, List<WeightedKeyword>> entry : source.entrySet()) {
            List<WeightedKeyword> value = entry.getValue();
            copy.put(entry.getKey(), value == null ? List.of() : new ArrayList<>(value));
        }
        return copy;
    }

    private static void applyKeywordTableOverrides(
        JsonObject root,
        String sectionName,
        Map<MockTemplateKind, List<WeightedKeyword>> destination
    ) {
        if (root == null || !root.has(sectionName) || !root.get(sectionName).isJsonObject()) {
            return;
        }
        JsonObject section = root.getAsJsonObject(sectionName);
        for (Map.Entry<String, JsonElement> entry : section.entrySet()) {
            MockTemplateKind kind = parseTemplateKind(entry.getKey());
            if (kind == null) {
                continue;
            }
            List<WeightedKeyword> keywords = parseWeightedKeywords(entry.getValue());
            if (!keywords.isEmpty()) {
                destination.put(kind, keywords);
            }
        }
    }

    private static MockTemplateKind parseTemplateKind(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return MockTemplateKind.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static List<WeightedKeyword> parseWeightedKeywords(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return List.of();
        }

        List<WeightedKeyword> keywords = new ArrayList<>();
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement item : array) {
                WeightedKeyword keyword = parseWeightedKeyword(item);
                if (keyword != null) {
                    keywords.add(keyword);
                }
            }
            return keywords;
        }

        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            for (Map.Entry<String, JsonElement> item : object.entrySet()) {
                String token = item.getKey();
                double weight = item.getValue().isJsonPrimitive() && item.getValue().getAsJsonPrimitive().isNumber()
                    ? item.getValue().getAsDouble()
                    : 1.0d;
                if (token != null && !token.isBlank() && Double.isFinite(weight)) {
                    keywords.add(kw(token, weight));
                }
            }
            return keywords;
        }

        WeightedKeyword keyword = parseWeightedKeyword(element);
        if (keyword != null) {
            keywords.add(keyword);
        }
        return keywords;
    }

    private static WeightedKeyword parseWeightedKeyword(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonPrimitive()) {
            String token = element.getAsString();
            if (token == null || token.isBlank()) {
                return null;
            }
            return kw(token, 1.0d);
        }
        if (!element.isJsonObject()) {
            return null;
        }

        JsonObject object = element.getAsJsonObject();
        if (!object.has("token")) {
            return null;
        }
        String token = object.get("token").getAsString();
        double weight = object.has("weight") ? object.get("weight").getAsDouble() : 1.0d;
        if (token == null || token.isBlank() || !Double.isFinite(weight)) {
            return null;
        }
        return kw(token, weight);
    }

    private static String buildScoreDebugText(List<TemplateSelection> rankedCandidates, int limit) {
        if (rankedCandidates == null || rankedCandidates.isEmpty()) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        int count = Math.min(limit, rankedCandidates.size());
        for (int i = 0; i < count; i++) {
            TemplateSelection item = rankedCandidates.get(i);
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(item.kind().name().toLowerCase(Locale.ROOT)).append("=")
                .append(String.format(Locale.ROOT, "%.2f", item.score()));
        }
        return builder.toString();
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

    private static double weightedKeywordScore(String lowerPrompt, List<WeightedKeyword> weightedKeywords) {
        if (lowerPrompt == null || lowerPrompt.isBlank() || weightedKeywords == null || weightedKeywords.isEmpty()) {
            return 0.0d;
        }
        double score = 0.0d;
        for (WeightedKeyword keyword : weightedKeywords) {
            if (keyword == null || keyword.token() == null || keyword.token().isBlank()) {
                continue;
            }
            if (lowerPrompt.contains(keyword.token().toLowerCase(Locale.ROOT))) {
                score += keyword.weight();
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

    private static WeightedKeyword kw(String token, double weight) {
        return new WeightedKeyword(token, weight);
    }

    private static Map<MockTemplateKind, List<WeightedKeyword>> createPositiveKeywordTable() {
        Map<MockTemplateKind, List<WeightedKeyword>> map = new HashMap<>();
        map.put(MockTemplateKind.PLACEMENT, List.of(
            kw("place", 4.0d), kw("add", 3.0d), kw("insert", 2.8d), kw("create", 2.6d), kw("node", 2.0d), kw("selector", 3.0d), kw("selected block", 4.0d), kw("block selector", 4.0d),
            kw("放置", 4.0d), kw("添加", 3.0d), kw("插入", 2.8d), kw("创建", 2.6d), kw("节点", 2.0d), kw("选择节点", 4.0d), kw("方块选择", 4.0d), kw("选中方块", 3.2d)
        ));
        map.put(MockTemplateKind.HELIX_PATH, List.of(
            kw("helix", 3.2d), kw("spiral", 2.6d), kw("coil", 2.4d), kw("spring", 2.2d),
            kw("curve", 1.6d), kw("path", 1.8d), kw("road", 1.4d), kw("trail", 1.3d), kw("ramp", 1.4d),
            kw("螺旋", 3.0d), kw("曲线", 1.8d), kw("路径", 1.8d), kw("道路", 1.4d), kw("轨迹", 1.5d), kw("坡道", 1.4d)
        ));
        map.put(MockTemplateKind.BOX_FILL, List.of(
            kw("box", 2.4d), kw("cube", 2.2d), kw("room", 1.8d), kw("wall", 1.5d), kw("region", 1.8d),
            kw("fill", 2.4d), kw("volume", 1.6d), kw("cuboid", 2.0d), kw("block", 1.2d),
            kw("盒", 2.4d), kw("立方体", 2.4d), kw("区域", 1.8d), kw("填充", 2.4d), kw("体积", 1.6d), kw("房间", 1.8d), kw("墙", 1.4d)
        ));
        map.put(MockTemplateKind.SPHERE, List.of(
            kw("sphere", 3.0d), kw("ball", 2.4d), kw("orb", 2.2d), kw("dome", 1.9d), kw("planet", 1.8d), kw("bubble", 1.6d),
            kw("球", 2.8d), kw("球体", 2.8d), kw("穹顶", 1.9d), kw("圆球", 2.3d)
        ));
        map.put(MockTemplateKind.TOWER, List.of(
            kw("tower", 3.0d), kw("pillar", 2.1d), kw("column", 2.0d), kw("vertical", 1.4d),
            kw("skyscraper", 2.0d), kw("spire", 1.7d), kw("cylinder", 1.8d),
            kw("塔", 2.8d), kw("塔楼", 2.8d), kw("高塔", 2.8d), kw("柱", 1.8d), kw("圆柱", 2.1d)
        ));
        map.put(MockTemplateKind.ARCH_PATH, List.of(
            kw("arch", 3.0d), kw("archway", 2.6d), kw("bridge", 2.2d), kw("gate", 1.6d), kw("span", 1.4d), kw("vault", 1.5d),
            kw("拱", 2.8d), kw("拱门", 2.9d), kw("拱桥", 2.6d), kw("桥", 1.8d)
        ));
        map.put(MockTemplateKind.RING_WALKWAY, List.of(
            kw("ring", 3.0d), kw("torus", 2.8d), kw("loop", 2.2d), kw("circular", 1.8d), kw("circle", 1.7d), kw("walkway", 1.8d),
            kw("环", 2.8d), kw("圆环", 3.0d), kw("环形", 2.7d), kw("环道", 2.2d)
        ));
        map.put(MockTemplateKind.MULTI_LEVEL_PLATFORM, List.of(
            kw("multi", 1.4d), kw("multi-level", 2.6d), kw("level", 1.6d), kw("tier", 2.0d), kw("terrace", 1.8d),
            kw("platform", 2.6d), kw("stage", 1.6d), kw("floor", 1.4d),
            kw("多层", 2.8d), kw("平台", 2.6d), kw("台阶", 2.0d), kw("层级", 2.0d), kw("楼层", 1.8d)
        ));
        return map;
    }

    private static Map<MockTemplateKind, List<WeightedKeyword>> createNegativeKeywordTable() {
        Map<MockTemplateKind, List<WeightedKeyword>> map = new HashMap<>();
        map.put(MockTemplateKind.PLACEMENT, List.of(
            kw("sphere", 1.5d), kw("box", 1.5d), kw("tower", 1.2d), kw("helix", 1.2d), kw("arch", 1.1d), kw("ring", 1.1d)
        ));
        map.put(MockTemplateKind.HELIX_PATH, List.of(
            kw("sphere", 1.4d), kw("ball", 1.2d), kw("tower", 1.3d), kw("box", 1.2d), kw("cube", 1.2d), kw("region", 0.9d), kw("fill", 0.9d)
        ));
        map.put(MockTemplateKind.BOX_FILL, List.of(
            kw("helix", 1.4d), kw("spiral", 1.3d), kw("sphere", 1.3d), kw("ring", 1.3d), kw("torus", 1.4d), kw("arch", 1.2d)
        ));
        map.put(MockTemplateKind.SPHERE, List.of(
            kw("helix", 1.4d), kw("spiral", 1.3d), kw("box", 1.2d), kw("cube", 1.2d), kw("tower", 1.3d), kw("platform", 1.2d)
        ));
        map.put(MockTemplateKind.TOWER, List.of(
            kw("sphere", 1.4d), kw("ring", 1.3d), kw("torus", 1.4d), kw("arch", 1.2d), kw("bridge", 1.1d), kw("platform", 1.1d)
        ));
        map.put(MockTemplateKind.ARCH_PATH, List.of(
            kw("helix", 1.3d), kw("spiral", 1.2d), kw("sphere", 1.2d), kw("tower", 1.2d), kw("platform", 1.0d)
        ));
        map.put(MockTemplateKind.RING_WALKWAY, List.of(
            kw("helix", 1.4d), kw("spiral", 1.3d), kw("box", 1.2d), kw("cube", 1.2d), kw("arch", 1.1d), kw("tower", 1.1d)
        ));
        map.put(MockTemplateKind.MULTI_LEVEL_PLATFORM, List.of(
            kw("sphere", 1.3d), kw("helix", 1.2d), kw("spiral", 1.2d), kw("ring", 1.2d), kw("torus", 1.3d), kw("arch", 1.1d)
        ));
        return map;
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

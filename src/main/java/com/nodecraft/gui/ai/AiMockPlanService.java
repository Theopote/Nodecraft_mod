package com.nodecraft.gui.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.ai.model.AiGraphPlan;
import com.nodecraft.nodesystem.registry.NodeRegistry;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
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

    public static final String ABSTAIN_CODE = "local_planner_no_confident_plan";
    public static final String ABSTAIN_MESSAGE =
            "本地模式无法可靠规划这个请求。请使用 Remote Planner，或选择一个模板。";
    public static final String ABSTAIN_MOBIUS_MESSAGE =
            "本地模式无法可靠生成 Möbius。请使用 Remote Planner，或选择一个模板。";

    private static final double MIN_CONFIDENT_SCORE = 2.5d;

    public record MockPlan(
            String summary,
            List<MockNode> nodes,
            List<MockConnection> connections,
            List<String> validationErrors,
            boolean abstained,
            String abstainCode
    ) {
        public MockPlan(String summary, List<MockNode> nodes, List<MockConnection> connections, List<String> validationErrors) {
            this(summary, nodes, connections, validationErrors, false, null);
        }

        public boolean isValid() {
            return !abstained && (validationErrors == null || validationErrors.isEmpty());
        }

        static MockPlan abstain(String code, String message) {
            return new MockPlan(message, List.of(), List.of(), List.of(code), true, code);
        }
    }

    record ParsedParameters(double radius, double width, double thickness, double turns, double pitch, double height) {
    }

    enum MockTemplateKind {
        PLACEMENT,
        SPHERE,
        BOX_FILL,
        HELIX_PATH,
        TOWER,
        ARCH_PATH,
        RING_WALKWAY,
        MULTI_LEVEL_PLATFORM
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

        if (isMobiusPrompt(lowerPrompt)) {
            return MockPlan.abstain(ABSTAIN_CODE, ABSTAIN_MOBIUS_MESSAGE);
        }

        TemplateSelectionResult selectionResult = selectTemplateCandidates(lowerPrompt);
        List<TemplateSelection> candidates = selectionResult.topCandidates();
        if (candidates.isEmpty() || candidates.getFirst().score() < MIN_CONFIDENT_SCORE) {
            return MockPlan.abstain(ABSTAIN_CODE, ABSTAIN_MESSAGE);
        }

        MockTemplateKind selectedTemplate = null;
        MockTemplateKind attemptedFallback = candidates.size() > 1 ? candidates.get(1).kind() : null;
        boolean fallbackUsed = false;
        List<MockNode> nodes = List.of();
        List<MockConnection> connections = List.of();
        List<String> errors = List.of();
        List<String> lastValidationErrors = new ArrayList<>();

        for (int i = 0; i < Math.min(2, candidates.size()); i++) {
            TemplateSelection candidate = candidates.get(i);
            if (candidate.score() < MIN_CONFIDENT_SCORE && i > 0) {
                break;
            }
            List<MockNode> trialNodes = new ArrayList<>();
            List<MockConnection> trialConnections = new ArrayList<>();
            List<String> trialErrors = new ArrayList<>();

            buildTemplate(candidate.kind(), params, prompt, trialNodes, trialConnections);
            validatePlan(trialNodes, trialConnections, trialErrors);
            if (!trialErrors.isEmpty()) {
                lastValidationErrors = new ArrayList<>(trialErrors);
                continue;
            }
            if (!passesRegistryValidation(trialNodes, trialConnections, trialErrors)) {
                lastValidationErrors = new ArrayList<>(trialErrors);
                continue;
            }
            nodes = trialNodes;
            connections = trialConnections;
            errors = trialErrors;
            selectedTemplate = candidate.kind();
            fallbackUsed = i > 0;
            break;
        }

        if (selectedTemplate == null || nodes.isEmpty()) {
            if (!lastValidationErrors.isEmpty()) {
                NodeCraft.LOGGER.warn("[AI_MOCK] All candidates failed validation: {}", lastValidationErrors);
            }
            return MockPlan.abstain(ABSTAIN_CODE, ABSTAIN_MESSAGE);
        }

        String summary = buildAiPlanSummary(
                params, selectedTemplate, attemptedFallback, fallbackUsed, selectionResult.rankedCandidates());
        return new MockPlan(summary, nodes, connections, errors, false, null);
    }

    /** Package-visible for tests: primary ranked kind, or null when abstaining. */
    static MockTemplateKind selectPrimaryTemplateKind(String prompt) {
        ensureTemplateWeightsLoaded();
        String lowerPrompt = prompt == null ? "" : prompt.toLowerCase(Locale.ROOT);
        if (isMobiusPrompt(lowerPrompt)) {
            return null;
        }
        TemplateSelectionResult selectionResult = selectTemplateCandidates(lowerPrompt);
        List<TemplateSelection> candidates = selectionResult.topCandidates();
        if (candidates.isEmpty() || candidates.getFirst().score() < MIN_CONFIDENT_SCORE) {
            return null;
        }
        return candidates.getFirst().kind();
    }

    private static boolean isMobiusPrompt(String lowerPrompt) {
        return containsAny(lowerPrompt, "mobius", "möbius", "莫比乌斯");
    }

    private static boolean isExplicitPlacementIntent(String lowerPrompt) {
        if (containsAny(lowerPrompt,
                "选择节点", "方块选择", "selected block", "block selector", "选中方块",
                "生成一个节点", "添加一个节点", "放置节点", "插入节点", "放一个节点")) {
            return true;
        }
        boolean action = containsAny(lowerPrompt, "place", "add", "create", "insert", "放置", "添加", "插入", "放一个");
        boolean context = containsAny(lowerPrompt, "node", "nodes", "canvas", "selector", "节点", "画布");
        return action && context;
    }

    private static void buildTemplate(
            MockTemplateKind kind,
            ParsedParameters params,
            String prompt,
            List<MockNode> nodes,
            List<MockConnection> connections
    ) {
        if (applyExternalTemplate(kind, nodes, connections)) {
            return;
        }

        switch (kind) {
            case PLACEMENT -> buildPlacementTemplate(nodes, connections);
            case HELIX_PATH -> buildHelixPathTemplate(params, prompt, nodes, connections);
            case BOX_FILL -> buildBoxFillTemplate(params, prompt, nodes, connections);
            case TOWER -> buildTowerTemplate(params, prompt, nodes, connections);
            case ARCH_PATH -> buildArchPathTemplate(params, prompt, nodes, connections);
            case RING_WALKWAY -> buildRingWalkwayTemplate(params, prompt, nodes, connections);
            case MULTI_LEVEL_PLATFORM -> buildMultiLevelPlatformTemplate(params, prompt, nodes, connections);
            case SPHERE -> buildSphereTemplate(params, prompt, nodes, connections);
        }
    }

    private static void buildPlacementTemplate(List<MockNode> nodes, List<MockConnection> connections) {
        nodes.add(new MockNode("selected_block", "world.selection.selected_block", 0.0f, 0.0f,
                createNodeState("showLabel", true)));
    }

    private static void buildSphereTemplate(
            ParsedParameters params,
            String prompt,
            List<MockNode> nodes,
            List<MockConnection> connections
    ) {
        nodes.add(new MockNode("center", "reference.points.block_position", -520.0f, -120.0f,
                createNodeState("x", 0, "y", 80, "z", 0, "showLabel", true)));
        nodes.add(new MockNode("radius", "input.numeric.float", -520.0f, 40.0f,
                createNodeState("value", (float) params.radius(), "min", 1.0f, "max", 2048.0f, "precision", 2)));
        nodes.add(new MockNode("sphere", "geometry.primitives.sphere", -180.0f, -20.0f, null));

        connections.add(new MockConnection("center", "output_coordinate", "sphere", "input_center"));
        connections.add(new MockConnection("radius", "output_value", "sphere", "input_radius"));
        appendSolidPreviewChain(nodes, connections, "sphere", prompt, params.thickness() >= 1.0d);
    }

    private static void buildBoxFillTemplate(
            ParsedParameters params,
            String prompt,
            List<MockNode> nodes,
            List<MockConnection> connections
    ) {
        int sizeX = clampInt((int) Math.round(params.radius() * 2.0d), 4, 256);
        int sizeY = clampInt((int) Math.round(params.height()), 4, 256);
        int sizeZ = clampInt((int) Math.round(Math.max(params.radius() * 1.8d, params.width() * 4.0d)), 4, 256);

        nodes.add(new MockNode("center", "reference.points.block_position", -660.0f, -120.0f,
                createNodeState("x", 0, "y", 72, "z", 0, "showLabel", true)));
        nodes.add(new MockNode("size_x", "input.numeric.integer", -660.0f, 60.0f,
                createNodeState("value", sizeX, "min", 1, "max", 1024, "step", 1)));
        nodes.add(new MockNode("size_y", "input.numeric.integer", -660.0f, 220.0f,
                createNodeState("value", sizeY, "min", 1, "max", 512, "step", 1)));
        nodes.add(new MockNode("size_z", "input.numeric.integer", -660.0f, 380.0f,
                createNodeState("value", sizeZ, "min", 1, "max", 1024, "step", 1)));
        nodes.add(new MockNode("box", "geometry.primitives.box", -280.0f, 180.0f, null));

        connections.add(new MockConnection("center", "output_coordinate", "box", "input_center"));
        connections.add(new MockConnection("size_x", "output_value", "box", "input_size_x"));
        connections.add(new MockConnection("size_y", "output_value", "box", "input_size_y"));
        connections.add(new MockConnection("size_z", "output_value", "box", "input_size_z"));
        appendSolidPreviewChain(nodes, connections, "box", prompt, true);
    }

    private static void buildHelixPathTemplate(
            ParsedParameters params,
            String prompt,
            List<MockNode> nodes,
            List<MockConnection> connections
    ) {
        int segmentsPerTurn = clampInt((int) Math.round(Math.max(12.0d, params.width() * 8.0d)), 12, 96);
        float profileRadius = (float) Math.max(0.6d, Math.min(2.5d, params.thickness()));

        nodes.add(new MockNode("center", "reference.points.block_position", -1000.0f, -180.0f,
                createNodeState("x", 0, "y", 72, "z", 0, "showLabel", true)));
        nodes.add(new MockNode("axis", "reference.vectors.vector", -1000.0f, 40.0f,
                createNodeState("x", 0.0d, "y", 1.0d, "z", 0.0d, "showLabel", false, "precision", 2)));
        nodes.add(new MockNode("radius", "input.numeric.float", -1000.0f, 220.0f,
                createNodeState("value", (float) params.radius(), "min", 1.0f, "max", 2048.0f, "precision", 2)));
        nodes.add(new MockNode("pitch", "input.numeric.float", -1000.0f, 380.0f,
                createNodeState("value", (float) params.pitch(), "min", 0.2f, "max", 256.0f, "precision", 2)));
        nodes.add(new MockNode("turns", "input.numeric.float", -760.0f, 380.0f,
                createNodeState("value", (float) params.turns(), "min", 0.5f, "max", 128.0f, "precision", 2)));
        nodes.add(new MockNode("segments", "input.numeric.integer", -760.0f, 220.0f,
                createNodeState("value", segmentsPerTurn, "min", 6, "max", 128, "step", 1)));
        nodes.add(new MockNode("profile_radius", "input.numeric.float", -760.0f, 40.0f,
                createNodeState("value", profileRadius, "min", 0.25f, "max", 8.0f, "precision", 2)));
        nodes.add(new MockNode("helix", "geometry.curves.helix", -520.0f, 280.0f, null));
        nodes.add(new MockNode("path_preview", "output.preview.preview_curves", -260.0f, 280.0f,
                createNodeState("previewEnabled", true, "pathColor", "#FFD933", "lineWidth", 1.8f, "showDirection", true)));
        nodes.add(new MockNode("profile", "geometry.profiles.circle_profile", -520.0f, 40.0f, null));
        nodes.add(new MockNode("sweep", "geometry.solids.sweep", 40.0f, 160.0f,
                createNodeState("orientToPath", true, "closeProfile", true)));

        connections.add(new MockConnection("center", "output_coordinate", "helix", "input_center"));
        connections.add(new MockConnection("axis", "output_vector", "helix", "input_axis"));
        connections.add(new MockConnection("radius", "output_value", "helix", "input_radius"));
        connections.add(new MockConnection("pitch", "output_value", "helix", "input_pitch"));
        connections.add(new MockConnection("turns", "output_value", "helix", "input_turns"));
        connections.add(new MockConnection("segments", "output_value", "helix", "input_segments_per_turn"));
        connections.add(new MockConnection("helix", "output_path", "path_preview", "input_path"));
        connections.add(new MockConnection("center", "output_coordinate", "profile", "input_center"));
        connections.add(new MockConnection("profile_radius", "output_value", "profile", "input_radius"));
        connections.add(new MockConnection("profile", "output_profile", "sweep", "input_profile"));
        connections.add(new MockConnection("helix", "output_path", "sweep", "input_path"));
        appendSweepPreviewChain(nodes, connections, "sweep", prompt);
    }

    private static void buildTowerTemplate(
            ParsedParameters params,
            String prompt,
            List<MockNode> nodes,
            List<MockConnection> connections
    ) {
        int baseY = 72;
        int gap = clampInt((int) Math.round(Math.max(6.0d, params.height() * 0.28d)), 6, 48);
        int baseSize = clampInt((int) Math.round(Math.max(8.0d, params.radius() * 0.9d)), 6, 64);
        int midSize = clampInt(baseSize - 2, 4, 56);
        int topSize = clampInt(midSize - 2, 3, 48);
        int slab = clampInt((int) Math.round(Math.max(2.0d, params.thickness())), 1, 12);

        nodes.add(new MockNode("base_center", "reference.points.block_position", -900.0f, -160.0f,
                createNodeState("x", 0, "y", baseY, "z", 0, "showLabel", true)));
        nodes.add(new MockNode("mid_center", "reference.points.block_position", -900.0f, 20.0f,
                createNodeState("x", 0, "y", baseY + gap, "z", 0, "showLabel", false)));
        nodes.add(new MockNode("top_center", "reference.points.block_position", -900.0f, 200.0f,
                createNodeState("x", 0, "y", baseY + gap * 2, "z", 0, "showLabel", false)));
        nodes.add(new MockNode("sx0", "input.numeric.integer", -700.0f, -200.0f,
                createNodeState("value", baseSize, "min", 2, "max", 128, "step", 1)));
        nodes.add(new MockNode("sx1", "input.numeric.integer", -700.0f, 0.0f,
                createNodeState("value", midSize, "min", 2, "max", 128, "step", 1)));
        nodes.add(new MockNode("sx2", "input.numeric.integer", -700.0f, 200.0f,
                createNodeState("value", topSize, "min", 2, "max", 128, "step", 1)));
        nodes.add(new MockNode("sy", "input.numeric.integer", -700.0f, 360.0f,
                createNodeState("value", slab, "min", 1, "max", 32, "step", 1)));
        nodes.add(new MockNode("base_box", "geometry.primitives.box", -420.0f, -120.0f, null));
        nodes.add(new MockNode("mid_box", "geometry.primitives.box", -420.0f, 60.0f, null));
        nodes.add(new MockNode("top_box", "geometry.primitives.box", -420.0f, 240.0f, null));
        nodes.add(new MockNode("union", "geometry.combine.geometry", -120.0f, 80.0f,
                createNodeState("inputCount", 3)));

        connections.add(new MockConnection("base_center", "output_coordinate", "base_box", "input_center"));
        connections.add(new MockConnection("sx0", "output_value", "base_box", "input_size_x"));
        connections.add(new MockConnection("sy", "output_value", "base_box", "input_size_y"));
        connections.add(new MockConnection("sx0", "output_value", "base_box", "input_size_z"));
        connections.add(new MockConnection("mid_center", "output_coordinate", "mid_box", "input_center"));
        connections.add(new MockConnection("sx1", "output_value", "mid_box", "input_size_x"));
        connections.add(new MockConnection("sy", "output_value", "mid_box", "input_size_y"));
        connections.add(new MockConnection("sx1", "output_value", "mid_box", "input_size_z"));
        connections.add(new MockConnection("top_center", "output_coordinate", "top_box", "input_center"));
        connections.add(new MockConnection("sx2", "output_value", "top_box", "input_size_x"));
        connections.add(new MockConnection("sy", "output_value", "top_box", "input_size_y"));
        connections.add(new MockConnection("sx2", "output_value", "top_box", "input_size_z"));
        connections.add(new MockConnection("base_box", "output_geometry", "union", "input_geometry_0"));
        connections.add(new MockConnection("mid_box", "output_geometry", "union", "input_geometry_1"));
        connections.add(new MockConnection("top_box", "output_geometry", "union", "input_geometry_2"));
        appendSolidPreviewChain(nodes, connections, "union", prompt, true);
    }

    private static void buildArchPathTemplate(
            ParsedParameters params,
            String prompt,
            List<MockNode> nodes,
            List<MockConnection> connections
    ) {
        int segments = clampInt((int) Math.round(Math.max(16.0d, params.width() * 10.0d)), 16, 120);
        float profileRadius = (float) Math.max(0.8d, Math.min(3.0d, params.thickness()));

        nodes.add(new MockNode("arch_center", "reference.points.block_position", -980.0f, -160.0f,
                createNodeState("x", 0, "y", 72, "z", 0, "showLabel", true)));
        nodes.add(new MockNode("arch_normal", "reference.vectors.vector", -980.0f, 20.0f,
                createNodeState("x", 0.0d, "y", 0.0d, "z", 1.0d, "showLabel", false, "precision", 2)));
        nodes.add(new MockNode("arch_radius", "input.numeric.float", -980.0f, 200.0f,
                createNodeState("value", (float) Math.max(4.0d, params.radius()), "min", 1.0f, "max", 512.0f, "precision", 2)));
        nodes.add(new MockNode("arch_start", "input.numeric.float", -980.0f, 360.0f,
                createNodeState("value", 180.0f, "min", -360.0f, "max", 360.0f, "precision", 1, "showLabel", false)));
        nodes.add(new MockNode("arch_end", "input.numeric.float", -760.0f, 360.0f,
                createNodeState("value", 0.0f, "min", -360.0f, "max", 360.0f, "precision", 1, "showLabel", false)));
        nodes.add(new MockNode("arch_segments", "input.numeric.integer", -760.0f, 200.0f,
                createNodeState("value", segments, "min", 8, "max", 256, "step", 1)));
        nodes.add(new MockNode("profile_radius", "input.numeric.float", -760.0f, 20.0f,
                createNodeState("value", profileRadius, "min", 0.25f, "max", 8.0f, "precision", 2)));
        nodes.add(new MockNode("arch_curve", "geometry.curves.arc", -540.0f, 200.0f, null));
        nodes.add(new MockNode("path_preview", "output.preview.preview_curves", -280.0f, 200.0f,
                createNodeState("previewEnabled", true, "pathColor", "#FFD933", "lineWidth", 1.8f, "showDirection", false)));
        nodes.add(new MockNode("profile", "geometry.profiles.circle_profile", -540.0f, 20.0f, null));
        nodes.add(new MockNode("sweep", "geometry.solids.sweep", 20.0f, 120.0f,
                createNodeState("orientToPath", true, "closeProfile", true)));

        connections.add(new MockConnection("arch_center", "output_coordinate", "arch_curve", "input_center"));
        connections.add(new MockConnection("arch_normal", "output_vector", "arch_curve", "input_normal"));
        connections.add(new MockConnection("arch_radius", "output_value", "arch_curve", "input_radius"));
        connections.add(new MockConnection("arch_start", "output_value", "arch_curve", "input_start_angle"));
        connections.add(new MockConnection("arch_end", "output_value", "arch_curve", "input_end_angle"));
        connections.add(new MockConnection("arch_segments", "output_value", "arch_curve", "input_resolution"));
        connections.add(new MockConnection("arch_curve", "output_path", "path_preview", "input_path"));
        connections.add(new MockConnection("arch_center", "output_coordinate", "profile", "input_center"));
        connections.add(new MockConnection("profile_radius", "output_value", "profile", "input_radius"));
        connections.add(new MockConnection("profile", "output_profile", "sweep", "input_profile"));
        connections.add(new MockConnection("arch_curve", "output_path", "sweep", "input_path"));
        appendSweepPreviewChain(nodes, connections, "sweep", prompt);
    }

    private static void buildRingWalkwayTemplate(
            ParsedParameters params,
            String prompt,
            List<MockNode> nodes,
            List<MockConnection> connections
    ) {
        float outerRadius = (float) Math.max(6.0d, params.radius());
        float walkwayWidth = (float) Math.max(1.5d, Math.min(8.0d, params.width()));
        float innerRadius = Math.max(1.0f, outerRadius - walkwayWidth);
        float thickness = (float) Math.max(0.8d, Math.min(4.0d, params.thickness()));

        nodes.add(new MockNode("center", "reference.points.block_position", -800.0f, -80.0f,
                createNodeState("x", 0, "y", 72, "z", 0, "showLabel", true)));
        nodes.add(new MockNode("outer_r", "input.numeric.float", -800.0f, 80.0f,
                createNodeState("value", outerRadius, "min", 2.0f, "max", 1024.0f, "precision", 2)));
        nodes.add(new MockNode("inner_r", "input.numeric.float", -800.0f, 220.0f,
                createNodeState("value", innerRadius, "min", 0.5f, "max", 1024.0f, "precision", 2)));
        nodes.add(new MockNode("up", "reference.vectors.vector", -800.0f, 360.0f,
                createNodeState("x", 0.0d, "y", thickness, "z", 0.0d, "showLabel", false, "precision", 2)));
        nodes.add(new MockNode("outer", "geometry.profiles.circle_profile", -480.0f, 40.0f, null));
        nodes.add(new MockNode("inner", "geometry.profiles.circle_profile", -480.0f, 220.0f, null));
        nodes.add(new MockNode("ring_region", "geometry.profiles.boolean_2d", -200.0f, 120.0f,
                createNodeState("operation", "DIFFERENCE")));
        nodes.add(new MockNode("extrude", "geometry.solids.extrude_region", 80.0f, 120.0f, null));

        connections.add(new MockConnection("center", "output_coordinate", "outer", "input_center"));
        connections.add(new MockConnection("outer_r", "output_value", "outer", "input_radius"));
        connections.add(new MockConnection("center", "output_coordinate", "inner", "input_center"));
        connections.add(new MockConnection("inner_r", "output_value", "inner", "input_radius"));
        connections.add(new MockConnection("outer", "output_profile", "ring_region", "input_profile_a"));
        connections.add(new MockConnection("inner", "output_profile", "ring_region", "input_profile_b"));
        connections.add(new MockConnection("ring_region", "output_region", "extrude", "input_region"));
        connections.add(new MockConnection("up", "output_vector", "extrude", "input_direction"));
        appendSolidPreviewChain(nodes, connections, "extrude", prompt, true);
    }

    private static void buildMultiLevelPlatformTemplate(
            ParsedParameters params,
            String prompt,
            List<MockNode> nodes,
            List<MockConnection> connections
    ) {
        int baseSize = clampInt((int) Math.round(Math.max(10.0d, params.radius() * 1.6d)), 8, 180);
        int middleSize = clampInt(baseSize - 4, 6, 160);
        int topSize = clampInt(middleSize - 4, 4, 140);
        int thickness = clampInt((int) Math.round(Math.max(2.0d, params.thickness() * 2.0d)), 1, 16);
        int gap = clampInt((int) Math.round(Math.max(4.0d, params.height() * 0.25d)), 3, 48);

        nodes.add(new MockNode("level0_center", "reference.points.block_position", -1120.0f, -180.0f,
                createNodeState("x", 0, "y", 70, "z", 0, "showLabel", true)));
        nodes.add(new MockNode("level1_center", "reference.points.block_position", -1120.0f, 0.0f,
                createNodeState("x", 0, "y", 70 + gap, "z", 0, "showLabel", false)));
        nodes.add(new MockNode("level2_center", "reference.points.block_position", -1120.0f, 180.0f,
                createNodeState("x", 0, "y", 70 + gap * 2, "z", 0, "showLabel", false)));
        nodes.add(new MockNode("sx0", "input.numeric.integer", -900.0f, -230.0f,
                createNodeState("value", baseSize, "min", 2, "max", 256, "step", 1, "showLabel", false)));
        nodes.add(new MockNode("sz0", "input.numeric.integer", -900.0f, -120.0f,
                createNodeState("value", baseSize, "min", 2, "max", 256, "step", 1, "showLabel", false)));
        nodes.add(new MockNode("sx1", "input.numeric.integer", -900.0f, -10.0f,
                createNodeState("value", middleSize, "min", 2, "max", 256, "step", 1, "showLabel", false)));
        nodes.add(new MockNode("sz1", "input.numeric.integer", -900.0f, 100.0f,
                createNodeState("value", middleSize, "min", 2, "max", 256, "step", 1, "showLabel", false)));
        nodes.add(new MockNode("sx2", "input.numeric.integer", -900.0f, 210.0f,
                createNodeState("value", topSize, "min", 2, "max", 256, "step", 1, "showLabel", false)));
        nodes.add(new MockNode("sz2", "input.numeric.integer", -900.0f, 320.0f,
                createNodeState("value", topSize, "min", 2, "max", 256, "step", 1, "showLabel", false)));
        nodes.add(new MockNode("sy", "input.numeric.integer", -900.0f, 430.0f,
                createNodeState("value", thickness, "min", 1, "max", 32, "step", 1, "showLabel", false)));
        nodes.add(new MockNode("box0", "geometry.primitives.box", -620.0f, -120.0f, null));
        nodes.add(new MockNode("box1", "geometry.primitives.box", -620.0f, 80.0f, null));
        nodes.add(new MockNode("box2", "geometry.primitives.box", -620.0f, 280.0f, null));
        nodes.add(new MockNode("union", "geometry.combine.geometry", -320.0f, 120.0f,
                createNodeState("inputCount", 3)));

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
        appendSolidPreviewChain(nodes, connections, "union", prompt, true);
    }

    private static void appendSolidPreviewChain(
            List<MockNode> nodes,
            List<MockConnection> connections,
            String geometryRef,
            String prompt,
            boolean fillGeometry
    ) {
        nodes.add(new MockNode("bake", "geometry.voxel.voxelize_geometry", 200.0f, 80.0f,
                createNodeState("fillGeometry", fillGeometry)));
        connections.add(new MockConnection(geometryRef, "output_geometry", "bake", "input_geometry"));
        appendBlocksMaterialPreviewChain(nodes, connections, "bake", "output_blocks", prompt);
    }

    private static void appendSweepPreviewChain(
            List<MockNode> nodes,
            List<MockConnection> connections,
            String sweepRef,
            String prompt
    ) {
        nodes.add(new MockNode("strip_blocks", "geometry.voxel.surface_strip_to_blocks", 200.0f, 80.0f,
                createNodeState("mode", "LATTICE")));
        connections.add(new MockConnection(sweepRef, "output_surface_strip", "strip_blocks", "input_surface_strip"));
        appendBlocksMaterialPreviewChain(nodes, connections, "strip_blocks", "output_blocks", prompt);
    }

    private static void appendBlocksMaterialPreviewChain(
            List<MockNode> nodes,
            List<MockConnection> connections,
            String blocksSourceRef,
            String blocksSourcePort,
            String prompt
    ) {
        nodes.add(new MockNode("block_type", "input.type_selectors.block_type_selector", 420.0f, -80.0f,
                createNodeState("selectedBlock", "minecraft:stone")));
        nodes.add(new MockNode("assign", "material.basic_assignment.assign_block_type", 420.0f, 80.0f, null));
        nodes.add(new MockNode("preview", "output.preview.preview_blocks", 680.0f, 40.0f,
                createNodeState("previewEnabled", true)));

        connections.add(new MockConnection(blocksSourceRef, blocksSourcePort, "assign", "input_coordinates"));
        connections.add(new MockConnection("block_type", "output_block_id", "assign", "input_block_type"));
        connections.add(new MockConnection("assign", "output_placements", "preview", "input_block_placements"));

        if (AiIntentAnalysisService.hasWorldApplyIntent(prompt)) {
            nodes.add(new MockNode("apply", "output.execute.apply_changes", 680.0f, 220.0f,
                    createNodeState("recordUndo", true, "useAsyncBake", true)));
            connections.add(new MockConnection("assign", "output_placements", "apply", "input_block_placements"));
        }
    }

    private static boolean passesRegistryValidation(
            List<MockNode> nodes,
            List<MockConnection> connections,
            List<String> errors
    ) {
        try {
            NodeRegistry registry = NodeRegistry.getInstance();
            if (registry == null) {
                return true;
            }
            boolean anyKnownType = false;
            for (MockNode node : nodes) {
                if (node == null || node.typeId() == null || node.typeId().isBlank()) {
                    continue;
                }
                if (registry.createNodeInstance(node.typeId()) != null) {
                    anyKnownType = true;
                    break;
                }
            }
            // Unit / early-bootstrap contexts may lack a populated registry.
            if (!anyKnownType) {
                return true;
            }

            AiGraphPlan plan = AiGraphPlanDslAdapterService.fromMockPlan(
                    new MockPlan("validate", nodes, connections, List.of()));
            AiGraphDslSupport.PlanValidationResult result =
                    AiGraphDslSupport.validatePlan(plan, registry);
            if (result == null || result.errors() == null || result.errors().isEmpty()) {
                return true;
            }
            errors.addAll(result.errors());
            NodeCraft.LOGGER.warn("[AI_MOCK] Registry validation failed for mock candidate: {}", result.errors());
            return false;
        } catch (Exception e) {
            NodeCraft.LOGGER.debug("AiMockPlanService: registry validation skipped: {}", e.toString());
            return true;
        }
    }

    private static TemplateSelectionResult selectTemplateCandidates(String lowerPrompt) {
        if (isExplicitPlacementIntent(lowerPrompt)) {
            List<TemplateSelection> ranked = List.of(
                    new TemplateSelection(MockTemplateKind.PLACEMENT, 1000.0d)
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
            if (candidate.score() >= MIN_CONFIDENT_SCORE) {
                top.add(candidate);
            }
            if (top.size() >= 2) {
                break;
            }
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

    private static String buildAiPlanSummary(
        ParsedParameters params,
        MockTemplateKind templateKind,
        MockTemplateKind fallbackTemplate,
        boolean fallbackUsed,
        List<TemplateSelection> rankedCandidates
    ) {
        String templateName = templateKind == null ? "none" : switch (templateKind) {
            case PLACEMENT -> "placement";
            case SPHERE -> "sphere";
            case BOX_FILL -> "box_fill";
            case HELIX_PATH -> "helix_path";
            case TOWER -> "tower";
            case ARCH_PATH -> "arch_path";
            case RING_WALKWAY -> "ring_walkway";
            case MULTI_LEVEL_PLATFORM -> "multi_level_platform";
        };
        String fallbackName = fallbackTemplate == null ? "none" : switch (fallbackTemplate) {
            case PLACEMENT -> "placement";
            case SPHERE -> "sphere";
            case BOX_FILL -> "box_fill";
            case HELIX_PATH -> "helix_path";
            case TOWER -> "tower";
            case ARCH_PATH -> "arch_path";
            case RING_WALKWAY -> "ring_walkway";
            case MULTI_LEVEL_PLATFORM -> "multi_level_platform";
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
                    weightsSource = "file:" + overridePath;
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
            kw("selected block", 4.0d), kw("block selector", 4.0d), kw("selector", 2.5d),
            kw("place a node", 5.0d), kw("add node", 5.0d), kw("insert node", 5.0d),
            kw("选择节点", 4.0d), kw("方块选择", 4.0d), kw("选中方块", 3.2d),
            kw("放置节点", 5.0d), kw("添加一个节点", 5.0d), kw("插入节点", 5.0d),
            kw("canvas", 1.5d), kw("画布", 1.5d), kw("节点", 1.2d)
        ));
        map.put(MockTemplateKind.HELIX_PATH, List.of(
            kw("helix", 3.2d), kw("spiral", 2.6d), kw("coil", 2.4d), kw("spring", 2.2d),
            kw("curve", 1.6d), kw("path", 1.8d), kw("road", 1.4d), kw("trail", 1.3d), kw("ramp", 1.4d),
            kw("螺旋", 3.0d), kw("曲线", 1.8d), kw("路径", 1.8d), kw("道路", 1.4d), kw("轨迹", 1.5d), kw("坡道", 1.4d)
        ));
        map.put(MockTemplateKind.BOX_FILL, List.of(
            kw("box", 2.4d), kw("cube", 2.2d), kw("room", 1.8d), kw("wall", 1.5d), kw("region", 1.8d),
            kw("fill", 2.4d), kw("volume", 1.6d), kw("cuboid", 2.0d), kw("block", 1.2d),
            kw("盒子", 2.4d), kw("立方体", 2.4d), kw("区域", 1.8d), kw("填充", 2.4d), kw("体积", 1.6d), kw("房间", 1.8d), kw("方块", 1.4d)
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

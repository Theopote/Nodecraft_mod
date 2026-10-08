package com.nodecraft.nodesystem.contract;

import com.nodecraft.gui.ai.AiIntentAnalysisService;
import com.nodecraft.gui.ai.AiNodeSchemaCatalog;
import com.nodecraft.gui.ai.AiPromptBuilder;
import com.nodecraft.gui.ai.AiRemotePlanningOrchestrator;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Schema retrieval must combine lexical seeds with Suggested Connections neighbors,
 * keep Always Include intent-aware, and default to Preview-first prompting.
 */
class AiSchemaRetrievalContractTest {

    private static final String WALL_SLAB = "geometry.architectural_primitives.wall_slab";
    private static final String WINDOW_ARRAY = "geometry.architectural_primitives.window_array";
    private static final String DIFFERENCE = "geometry.boolean.difference";
    private static final String PREVIEW_GEOMETRY = "output.preview.preview_geometry";

    private static NodeRegistry registry;
    private static List<AiNodeSchemaCatalog.NodeSchema> allSchemas;

    @BeforeAll
    static void ensureRegistry() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
        allSchemas = AiNodeSchemaCatalog.collectAll(registry);
    }

    @Test
    void wallWithWindowsSliceIncludesDifferenceAndPreview() {
        List<AiNodeSchemaCatalog.NodeSchema> slice = AiNodeSchemaCatalog.selectRelevant(
                allSchemas,
                "做一面带窗户的墙",
                36
        );
        Set<String> typeIds = typeIds(slice);

        assertTrue(typeIds.contains(WALL_SLAB) || typeIds.stream().anyMatch(id -> id.contains("wall")),
                "slice should include a wall node: " + typeIds);
        assertTrue(typeIds.contains(WINDOW_ARRAY) || typeIds.stream().anyMatch(id -> id.contains("window")),
                "slice should include a window node: " + typeIds);
        assertTrue(typeIds.contains(DIFFERENCE),
                "semantic expansion must pull Difference for window openings: " + typeIds);
        assertTrue(typeIds.contains(PREVIEW_GEOMETRY) || typeIds.stream().anyMatch(id -> id.startsWith("output.preview.")),
                "slice should keep a preview sink: " + typeIds);
    }

    @Test
    void wallWithWindowsHintsExposeDifferenceEdge() {
        List<AiNodeSchemaCatalog.NodeSchema> slice = AiNodeSchemaCatalog.selectRelevant(
                allSchemas,
                "做一面带窗户的墙",
                72
        );
        AiNodeSchemaCatalog.NodeSchema window = slice.stream()
                .filter(s -> WINDOW_ARRAY.equals(s.typeId()))
                .findFirst()
                .orElse(null);
        if (window == null) {
            // Still require Difference in the slice even if Window Array lost the lexical race.
            assertTrue(typeIds(slice).contains(DIFFERENCE));
            return;
        }
        assertTrue(window.recommendedNext().stream().anyMatch(h -> DIFFERENCE.equals(h.nodeId())),
                "Window Array should advertise Difference via recommendedNext: " + window.recommendedNext());
    }

    @Test
    void architecturePromptDoesNotFloodScalarMath() {
        List<AiNodeSchemaCatalog.NodeSchema> slice = AiNodeSchemaCatalog.selectRelevant(
                allSchemas,
                "做一座带窗户的塔",
                36
        );
        long scalarMath = slice.stream()
                .filter(s -> s.typeId() != null && s.typeId().startsWith("math.scalar_math."))
                .count();
        long worldWrite = slice.stream()
                .filter(s -> s.typeId() != null && s.typeId().startsWith("world.write."))
                .count();
        long execute = slice.stream()
                .filter(s -> s.typeId() != null && s.typeId().startsWith("output.execute."))
                .count();

        assertTrue(scalarMath <= 2, "scalar_math should not always-include: " + scalarMath);
        assertTrue(worldWrite == 0, "world.write should not always-include for architecture prompt: " + worldWrite);
        assertTrue(execute == 0, "output.execute should not always-include for architecture prompt: " + execute);
        assertTrue(typeIds(slice).contains(DIFFERENCE), "Difference must still fit under the cap: " + typeIds(slice));
    }

    @Test
    void mathPromptIncludesScalarMath() {
        List<AiNodeSchemaCatalog.NodeSchema> slice = AiNodeSchemaCatalog.selectRelevant(
                allSchemas,
                "用数学节点计算并 remap 一个数值",
                36
        );
        assertTrue(slice.stream().anyMatch(s -> s.typeId() != null && s.typeId().startsWith("math.scalar_math.")),
                "math domain tag should include scalar_math: " + typeIds(slice));
    }

    @Test
    void worldApplyPromptIncludesExecuteOrWrite() {
        assertTrue(AiIntentAnalysisService.hasWorldApplyIntent("把结果应用到世界"));
        List<AiNodeSchemaCatalog.NodeSchema> slice = AiNodeSchemaCatalog.selectRelevant(
                allSchemas,
                "生成一个盒子并应用到世界",
                36
        );
        assertTrue(slice.stream().anyMatch(s ->
                        s.typeId() != null
                                && (s.typeId().startsWith("world.write.")
                                || s.typeId().startsWith("output.execute."))),
                "explicit world-apply intent should recall write/execute: " + typeIds(slice));
    }

    @Test
    void systemPromptIsPreviewFirst() {
        List<AiNodeSchemaCatalog.NodeSchema> slice = AiNodeSchemaCatalog.selectRelevant(
                allSchemas,
                "生成一个球体",
                36
        );
        String prompt = AiPromptBuilder.buildSystemPrompt(slice);
        String lower = prompt.toLowerCase(Locale.ROOT);

        assertTrue(prompt.contains("output.preview.*"));
        assertTrue(lower.contains("explicitly") || prompt.contains("明确"));
        assertTrue(prompt.contains("recommendedNext") || slice.stream().anyMatch(s ->
                s.recommendedNext() != null && !s.recommendedNext().isEmpty()));
        assertFalse(prompt.contains("usually output.preview.* or output.execute.*"));
    }

    @Test
    void expansionHintIsPreviewFirst() throws Exception {
        var field = AiRemotePlanningOrchestrator.class.getDeclaredField("CONNECTED_GRAPH_EXPANSION_HINT");
        field.setAccessible(true);
        String hint = (String) field.get(null);
        assertTrue(hint.contains("output.preview.*"));
        assertTrue(hint.toLowerCase(Locale.ROOT).contains("explicitly"));
        assertFalse(hint.contains("Use output.preview.* or output.execute.* nodes when compatible"));
    }

    private static Set<String> typeIds(List<AiNodeSchemaCatalog.NodeSchema> schemas) {
        return schemas.stream()
                .map(AiNodeSchemaCatalog.NodeSchema::typeId)
                .collect(Collectors.toSet());
    }
}

package com.nodecraft.nodesystem.contract;

import com.nodecraft.gui.ai.AiGraphConversionRewriter;
import com.nodecraft.gui.ai.AiGraphDslSupport;
import com.nodecraft.gui.ai.AiNodeSchemaCatalog;
import com.nodecraft.gui.ai.AiPlanValidator;
import com.nodecraft.gui.ai.AiPromptBuilder;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.TypeConversionRegistry;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Batch 15 freeze: AI graph authoring honors NodeCraft language v1 + conversion bridges.
 */
class AiAuthoringLanguageContractTest {

    private static NodeRegistry registry;
    private static AiPlanValidator validator;

    @BeforeAll
    static void ensureRegistry() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
        validator = new AiPlanValidator(registry);
    }

    @Test
    void systemPromptEncodesLanguageV1Rules() {
        List<AiNodeSchemaCatalog.NodeSchema> schemas = AiNodeSchemaCatalog.collectAll(registry);
        String prompt = AiPromptBuilder.buildSystemPrompt(schemas);

        assertTrue(prompt.contains("NODECRAFT_LANGUAGE_V1"));
        assertTrue(prompt.toLowerCase(Locale.ROOT).contains("degrees"));
        assertTrue(prompt.contains("Never emit radians") || prompt.contains("not radians"));
        assertTrue(prompt.contains("EXEC"));
        assertTrue(prompt.contains("Graft List") || prompt.contains("LIST and DATA_TREE"));
        assertTrue(prompt.contains("PATH"));
    }

    @Test
    void schemaCatalogAlwaysIncludesConversionBridges() {
        List<AiNodeSchemaCatalog.NodeSchema> schemas = AiNodeSchemaCatalog.collectAll(registry);
        assertTrue(schemas.stream().anyMatch(s -> "math.data_tree.graft_list".equals(s.typeId())));
        assertTrue(schemas.stream().anyMatch(s -> "math.data_tree.flatten".equals(s.typeId())));
        assertTrue(schemas.stream().anyMatch(s -> s.typeId().startsWith("math.fields.")));
    }

    @Test
    void rewriterInsertsGraftListForListToDataTree() {
        assertNotNull(TypeConversionRegistry.getSuggestedConversion(
                NodeDataType.LIST, NodeDataType.DATA_TREE));

        AiGraphDslSupport.DslGraph raw = new AiGraphDslSupport.DslGraph(
                List.of(
                        new AiGraphDslSupport.DslNode(
                                "n1", "math.list.create_list", java.util.Map.of(),
                                new AiGraphDslSupport.DslPosition(0, 0)),
                        new AiGraphDslSupport.DslNode(
                                "n2", "math.data_tree.viewer", java.util.Map.of(),
                                new AiGraphDslSupport.DslPosition(300, 0))
                ),
                List.of(new AiGraphDslSupport.DslConnection(
                        new AiGraphDslSupport.DslEndpoint("n1", "output_list"),
                        new AiGraphDslSupport.DslEndpoint("n2", "input_tree")
                )),
                "list to viewer"
        );

        AiGraphConversionRewriter.RewriteResult rewritten =
                AiGraphConversionRewriter.rewrite(raw, registry);

        assertEquals(1, rewritten.insertedConverters());
        assertEquals(3, rewritten.graph().nodes().size());
        assertTrue(rewritten.graph().nodes().stream()
                .anyMatch(n -> "math.data_tree.graft_list".equals(n.type())));
        assertEquals(2, rewritten.graph().connections().size());
    }

    @Test
    void parseAndValidateAutoRewritesListToDataTreeConnection() {
        String dsl = """
                {
                  "description": "list to tree viewer",
                  "nodes": [
                    {"id": "n1", "type": "math.list.create_list", "params": {}, "position": {"x": 0, "y": 0}},
                    {"id": "n2", "type": "math.data_tree.viewer", "params": {}, "position": {"x": 280, "y": 0}}
                  ],
                  "connections": [
                    {
                      "from": {"nodeId": "n1", "port": "output_list"},
                      "to": {"nodeId": "n2", "port": "input_tree"}
                    }
                  ]
                }
                """;

        AiGraphDslSupport.ParseValidationResult parsed = validator.parseAndValidateJson(dsl);
        assertTrue(parsed.isSuccess(), () -> String.valueOf(parsed.errors()));
        assertNotNull(parsed.graph());
        assertTrue(parsed.graph().nodes().stream()
                .anyMatch(n -> "math.data_tree.graft_list".equals(n.type())));
        assertFalse(parsed.warnings().isEmpty());
        assertTrue(parsed.warnings().stream().anyMatch(w -> w.contains("math.data_tree.graft_list")));
    }

    @Test
    void unsupportedExecToDataHasNoSuggestedConversion() {
        assertEquals(
                TypeConversionRegistry.ConversionPolicy.UNSUPPORTED,
                TypeConversionRegistry.classify(NodeDataType.EXEC, NodeDataType.GEOMETRY));
        assertEquals(
                TypeConversionRegistry.ConversionPolicy.UNSUPPORTED,
                TypeConversionRegistry.classify(NodeDataType.DOUBLE, NodeDataType.EXEC));
        assertEquals(null, TypeConversionRegistry.getSuggestedConversion(
                NodeDataType.EXEC, NodeDataType.GEOMETRY));
    }

    @Test
    void parseRejectsExecWiredToDataPort() {
        String dsl = """
                {
                  "description": "illegal exec to data",
                  "nodes": [
                    {"id": "n1", "type": "flow.control.branch", "params": {}, "position": {"x": 0, "y": 0}},
                    {"id": "n2", "type": "math.list.create_list", "params": {}, "position": {"x": 280, "y": 0}}
                  ],
                  "connections": [
                    {
                      "from": {"nodeId": "n1", "port": "exec_true"},
                      "to": {"nodeId": "n2", "port": "input_0"}
                    }
                  ]
                }
                """;

        AiGraphDslSupport.ParseValidationResult parsed = validator.parseAndValidateJson(dsl);
        assertFalse(parsed.isSuccess());
        assertTrue(parsed.errors().stream().anyMatch(e ->
                e.toLowerCase(Locale.ROOT).contains("type mismatch")
                        || e.toLowerCase(Locale.ROOT).contains("port not found")));
    }
}

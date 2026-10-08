package com.nodecraft.gui.ai;

import com.nodecraft.gui.ai.model.AiGraphPlan;
import com.nodecraft.gui.ai.model.AiPlanConnection;
import com.nodecraft.gui.ai.model.AiPlanNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiGraphPlanDslAdapterServiceTest {

    @BeforeAll
    static void ensureRegistry() {
        NodeRegistry registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void planSurvivesDslJsonRoundTrip() {
        Map<String, Object> sourceParams = new LinkedHashMap<>();
        sourceParams.put("value", 2.5d);
        sourceParams.put("badNumber", Double.POSITIVE_INFINITY);

        AiGraphPlan plan = new AiGraphPlan(
                "round trip",
                List.of(
                        new AiPlanNode("source", "input.values.boolean_toggle", 10.0f, 20.0f, sourceParams),
                        new AiPlanNode("target", "input.values.text_input", 300.0f, 20.0f, Map.of())
                ),
                List.of(),
                List.of()
        );

        String json = AiGraphPlanDslAdapterService.toDslJson(plan);
        AiGraphPlan restored = AiGraphPlanDslAdapterService.fromDsl(
                AiGraphDslSupport.parseAndValidate(json, NodeRegistry.getInstance()).graph()
        );

        assertEquals(plan.summary(), restored.summary());
        assertEquals(2, restored.nodes().size());
        assertEquals("source", restored.nodes().get(0).ref());
        assertEquals("input.values.boolean_toggle", restored.nodes().get(0).typeId());
        assertEquals(10.0f, restored.nodes().get(0).offsetX(), 0.001f);
        assertEquals(20.0f, restored.nodes().get(0).offsetY(), 0.001f);
        assertEquals(2.5d, ((Map<?, ?>) restored.nodes().get(0).nodeState()).get("value"));
        assertEquals(0.0d, ((Map<?, ?>) restored.nodes().get(0).nodeState()).get("badNumber"));
        assertTrue(restored.connections().isEmpty());
    }

    @Test
    void compactJsonOmitsPrettyWhitespace() {
        String compactJson = AiGraphPlanDslAdapterService.toDslJsonCompact(new AiGraphPlan(
                "compact",
                List.of(new AiPlanNode("n1", "input.values.boolean_toggle", 0.0f, 0.0f, Map.of())),
                List.of(),
                List.of()
        ));
        assertFalse(compactJson.contains("\n"));
        assertTrue(compactJson.contains("\"n1\""));
    }
}

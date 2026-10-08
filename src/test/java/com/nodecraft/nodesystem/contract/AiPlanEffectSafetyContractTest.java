package com.nodecraft.nodesystem.contract;

import com.nodecraft.gui.ai.AiGraphApplyService;
import com.nodecraft.gui.ai.AiPlanApplyCoordinatorService;
import com.nodecraft.gui.ai.AiPlanEffectPolicy;
import com.nodecraft.gui.ai.AiPlanValidator;
import com.nodecraft.gui.ai.model.AiGraphPlan;
import com.nodecraft.gui.ai.model.AiPlanNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AI apply may author preview-forbidden nodes but must never auto-execute them.
 */
class AiPlanEffectSafetyContractTest {

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
    void analyzeDetectsPreviewForbiddenWorldWriteNodes() {
        AiGraphPlan plan = new AiGraphPlan(
                "bake",
                List.of(
                        new AiPlanNode("n1", "geometry.primitives.box", 0, 0, null),
                        new AiPlanNode("n2", "world.write.set_block", 300, 0, null)
                ),
                List.of(),
                List.of()
        );

        AiPlanEffectPolicy.EffectSummary summary = AiPlanEffectPolicy.analyze(plan, registry);
        assertTrue(summary.hasPreviewForbiddenNodes());
        assertTrue(summary.previewForbiddenTypeIds().contains("world.write.set_block"));
    }

    @Test
    void checkBeforeApplyDoesNotRejectWorldWriteAuthoring() {
        AiGraphPlan plan = new AiGraphPlan(
                "author world write",
                List.of(new AiPlanNode("n1", "input.values.boolean_toggle", 0, 0, null)),
                List.of(),
                List.of()
        );
        assertTrue(validator.checkBeforeApply(plan).allowed());
    }

    @Test
    void applyServicesDoNotReferenceRuntimeExecution() throws Exception {
        assertNoRuntimeExecutionReferences(Path.of("src/main/java/com/nodecraft/gui/ai/AiGraphApplyService.java"));
        assertNoRuntimeExecutionReferences(Path.of("src/main/java/com/nodecraft/gui/ai/AiPlanApplyCoordinatorService.java"));
        assertNoRuntimeExecutionReferences(Path.of("src/main/java/com/nodecraft/gui/ai/AiGraphApplyAdapterService.java"));
    }

    private static void assertNoRuntimeExecutionReferences(Path sourcePath) throws Exception {
        String source = Files.readString(sourcePath);
        assertFalse(source.contains("NodeExecutor"), sourcePath + " must not invoke NodeExecutor");
        assertFalse(source.contains("processNode("), sourcePath + " must not invoke processNode");
        assertFalse(source.contains("BakePlacementService"), sourcePath + " must not invoke bake services");
        assertFalse(source.contains("ExecutionPlan."), sourcePath + " must not build execution plans");
    }
}

package com.nodecraft.nodesystem.contract;

import com.nodecraft.gui.ai.AiPlanCapabilityCoverage;
import com.nodecraft.gui.ai.AiRemotePlanningOrchestrator;
import com.nodecraft.gui.ai.model.AiGraphPlan;
import com.nodecraft.gui.ai.model.AiPlanConnection;
import com.nodecraft.gui.ai.model.AiPlanNode;
import com.nodecraft.nodesystem.semantic.NodeCapability;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Graph expansion should fire on missing modeling capabilities, not only tiny graphs.
 */
class AiPlanCapabilityCoverageContractTest {

    private final AiRemotePlanningOrchestrator orchestrator = new AiRemotePlanningOrchestrator();

    @Test
    void wallWithWindowPromptRequiresOpeningAndCut() {
        Set<NodeCapability> required =
                AiPlanCapabilityCoverage.requiredFromPrompt("做一面带窗户的墙");
        assertTrue(required.contains(NodeCapability.WALL));
        assertTrue(required.contains(NodeCapability.WINDOW)
                || required.contains(NodeCapability.OPENING));
        assertTrue(required.contains(NodeCapability.BOOLEAN_CUT));
        assertTrue(required.contains(NodeCapability.PREVIEW));
    }

    @Test
    void wallPlusPreviewMissingWindowTriggersExpansion() {
        AiGraphPlan plan = new AiGraphPlan(
                "wall only",
                List.of(
                        new AiPlanNode("n1", "geometry.architectural_primitives.wall_slab", 0, 0, null),
                        new AiPlanNode("n2", "output.preview.preview_geometry", 300, 0, null)
                ),
                List.of(new AiPlanConnection("n1", "output_geometry", "n2", "input_geometry")),
                List.of()
        );

        AiPlanCapabilityCoverage.CoverageResult coverage =
                AiPlanCapabilityCoverage.analyze("做一面带窗户的墙", plan);
        assertTrue(coverage.hasMissing());
        assertTrue(coverage.missing().contains(NodeCapability.WINDOW)
                || coverage.missing().contains(NodeCapability.OPENING)
                || coverage.missing().contains(NodeCapability.BOOLEAN_CUT));

        assertTrue(orchestrator.shouldRequestConnectedGraphExpansion(
                "做一面带窗户的墙",
                plan,
                0,
                true
        ));
    }

    @Test
    void spherePlusPreviewDoesNotTriggerExpansion() {
        AiGraphPlan plan = new AiGraphPlan(
                "sphere",
                List.of(
                        new AiPlanNode("n1", "geometry.primitives.sphere", 0, 0, null),
                        new AiPlanNode("n2", "output.preview.preview_geometry", 300, 0, null)
                ),
                List.of(new AiPlanConnection("n1", "output_geometry", "n2", "input_geometry")),
                List.of()
        );

        AiPlanCapabilityCoverage.CoverageResult coverage =
                AiPlanCapabilityCoverage.analyze("生成一个球体", plan);
        assertFalse(coverage.hasMissing(), "missing=" + coverage.missing());
        assertFalse(orchestrator.shouldRequestConnectedGraphExpansion(
                "生成一个球体",
                plan,
                0,
                true
        ));
    }

    @Test
    void tooSmallGraphStillTriggersAsFallback() {
        AiGraphPlan plan = new AiGraphPlan(
                "tiny",
                List.of(new AiPlanNode("n1", "geometry.primitives.sphere", 0, 0, null)),
                List.of(),
                List.of()
        );
        assertTrue(orchestrator.shouldRequestConnectedGraphExpansion(
                "生成一个复杂的多层塔楼",
                plan,
                0,
                true
        ));
    }
}

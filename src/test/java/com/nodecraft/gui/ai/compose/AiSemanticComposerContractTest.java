package com.nodecraft.gui.ai.compose;

import com.nodecraft.gui.ai.model.AiGraphPlan;
import com.nodecraft.gui.ai.model.AiPlanNode;
import com.nodecraft.gui.recommendation.NodeRecommendations;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.semantic.NodeCapability;
import com.nodecraft.nodesystem.semantic.NodeDomain;
import com.nodecraft.nodesystem.semantic.NodeSemanticCatalog;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract tests for AiSemanticComposer v1 — Preview-first, effect-gated workflows.
 */
class AiSemanticComposerContractTest {

    @BeforeAll
    static void ensureRegistry() {
        NodeRegistry registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
        NodeRecommendations.get().initialize();
        NodeRecommendations.get().reloadRules();
        NodeRecommendations.get().invalidateCache();
        NodeSemanticCatalog.get().refreshIfNeeded();
    }

    @Test
    void composeSphereEndsInPreview() {
        AiComposeResult result = AiSemanticComposer.composeFromPrompt("create a sphere");
        assertTrue(result.isSuccess(), () -> "abstain: " + result.abstainMessage());
        Set<String> types = typeIds(result.plan());
        assertTrue(types.contains("geometry.primitives.sphere"));
        assertTrue(types.contains("geometry.voxel.voxelize_geometry"));
        assertTrue(types.contains("material.basic_assignment.assign_block_type"));
        assertTrue(types.contains("output.preview.preview_blocks"));
        assertFalse(types.contains("output.execute.apply_changes"));
    }

    @Test
    void composeSphereWithWorldIntentMayIncludeApply() {
        AiComposeResult result = AiSemanticComposer.composeFromPrompt("create a sphere apply to world");
        assertTrue(result.isSuccess(), () -> "abstain: " + result.abstainMessage());
        Set<String> types = typeIds(result.plan());
        assertTrue(types.contains("geometry.primitives.sphere"));
        assertTrue(types.contains("output.preview.preview_blocks"));
        assertTrue(types.contains("output.execute.apply_changes"));
    }

    @Test
    void composeWallWithWindowsUsesDifference() {
        AiComposeResult result = AiSemanticComposer.composeFromPrompt("wall with windows");
        assertTrue(result.isSuccess(), () -> "abstain: " + result.abstainMessage());
        Set<String> types = typeIds(result.plan());
        assertTrue(types.contains("geometry.architectural_primitives.wall_slab"));
        assertTrue(types.contains("geometry.architectural_primitives.window_array"));
        assertTrue(types.contains("geometry.boolean.difference"));
        assertTrue(types.contains("output.preview.preview_blocks"));
        assertFalse(types.contains("output.execute.apply_changes"));
    }

    @Test
    void composePathAndProfileUsesSweep() {
        AiComposeResult result = AiSemanticComposer.composeFromPrompt("sweep helix path with profile");
        assertTrue(result.isSuccess(), () -> "abstain: " + result.abstainMessage());
        Set<String> types = typeIds(result.plan());
        assertTrue(types.contains("geometry.curves.helix"));
        assertTrue(types.contains("geometry.profiles.rectangle_profile")
                || types.contains("geometry.profiles.circle_profile"));
        assertTrue(types.contains("geometry.solids.sweep"));
        assertTrue(types.contains("geometry.voxel.surface_strip_to_blocks")
                || types.contains("geometry.voxel.voxelize_geometry"));
        assertTrue(types.contains("output.preview.preview_blocks"));
        assertFalse(types.contains("output.execute.apply_changes"));
    }

    @Test
    void composerDoesNotTraverseWorldWriteWithoutExplicitIntent() {
        assertFalse(AiSemanticComposer.isEffectAllowed(NodeEffect.WORLD_WRITE, false));
        assertTrue(AiSemanticComposer.isEffectAllowed(NodeEffect.WORLD_WRITE, true));
        assertFalse(AiSemanticComposer.isEffectAllowed(NodeEffect.FILE_IO, false));
        assertFalse(AiSemanticComposer.isEffectAllowed(NodeEffect.FILE_IO, true));
        assertFalse(AiSemanticComposer.isEffectAllowed(NodeEffect.CONTEXT_WRITE, false));

        AiComposeResult result = AiSemanticComposer.composeFromPrompt("create a sphere");
        assertTrue(result.isSuccess(), () -> "abstain: " + result.abstainMessage());
        assertFalse(typeIds(result.plan()).stream().anyMatch(id -> id.startsWith("output.execute.")));
        assertFalse(typeIds(result.plan()).stream().anyMatch(id -> id.contains("export")));
    }

    @Test
    void composerDoesNotUseGenericScalarCompatibilityAsWorkflow() {
        // No seed / no supported caps → abstain rather than invent DOUBLE/FLOAT chains.
        AiComposeRequest request = new AiComposeRequest(
                Set.of(),
                EnumSet.noneOf(NodeDomain.class),
                List.of(),
                AiComposeGoal.PREVIEW,
                false,
                12,
                "numeric height radius only"
        );
        AiComposeResult result = AiSemanticComposer.compose(request);
        assertTrue(result.abstained(), "Composer must abstain without geometry seeds");
    }

    @Test
    void maxNodesAbstain() {
        AiComposeRequest request = new AiComposeRequest(
                EnumSet.of(NodeCapability.SPHERE, NodeCapability.PREVIEW),
                EnumSet.of(NodeDomain.GEOMETRY),
                List.of("geometry.primitives.sphere"),
                AiComposeGoal.PREVIEW,
                false,
                2,
                "sphere"
        );
        AiComposeResult result = AiSemanticComposer.compose(request);
        assertTrue(result.abstained(), "maxNodes=2 must abstain before Preview chain completes");
    }

    @Test
    void missingCapabilityAbstainOnHeavyDomains() {
        AiComposeRequest request = new AiComposeRequest(
                Set.of(),
                EnumSet.of(NodeDomain.SDF, NodeDomain.FIELD),
                List.of(),
                AiComposeGoal.PREVIEW,
                false,
                12,
                "sdf field terrain only"
        );
        AiComposeResult result = AiSemanticComposer.compose(request);
        assertTrue(result.abstained());
        assertNotNull(result.abstainMessage());
    }

    @Test
    void seedSelectionPrefersWindowArrayForWallCut() {
        NodeSemanticCatalog catalog = NodeSemanticCatalog.get();
        AiComposeRequest request = new AiComposeRequest(
                EnumSet.of(
                        NodeCapability.WALL,
                        NodeCapability.WINDOW,
                        NodeCapability.BOOLEAN_CUT,
                        NodeCapability.ARRAY,
                        NodeCapability.PREVIEW
                ),
                EnumSet.of(NodeDomain.ARCHITECTURE, NodeDomain.ARRAY),
                List.of(),
                AiComposeGoal.PREVIEW,
                false,
                12,
                "wall windows array"
        );
        List<String> seeds = AiSemanticComposer.selectSeeds(request, catalog);
        assertTrue(seeds.contains("geometry.architectural_primitives.window_array"));
        assertTrue(seeds.contains("geometry.architectural_primitives.wall_slab"));
    }

    private static Set<String> typeIds(AiGraphPlan plan) {
        return plan.nodes().stream().map(AiPlanNode::typeId).collect(Collectors.toSet());
    }
}

package com.nodecraft.gui.ai.compose;

import com.nodecraft.gui.ai.AiPlanValidator;
import com.nodecraft.gui.ai.model.AiGraphPlan;
import com.nodecraft.gui.ai.model.AiPlanConnection;
import com.nodecraft.gui.ai.model.AiPlanNode;
import com.nodecraft.gui.recommendation.NodeRecommendations;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.semantic.NodeCapability;
import com.nodecraft.nodesystem.semantic.NodeDomain;
import com.nodecraft.nodesystem.semantic.NodeSemanticCatalog;
import com.nodecraft.nodesystem.semantic.NodeSemanticEdgeKind;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract tests for Catalog-driven AiSemanticComposer v1.
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
        NodeSemanticCatalog.get().forceRefresh();
    }

    @Test
    void composeSpherePreview() {
        AiComposeResult result = AiSemanticComposer.composeFromPrompt("create a sphere");
        assertTrue(result.isSuccess(), () -> "abstain: " + result.message());
        assertApplyGateAllows(result.plan());
        Set<String> types = typeIds(result.plan());
        assertTrue(types.contains("geometry.primitives.sphere"));
        assertTrue(types.contains("geometry.voxel.voxelize_geometry"));
        assertTrue(types.contains("material.basic_assignment.assign_block_type"));
        assertTrue(types.contains("output.preview.preview_blocks"));
        assertFalse(types.contains("output.execute.apply_changes"));
        assertFalse(hasWorldWrite(result.plan()));
    }

    @Test
    void composeSphereWorldOutput() {
        AiComposeResult result = AiSemanticComposer.composeFromPrompt("create a sphere apply to world");
        assertTrue(result.isSuccess(), () -> "abstain: " + result.message());
        assertApplyGateAllows(result.plan());
        Set<String> types = typeIds(result.plan());
        assertTrue(types.contains("geometry.primitives.sphere"));
        assertTrue(types.contains("output.preview.preview_blocks"));
        assertTrue(types.contains("output.execute.apply_changes"));
    }

    @Test
    void composeWallWithWindowsUsesBooleanCut() {
        AiComposeResult result = AiSemanticComposer.composeFromPrompt("wall with windows");
        assertTrue(result.isSuccess(), () -> "abstain: " + result.message());
        AiGraphPlan plan = result.plan();
        assertApplyGateAllows(plan);
        Set<String> types = typeIds(plan);
        assertTrue(types.contains("geometry.architectural_primitives.wall_slab"));
        assertTrue(types.contains("geometry.architectural_primitives.window_array"));
        assertTrue(types.contains("geometry.boolean.difference"));
        assertTrue(types.contains("output.preview.preview_blocks"));
        assertFalse(types.contains("output.execute.apply_changes"));

        assertTrue(hasConnection(plan,
                "geometry.architectural_primitives.wall_slab", "output_geometry",
                "geometry.boolean.difference", "input_base"),
                "wall.geometry → difference.base");
        assertTrue(hasConnection(plan,
                "geometry.architectural_primitives.window_array", "output_openings",
                "geometry.boolean.difference", "input_cutter"),
                "window.openings → difference.cutter");

        // P1: required BOX_FACE inputs completed via Catalog upstream (Box → GetBoxFace).
        assertTrue(types.contains("reference.points.get_box_face")
                        || types.contains("geometry.primitives.box"),
                "face chain producer present");
        assertTrue(hasIncoming(plan, "geometry.architectural_primitives.wall_slab", "input_face"),
                "wall.input_face connected");
        assertTrue(hasIncoming(plan, "geometry.architectural_primitives.window_array", "input_face"),
                "window.input_face connected");
    }

    @Test
    void composeProfileToExtrude() {
        AiComposeResult result = AiSemanticComposer.composeFromPrompt("extrude a rectangle profile");
        assertTrue(result.isSuccess(), () -> "abstain: " + result.message());
        assertApplyGateAllows(result.plan());
        Set<String> types = typeIds(result.plan());
        assertTrue(types.stream().anyMatch(t -> t.contains("rectangle_profile") || t.contains("circle_profile")));
        assertTrue(types.contains("geometry.profiles.profile_to_region"));
        assertTrue(types.contains("geometry.solids.extrude_region"));
        assertTrue(types.contains("output.preview.preview_blocks")
                || types.contains("output.preview.preview_geometry"));
    }

    @Test
    void composePathAndProfileThroughSweep() {
        AiComposeResult result = AiSemanticComposer.composeFromPrompt("sweep helix path with profile");
        assertTrue(result.isSuccess(), () -> "abstain: " + result.message());
        assertApplyGateAllows(result.plan());
        Set<String> types = typeIds(result.plan());
        assertTrue(types.contains("geometry.curves.helix"));
        assertTrue(types.contains("geometry.profiles.rectangle_profile")
                || types.contains("geometry.profiles.circle_profile"));
        assertTrue(types.contains("geometry.solids.sweep"));
        assertTrue(types.contains("geometry.voxel.surface_strip_to_blocks"));
        assertTrue(types.contains("material.basic_assignment.assign_block_type"));
        assertTrue(types.contains("output.preview.preview_blocks"));
        assertFalse(types.contains("geometry.primitives.sphere"));
        assertFalse(types.contains("pattern.linear.curve_array"));
        assertFalse(types.contains("output.execute.apply_changes"));
    }

    @Test
    void previewGoalNeverIncludesWorldWrite() {
        assertFalse(AiSemanticComposer.isEffectAllowed(NodeEffect.WORLD_WRITE, false));
        assertTrue(AiSemanticComposer.isEffectAllowed(NodeEffect.WORLD_WRITE, true));
        AiComposeResult result = AiSemanticComposer.composeFromPrompt("create a sphere");
        assertTrue(result.isSuccess(), () -> "abstain: " + result.message());
        assertApplyGateAllows(result.plan());
        assertFalse(hasWorldWrite(result.plan()));
        assertFalse(typeIds(result.plan()).stream().anyMatch(id -> id.startsWith("output.execute.")));
    }

    @Test
    void previewGoalNeverIncludesFileIo() {
        assertFalse(AiSemanticComposer.isEffectAllowed(NodeEffect.FILE_IO, false));
        assertFalse(AiSemanticComposer.isEffectAllowed(NodeEffect.FILE_IO, true));
        AiComposeResult result = AiSemanticComposer.composeFromPrompt("wall with windows");
        assertTrue(result.isSuccess(), () -> "abstain: " + result.message());
        assertApplyGateAllows(result.plan());
        assertFalse(typeIds(result.plan()).stream().anyMatch(id -> id.contains("export")));
    }

    @Test
    void genericDoubleDoesNotCreateWorkflowEdge() {
        assertFalse(AiSemanticComposer.wouldExpandScalarEdge(NodeSemanticEdgeKind.CATEGORY, NodeDataType.DOUBLE));
        assertFalse(AiSemanticComposer.wouldExpandScalarEdge(NodeSemanticEdgeKind.TYPE, NodeDataType.FLOAT));
        assertTrue(AiSemanticComposer.wouldExpandScalarEdge(NodeSemanticEdgeKind.EXACT, NodeDataType.DOUBLE));

        AiComposeRequest request = new AiComposeRequest(
                "numeric height radius only",
                Set.of(),
                EnumSet.noneOf(NodeDomain.class),
                List.of(),
                AiComposeGoal.PREVIEW,
                12
        );
        AiComposeResult result = AiSemanticComposer.compose(request);
        assertTrue(result.abstained(), "Composer must abstain without geometry seeds");
        assertEquals(AiComposeResult.ABSTAIN_CODE, result.abstainCode());
    }

    @Test
    void explicitListTreeConversionIsInserted() {
        NodeSemanticCatalog catalog = NodeSemanticCatalog.get();
        var suggestion = catalog.suggestedConversion(NodeDataType.LIST, NodeDataType.DATA_TREE);
        assertNotNull(suggestion, "LIST→DATA_TREE suggestion required");
        assertEquals("math.data_tree.graft_list", suggestion.nodeId());

        String[] fail = new String[1];
        AiGraphPlan plan = AiSemanticComposer.wireConversionFixture(
                "math.list.create_list", "output_list",
                "math.data_tree.flatten", "input_tree",
                fail
        );
        assertNotNull(plan, () -> "conversion wire failed: " + fail[0]);
        Set<String> types = typeIds(plan);
        assertTrue(types.contains("math.data_tree.graft_list"), "graft_list must be inserted");
        assertTrue(hasConnection(plan,
                        "math.list.create_list", "output_list",
                        "math.data_tree.graft_list", "input_list"),
                "source → graft input");
        assertTrue(hasConnection(plan,
                        "math.data_tree.graft_list", "output_tree",
                        "math.data_tree.flatten", "input_tree"),
                "graft output → target");
    }

    @Test
    void unsupportedConversionWireFails() {
        String[] fail = new String[1];
        AiGraphPlan plan = AiSemanticComposer.wireConversionFixture(
                "geometry.primitives.sphere", "output_geometry",
                "math.data_tree.flatten", "input_tree",
                fail
        );
        assertTrue(plan == null, "GEOMETRY→DATA_TREE must be unsupported");
        assertNotNull(fail[0]);
        assertTrue(fail[0].toLowerCase().contains("unsupported")
                        || fail[0].toLowerCase().contains("no converter"),
                () -> "expected unsupported message, got: " + fail[0]);
    }

    @Test
    void missingFaceUpstreamAbstainsWhenBudgetTooTight() {
        // Wall+Window join hubs without room to spawn Box→GetBoxFace for required faces.
        AiComposeRequest request = new AiComposeRequest(
                "wall with windows",
                EnumSet.of(
                        NodeCapability.WALL,
                        NodeCapability.WINDOW,
                        NodeCapability.BOOLEAN_CUT,
                        NodeCapability.PREVIEW
                ),
                EnumSet.of(NodeDomain.ARCHITECTURE),
                List.of(
                        "geometry.architectural_primitives.wall_slab",
                        "geometry.architectural_primitives.window_array"
                ),
                AiComposeGoal.PREVIEW,
                4
        );
        AiComposeResult result = AiSemanticComposer.compose(request);
        assertTrue(result.abstained(), "tight maxNodes must abstain when faces cannot complete");
        assertEquals(AiComposeResult.ABSTAIN_CODE, result.abstainCode());
    }

    @Test
    void unsupportedConversionAbstains() {
        // Force a dead-end seed with maxNodes too small to reach Preview → abstain, not invent edges.
        AiComposeRequest request = new AiComposeRequest(
                "sphere",
                EnumSet.of(NodeCapability.SPHERE, NodeCapability.PREVIEW),
                EnumSet.of(NodeDomain.GEOMETRY),
                List.of("geometry.primitives.sphere"),
                AiComposeGoal.PREVIEW,
                2
        );
        AiComposeResult result = AiSemanticComposer.compose(request);
        assertTrue(result.abstained());
        assertEquals(AiComposeResult.ABSTAIN_CODE, result.abstainCode());
    }

    @Test
    void maxNodeBudgetCausesAbstain() {
        AiComposeRequest request = new AiComposeRequest(
                "sphere",
                EnumSet.of(NodeCapability.SPHERE, NodeCapability.PREVIEW),
                EnumSet.of(NodeDomain.GEOMETRY),
                List.of("geometry.primitives.sphere"),
                AiComposeGoal.PREVIEW,
                2
        );
        AiComposeResult result = AiSemanticComposer.compose(request);
        assertTrue(result.abstained(), "maxNodes=2 must abstain before Preview chain completes");
    }

    @Test
    void missingRequiredCapabilityCausesAbstain() {
        AiComposeRequest request = new AiComposeRequest(
                "sdf field terrain only",
                Set.of(),
                EnumSet.of(NodeDomain.SDF, NodeDomain.FIELD),
                List.of(),
                AiComposeGoal.PREVIEW,
                12
        );
        AiComposeResult result = AiSemanticComposer.compose(request);
        assertTrue(result.abstained());
        assertNotNull(result.message());
    }

    @Test
    void seedSelectionPrefersWindowArrayForWallCut() {
        NodeSemanticCatalog catalog = NodeSemanticCatalog.get();
        AiComposeRequest request = new AiComposeRequest(
                "wall windows array",
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
                12
        );
        List<String> seeds = AiSemanticComposer.selectSeeds(request, catalog);
        assertTrue(seeds.contains("geometry.architectural_primitives.window_array"));
        assertTrue(seeds.contains("geometry.architectural_primitives.wall_slab"));
    }

    private static void assertApplyGateAllows(AiGraphPlan plan) {
        AiPlanValidator.GateResult gate = new AiPlanValidator().checkBeforeApply(plan);
        assertTrue(gate.allowed(), () -> "Apply gate rejected: " + gate.rejectionMessage());
    }

    private static Set<String> typeIds(AiGraphPlan plan) {
        return plan.nodes().stream().map(AiPlanNode::typeId).collect(Collectors.toSet());
    }

    private static boolean hasWorldWrite(AiGraphPlan plan) {
        NodeSemanticCatalog catalog = NodeSemanticCatalog.get();
        for (AiPlanNode node : plan.nodes()) {
            if (catalog.effect(node.typeId()) == NodeEffect.WORLD_WRITE) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasIncoming(AiGraphPlan plan, String toType, String toPort) {
        Set<String> toRefs = plan.nodes().stream()
                .filter(n -> toType.equalsIgnoreCase(n.typeId()))
                .map(AiPlanNode::ref)
                .collect(Collectors.toSet());
        for (AiPlanConnection c : plan.connections()) {
            if (toRefs.contains(c.targetRef()) && toPort.equals(c.targetPortId())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasConnection(
            AiGraphPlan plan,
            String fromType,
            String fromPort,
            String toType,
            String toPort
    ) {
        Set<String> fromRefs = plan.nodes().stream()
                .filter(n -> fromType.equalsIgnoreCase(n.typeId()))
                .map(AiPlanNode::ref)
                .collect(Collectors.toSet());
        Set<String> toRefs = plan.nodes().stream()
                .filter(n -> toType.equalsIgnoreCase(n.typeId()))
                .map(AiPlanNode::ref)
                .collect(Collectors.toSet());
        for (AiPlanConnection c : plan.connections()) {
            if (fromRefs.contains(c.sourceRef())
                    && toRefs.contains(c.targetRef())
                    && fromPort.equals(c.sourcePortId())
                    && toPort.equals(c.targetPortId())) {
                return true;
            }
        }
        return false;
    }
}

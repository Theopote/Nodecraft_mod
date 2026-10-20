package com.nodecraft.nodesystem.nodes.output.preview;

import com.google.gson.Gson;
import com.nodecraft.gui.preset.GraphPresetRules;
import com.nodecraft.gui.preset.GraphPresetTestResources;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.bake.BakePlacementService;
import com.nodecraft.nodesystem.contract.support.MiniBuildingIntegrityAssert;
import com.nodecraft.nodesystem.contract.support.PreviewApplyPlacementAssert;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.execution.ExecutionRunLimits;
import com.nodecraft.nodesystem.execution.IncrementalExecutionOptions;
import com.nodecraft.nodesystem.execution.NodeExecutor;
import com.nodecraft.nodesystem.execution.runtime.CancellationToken;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.preview.PreviewManager;
import com.nodecraft.nodesystem.preview.protocol.PreviewBlock;
import com.nodecraft.nodesystem.preview.protocol.PreviewBlocksPayload;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.BlockStateResolver;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.PlacementSubmitPlan;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3d;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 6D: Mini Building placements → PreviewPlacementsNode payload must match Apply plan
 * BlockStates (full set or strict sampled subset), without world writes.
 */
class MiniBuildingPreviewPayloadContractTest {

    private static final String PRESET_ID = "architectural.residential.mini_building_v1";
    private static final Gson GSON = new Gson();
    private static final Vector3d PLAYER_ORIGIN = new Vector3d(0.5d, 0.0d, 0.5d);

    private static NodeRegistry registry;

    @BeforeAll
    static void ensureRegistry() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void miniBuildingPreviewPayloadMatchesApplyPlanAndSkipsWorldWrite() throws Exception {
        Assumptions.assumeTrue(BlockStateResolver.isRegistryReady(), "Live BLOCK registry required");

        GraphPresetRules.GraphPresetDefinition preset = requirePreset(PRESET_ID);
        MaterializedGraph material = materialize(preset);
        seedPlayerPosition(material.byRef.get("player_pos"), PLAYER_ORIGIN.x, PLAYER_ORIGIN.y, PLAYER_ORIGIN.z);

        ExecutionContext context = new ExecutionContext(null, null);
        NodeExecutor executor = new NodeExecutor(
                material.graph,
                context,
                null,
                IncrementalExecutionOptions.defaults(),
                ExecutionRunLimits.defaults(),
                CancellationToken.none(),
                true,
                null,
                true
        );
        assertTrue(executor.executeSync(), "mini_building_v1 must execute");

        INode materialNode = material.byRef.get("material");
        INode windows = material.byRef.get("windows");
        INode placeFrames = material.byRef.get("place_frames");
        INode frontFace = material.byRef.get("front_face");
        Object placementsObj = materialNode.getOutput("output_placements");
        assertNotNull(placementsObj);
        assertTrue(placementsObj instanceof List<?> list && !list.isEmpty());

        PlacementSubmitPlan.Plan applyPlan = PlacementSubmitPlan.resolve(
                placementsObj, null, null, null, null, null, null, null, "minecraft:stone_bricks", true);
        assertTrue(applyPlan.valid(), applyPlan.error());
        assertTrue(applyPlan.size() >= 40);

        // Real PreviewPlacementsNode evaluation path (not a second resolve() with identical args).
        PreviewPlacementsNode preview = assertInstanceOf(
                PreviewPlacementsNode.class,
                registry.createNodeInstance("output.preview.preview_placements"));
        preview.setInput("input_block_placements", placementsObj);
        int queueBefore = BakePlacementService.getInstance().getQueueSize();
        preview.processNode(context);

        assertEquals(PlacementSubmitPlan.Source.PLACEMENTS.name(), preview.getOutput("output_source"));
        assertEquals(applyPlan.uniqueCount(), preview.getOutput("output_unique_count"));
        assertEquals(applyPlan.rawEntryCount(), preview.getOutput("output_raw_count"));

        int shown = ((Number) preview.getOutput("output_shown_count")).intValue();
        boolean sampled = Boolean.TRUE.equals(preview.getOutput("output_sampled"));
        assertEquals(applyPlan.uniqueCount() > GenerationLimits.MAX_PREVIEW_BLOCKS, sampled);
        if (!sampled) {
            assertEquals(applyPlan.uniqueCount(), shown);
        } else {
            assertTrue(shown <= GenerationLimits.MAX_PREVIEW_BLOCKS);
            assertTrue(shown < applyPlan.uniqueCount());
        }

        // Package-visible sampleGhosts is the same payload builder processNode uses for GHOST.
        PreviewPlacementsNode.GhostSample ghosts =
                PreviewPlacementsNode.sampleGhosts(applyPlan.entries(), GenerationLimits.MAX_PREVIEW_BLOCKS);
        assertEquals(sampled, ghosts.sampled());
        assertEquals(shown, ghosts.ghosts().size());
        PreviewBlocksPayload payload = new PreviewBlocksPayload(ghosts.ghosts());
        assertGhostPayloadAgainstPlan(applyPlan, payload.getBlocks(), sampled);

        // GHOST must not enqueue bake / mutate the world queue.
        assertEquals(queueBefore, BakePlacementService.getInstance().getQueueSize());

        // Refresh: counts stay stable. Renderer may reject without a client — still publish counts.
        Object previewIdAfterFirst = preview.getOutput("output_preview_id");
        preview.processNode(context);
        assertEquals(applyPlan.uniqueCount(), preview.getOutput("output_unique_count"));
        assertEquals(shown, preview.getOutput("output_shown_count"));
        if (previewIdAfterFirst != null) {
            assertEquals(String.valueOf(previewIdAfterFirst),
                    String.valueOf(preview.getOutput("output_preview_id")));
        }

        PreviewManager.hideNodePreviews(preview.getId().toString());
        preview.setNodeState(Map.of("previewEnabled", false));
        preview.processNode(context);
        assertEquals(Boolean.FALSE, preview.getOutput("output_success"));
        assertEquals("Preview disabled", preview.getOutput("output_error"));

        Map<BlockPos, BlockState> solidMap = applyPlan.toPositionMap();
        Set<BlockPos> solid = solidMap.keySet();
        MiniBuildingIntegrityAssert.Bounds bounds = MiniBuildingIntegrityAssert.boundsOf(solid);
        MiniBuildingIntegrityAssert.assertFloorContinuous(solid, bounds);
        MiniBuildingIntegrityAssert.assertRoofCoverage(solid, bounds);
        MiniBuildingIntegrityAssert.assertWallsAndCorners(solid, bounds);
        MiniBuildingIntegrityAssert.assertDominantMaterial(solidMap, Blocks.STONE_BRICKS, 0.85d);

        @SuppressWarnings("unchecked")
        List<PointData> centers = (List<PointData>) windows.getOutput("output_centers");
        Vector3d faceNormal = ((BoxFaceData) frontFace.getOutput("output_face")).getNormal();
        MiniBuildingIntegrityAssert.assertOpeningsAndPiers(solid, centers, PLAYER_ORIGIN, faceNormal);

        GeometryData frameGeom = assertInstanceOf(GeometryData.class, placeFrames.getOutput("output_geometry"));
        GeometryData cutWallGeom = assertInstanceOf(GeometryData.class,
                material.byRef.get("cut_walls").getOutput("output_geometry"));
        int frameCount = placeFrames.getOutput("output_count") instanceof Number n ? n.intValue() : -1;
        MiniBuildingIntegrityAssert.assertFramesFromBranch(
                frameGeom, cutWallGeom, centers, faceNormal, frameCount);
    }

    @Test
    void sampledPreviewIsStrictSubsetOfMiniBuildingApplyPlan() throws Exception {
        Assumptions.assumeTrue(BlockStateResolver.isRegistryReady(), "Live BLOCK registry required");

        GraphPresetRules.GraphPresetDefinition preset = requirePreset(PRESET_ID);
        MaterializedGraph material = materialize(preset);
        seedPlayerPosition(material.byRef.get("player_pos"), PLAYER_ORIGIN.x, PLAYER_ORIGIN.y, PLAYER_ORIGIN.z);
        ExecutionContext context = new ExecutionContext(null, null);
        NodeExecutor executor = new NodeExecutor(
                material.graph, context, null,
                IncrementalExecutionOptions.defaults(), ExecutionRunLimits.defaults(),
                CancellationToken.none(), true, null, true);
        assertTrue(executor.executeSync());

        Object placementsObj = material.byRef.get("material").getOutput("output_placements");
        PlacementSubmitPlan.Plan applyPlan = PlacementSubmitPlan.resolve(
                placementsObj, null, null, null, null, null, null, null, "minecraft:stone_bricks", true);
        assertTrue(applyPlan.valid(), applyPlan.error());
        assertTrue(applyPlan.uniqueCount() > 8, "need enough cells to force sampling");

        PreviewPlacementsNode.GhostSample sample =
                PreviewPlacementsNode.sampleGhosts(applyPlan.entries(), 8);
        assertTrue(sample.sampled());
        assertEquals(8, sample.ghosts().size());
        assertGhostPayloadAgainstPlan(applyPlan, sample.ghosts(), true);
    }

    private static void assertGhostPayloadAgainstPlan(
            PlacementSubmitPlan.Plan plan,
            List<PreviewBlock> ghosts,
            boolean sampled
    ) {
        Map<BlockPos, BlockState> planMap = plan.toPositionMap();
        Set<BlockPos> ghostPositions = new HashSet<>();
        for (PreviewBlock ghost : ghosts) {
            BlockPos pos = new BlockPos((int) Math.floor(ghost.x()), (int) Math.floor(ghost.y()),
                    (int) Math.floor(ghost.z()));
            ghostPositions.add(pos);
            assertTrue(planMap.containsKey(pos), "ghost cell missing from Apply plan: " + pos);
            BlockState resolved = BlockStateResolver.resolve(ghost.blockId(), ghost.stateData());
            assertNotNull(resolved, "ghost BlockState resolve failed: " + ghost.blockId());
            assertEquals(planMap.get(pos), resolved, "ghost BlockState mismatch at " + pos);
        }
        if (!sampled) {
            PreviewApplyPlacementAssert.assertSamePositions(planMap.keySet(), ghostPositions,
                    "unsampled Preview payload");
        } else {
            assertTrue(planMap.keySet().containsAll(ghostPositions));
            assertTrue(ghostPositions.size() < planMap.size());
        }
    }

    private static GraphPresetRules.GraphPresetDefinition requirePreset(String presetId) {
        try (InputStream stream = MiniBuildingPreviewPayloadContractTest.class
                .getResourceAsStream(GraphPresetTestResources.BUILTIN_GRAPH_PRESETS)) {
            assertNotNull(stream, "Missing " + GraphPresetTestResources.BUILTIN_GRAPH_PRESETS);
            GraphPresetRules rules = GSON.fromJson(
                    new InputStreamReader(stream, StandardCharsets.UTF_8), GraphPresetRules.class);
            for (GraphPresetRules.PresetCategory category : rules.categories) {
                if (category == null || category.presets == null) {
                    continue;
                }
                for (GraphPresetRules.GraphPresetDefinition preset : category.presets) {
                    if (preset != null && presetId.equals(preset.id)) {
                        return preset;
                    }
                }
            }
            throw new AssertionError("Missing preset " + presetId);
        } catch (AssertionError e) {
            throw e;
        } catch (Exception e) {
            throw new AssertionError("Failed to load preset catalog", e);
        }
    }

    private static MaterializedGraph materialize(GraphPresetRules.GraphPresetDefinition preset) {
        NodeGraph graph = new NodeGraph(Objects.requireNonNullElse(preset.displayName, preset.id));
        Map<String, INode> byRef = new HashMap<>();
        Map<String, UUID> idByRef = new HashMap<>();
        for (GraphPresetRules.PresetNode presetNode : preset.nodes) {
            INode node = registry.createNodeInstance(presetNode.typeId);
            assertNotNull(node, "unknown type " + presetNode.typeId);
            if (presetNode.state != null) {
                node.setNodeState(presetNode.state);
            }
            assertTrue(graph.addNode(node));
            byRef.put(presetNode.ref, node);
            idByRef.put(presetNode.ref, node.getId());
        }
        for (GraphPresetRules.PresetConnection connection : preset.connections) {
            assertTrue(graph.connect(
                    idByRef.get(connection.fromRef), connection.fromPort,
                    idByRef.get(connection.toRef), connection.toPort));
        }
        return new MaterializedGraph(graph, byRef);
    }

    private static void seedPlayerPosition(INode playerPos, double x, double y, double z) throws Exception {
        Method setter = playerPos.getClass().getDeclaredMethod(
                "setCachedPosition", double.class, double.class, double.class);
        setter.setAccessible(true);
        setter.invoke(playerPos, x, y, z);
    }

    private record MaterializedGraph(NodeGraph graph, Map<String, INode> byRef) {
    }
}

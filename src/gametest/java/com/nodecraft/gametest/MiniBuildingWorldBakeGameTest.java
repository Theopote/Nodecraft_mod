package com.nodecraft.gametest;

import com.google.gson.Gson;
import com.nodecraft.gui.preset.GraphPresetRules;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.bake.BakeHistory;
import com.nodecraft.nodesystem.bake.BakeOperationKind;
import com.nodecraft.nodesystem.bake.BakePlacementService;
import com.nodecraft.nodesystem.bake.BakeTask;
import com.nodecraft.nodesystem.bake.PlacementMode;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.execution.ExecutionRunLimits;
import com.nodecraft.nodesystem.execution.IncrementalExecutionOptions;
import com.nodecraft.nodesystem.execution.NodeExecutor;
import com.nodecraft.nodesystem.execution.runtime.CancellationToken;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.PlacementSubmitPlan;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.joml.Vector3d;
import org.jspecify.annotations.NonNull;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Phase 6C: load architectural.residential.mini_building_v1, bake placements into a GameTest
 * world, verify BlockState map / openings / frames / roof+floor, then Undo and re-apply.
 */
public class MiniBuildingWorldBakeGameTest implements CustomTestMethodInvoker {

    private static final Object SHARED_SERVICE_LOCK = new Object();
    private static final String PRESET_ID = "architectural.residential.mini_building_v1";
    private static final String PRESET_RESOURCE = "/nodecraft/graph_presets.json";
    private static final Gson GSON = new Gson();

    @GameTest(structure = "nodecraft:pad_16x12x16", maxTicks = 200)
    public void miniBuildingPresetBakesIntoWorldWithUndo(TestContext ctx) throws Exception {
        ensureRegistry();
        World world = ctx.getWorld();
        BakePlacementService service = BakePlacementService.getInstance();
        resetBakeService(service);

        UUID actorId = UUID.randomUUID();
        BakeHistory history = service.getHistory(actorId, world);
        history.clear();

        BlockPos playerRel = new BlockPos(2, 1, 2);
        BlockPos playerAbs = ctx.getAbsolutePos(playerRel);

        GraphPresetRules.GraphPresetDefinition preset = requirePreset(PRESET_ID);
        MaterializedGraph material = materialize(preset);
        seedPlayerPosition(
                material.byRef.get("player_pos"),
                playerAbs.getX() + 0.5d,
                playerAbs.getY(),
                playerAbs.getZ() + 0.5d
        );

        ExecutionContext context = new ExecutionContext(world, null);
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
        ctx.assertTrue(executor.executeSync(), "mini_building_v1 graph must execute");

        INode windows = material.byRef.get("windows");
        INode placeFrames = material.byRef.get("place_frames");
        INode floor = material.byRef.get("floor");
        INode roof = material.byRef.get("roof");
        INode materialNode = material.byRef.get("material");
        INode frontFace = material.byRef.get("front_face");

        ctx.assertTrue(Boolean.TRUE.equals(windows.getOutput("output_valid")),
                "windows valid: " + windows.getOutput("output_error"));
        ctx.assertTrue(Boolean.TRUE.equals(placeFrames.getOutput("output_valid")),
                "frames valid: " + placeFrames.getOutput("output_error"));
        ctx.assertTrue(Boolean.TRUE.equals(floor.getOutput("output_valid")),
                "floor valid: " + floor.getOutput("output_error"));
        ctx.assertTrue(Boolean.TRUE.equals(roof.getOutput("output_valid")),
                "roof valid: " + roof.getOutput("output_error"));
        ctx.assertTrue(Boolean.TRUE.equals(materialNode.getOutput("output_valid")),
                "material valid: " + materialNode.getOutput("output_error"));
        ctx.assertEquals(windows.getOutput("output_count"), placeFrames.getOutput("output_count"),
                "frame instance count must match window openings");

        Object placementsObj = materialNode.getOutput("output_placements");
        ctx.assertTrue(placementsObj instanceof List<?> list && !list.isEmpty(),
                "material placements non-empty");

        PlacementSubmitPlan.Plan applyPlan = PlacementSubmitPlan.resolve(
                placementsObj, null, null, null, null, null, null, null, "minecraft:stone_bricks", true);
        ctx.assertTrue(applyPlan.valid(), "apply plan valid: " + applyPlan.error());
        ctx.assertTrue(applyPlan.size() >= 40, "mini building should produce a substantial placement set");

        // Preview/Apply payload BlockState parity is covered by MiniBuildingPreviewPayloadContractTest
        // (real PreviewPlacementsNode.sampleGhosts path). Here we keep Bake + world integrity.
        assertSpatialIntegrity(ctx, applyPlan.toPositionMap().keySet());
        assertPlacementsInsidePad(ctx, applyPlan);

        List<BakeTask.Placement> bakePlacements = toBakePlacements(applyPlan);
        service.enqueuePlacements(
                world,
                bakePlacements,
                PlacementMode.OVERWRITE,
                true,
                BakeOperationKind.APPLY,
                2000,
                5_000_000L,
                actorId,
                null
        );
        drainTasks(service);
        ctx.assertEquals(1, history.size(), "bake history after apply");

        Map<BlockPos, BlockState> expected = applyPlan.toPositionMap();
        assertWorldMatches(ctx, world, expected);

        @SuppressWarnings("unchecked")
        List<PointData> centers = (List<PointData>) windows.getOutput("output_centers");
        ctx.assertTrue(centers != null && centers.size() == 2, "two window centers");
        Vector3d move = new Vector3d(playerAbs.getX() + 0.5d, playerAbs.getY(), playerAbs.getZ() + 0.5d);
        Set<BlockPos> solid = expected.keySet();
        for (PointData center : centers) {
            BlockPos opening = BlockPos.ofFloored(
                    center.position().x + move.x,
                    center.position().y + move.y,
                    center.position().z + move.z
            );
            ctx.assertTrue(!solid.contains(opening), "opening center must not be in placement set: " + opening);
            ctx.checkBlockState(
                    toRelative(ctx, opening),
                    state -> state.isAir(),
                    state -> Text.literal("Expected air at window center " + opening + " got " + state)
            );
        }

        Vector3d faceNormal = ((BoxFaceData) frontFace.getOutput("output_face")).getNormal();
        Vector3d pierMid = new Vector3d(centers.get(0).position())
                .add(centers.get(1).position())
                .mul(0.5d)
                .add(move);
        BlockPos pier = BlockPos.ofFloored(pierMid.x, pierMid.y, pierMid.z);
        // Sample half-thickness into the wall if the mid-point lands in the opening gap.
        if (!solid.contains(pier)) {
            pier = BlockPos.ofFloored(
                    pierMid.x + faceNormal.x * 0.6d,
                    pierMid.y + faceNormal.y * 0.6d,
                    pierMid.z + faceNormal.z * 0.6d
            );
        }
        ctx.assertTrue(solid.contains(pier), "pier between windows should remain solid at " + pier);

        UUID undoTaskId = service.undoLastAsync(actorId, world, 2000, 5_000_000L);
        ctx.assertTrue(undoTaskId != null, "undo task id");
        drainTasks(service);
        ctx.assertEquals(0, history.size(), "history empty after undo");
        for (BlockPos abs : expected.keySet()) {
            ctx.checkBlockState(
                    toRelative(ctx, abs),
                    state -> state.isAir(),
                    state -> Text.literal("Expected air after undo at " + abs + " got " + state)
            );
        }

        service.enqueuePlacements(
                world,
                bakePlacements,
                PlacementMode.OVERWRITE,
                true,
                BakeOperationKind.APPLY,
                2000,
                5_000_000L,
                actorId,
                null
        );
        drainTasks(service);
        assertWorldMatches(ctx, world, expected);
        ctx.assertEquals(1, history.size(), "history after deterministic re-apply");

        ctx.complete();
    }

    private static void assertPlacementsInsidePad(TestContext ctx, PlacementSubmitPlan.Plan plan) {
        for (PlacementSubmitPlan.Entry entry : plan.entries()) {
            BlockPos rel = toRelative(ctx, entry.pos());
            ctx.assertTrue(rel.getX() >= 0 && rel.getX() < 16, "x in pad: " + rel);
            ctx.assertTrue(rel.getY() >= 0 && rel.getY() < 12, "y in pad: " + rel);
            ctx.assertTrue(rel.getZ() >= 0 && rel.getZ() < 16, "z in pad: " + rel);
        }
    }

    /**
     * Spatial integrity beyond non-empty Y-band presence: floor continuity, roof span,
     * and four wall faces at mid-height (mirrors MiniBuildingIntegrityAssert for GameTest).
     */
    private static void assertSpatialIntegrity(TestContext ctx, Set<BlockPos> solid) {
        ctx.assertTrue(solid != null && !solid.isEmpty(), "solid set non-empty");
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : solid) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        ctx.assertTrue(maxY - minY >= 3, "building height too short for roof+walls");

        int floorCount = 0;
        int roofCount = 0;
        for (BlockPos pos : solid) {
            if (pos.getY() == minY) {
                floorCount++;
            }
            if (pos.getY() >= maxY - 1) {
                roofCount++;
            }
        }
        int footprint = (maxX - minX + 1) * (maxZ - minZ + 1);
        ctx.assertTrue(floorCount >= 20, "floor band too sparse: " + floorCount);
        ctx.assertTrue(floorCount * 4 >= footprint,
                "floor coverage too low: cells=" + floorCount + " footprint=" + footprint);
        ctx.assertTrue(roofCount >= 16, "roof band too sparse: " + roofCount);

        // First storey above the floor (not (min+max)/2) so gable/roof fill is excluded.
        ctx.assertTrue(maxY - minY >= 2, "need floor + wall height");
        int midY = minY + 1;
        int wallMinX = Integer.MAX_VALUE;
        int wallMaxX = Integer.MIN_VALUE;
        int wallMinZ = Integer.MAX_VALUE;
        int wallMaxZ = Integer.MIN_VALUE;
        java.util.ArrayList<BlockPos> midCells = new java.util.ArrayList<>();
        for (BlockPos pos : solid) {
            if (pos.getY() == midY) {
                midCells.add(pos);
            }
        }
        if (midCells.isEmpty() && midY + 1 < maxY) {
            midY = midY + 1;
            for (BlockPos pos : solid) {
                if (pos.getY() == midY) {
                    midCells.add(pos);
                }
            }
        }
        ctx.assertTrue(!midCells.isEmpty(), "mid-wall band empty");
        for (BlockPos pos : midCells) {
            wallMinX = Math.min(wallMinX, pos.getX());
            wallMaxX = Math.max(wallMaxX, pos.getX());
            wallMinZ = Math.min(wallMinZ, pos.getZ());
            wallMaxZ = Math.max(wallMaxZ, pos.getZ());
        }
        ctx.assertTrue(wallMaxX - wallMinX >= 2, "wall X span too narrow");
        ctx.assertTrue(wallMaxZ - wallMinZ >= 2, "wall Z span too narrow");

        int edgeMinX = 0;
        int edgeMaxX = 0;
        int edgeMinZ = 0;
        int edgeMaxZ = 0;
        int interior = 0;
        for (BlockPos pos : midCells) {
            boolean onMinX = pos.getX() == wallMinX;
            boolean onMaxX = pos.getX() == wallMaxX;
            boolean onMinZ = pos.getZ() == wallMinZ;
            boolean onMaxZ = pos.getZ() == wallMaxZ;
            if (onMinX) {
                edgeMinX++;
            }
            if (onMaxX) {
                edgeMaxX++;
            }
            if (onMinZ) {
                edgeMinZ++;
            }
            if (onMaxZ) {
                edgeMaxZ++;
            }
            if (!onMinX && !onMaxX && !onMinZ && !onMaxZ) {
                interior++;
            }
        }
        ctx.assertTrue(edgeMinX >= 2 && edgeMaxX >= 2 && edgeMinZ >= 2 && edgeMaxZ >= 2,
                "wall edges too sparse at mid band: minX=" + edgeMinX + " maxX=" + edgeMaxX
                        + " minZ=" + edgeMinZ + " maxZ=" + edgeMaxZ);
        int wallFootprint = (wallMaxX - wallMinX + 1) * (wallMaxZ - wallMinZ + 1);
        ctx.assertTrue(midCells.size() < wallFootprint,
                "mid-band fully solid (expected wall shell): solids=" + midCells.size()
                        + " footprint=" + wallFootprint + " interiorSolids=" + interior);

        int cornerHits = 0;
        BlockPos[] corners = {
                new BlockPos(wallMinX, midY, wallMinZ),
                new BlockPos(wallMinX, midY, wallMaxZ),
                new BlockPos(wallMaxX, midY, wallMinZ),
                new BlockPos(wallMaxX, midY, wallMaxZ)
        };
        for (BlockPos corner : corners) {
            if (solid.contains(corner) || solid.contains(corner.up()) || solid.contains(corner.down())) {
                cornerHits++;
            }
        }
        ctx.assertTrue(cornerHits >= 3, "expected ≥3 wall corners connected, got " + cornerHits);
    }

    private static void assertWorldMatches(
            TestContext ctx,
            World world,
            Map<BlockPos, BlockState> expected
    ) {
        for (Map.Entry<BlockPos, BlockState> entry : expected.entrySet()) {
            BlockState actual = world.getBlockState(entry.getKey());
            ctx.assertTrue(
                    Objects.equals(entry.getValue(), actual),
                    "BlockState mismatch at " + entry.getKey()
                            + " expected=" + entry.getValue() + " actual=" + actual
            );
            // Prefer stone_bricks for the mini building material.
            ctx.assertTrue(
                    actual.isOf(Blocks.STONE_BRICKS) || entry.getValue().equals(actual),
                    "unexpected block at " + entry.getKey() + ": " + actual
            );
        }
    }

    private static BlockPos toRelative(TestContext ctx, BlockPos absolute) {
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
        return absolute.subtract(origin);
    }

    private static List<BakeTask.Placement> toBakePlacements(PlacementSubmitPlan.Plan plan) {
        List<BakeTask.Placement> out = new ArrayList<>(plan.size());
        for (PlacementSubmitPlan.Entry entry : plan.entries()) {
            out.add(new BakeTask.Placement(entry.pos(), entry.state()));
        }
        return out;
    }

    private static void ensureRegistry() {
        NodeRegistry registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    private static GraphPresetRules.GraphPresetDefinition requirePreset(String presetId) {
        try (InputStream stream = MiniBuildingWorldBakeGameTest.class.getResourceAsStream(PRESET_RESOURCE)) {
            if (stream == null) {
                throw new AssertionError("Missing " + PRESET_RESOURCE);
            }
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
        NodeRegistry registry = NodeRegistry.getInstance();
        NodeGraph graph = new NodeGraph(Objects.requireNonNullElse(preset.displayName, preset.id));
        Map<String, INode> byRef = new HashMap<>();
        Map<String, UUID> idByRef = new HashMap<>();

        for (GraphPresetRules.PresetNode presetNode : preset.nodes) {
            INode node = registry.createNodeInstance(presetNode.typeId);
            if (node == null) {
                throw new AssertionError("unknown type " + presetNode.typeId);
            }
            if (presetNode.state != null) {
                node.setNodeState(presetNode.state);
            }
            if (!graph.addNode(node)) {
                throw new AssertionError("failed to add " + presetNode.ref);
            }
            byRef.put(presetNode.ref, node);
            idByRef.put(presetNode.ref, node.getId());
        }

        for (GraphPresetRules.PresetConnection connection : preset.connections) {
            UUID fromId = idByRef.get(connection.fromRef);
            UUID toId = idByRef.get(connection.toRef);
            if (!graph.connect(fromId, connection.fromPort, toId, connection.toPort)) {
                throw new AssertionError("FAILED: " + connection.fromRef + "." + connection.fromPort
                        + " -> " + connection.toRef + "." + connection.toPort);
            }
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

    @Override
    public void invokeTestMethod(@NonNull TestContext context, Method method) throws ReflectiveOperationException {
        synchronized (SHARED_SERVICE_LOCK) {
            try {
                method.invoke(this, context);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                com.nodecraft.core.NodeCraft.LOGGER.error(
                        "GameTest {} failed: {}",
                        method.getName(),
                        cause.toString(),
                        cause
                );
                if (cause instanceof RuntimeException runtime) {
                    throw runtime;
                }
                if (cause instanceof Error error) {
                    throw error;
                }
                throw new RuntimeException(cause);
            }
        }
    }

    private static void resetBakeService(BakePlacementService service) {
        service.shutdownFlush();
    }

    private static void drainTasks(BakePlacementService service) {
        for (int i = 0; i < 200 && service.getQueueSize() > 0; i++) {
            service.processTick();
        }
    }
}

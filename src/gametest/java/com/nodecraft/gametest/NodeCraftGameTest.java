package com.nodecraft.gametest;

import com.nodecraft.gui.editor.preview.AutoPreviewController;
import com.nodecraft.nodesystem.bake.BakeHistory;
import com.nodecraft.nodesystem.bake.BakeOperationKind;
import com.nodecraft.nodesystem.bake.BakePlacementService;
import com.nodecraft.nodesystem.bake.BakeTask;
import com.nodecraft.nodesystem.bake.BakeTaskState;
import com.nodecraft.nodesystem.bake.PlacementMode;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.execution.ExecutionProfiler;
import com.nodecraft.nodesystem.execution.NodeExecutor;
import com.nodecraft.nodesystem.execution.runtime.CancellationToken;
import com.nodecraft.nodesystem.execution.runtime.ExecutionMode;
import com.nodecraft.nodesystem.execution.runtime.ExecutionPlan;
import com.nodecraft.nodesystem.execution.runtime.ExecutionSession;
import com.nodecraft.nodesystem.execution.runtime.NodeExecutionScheduler;
import com.nodecraft.nodesystem.execution.runtime.SimpleCancellationToken;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.preview.TrackedPreviewPlacementService;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.PlacementSubmitPlan;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import org.jspecify.annotations.NonNull;

/**
 * Server-side GameTests for preview cleanup and async bake undo/redo integration.
 * <p>
 * {@link TestContext#expectBlock} / {@link TestContext#checkBlockState} take
 * <strong>relative</strong> structure coordinates. World mutations and bake placements
 * must use {@link TestContext#getAbsolutePos(BlockPos)}.
 */
public class NodeCraftGameTest implements CustomTestMethodInvoker {

    /**
     * BakePlacementService / TrackedPreviewPlacementService are process-wide singletons.
     * Fabric may run GameTests in the same batch concurrently; serialize this suite so
     * {@link BakePlacementService#shutdownFlush()} in one test cannot cancel another's queue.
     */
    private static final Object SHARED_SERVICE_LOCK = new Object();

    @GameTest
    public void trackedPreviewCleanupRestoresBlocks(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        TrackedPreviewPlacementService service = TrackedPreviewPlacementService.getInstance();
        ExecutionContext executionContext = ExecutionContext.createEmpty(world);

        BlockPos rel1 = new BlockPos(1, 1, 1);
        BlockPos rel2 = new BlockPos(2, 1, 1);
        BlockPos abs1 = ctx.getAbsolutePos(rel1);
        BlockPos abs2 = ctx.getAbsolutePos(rel2);
        String nodeId = "preview-cleanup-test";

        service.updateTrackedPreview(
            world,
            nodeId,
            List.of(abs1, abs2),
            Blocks.STONE.getDefaultState(),
            PlacementMode.OVERWRITE
        );

        ctx.expectBlock(Blocks.STONE, rel1);
        ctx.expectBlock(Blocks.STONE, rel2);

        int restored = service.clearTrackedPreviewOnWorldThread(world, nodeId, executionContext);
        ctx.assertEquals(2, restored, "restored block count");
        ctx.checkBlockState(rel1, state -> state.isOf(Blocks.AIR), state -> Text.literal("Expected air at pos1"));
        ctx.checkBlockState(rel2, state -> state.isOf(Blocks.AIR), state -> Text.literal("Expected air at pos2"));

        ctx.complete();
    }

    /**
     * Tracked preview must not overwrite external edits made after preview placement.
     * Original=air -> preview=stone -> external=oak_planks -> clear must leave oak_planks.
     */
    @GameTest
    public void trackedPreviewClearSkipsExternallyModifiedBlocks(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        TrackedPreviewPlacementService service = TrackedPreviewPlacementService.getInstance();
        ExecutionContext executionContext = ExecutionContext.createEmpty(world);

        BlockPos relPreview = new BlockPos(1, 1, 1);
        BlockPos relExternal = new BlockPos(2, 1, 1);
        BlockPos absPreview = ctx.getAbsolutePos(relPreview);
        BlockPos absExternal = ctx.getAbsolutePos(relExternal);
        String nodeId = "preview-external-edit-guard";

        service.updateTrackedPreview(
            world,
            nodeId,
            List.of(absPreview, absExternal),
            Blocks.STONE.getDefaultState(),
            PlacementMode.OVERWRITE
        );
        ctx.expectBlock(Blocks.STONE, relPreview);
        ctx.expectBlock(Blocks.STONE, relExternal);

        // External mutation after preview (player / command / other mod).
        world.setBlockState(absExternal, Blocks.OAK_PLANKS.getDefaultState());
        ctx.expectBlock(Blocks.OAK_PLANKS, relExternal);

        int restored = service.clearTrackedPreviewOnWorldThread(world, nodeId, executionContext);
        ctx.assertEquals(1, restored, "only unmodified preview cell should restore");
        ctx.checkBlockState(relPreview, state -> state.isOf(Blocks.AIR), state -> Text.literal("unmodified preview cell restores original"));
        ctx.checkBlockState(
            relExternal,
            state -> state.isOf(Blocks.OAK_PLANKS),
            state -> Text.literal("external edit must survive tracked preview clear")
        );

        ctx.complete();
    }

    @GameTest
    public void trackedPreviewCleanupWithoutContextOnServerThread(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        TrackedPreviewPlacementService service = TrackedPreviewPlacementService.getInstance();

        BlockPos rel = new BlockPos(1, 1, 1);
        BlockPos abs = ctx.getAbsolutePos(rel);
        String nodeId = "preview-direct-cleanup";

        service.updateTrackedPreview(
            world,
            nodeId,
            List.of(abs),
            Blocks.STONE.getDefaultState(),
            PlacementMode.OVERWRITE
        );

        int restored = service.clearTrackedPreview(world, nodeId);
        ctx.assertEquals(1, restored, "restored block count");
        ctx.checkBlockState(rel, state -> state.isOf(Blocks.AIR), state -> Text.literal("Expected air"));

        ctx.complete();
    }

    @GameTest
    public void bakeAsyncUndoRedoCycle(TestContext ctx) {
        World world = ctx.getWorld();
        BakePlacementService service = BakePlacementService.getInstance();
        resetBakeService(service);

        UUID actorId = UUID.randomUUID();
        BakeHistory history = service.getHistory(actorId, world);
        history.clear();

        BlockPos rel = new BlockPos(1, 1, 1);
        BlockPos abs = ctx.getAbsolutePos(rel);

        service.enqueuePlacements(
            world,
            List.of(new BakeTask.Placement(abs, Blocks.STONE.getDefaultState())),
            PlacementMode.OVERWRITE,
            true,
            BakeOperationKind.APPLY,
            1000,
            1_000_000L,
            actorId,
            null
        );
        drainTasks(service);

        ctx.assertEquals(1, history.size(), "history size after apply");
        ctx.assertEquals(0, history.redoSize(), "redo size after apply");
        ctx.expectBlock(Blocks.STONE, rel);

        UUID undoTaskId = service.undoLastAsync(actorId, world, 1000, 1_000_000L);
        ctx.assertTrue(undoTaskId != null, "undo task id");
        drainTasks(service);

        ctx.assertEquals(0, history.size(), "history size after undo");
        ctx.assertEquals(1, history.redoSize(), "redo size after undo");
        ctx.checkBlockState(rel, state -> state.isOf(Blocks.AIR), state -> Text.literal("Expected air after undo"));

        UUID redoTaskId = service.redoLastAsync(actorId, world, 1000, 1_000_000L);
        ctx.assertTrue(redoTaskId != null, "redo task id");
        drainTasks(service);

        ctx.assertEquals(1, history.size(), "history size after redo");
        ctx.assertEquals(0, history.redoSize(), "redo size after redo");
        ctx.expectBlock(Blocks.STONE, rel);

        ctx.complete();
    }

    @GameTest
    public void bakeApplyCancelRollsBackWorld(TestContext ctx) {
        World world = ctx.getWorld();
        BakePlacementService service = BakePlacementService.getInstance();
        resetBakeService(service);

        UUID actorId = UUID.randomUUID();
        BakeHistory history = service.getHistory(actorId, world);
        history.clear();

        BlockPos rel1 = new BlockPos(1, 1, 1);
        BlockPos rel2 = new BlockPos(2, 1, 1);
        BlockPos rel3 = new BlockPos(3, 1, 1);
        BlockPos abs1 = ctx.getAbsolutePos(rel1);
        BlockPos abs2 = ctx.getAbsolutePos(rel2);
        BlockPos abs3 = ctx.getAbsolutePos(rel3);

        UUID taskId = service.enqueuePlacements(
            world,
            List.of(
                new BakeTask.Placement(abs1, Blocks.STONE.getDefaultState()),
                new BakeTask.Placement(abs2, Blocks.STONE.getDefaultState()),
                new BakeTask.Placement(abs3, Blocks.STONE.getDefaultState())
            ),
            PlacementMode.OVERWRITE,
            true,
            BakeOperationKind.APPLY,
            1,
            0L,
            actorId,
            null
        );
        ctx.assertTrue(taskId != null, "apply task id");

        service.processTick();
        ctx.expectBlock(Blocks.STONE, rel1);
        ctx.checkBlockState(rel2, state -> state.isOf(Blocks.AIR), state -> Text.literal("pos2 still air before cancel"));
        ctx.assertEquals(0, history.size(), "history empty while task still running");

        ctx.assertTrue(service.cancelTask(taskId), "cancel apply");
        drainTasks(service);

        ctx.assertEquals(0, history.size(), "cancelled apply must not commit history");
        ctx.assertEquals(0, history.redoSize(), "redo empty after apply rollback");
        ctx.checkBlockState(rel1, state -> state.isOf(Blocks.AIR), state -> Text.literal("rolled back pos1"));
        ctx.checkBlockState(rel2, state -> state.isOf(Blocks.AIR), state -> Text.literal("pos2 stays air"));
        ctx.checkBlockState(rel3, state -> state.isOf(Blocks.AIR), state -> Text.literal("pos3 stays air"));

        BakePlacementService.TaskSnapshot snapshot = service.getTaskSnapshot(taskId);
        ctx.assertTrue(snapshot != null, "snapshot retained");
        if (snapshot != null) {
            ctx.assertEquals(BakeTaskState.CANCELLED, snapshot.state(), "terminal cancel state");
        }

        ctx.complete();
    }

    /**
     * Timeout abort uses the same transaction rollback as cancel, but terminals as {@link BakeTaskState#TIMED_OUT}.
     * Mirrors ApplyChangesNode sync-await timeout: cancelTask(..., TIMED_OUT) then await abort/rollback.
     */
    @GameTest
    public void bakeApplyTimeoutRollsBackWorld(TestContext ctx) {
        World world = ctx.getWorld();
        BakePlacementService service = BakePlacementService.getInstance();
        resetBakeService(service);

        UUID actorId = UUID.randomUUID();
        BakeHistory history = service.getHistory(actorId, world);
        history.clear();

        BlockPos rel1 = new BlockPos(1, 1, 1);
        BlockPos rel2 = new BlockPos(2, 1, 1);
        BlockPos rel3 = new BlockPos(3, 1, 1);
        BlockPos abs1 = ctx.getAbsolutePos(rel1);
        BlockPos abs2 = ctx.getAbsolutePos(rel2);
        BlockPos abs3 = ctx.getAbsolutePos(rel3);

        // Seed so rollback must restore STONE, not only air.
        ctx.setBlockState(rel1, Blocks.STONE.getDefaultState());

        UUID taskId = service.enqueuePlacements(
            world,
            List.of(
                new BakeTask.Placement(abs1, Blocks.GOLD_BLOCK.getDefaultState()),
                new BakeTask.Placement(abs2, Blocks.GOLD_BLOCK.getDefaultState()),
                new BakeTask.Placement(abs3, Blocks.GOLD_BLOCK.getDefaultState())
            ),
            PlacementMode.OVERWRITE,
            true,
            BakeOperationKind.APPLY,
            1,
            0L,
            actorId,
            null
        );
        ctx.assertTrue(taskId != null, "apply task id");

        service.processTick();
        ctx.expectBlock(Blocks.GOLD_BLOCK, rel1);
        ctx.checkBlockState(rel2, state -> state.isOf(Blocks.AIR), state -> Text.literal("pos2 still air before timeout"));
        ctx.assertEquals(0, history.size(), "history empty while task still running");

        ctx.assertTrue(service.cancelTask(taskId, BakeTaskState.TIMED_OUT), "timeout abort");
        boolean aborted = service.awaitTaskAborted(taskId, System.currentTimeMillis() + 5_000L);
        ctx.assertTrue(aborted, "await timed-out abort finished");

        ctx.assertEquals(0, history.size(), "timed-out apply must not commit history");
        ctx.assertEquals(0, history.redoSize(), "redo empty after timeout rollback");
        ctx.expectBlock(Blocks.STONE, rel1);
        ctx.checkBlockState(rel2, state -> state.isOf(Blocks.AIR), state -> Text.literal("pos2 stays air"));
        ctx.checkBlockState(rel3, state -> state.isOf(Blocks.AIR), state -> Text.literal("pos3 stays air"));

        BakePlacementService.TaskSnapshot snapshot = service.getTaskSnapshot(taskId);
        ctx.assertTrue(snapshot != null, "snapshot retained");
        if (snapshot != null) {
            ctx.assertEquals(BakeTaskState.TIMED_OUT, snapshot.state(), "terminal timeout state");
        }
        if (snapshot != null) {
            ctx.assertEquals(0, snapshot.rollbackFailedCount(), "clean timeout rollback");
        }

        ctx.complete();
    }

    @GameTest
    public void bakeApplyCancelUsesTimeSlicedRollback(TestContext ctx) {
        World world = ctx.getWorld();
        BakePlacementService service = BakePlacementService.getInstance();
        resetBakeService(service);

        UUID actorId = UUID.randomUUID();
        BakeHistory history = service.getHistory(actorId, world);
        history.clear();

        BlockPos rel1 = new BlockPos(1, 1, 1);
        BlockPos rel2 = new BlockPos(2, 1, 1);
        BlockPos abs1 = ctx.getAbsolutePos(rel1);
        BlockPos abs2 = ctx.getAbsolutePos(rel2);

        // Seed prior world state so rollback has a non-air previous state to restore.
        // TestContext#setBlockState also expects relative coordinates.
        ctx.setBlockState(rel1, Blocks.STONE.getDefaultState());
        ctx.setBlockState(rel2, Blocks.STONE.getDefaultState());

        UUID taskId = service.enqueuePlacements(
            world,
            List.of(
                new BakeTask.Placement(abs1, Blocks.GOLD_BLOCK.getDefaultState()),
                new BakeTask.Placement(abs2, Blocks.GOLD_BLOCK.getDefaultState())
            ),
            PlacementMode.OVERWRITE,
            true,
            BakeOperationKind.APPLY,
            1,
            0L,
            actorId,
            null
        );
        ctx.assertTrue(taskId != null, "apply task id");

        service.processTick();
        ctx.expectBlock(Blocks.GOLD_BLOCK, rel1);
        ctx.expectBlock(Blocks.STONE, rel2);

        ctx.assertTrue(service.cancelTask(taskId), "cancel mid apply");
        BakePlacementService.TaskSnapshot rolling = service.getTaskSnapshot(taskId);
        ctx.assertTrue(rolling != null, "live snapshot during rollback");
        if (rolling != null) {
            ctx.assertEquals(BakeTaskState.ROLLING_BACK, rolling.state(), "enters ROLLING_BACK before drain");
        }

        drainTasks(service);
        ctx.expectBlock(Blocks.STONE, rel1);
        ctx.expectBlock(Blocks.STONE, rel2);
        ctx.assertEquals(0, history.size(), "no history on aborted apply");
        ctx.assertEquals(BakeTaskState.CANCELLED, Objects.requireNonNull(service.getTaskSnapshot(taskId)).state(), "abort terminal");

        ctx.complete();
    }

    @GameTest
    public void bakeUndoCancelAbortsAndRestoresStack(TestContext ctx) {
        World world = ctx.getWorld();
        BakePlacementService service = BakePlacementService.getInstance();
        resetBakeService(service);

        UUID actorId = UUID.randomUUID();
        BakeHistory history = service.getHistory(actorId, world);
        history.clear();

        BlockPos rel1 = new BlockPos(1, 1, 1);
        BlockPos rel2 = new BlockPos(2, 1, 1);
        BlockPos abs1 = ctx.getAbsolutePos(rel1);
        BlockPos abs2 = ctx.getAbsolutePos(rel2);

        service.enqueuePlacements(
            world,
            List.of(
                new BakeTask.Placement(abs1, Blocks.STONE.getDefaultState()),
                new BakeTask.Placement(abs2, Blocks.STONE.getDefaultState())
            ),
            PlacementMode.OVERWRITE,
            true,
            BakeOperationKind.APPLY,
            1000,
            1_000_000L,
            actorId,
            null
        );
        drainTasks(service);
        ctx.assertEquals(1, history.size(), "history after apply");
        ctx.expectBlock(Blocks.STONE, rel1);
        ctx.expectBlock(Blocks.STONE, rel2);

        UUID undoTaskId = service.undoLastAsync(actorId, world, 1, 0L);
        ctx.assertTrue(undoTaskId != null, "undo task id");
        ctx.assertEquals(0, history.size(), "undo record popped at enqueue");

        service.processTick();

        ctx.assertTrue(service.cancelTask(undoTaskId), "cancel undo");
        drainTasks(service);

        ctx.assertEquals(1, history.size(), "cancelled undo must restore undo stack");
        ctx.assertEquals(0, history.redoSize(), "cancelled undo must not leave redo residue");
        ctx.expectBlock(Blocks.STONE, rel1);
        ctx.expectBlock(Blocks.STONE, rel2);

        BakePlacementService.TaskSnapshot snapshot = service.getTaskSnapshot(undoTaskId);
        ctx.assertTrue(snapshot != null, "undo cancel snapshot retained");
        if (snapshot != null) {
            ctx.assertEquals(BakeTaskState.CANCELLED, snapshot.state(), "undo cancel terminal state");
        }

        ctx.complete();
    }

    /**
     * After last-write-wins dedupe, cancel mid-task still restores pre-transaction world
     * (putIfAbsent), never an intermediate duplicate write.
     */
    @GameTest
    public void bakeDuplicatePosCancelRestoresPreTransactionState(TestContext ctx) {
        World world = ctx.getWorld();
        BakePlacementService service = BakePlacementService.getInstance();
        resetBakeService(service);

        UUID actorId = UUID.randomUUID();
        BakeHistory history = service.getHistory(actorId, world);
        history.clear();

        BlockPos relP = new BlockPos(1, 1, 1);
        BlockPos relQ = new BlockPos(2, 1, 1);
        BlockPos absP = ctx.getAbsolutePos(relP);
        BlockPos absQ = ctx.getAbsolutePos(relQ);

        // Dedupe collapses P:stone+P:gold -> P:gold, then Q. blocksPerTick=1 leaves Q pending.
        UUID taskId = service.enqueuePlacements(
            world,
            List.of(
                new BakeTask.Placement(absP, Blocks.STONE.getDefaultState()),
                new BakeTask.Placement(absP, Blocks.GOLD_BLOCK.getDefaultState()),
                new BakeTask.Placement(absQ, Blocks.STONE.getDefaultState())
            ),
            PlacementMode.OVERWRITE,
            true,
            BakeOperationKind.APPLY,
            1,
            0L,
            actorId,
            null
        );
        ctx.assertTrue(taskId != null, "apply task id");

        service.processTick();
        ctx.expectBlock(Blocks.GOLD_BLOCK, relP);
        ctx.checkBlockState(relQ, state -> state.isOf(Blocks.AIR), state -> Text.literal("Q still pending"));

        ctx.assertTrue(service.cancelTask(taskId), "cancel after first unique cell write");
        drainTasks(service);

        ctx.checkBlockState(relP, state -> state.isOf(Blocks.AIR),
            state -> Text.literal("P must restore to pre-txn AIR, not intermediate STONE"));
        ctx.checkBlockState(relQ, state -> state.isOf(Blocks.AIR), state -> Text.literal("Q stays air"));
        ctx.assertEquals(0, history.size(), "cancelled apply must not commit history");

        ctx.complete();
    }

    /**
     * INCREMENTAL + duplicates: last-write-wins dedupe so air at P becomes gold, not stone
     * (pre-dedupe INCREMENTAL would place stone then skip gold).
     */
    @GameTest
    public void bakeDuplicatePosIncrementalUsesLastWriteWins(TestContext ctx) {
        World world = ctx.getWorld();
        BakePlacementService service = BakePlacementService.getInstance();
        resetBakeService(service);

        UUID actorId = UUID.randomUUID();
        BlockPos relP = new BlockPos(1, 1, 1);
        BlockPos absP = ctx.getAbsolutePos(relP);

        service.enqueuePlacements(
            world,
            List.of(
                new BakeTask.Placement(absP, Blocks.STONE.getDefaultState()),
                new BakeTask.Placement(absP, Blocks.GOLD_BLOCK.getDefaultState())
            ),
            PlacementMode.INCREMENTAL,
            true,
            BakeOperationKind.APPLY,
            1000,
            1_000_000L,
            actorId,
            null
        );
        drainTasks(service);

        ctx.expectBlock(Blocks.GOLD_BLOCK, relP);
        ctx.complete();
    }

    /**
     * Completed apply with duplicate positions commits one history entry per unique pos
     * and undo restores the pre-transaction world.
     */
    @GameTest
    public void bakeDuplicatePosCommitUndoRestoresOriginal(TestContext ctx) {
        World world = ctx.getWorld();
        BakePlacementService service = BakePlacementService.getInstance();
        resetBakeService(service);

        UUID actorId = UUID.randomUUID();
        BakeHistory history = service.getHistory(actorId, world);
        history.clear();

        BlockPos relP = new BlockPos(1, 1, 1);
        BlockPos absP = ctx.getAbsolutePos(relP);

        service.enqueuePlacements(
            world,
            List.of(
                new BakeTask.Placement(absP, Blocks.STONE.getDefaultState()),
                new BakeTask.Placement(absP, Blocks.GOLD_BLOCK.getDefaultState())
            ),
            PlacementMode.OVERWRITE,
            true,
            BakeOperationKind.APPLY,
            1000,
            1_000_000L,
            actorId,
            null
        );
        drainTasks(service);

        ctx.expectBlock(Blocks.GOLD_BLOCK, relP);
        ctx.assertEquals(1, history.size(), "one undo transaction");
        BakeHistory.UndoRecord committed = history.peek();
        ctx.assertTrue(committed != null, "committed record");
        if (committed != null) {
            ctx.assertEquals(1, committed.size(), "one unique position in history");
        }

        UUID undoId = service.undoLastAsync(actorId, world, 1000, 1_000_000L);
        ctx.assertTrue(undoId != null, "undo queued");
        drainTasks(service);

        ctx.checkBlockState(relP, state -> state.isOf(Blocks.AIR),
            state -> Text.literal("undo must restore pre-txn AIR"));
        ctx.assertEquals(0, history.size(), "undo stack empty");
        ctx.assertEquals(1, history.redoSize(), "redo has inverse");

        ctx.complete();
    }

    /**
     * Phase 5A: PlacementSubmitPlan (Apply resolution) matches Bake writes under a live BLOCK registry.
     * Placement inputs take priority over Geometry; duplicate positions last-write-wins in the world.
     */
    @GameTest
    public void placementSubmitPlanMatchesBakeAndPrefersPlacements(TestContext ctx) {
        World world = ctx.getWorld();
        BakePlacementService service = BakePlacementService.getInstance();
        resetBakeService(service);

        BlockPos relGeom = new BlockPos(1, 1, 1);
        BlockPos absGeom = ctx.getAbsolutePos(relGeom);
        BoxGeometryData oneCell = new BoxGeometryData(
                new org.joml.Vector3d(absGeom.getX() + 0.5, absGeom.getY() + 0.5, absGeom.getZ() + 0.5),
                new org.joml.Vector3d(0.5, 0.5, 0.5)
        );

        PlacementSubmitPlan.Plan geomPlan = PlacementSubmitPlan.resolve(
                null, null, null, oneCell, null, null, null, null, "minecraft:stone", true);
        ctx.assertTrue(geomPlan.valid(), "geometry plan valid: " + geomPlan.error());
        ctx.assertEquals(PlacementSubmitPlan.Source.GEOMETRY_VOXELS, geomPlan.source(), "geometry source");
        ctx.assertTrue(geomPlan.size() >= 1, "geometry plan non-empty");

        UUID actorId = UUID.randomUUID();
        List<BakeTask.Placement> geomBake = new ArrayList<>(geomPlan.size());
        for (PlacementSubmitPlan.Entry entry : geomPlan.entries()) {
            geomBake.add(new BakeTask.Placement(entry.pos(), entry.state()));
        }
        service.enqueuePlacements(
                world, geomBake, PlacementMode.OVERWRITE, true,
                BakeOperationKind.APPLY, 1000, 1_000_000L, actorId, null);
        drainTasks(service);
        ctx.expectBlock(Blocks.STONE, relGeom);

        BlockPos relPlace = new BlockPos(2, 1, 1);
        BlockPos absPlace = ctx.getAbsolutePos(relPlace);
        BlockPlacementData placement = new BlockPlacementData(absPlace, "minecraft:oak_planks");
        PlacementSubmitPlan.Plan priorityPlan = PlacementSubmitPlan.resolve(
                List.of(placement), null, null, oneCell, null, null, null, null, "minecraft:stone", true);
        ctx.assertTrue(priorityPlan.valid(), "priority plan valid: " + priorityPlan.error());
        ctx.assertEquals(PlacementSubmitPlan.Source.PLACEMENTS, priorityPlan.source(), "placement source");
        ctx.assertTrue(priorityPlan.geometryIgnoredDueToPlacements(), "geometry ignored when placements present");
        ctx.assertEquals(1, priorityPlan.size(), "only placement entries");

        List<BakeTask.Placement> placeBake = List.of(
                new BakeTask.Placement(priorityPlan.entries().getFirst().pos(), priorityPlan.entries().getFirst().state())
        );
        service.enqueuePlacements(
                world, placeBake, PlacementMode.OVERWRITE, true,
                BakeOperationKind.APPLY, 1000, 1_000_000L, actorId, null);
        drainTasks(service);
        ctx.expectBlock(Blocks.OAK_PLANKS, relPlace);
        // Geometry cell from the first bake must not have been re-submitted by the priority plan.
        ctx.expectBlock(Blocks.STONE, relGeom);

        ctx.complete();
    }

    /**
     * Smoke: AutoPreviewController preview path runs on the server GameTest world without
     * committing bake history, and skips {@code output.execute.*} side effects.
     * <p>
     * Uses a same-thread scheduler so the dedicated server thread never blocks on
     * {@code Future.get()} waiting for a background worker (deadlock risk in GameTest).
     */
    @GameTest
    public void autoPreviewControllerSmokeSkipsExecuteAndLeavesHistoryEmpty(TestContext ctx) {
        World world = ctx.getWorld();
        BakePlacementService bake = BakePlacementService.getInstance();
        resetBakeService(bake);

        UUID actorId = UUID.randomUUID();
        BakeHistory history = bake.getHistory(actorId, world);
        history.clear();

        AtomicLong clock = new AtomicLong(5_000L);
        AtomicLong dirtyVersion = new AtomicLong(0L);

        NodeGraph graph = new NodeGraph("preview-smoke");
        PreviewPassThroughNode source = new PreviewPassThroughNode("src", "value");
        PreviewSideEffectNode sideEffect = new PreviewSideEffectNode();
        graph.addNode(source);
        graph.addNode(sideEffect);
        graph.connect(source.getId(), "out", sideEffect.getId(), "in");

        SyncPreviewScheduler scheduler = new SyncPreviewScheduler();
        AutoPreviewController controller = new AutoPreviewController(
                () -> graph,
                new AutoPreviewController.DirtyVersionSource() {
                    @Override
                    public long getDirtyVersion() {
                        return dirtyVersion.get();
                    }

                    @Override
                    public void markDirty() {
                        dirtyVersion.incrementAndGet();
                    }
                },
                () -> ExecutionContext.createEmpty(world),
                clock::get,
                scheduler
        );

        controller.notifyNodeDirty(source, 1L);
        // Debounce is stamped at notify time; advance clock then submit.
        clock.addAndGet(AutoPreviewController.DEBOUNCE_MS + 1L);
        controller.tick();

        ExecutionSession session = controller.lastSubmittedSession();
        ctx.assertTrue(session != null, "preview session started after debounce");
        ctx.assertTrue(session != null && session.result().isDone(), "sync preview finished on caller thread");
        ctx.assertTrue(session != null && Boolean.TRUE.equals(session.result().getNow(false)),
                "preview session completed successfully");
        ctx.assertEquals(0, sideEffect.executionCount(), "output.execute skipped in preview");
        ctx.assertEquals(0, history.size(), "preview must not commit bake history");

        scheduler.cancelPreview();
        ctx.complete();
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
        // Tests need a clean queue; shutdownFlush request-cancels then drains rollbacks.
        service.shutdownFlush();
    }

    private static void drainTasks(BakePlacementService service) {
        for (int i = 0; i < 100 && service.getQueueSize() > 0; i++) {
            service.processTick();
        }
    }

    /**
     * Runs preview graphs on the calling thread via {@link NodeExecutor#executeSync()}.
     */
    private static final class SyncPreviewScheduler implements NodeExecutionScheduler {
        private volatile ExecutionSession active;

        @Override
        public ExecutionSession submit(
                NodeGraph graph,
                @Nullable ExecutionContext context,
                ExecutionPlan plan,
                long generation
        ) {
            SimpleCancellationToken token = new SimpleCancellationToken();
            NodeExecutor executor = new NodeExecutor(
                    graph,
                    context,
                    plan.scopeNodeIds(),
                    plan.options(),
                    null,
                    token,
                    plan.skipOutputExecuteSideEffects(),
                    null,
                    true
            );
            boolean ok = executor.executeSync();
            CompletableFuture<Boolean> result = CompletableFuture.completedFuture(ok);
            ExecutionSession session = new ExecutionSession() {
                private final UUID id = UUID.randomUUID();

                @Override
                public UUID sessionId() {
                    return id;
                }

                @Override
                public long generation() {
                    return generation;
                }

                @Override
                public ExecutionMode mode() {
                    return plan.mode();
                }

                @Override
                public CancellationToken cancellation() {
                    return token;
                }

                @Override
                public CompletableFuture<Boolean> result() {
                    return result;
                }

                @Override
                public @Nullable ExecutionProfiler.Profile lastProfile() {
                    return executor.getLastExecutionProfile();
                }
            };
            active = session;
            return session;
        }

        @Override
        public void cancelPreview() {
            active = null;
        }

        @Override
        public void cancelManual() {
        }

        @Override
        public Optional<ExecutionSession> activePreview() {
            return Optional.ofNullable(active);
        }

        @Override
        public Optional<ExecutionSession> activeManual() {
            return Optional.empty();
        }
    }

    private static final class PreviewPassThroughNode extends com.nodecraft.nodesystem.core.BaseNode {
        private final Object payload;

        private PreviewPassThroughNode(String suffix, Object payload) {
            super(UUID.randomUUID(), "test.pass." + suffix);
            this.payload = payload;
            addInputPort(new com.nodecraft.nodesystem.core.BasePort(
                    "in", "In", "input", com.nodecraft.nodesystem.api.NodeDataType.ANY, this));
            addOutputPort(new com.nodecraft.nodesystem.core.BasePort(
                    "out", "Out", "output", com.nodecraft.nodesystem.api.NodeDataType.ANY, this));
        }

        @Override
        public void processNode(@org.jetbrains.annotations.Nullable ExecutionContext context) {
            Object incoming = inputValues.get("in");
            outputValues.put("out", incoming != null ? incoming : payload);
        }
    }

    private static final class PreviewSideEffectNode extends com.nodecraft.nodesystem.core.BaseNode {
        private final java.util.concurrent.atomic.AtomicInteger executions =
                new java.util.concurrent.atomic.AtomicInteger();

        private PreviewSideEffectNode() {
            super(UUID.randomUUID(), "output.execute.test_side_effect");
            addInputPort(new com.nodecraft.nodesystem.core.BasePort(
                    "in", "In", "input", com.nodecraft.nodesystem.api.NodeDataType.ANY, this));
            addOutputPort(new com.nodecraft.nodesystem.core.BasePort(
                    "out", "Out", "output", com.nodecraft.nodesystem.api.NodeDataType.ANY, this));
        }

        @Override
        public void processNode(@org.jetbrains.annotations.Nullable ExecutionContext context) {
            executions.incrementAndGet();
            outputValues.put("out", inputValues.get("in"));
        }

        int executionCount() {
            return executions.get();
        }
    }
}

package com.nodecraft.gametest;

import com.nodecraft.nodesystem.bake.BakeHistory;
import com.nodecraft.nodesystem.bake.BakeOperationKind;
import com.nodecraft.nodesystem.bake.BakePlacementService;
import com.nodecraft.nodesystem.bake.BakeTask;
import com.nodecraft.nodesystem.bake.BakeTaskState;
import com.nodecraft.nodesystem.bake.PlacementMode;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.preview.TrackedPreviewPlacementService;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;

/**
 * Server-side GameTests for preview cleanup and async bake undo/redo integration.
 * <p>
 * {@link TestContext#expectBlock} / {@link TestContext#checkBlockState} take
 * <strong>relative</strong> structure coordinates. World mutations and bake placements
 * must use {@link TestContext#getAbsolutePos(BlockPos)}.
 */
public class NodeCraftGameTest implements CustomTestMethodInvoker {

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
        BakeHistory history = service.getHistory(actorId);
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
        BakeHistory history = service.getHistory(actorId);
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
        ctx.assertEquals(BakeTaskState.CANCELLED, snapshot.state(), "terminal cancel state");

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
        BakeHistory history = service.getHistory(actorId);
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
        ctx.assertEquals(BakeTaskState.TIMED_OUT, snapshot.state(), "terminal timeout state");
        ctx.assertEquals(0, snapshot.rollbackFailedCount(), "clean timeout rollback");

        ctx.complete();
    }

    @GameTest
    public void bakeApplyCancelUsesTimeSlicedRollback(TestContext ctx) {
        World world = ctx.getWorld();
        BakePlacementService service = BakePlacementService.getInstance();
        resetBakeService(service);

        UUID actorId = UUID.randomUUID();
        BakeHistory history = service.getHistory(actorId);
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
        ctx.assertEquals(BakeTaskState.ROLLING_BACK, rolling.state(), "enters ROLLING_BACK before drain");

        drainTasks(service);
        ctx.expectBlock(Blocks.STONE, rel1);
        ctx.expectBlock(Blocks.STONE, rel2);
        ctx.assertEquals(0, history.size(), "no history on aborted apply");
        ctx.assertEquals(BakeTaskState.CANCELLED, service.getTaskSnapshot(taskId).state(), "abort terminal");

        ctx.complete();
    }

    @GameTest
    public void bakeUndoCancelAbortsAndRestoresStack(TestContext ctx) {
        World world = ctx.getWorld();
        BakePlacementService service = BakePlacementService.getInstance();
        resetBakeService(service);

        UUID actorId = UUID.randomUUID();
        BakeHistory history = service.getHistory(actorId);
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
        ctx.assertEquals(BakeTaskState.CANCELLED, snapshot.state(), "undo cancel terminal state");

        ctx.complete();
    }

    /**
     * Transaction invariant: repeated writes to the same BlockPos in one BakeTask must
     * rollback to the pre-transaction state (putIfAbsent), not an intermediate write.
     */
    @GameTest
    public void bakeDuplicatePosCancelRestoresPreTransactionState(TestContext ctx) {
        World world = ctx.getWorld();
        BakePlacementService service = BakePlacementService.getInstance();
        resetBakeService(service);

        UUID actorId = UUID.randomUUID();
        BakeHistory history = service.getHistory(actorId);
        history.clear();

        BlockPos relP = new BlockPos(1, 1, 1);
        BlockPos relQ = new BlockPos(2, 1, 1);
        BlockPos absP = ctx.getAbsolutePos(relP);
        BlockPos absQ = ctx.getAbsolutePos(relQ);

        // Keep a third placement so the task is still RUNNING after two writes to P.
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
            2,
            0L,
            actorId,
            null
        );
        ctx.assertTrue(taskId != null, "apply task id");

        service.processTick();
        ctx.expectBlock(Blocks.GOLD_BLOCK, relP);
        ctx.checkBlockState(relQ, state -> state.isOf(Blocks.AIR), state -> Text.literal("Q still pending"));

        ctx.assertTrue(service.cancelTask(taskId), "cancel after duplicate writes to P");
        drainTasks(service);

        ctx.checkBlockState(relP, state -> state.isOf(Blocks.AIR),
            state -> Text.literal("P must restore to pre-txn AIR, not intermediate STONE"));
        ctx.checkBlockState(relQ, state -> state.isOf(Blocks.AIR), state -> Text.literal("Q stays air"));
        ctx.assertEquals(0, history.size(), "cancelled apply must not commit history");

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
        BakeHistory history = service.getHistory(actorId);
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
        ctx.assertEquals(1, committed.size(), "one unique position in history");

        UUID undoId = service.undoLastAsync(actorId, world, 1000, 1_000_000L);
        ctx.assertTrue(undoId != null, "undo queued");
        drainTasks(service);

        ctx.checkBlockState(relP, state -> state.isOf(Blocks.AIR),
            state -> Text.literal("undo must restore pre-txn AIR"));
        ctx.assertEquals(0, history.size(), "undo stack empty");
        ctx.assertEquals(1, history.redoSize(), "redo has inverse");

        ctx.complete();
    }

    @Override
    public void invokeTestMethod(TestContext context, Method method) throws ReflectiveOperationException {
        method.invoke(this, context);
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
}

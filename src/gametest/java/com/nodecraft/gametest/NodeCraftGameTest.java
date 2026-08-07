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
 */
public class NodeCraftGameTest implements CustomTestMethodInvoker {

    @GameTest
    public void trackedPreviewCleanupRestoresBlocks(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        TrackedPreviewPlacementService service = TrackedPreviewPlacementService.getInstance();
        ExecutionContext executionContext = ExecutionContext.createEmpty(world);

        BlockPos pos1 = ctx.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos pos2 = ctx.getAbsolutePos(new BlockPos(2, 1, 1));
        String nodeId = "preview-cleanup-test";

        service.updateTrackedPreview(
            world,
            nodeId,
            List.of(pos1, pos2),
            Blocks.STONE.getDefaultState(),
            PlacementMode.OVERWRITE
        );

        ctx.expectBlock(Blocks.STONE, pos1);
        ctx.expectBlock(Blocks.STONE, pos2);

        int restored = service.clearTrackedPreviewOnWorldThread(world, nodeId, executionContext);
        ctx.assertEquals(2, restored, "restored block count");
        ctx.checkBlockState(pos1, state -> state.isOf(Blocks.AIR), state -> Text.literal("Expected air at pos1"));
        ctx.checkBlockState(pos2, state -> state.isOf(Blocks.AIR), state -> Text.literal("Expected air at pos2"));

        ctx.complete();
    }

    @GameTest
    public void trackedPreviewCleanupWithoutContextOnServerThread(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        TrackedPreviewPlacementService service = TrackedPreviewPlacementService.getInstance();

        BlockPos pos = ctx.getAbsolutePos(new BlockPos(1, 1, 1));
        String nodeId = "preview-direct-cleanup";

        service.updateTrackedPreview(
            world,
            nodeId,
            List.of(pos),
            Blocks.STONE.getDefaultState(),
            PlacementMode.OVERWRITE
        );

        int restored = service.clearTrackedPreview(world, nodeId);
        ctx.assertEquals(1, restored, "restored block count");
        ctx.checkBlockState(pos, state -> state.isOf(Blocks.AIR), state -> Text.literal("Expected air"));

        ctx.complete();
    }

    @GameTest
    public void bakeAsyncUndoRedoCycle(TestContext ctx) {
        World world = ctx.getWorld();
        BakePlacementService service = BakePlacementService.getInstance();
        service.cancelAll();

        UUID actorId = UUID.randomUUID();
        BakeHistory history = service.getHistory(actorId);
        history.clear();

        BlockPos pos = ctx.getAbsolutePos(new BlockPos(1, 1, 1));

        service.enqueuePlacements(
            world,
            List.of(new BakeTask.Placement(pos, Blocks.STONE.getDefaultState())),
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
        ctx.expectBlock(Blocks.STONE, pos);

        UUID undoTaskId = service.undoLastAsync(actorId, world, 1000, 1_000_000L);
        ctx.assertTrue(undoTaskId != null, "undo task id");
        drainTasks(service);

        ctx.assertEquals(0, history.size(), "history size after undo");
        ctx.assertEquals(1, history.redoSize(), "redo size after undo");
        ctx.checkBlockState(pos, state -> state.isOf(Blocks.AIR), state -> Text.literal("Expected air after undo"));

        UUID redoTaskId = service.redoLastAsync(actorId, world, 1000, 1_000_000L);
        ctx.assertTrue(redoTaskId != null, "redo task id");
        drainTasks(service);

        ctx.assertEquals(1, history.size(), "history size after redo");
        ctx.assertEquals(0, history.redoSize(), "redo size after redo");
        ctx.expectBlock(Blocks.STONE, pos);

        ctx.complete();
    }

    @GameTest
    public void bakeApplyCancelRollsBackWorld(TestContext ctx) {
        World world = ctx.getWorld();
        BakePlacementService service = BakePlacementService.getInstance();
        service.cancelAll();

        UUID actorId = UUID.randomUUID();
        BakeHistory history = service.getHistory(actorId);
        history.clear();

        BlockPos pos1 = ctx.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos pos2 = ctx.getAbsolutePos(new BlockPos(2, 1, 1));
        BlockPos pos3 = ctx.getAbsolutePos(new BlockPos(3, 1, 1));

        UUID taskId = service.enqueuePlacements(
            world,
            List.of(
                new BakeTask.Placement(pos1, Blocks.STONE.getDefaultState()),
                new BakeTask.Placement(pos2, Blocks.STONE.getDefaultState()),
                new BakeTask.Placement(pos3, Blocks.STONE.getDefaultState())
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
        ctx.expectBlock(Blocks.STONE, pos1);
        ctx.checkBlockState(pos2, state -> state.isOf(Blocks.AIR), state -> Text.literal("pos2 still air before cancel"));
        ctx.assertEquals(0, history.size(), "history empty while task still running");

        ctx.assertTrue(service.cancelTask(taskId), "cancel apply");
        drainTasks(service);

        ctx.assertEquals(0, history.size(), "cancelled apply must not commit history");
        ctx.assertEquals(0, history.redoSize(), "redo empty after apply rollback");
        ctx.checkBlockState(pos1, state -> state.isOf(Blocks.AIR), state -> Text.literal("rolled back pos1"));
        ctx.checkBlockState(pos2, state -> state.isOf(Blocks.AIR), state -> Text.literal("pos2 stays air"));
        ctx.checkBlockState(pos3, state -> state.isOf(Blocks.AIR), state -> Text.literal("pos3 stays air"));

        BakePlacementService.TaskSnapshot snapshot = service.getTaskSnapshot(taskId);
        ctx.assertTrue(snapshot != null, "snapshot retained");
        ctx.assertEquals(BakeTaskState.CANCELLED, snapshot.state(), "terminal cancel state");

        ctx.complete();
    }

    @GameTest
    public void bakeApplyCancelUsesTimeSlicedRollback(TestContext ctx) {
        World world = ctx.getWorld();
        BakePlacementService service = BakePlacementService.getInstance();
        service.cancelAll();

        UUID actorId = UUID.randomUUID();
        BakeHistory history = service.getHistory(actorId);
        history.clear();

        BlockPos pos1 = ctx.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos pos2 = ctx.getAbsolutePos(new BlockPos(2, 1, 1));

        // Seed prior world state so rollback has a non-air previous state to restore.
        world.setBlockState(pos1, Blocks.STONE.getDefaultState());
        world.setBlockState(pos2, Blocks.STONE.getDefaultState());

        UUID taskId = service.enqueuePlacements(
            world,
            List.of(
                new BakeTask.Placement(pos1, Blocks.GOLD_BLOCK.getDefaultState()),
                new BakeTask.Placement(pos2, Blocks.GOLD_BLOCK.getDefaultState())
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
        ctx.expectBlock(Blocks.GOLD_BLOCK, pos1);
        ctx.expectBlock(Blocks.STONE, pos2);

        ctx.assertTrue(service.cancelTask(taskId), "cancel mid apply");
        BakePlacementService.TaskSnapshot rolling = service.getTaskSnapshot(taskId);
        ctx.assertTrue(rolling != null, "live snapshot during rollback");
        ctx.assertEquals(BakeTaskState.ROLLING_BACK, rolling.state(), "enters ROLLING_BACK before drain");

        drainTasks(service);
        ctx.expectBlock(Blocks.STONE, pos1);
        ctx.expectBlock(Blocks.STONE, pos2);
        ctx.assertEquals(0, history.size(), "no history on aborted apply");
        ctx.assertEquals(BakeTaskState.CANCELLED, service.getTaskSnapshot(taskId).state(), "abort terminal");

        ctx.complete();
    }

    @GameTest
    public void bakeUndoCancelAbortsAndRestoresStack(TestContext ctx) {
        World world = ctx.getWorld();
        BakePlacementService service = BakePlacementService.getInstance();
        service.cancelAll();

        UUID actorId = UUID.randomUUID();
        BakeHistory history = service.getHistory(actorId);
        history.clear();

        BlockPos pos1 = ctx.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos pos2 = ctx.getAbsolutePos(new BlockPos(2, 1, 1));

        service.enqueuePlacements(
            world,
            List.of(
                new BakeTask.Placement(pos1, Blocks.STONE.getDefaultState()),
                new BakeTask.Placement(pos2, Blocks.STONE.getDefaultState())
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
        ctx.expectBlock(Blocks.STONE, pos1);
        ctx.expectBlock(Blocks.STONE, pos2);

        UUID undoTaskId = service.undoLastAsync(actorId, world, 1, 0L);
        ctx.assertTrue(undoTaskId != null, "undo task id");
        ctx.assertEquals(0, history.size(), "undo record popped at enqueue");

        service.processTick();

        ctx.assertTrue(service.cancelTask(undoTaskId), "cancel undo");
        drainTasks(service);

        ctx.assertEquals(1, history.size(), "cancelled undo must restore undo stack");
        ctx.assertEquals(0, history.redoSize(), "cancelled undo must not leave redo residue");
        ctx.expectBlock(Blocks.STONE, pos1);
        ctx.expectBlock(Blocks.STONE, pos2);

        BakePlacementService.TaskSnapshot snapshot = service.getTaskSnapshot(undoTaskId);
        ctx.assertTrue(snapshot != null, "undo cancel snapshot retained");
        ctx.assertEquals(BakeTaskState.CANCELLED, snapshot.state(), "undo cancel terminal state");

        ctx.complete();
    }

    @Override
    public void invokeTestMethod(TestContext context, Method method) throws ReflectiveOperationException {
        method.invoke(this, context);
    }

    private static void drainTasks(BakePlacementService service) {
        for (int i = 0; i < 100 && service.getQueueSize() > 0; i++) {
            service.processTick();
        }
    }
}

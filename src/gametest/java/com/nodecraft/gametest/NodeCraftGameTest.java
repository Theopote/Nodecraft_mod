package com.nodecraft.gametest;

import com.nodecraft.nodesystem.bake.BakeHistory;
import com.nodecraft.nodesystem.bake.BakeOperationKind;
import com.nodecraft.nodesystem.bake.BakePlacementService;
import com.nodecraft.nodesystem.bake.BakeTask;
import com.nodecraft.nodesystem.bake.PlacementMode;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.preview.TrackedPreviewPlacementService;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
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

    @GameTest(templateName = "empty_5x5x5")
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

        ctx.assertBlockState(pos1, state -> state.isOf(Blocks.STONE));
        ctx.assertBlockState(pos2, state -> state.isOf(Blocks.STONE));

        int restored = service.clearTrackedPreviewOnWorldThread(world, nodeId, executionContext);
        ctx.assertValueEqual(restored, 2);
        ctx.assertBlockState(pos1, state -> state.isOf(Blocks.AIR));
        ctx.assertBlockState(pos2, state -> state.isOf(Blocks.AIR));

        ctx.complete();
    }

    @GameTest(templateName = "empty_5x5x5")
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
        ctx.assertValueEqual(restored, 1);
        ctx.assertBlockState(pos, state -> state.isOf(Blocks.AIR));

        ctx.complete();
    }

    @GameTest(templateName = "empty_5x5x5")
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

        ctx.assertValueEqual(history.size(), 1);
        ctx.assertValueEqual(history.redoSize(), 0);
        ctx.assertBlockState(pos, state -> state.isOf(Blocks.STONE));

        UUID undoTaskId = service.undoLastAsync(actorId, world, 1000, 1_000_000L);
        ctx.assertValueNonNull(undoTaskId);
        drainTasks(service);

        ctx.assertValueEqual(history.size(), 0);
        ctx.assertValueEqual(history.redoSize(), 1);
        ctx.assertBlockState(pos, state -> state.isOf(Blocks.AIR));

        UUID redoTaskId = service.redoLastAsync(actorId, world, 1000, 1_000_000L);
        ctx.assertValueNonNull(redoTaskId);
        drainTasks(service);

        ctx.assertValueEqual(history.size(), 1);
        ctx.assertValueEqual(history.redoSize(), 0);
        ctx.assertBlockState(pos, state -> state.isOf(Blocks.STONE));

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
